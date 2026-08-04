#!/usr/bin/env bash
set -euo pipefail

# oasdiff로 base/revised OpenAPI 스펙을 비교해 breaking change·non-breaking 필드 추가가 있으면
# Discord #frontend 웹훅으로 알림을 보낸다. breaking change가 있으면 exit 1(CI 실패로 눈에 띄게),
# non-breaking 필드 추가만 있으면 exit 0(정보 알림만, CI는 통과). 둘 다 없으면 조용히 통과한다.
# 필요 도구: oasdiff, jq, git, curl (CI에서 사전 설치)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_SPEC="${BASE_SPEC:?BASE_SPEC (base openapi.json 경로) is required}"
REVISED_SPEC="${REVISED_SPEC:?REVISED_SPEC (revised openapi.json 경로) is required}"
DISCORD_WEBHOOK_URL="${DISCORD_WEBHOOK_URL:?DISCORD_WEBHOOK_URL secret is required}"
GIT_RANGE="${GIT_RANGE:?GIT_RANGE (예: origin/main..HEAD) is required}"
GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-TripFit-server}"
GITHUB_SHA="${GITHUB_SHA:?GITHUB_SHA is required}"
GITHUB_SERVER_URL="${GITHUB_SERVER_URL:-https://github.com}"
PR_NUMBER="${PR_NUMBER:-}"
DISCORD_BOT_USERNAME="${DISCORD_BOT_USERNAME:-TripFit CI}"
DISCORD_BOT_AVATAR_URL="${DISCORD_BOT_AVATAR_URL:-https://github.com/${GITHUB_REPOSITORY%%/*}.png}"

log() {
  printf '[notify-api-breaking-change] %s\n' "$*" >&2
}

BREAKING_JSON="$(mktemp)"
ADDITIONS_JSON="$(mktemp)"
trap 'rm -f "$BREAKING_JSON" "$ADDITIONS_JSON"' EXIT

oasdiff breaking "$BASE_SPEC" "$REVISED_SPEC" --format json > "$BREAKING_JSON"

# non-breaking이지만 프론트가 알아야 하는 요청 필드 추가 — oasdiff changelog(INFO 레벨)에서
# new-optional-request-property(optional 요청 속성 추가)만 골라낸다. breaking 쪽과 중복되지 않음
# (필수화·제거는 이미 breaking 목록에 잡힘).
oasdiff changelog "$BASE_SPEC" "$REVISED_SPEC" --format json --level INFO \
  | jq '[.[] | select(.id == "new-optional-request-property")]' > "$ADDITIONS_JSON"

BREAKING_COUNT="$(jq 'length' "$BREAKING_JSON")"
ADDITIONS_COUNT="$(jq 'length' "$ADDITIONS_JSON")"

if [[ "$BREAKING_COUNT" -eq 0 && "$ADDITIONS_COUNT" -eq 0 ]]; then
  log "breaking change·필드 추가 없음 — 통과"
  exit 0
fi

log "breaking change ${BREAKING_COUNT}건, 요청 필드 추가 ${ADDITIONS_COUNT}건 감지 — Discord 알림 발송"

REPO_URL="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}"
SHORT_SHA="${GITHUB_SHA:0:7}"

# 제목 클릭 시 이동할 링크 — PR이 있으면 PR로, 없으면(push) 커밋으로
if [[ -n "$PR_NUMBER" ]]; then
  TITLE_URL="${REPO_URL}/pull/${PR_NUMBER}"
else
  TITLE_URL="${REPO_URL}/commit/${GITHUB_SHA}"
fi

# 해당 범위의 커밋 short SHA를 전부 나열 — 여러 커밋이면 쉼표로 이어붙임. GIT_RANGE에 커밋이 없으면(edge case) 트리거 커밋만
# paste -sd에 ", "(2글자)를 그대로 주면 구분자를 한 글자씩 번갈아 써서(콤마→스페이스→콤마…) 깨지므로, 콤마 하나로 합친 뒤 별도로 공백을 넣는다
COMMIT_SHAS="$(git log "$GIT_RANGE" --format=%h | paste -sd, - | sed -E 's/,/, /g' || true)"
if [[ -z "$COMMIT_SHAS" ]]; then
  COMMIT_SHAS="$SHORT_SHA"
