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

if [[ "$BREAKING_COUNT" -gt 0 ]]; then
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

  BREAKING_CHANGES_TEXT="$(jq -r '
    group_by(.path + " " + .operation)
    | map(
        "• " + .[0].operation + " " + .[0].path + "\n"
        + (map("  - " + .text) | join("\n"))
      )
    | join("\n\n")
  ' <<< "$TRANSLATED_BREAKING_JSON")"
  # Discord embed field value 상한(1024자) 방어 — 초과 시 자르고 안내 추가
  if [[ "${#BREAKING_CHANGES_TEXT}" -gt 1000 ]]; then
    BREAKING_CHANGES_TEXT="${BREAKING_CHANGES_TEXT:0:1000}...\n(전체 목록은 GitHub Actions 로그 참고)"
  fi

  BREAKING_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg changes "$BREAKING_CHANGES_TEXT" \
    --arg reason "$REASON" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "🚨 API Breaking Change",
      url: $url,
      color: 15158332,
      fields: [
        { name: "발견된 변경", value: $changes, inline: false },
        { name: "왜 변경했는가", value: $reason, inline: false },
        { name: "프론트 작업", value: "1. orval 재생성  2. 타입 오류 확인  3. 영향받는 API 수정", inline: false }
      ],
      footer: { text: $footer }
    }')"
  EMBEDS_JSON="$(jq --argjson e "$BREAKING_EMBED" '. + [$e]' <<< "$EMBEDS_JSON")"
fi

if [[ "$ADDITIONS_COUNT" -gt 0 ]]; then
  TRANSLATED_ADDITIONS_JSON="$(jq '
    def translate:
      (.text | capture("request property `(?<p>[^`]+)`")) as $c
      | "요청 속성 `" + $c.p + "` 추가됨(optional) — 안 보내도 기존 동작 그대로, 필요 시 프론트에서 함께 반영";
    map(.text = translate)
  ' "$ADDITIONS_JSON")"

  ADDITIONS_TEXT="$(jq -r '
    group_by(.path + " " + .operation)
    | map(
        "• " + .[0].operation + " " + .[0].path + "\n"
        + (map("  - " + .text) | join("\n"))
      )
    | join("\n\n")
  ' <<< "$TRANSLATED_ADDITIONS_JSON")"
  if [[ "${#ADDITIONS_TEXT}" -gt 1000 ]]; then
    ADDITIONS_TEXT="${ADDITIONS_TEXT:0:1000}...\n(전체 목록은 GitHub Actions 로그 참고)"
  fi

  ADDITIONS_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg changes "$ADDITIONS_TEXT" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "ℹ️ API 필드 추가 (non-breaking)",
      url: $url,
      color: 3447003,
      fields: [
        { name: "추가된 optional 필드", value: $changes, inline: false },
        { name: "프론트 작업", value: "기존 클라이언트는 영향 없음 — 새 기능에서 이 필드를 쓰려면 orval 재생성 후 반영", inline: false }
      ],
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
