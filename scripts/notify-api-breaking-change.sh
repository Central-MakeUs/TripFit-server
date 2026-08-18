#!/usr/bin/env bash
set -euo pipefail

# oasdiff로 base/revised OpenAPI 스펙을 비교해 breaking change·non-breaking 필드 추가가 있으면
# Discord #frontend 웹훅으로 알림을 보낸다. breaking change·필드 추가 어느 쪽이든 알림만 보내고
# job은 항상 통과시킨다(CI를 실패로 표시하지 않음). 둘 다 없으면 조용히 통과한다.
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

# non-breaking이지만 프론트가 알아야 하는 필드 추가 — oasdiff changelog(INFO 레벨)에서
# new-optional-request-property(optional 요청 속성 추가) · response-optional-property-added
# (optional 응답 속성 추가) 둘 다 골라낸다. breaking 쪽과 중복되지 않음(필수화·제거는 이미 breaking 목록에 잡힘).
oasdiff changelog "$BASE_SPEC" "$REVISED_SPEC" --format json --level INFO \
  | jq '[.[] | select(.id == "new-optional-request-property" or .id == "response-optional-property-added")]' > "$ADDITIONS_JSON"

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

  # 커밋 트레일러에서 사유 추출 — 값이 다음 줄로 wrap된 경우(들여쓰기 없는 연속 줄)까지 한 사유로 접어서
  # 합친다. blank 줄이나 다른 트레일러 형식(`Key: value`) 줄을 만나면 그 사유를 종료. 여러 커밋에 있으면
  # 커밋별로 나열(짧은 SHA 접두 — 어느 커밋 사유인지 구분되게). 하나도 없으면 안내문(하드코딩 값 아님)
  # 커밋 1개씩 순회(NUL 구분 다중 레코드 방식은 BSD awk에서 NUL 바이트를 제대로 못 다뤄 회피)
  REASON="$( { while IFS= read -r sha; do
    body="$(git log -1 --format=%B "$sha")"
    awk -v sha="$sha" '
      /^[Bb]reaking-[Cc]hange-[Rr]eason:/ {
        capturing = 1
        sub(/^[Bb]reaking-[Cc]hange-[Rr]eason:[[:space:]]*/, "")
        buf = $0
        next
      }
      capturing && /^[[:space:]]*$/ { print sha ": " buf; capturing = 0; buf = ""; next }
      capturing && /^[A-Za-z][A-Za-z-]*:[[:space:]]/ { print sha ": " buf; capturing = 0; buf = ""; next }
      capturing { buf = buf " " $0; next }
      END { if (capturing && buf != "") print sha ": " buf }
    ' <<< "$body"
  done < <(git log "$GIT_RANGE" --format=%h); } | awk '!seen[$0]++' | paste -sd $'\n' - || true)"
  if [[ -z "$REASON" ]]; then
    REASON="⚠️ 사유 미기재 — 커밋 메시지에 \`Breaking-Change-Reason:\` 트레일러를 추가해 주세요."
  fi

  # oasdiff 원문(영어) 그대로 노출 — 한글 템플릿 매핑은 id 커버리지(oasdiff breaking check 80개+)를
  # 유지보수할 수 없고, 매핑 안 된 id만 영어로 남아 한/영이 뒤섞이는 문제가 있어 제거함(2026-07-29 amend)
  TRANSLATED_BREAKING_JSON="$(jq '.' "$BREAKING_JSON")"

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
  # 찾아 그대로 붙인다. oasdiff는 중첩 경로를 "items/items/slots"처럼 슬래시로 이어붙이는데, 배열
  # 프로퍼티 이름이 우연히 "items"이면 실제 프로퍼티명과 배열 순회 마커를 문자열만으론 구분 못 한다
  # (schema.type이 array인지로 구분). requestBody·response 스키마가 $ref면 컴포넌트까지 따라가며
  # 각 단계 properties[token].description을 조회 — description이 없으면(작성 규칙 위반) 안내 문구로
  # 대체. 요청 속성(new-optional-request-property)과 응답 속성(response-optional-property-added)은
  # oasdiff 문구 패턴·스키마 진입점(requestBody vs responses[code])이 달라 id별로 분기한다.
  # 응답 쪽 content-type 키는 요청과 달리 springdoc이 produces를 명시하지 않아 "*/*"로 나오므로
  # "application/json"을 하드코딩하지 않고 첫 content-type 항목을 그대로 쓴다.
  TRANSLATED_ADDITIONS_JSON="$(jq --slurpfile spec "$REVISED_SPEC" '
    def resolve_ref:
      if type == "object" and has("$ref") then
        $spec[0].components.schemas[(.["$ref"] | sub("#/components/schemas/"; ""))]
      else . end;
    def schema_desc(propPath; $root):
      (propPath | split("/")) as $tokens
      | reduce $tokens[] as $tok
          ({schema: $root, desc: null};
            if (.schema.type // "") == "array" then
              {schema: ((.schema.items // {}) | resolve_ref), desc: .desc}
            else
              (.schema.properties[$tok] // {}) as $propSchema
              | {schema: ($propSchema | resolve_ref), desc: ($propSchema.description // null)}
            end
          )
      | .desc;
    def translate:
      if .id == "response-optional-property-added" then
        (.text | capture("property `(?<p>[^`]+)` to the response with the `(?<code>[^`]+)` status")) as $c
        | ($spec[0].paths[.path][(.operation | ascii_downcase)].responses[$c.code].content
            | to_entries[0].value.schema | resolve_ref) as $root
        | (schema_desc($c.p; $root)) as $d
        | "응답 속성 `" + $c.p + "` 추가됨(optional) — "
          + ( $d // "⚠️ @Schema(description) 없음 — 왜 추가했는지 필드에 적어주세요" )
          + " (기존 클라이언트는 무시하면 됨)"
      else
        (.text | capture("request property `(?<p>[^`]+)`")) as $c
        | ($spec[0].paths[.path][(.operation | ascii_downcase)].requestBody.content["application/json"].schema
            | resolve_ref) as $root
        | (schema_desc($c.p; $root)) as $d
        | "요청 속성 `" + $c.p + "` 추가됨(optional) — "
          + ( $d // "⚠️ @Schema(description) 없음 — 왜 추가했는지 필드에 적어주세요" )
          + " (안 보내도 기존 동작 그대로)"
      end;
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

exit 0