fi
FOOTER_TEXT="Commit ID: ${COMMIT_SHAS}"

EMBEDS_JSON='[]'

# 경로 → 도메인 라벨. 패키지 구조(auth/trip/trip.member/user/user.googlecalendar/user.schedule) 기준.
# 더 구체적인 패턴(google-calendar, trips/{id}/members, users/schedule)을 먼저 검사해야
# 상위 패턴(trips, users)에 잘못 먼저 매칭되지 않는다.
read -r -d '' JQ_DOMAIN_DEF << 'EOF' || true
def domain($p):
  if ($p | test("^/api/v1/auth")) then "Auth"
  elif ($p | test("^/api/v1/users/google-calendar")) then "Google Calendar"
  elif ($p | test("^/api/v1/trips/[^/]+/members")) then "Trip Members"
  elif ($p | test("^/api/v1/trips")) then "Trip"
  elif ($p | test("^/api/v1/users/schedule")) then "User Schedule"
  elif ($p | test("^/api/v1/users")) then "User"
  else "기타"
  end;
EOF

# Release Gate #65 대상 엔드포인트 — 로그인·탈퇴는 앱 스토어 심사(Apple S2S #5·OAuth 콘솔 #62·
# 탈퇴 시 provider revoke #64)와 직결돼 프론트와 사전 논의가 필요하다. 도메인 태그(Auth/User)만으론
# 이 둘을 다른 auth/user 변경과 구분하지 못하므로 path+method로 별도 표시한다.
read -r -d '' JQ_GATE_DEF << 'EOF' || true
def is_gate_critical($p; $op):
  ($p == "/api/v1/auth/login") or
  ($p == "/api/v1/users/me" and ($op | ascii_upcase) == "DELETE");
EOF

# 소셜 로그인/탈퇴 변경 텍스트에서 어떤 provider(GOOGLE/KAKAO/APPLE)와 관련 있는지 뽑아
# Discord에서 "이거 카카오만 영향 있나 전체 다 영향 있나"를 필드명만으론 알 수 없는 문제를 보완한다.
gate_callout_field() {
  local translated_json="$1" label="$2"
  local gate_json gate_count providers
  gate_json="$(jq "$JQ_DOMAIN_DEF"$'\n'"$JQ_GATE_DEF"'
    [.[] | select(is_gate_critical(.path; .operation))]
  ' <<< "$translated_json")"
  gate_count="$(jq 'length' <<< "$gate_json")"
  if [[ "$gate_count" -eq 0 ]]; then
    echo '[]'
    return
  fi
  providers="$(jq -r '[.[].text] | join(" ") | [scan("GOOGLE|KAKAO|APPLE")] | unique | join(", ")' <<< "$gate_json")"
  if [[ -z "$providers" ]]; then
    providers="특정 provider 한정 아님(전체 영향)"
  fi
  jq -n --arg providers "$providers" --arg label "$label" '[{
    name: "⚠️ Release Gate #65 관련 — 프론트 사전 논의 필요",
    value: ("로그인/탈퇴 API " + $label + " — 앱 스토어 심사에 영향, 병합 전 프론트와 논의 권장.\n관련 provider: " + $providers + "\n관련 이슈: #5(Apple S2S webhook) · #62(OAuth 콘솔 설정) · #64(탈퇴 시 provider revoke)"),
    inline: false
  }]'
}

if [[ "$BREAKING_COUNT" -gt 0 ]]; then
  BREAKING_DOMAINS="$(jq -r "$JQ_DOMAIN_DEF"'
    [.[] | domain(.path)] | unique | join(", ")
  ' "$BREAKING_JSON")"

  # 커밋 트레일러에서 사유 추출 — 여러 커밋에 있으면 모두 이어붙임. 없으면 안내문(하드코딩 값 아님)
  REASON="$(git log "$GIT_RANGE" --pretty=format:%B \
    | grep -i '^Breaking-Change-Reason:' \
    | sed -E 's/^[Bb]reaking-[Cc]hange-[Rr]eason:[[:space:]]*//' \
    | awk '!seen[$0]++' \
    | paste -sd $'\n' - || true)"
  if [[ -z "$REASON" ]]; then
    REASON="⚠️ 사유 미기재 — 커밋 메시지에 \`Breaking-Change-Reason:\` 트레일러를 추가해 주세요."
  fi

  # oasdiff의 영어 breaking-change 문구를 id 기준으로 한글 템플릿에 매핑 — 매핑 안 된 id는 원문 영어 그대로(추측 번역 안 함)
  TRANSLATED_BREAKING_JSON="$(jq '
    def translate:
      try (
        if .id == "request-property-removed" then
          "요청 속성 `" + (.text | capture("`(?<p>[^`]+)`")).p + "` 제거됨 — 더 이상 보내지 않아도 됨"
        elif .id == "response-property-removed" then
          "응답 속성 `" + (.text | capture("`(?<p>[^`]+)`")).p + "` 제거됨 — 더 이상 응답에 포함되지 않음"
        elif .id == "request-property-became-required" then
          "요청 속성 `" + (.text | capture("`(?<p>[^`]+)`")).p + "` 필수값으로 변경 — 요청 시 반드시 포함해야 함"
        elif .id == "request-property-enum-value-removed" then
          (.text | capture("enum value `(?<v>[^`]+)` of the request property `(?<p>[^`]+)`")) as $c
          | "요청 속성 `" + $c.p + "`의 enum 값 `" + $c.v + "` 제거됨 — 더 이상 이 값을 보낼 수 없음"
        elif .id == "api-path-removed-without-deprecation" then
          "API 경로가 deprecated 처리 없이 삭제됨 — 더 이상 호출 불가"
        else
          .text
        end
      ) catch .text;
    map(.text = translate)
  ' "$BREAKING_JSON")"

  # 엔드포인트 1개 = 필드 1개로 분리 — Discord embed에서 필드명만 굵게 렌더링되므로,
  # 한 필드에 전부 몰아넣으면 계층 없이 다 같은 크기로 보임(가독성 저하). 필드명을 소제목처럼 씀.
  # Discord embed 필드 최대 25개 — breaking 쪽에 왜/체크리스트 필드 2개를 더 쓰므로 넉넉히 20개로 캡.
  BREAKING_FIELDS_JSON="$(jq "$JQ_DOMAIN_DEF"'
    group_by(.path + " " + .operation)
    | .[:20]
    | map({
        name: ("[" + domain(.[0].path) + "] " + .[0].operation + " " + .[0].path),
        value: (map("• " + .text) | join("\n") | if length > 1000 then .[0:1000] + "…" else . end),
        inline: false
      })
  ' <<< "$TRANSLATED_BREAKING_JSON")"

  BREAKING_GATE_FIELDS_JSON="$(gate_callout_field "$TRANSLATED_BREAKING_JSON" "변경")"

  BREAKING_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg domains "$BREAKING_DOMAINS" \
    --argjson gateFields "$BREAKING_GATE_FIELDS_JSON" \
    --argjson fields "$BREAKING_FIELDS_JSON" \
    --arg reason "$REASON" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "🚨 API Breaking Change",
      url: $url,
      color: 15158332,
      fields: ([{ name: "영향 도메인", value: $domains, inline: false }] + $gateFields + $fields + [
        { name: "왜 변경했는가", value: $reason, inline: false },
        { name: "프론트 작업", value: "1. orval 재생성  2. 타입 오류 확인  3. 영향받는 API 수정", inline: false }
      ]),
      footer: { text: $footer }
    }')"
  EMBEDS_JSON="$(jq --argjson e "$BREAKING_EMBED" '. + [$e]' <<< "$EMBEDS_JSON")"
fi

if [[ "$ADDITIONS_COUNT" -gt 0 ]]; then
  ADDITIONS_DOMAINS="$(jq -r "$JQ_DOMAIN_DEF"'
    [.[] | domain(.path)] | unique | join(", ")
  ' "$ADDITIONS_JSON")"

  # 필드명만으로는 "왜"가 안 보임(#64 사고) — 같은 필드의 @Schema(description)를 REVISED_SPEC에서
  # 찾아 그대로 붙인다. requestBody가 $ref면 컴포넌트까지 따라가서 properties[prop].description을 조회.
  # description이 없으면(작성 규칙 위반) 안내 문구로 대체 — 조용히 생략하지 않음.
  TRANSLATED_ADDITIONS_JSON="$(jq --slurpfile spec "$REVISED_SPEC" '
    def schema_desc(path; op; prop):
      ($spec[0].paths[path][(op | ascii_downcase)].requestBody.content["application/json"].schema["$ref"] // "") as $ref
      | if $ref == "" then null
        else ($ref | sub("#/components/schemas/"; "")) as $comp
        | $spec[0].components.schemas[$comp].properties[prop].description // null
        end;
    def translate:
      (.text | capture("request property `(?<p>[^`]+)`")) as $c
      | (schema_desc(.path; .operation; $c.p)) as $d
      | "요청 속성 `" + $c.p + "` 추가됨(optional) — "
        + ( $d // "⚠️ @Schema(description) 없음 — 왜 추가했는지 필드에 적어주세요" )
        + " (안 보내도 기존 동작 그대로)";
    map(.text = translate)
  ' "$ADDITIONS_JSON")"

  ADDITIONS_FIELDS_JSON="$(jq "$JQ_DOMAIN_DEF"'
    group_by(.path + " " + .operation)
    | .[:22]
    | map({
        name: ("[" + domain(.[0].path) + "] " + .[0].operation + " " + .[0].path),
        value: (map("• " + .text) | join("\n") | if length > 1000 then .[0:1000] + "…" else . end),
        inline: false
      })
  ' <<< "$TRANSLATED_ADDITIONS_JSON")"

  ADDITIONS_GATE_FIELDS_JSON="$(gate_callout_field "$TRANSLATED_ADDITIONS_JSON" "필드 추가")"

  ADDITIONS_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg domains "$ADDITIONS_DOMAINS" \
    --argjson gateFields "$ADDITIONS_GATE_FIELDS_JSON" \
    --argjson fields "$ADDITIONS_FIELDS_JSON" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "ℹ️ API 필드 추가 (non-breaking)",
      url: $url,
      color: 3447003,
      fields: ([{ name: "영향 도메인", value: $domains, inline: false }] + $gateFields + $fields + [
        { name: "프론트 작업", value: "기존 클라이언트는 영향 없음 — 새 기능에서 이 필드를 쓰려면 orval 재생성 후 반영", inline: false }
      ]),
      footer: { text: $footer }
    }')"
  EMBEDS_JSON="$(jq --argjson e "$ADDITIONS_EMBED" '. + [$e]' <<< "$EMBEDS_JSON")"
fi

PAYLOAD="$(jq -n \
  --arg username "$DISCORD_BOT_USERNAME" \
  --arg avatar "$DISCORD_BOT_AVATAR_URL" \
  --argjson embeds "$EMBEDS_JSON" \
  '{
    username: $username,
    avatar_url: $avatar,
    embeds: $embeds
  }')"

if [[ "${DRY_RUN:-false}" == "true" ]]; then
  log "DRY_RUN=true — Discord로 보내지 않고 payload만 출력"
  echo "$PAYLOAD" | jq .
else
  curl -fsS -X POST -H "Content-Type: application/json" -d "$PAYLOAD" "$DISCORD_WEBHOOK_URL" > /dev/null
fi

if [[ "$BREAKING_COUNT" -gt 0 ]]; then
  exit 1
fi
exit 0
