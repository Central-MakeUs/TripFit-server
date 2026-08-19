#!/usr/bin/env bash
set -euo pipefail

# oasdiff로 base/revised OpenAPI 스펙을 비교해 breaking change·non-breaking 필드 추가가 있으면
# Discord #frontend 웹훅으로 알림을 보낸다. breaking change·필드 추가 어느 쪽이든 알림만 보내고
# job은 항상 통과시킨다(CI를 실패로 표시하지 않음). 둘 다 없으면 조용히 통과한다.
# 필요 도구: oasdiff, jq, git, curl, perl (CI에서 사전 설치)

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

# 커밋 트레일러에서 사유 추출 — 값이 다음 줄로 wrap된 경우(들여쓰기 없는 연속 줄)까지 한 사유로 접어서
# 합친다. blank 줄이나 다른 트레일러 형식(`Key: value`) 줄을 만나면 그 사유를 종료. 여러 커밋에 있으면
# 커밋별로 나열(짧은 SHA 접두 — 어느 커밋 사유인지 구분되게).
# 커밋 1개씩 순회(NUL 구분 다중 레코드 방식은 BSD awk에서 NUL 바이트를 제대로 못 다뤄 회피)
# oasdiff는 OpenAPI 스키마 diff만 보므로 못 잡는 변경(필드 조건부 필수화·ErrorResponse.code 신규 값 등)이
# 있다 — #64 authorizationCode 조건부 필수화(2026-07-30)가 실제 사례. 이런 변경은 STOP §5 컨벤션상
# 원래도 Breaking-Change-Reason 트레일러를 남기게 돼 있으므로, oasdiff 결과와 무관하게 여기서 미리
# 추출해 독립 트리거로 쓴다.
TRAILER_REASON="$( { while IFS= read -r sha; do
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

# 신규 ErrorCode enum 상수 추가를 기계적으로 탐지 — 트레일러를 깜빡 안 남겨도 걸리도록 하는 2차 방어선.
# *ErrorCode.java의 "NAME(HttpStatus...." 한 상수 = 한 줄 컨벤션(spring-boot-java.md)에 의존.
# 주의: 추가된 줄(+)만 보면 "완전히 새 상수"와 "기존 상수의 HttpStatus·메시지만 바뀐 경우"를 구분 못
# 한다 — diff에서 기존 줄이 -old/+new로 통째로 찍히기 때문(#75 코멘트 지적, AUTH_FORBIDDEN 재현 확인).
# 같은 이름이 제거된 줄(-)에도 있으면 "변경", 없으면 "신규"로 나눠 Discord 문구가 실제와 다르게
# "신규"라고 오해하게 두지 않는다.
ADDED_ERROR_CODE_LINES="$(git diff "$GIT_RANGE" -- '**/*ErrorCode.java' 2>/dev/null \
  | grep -E '^\+[[:space:]]*[A-Z][A-Z0-9_]*\(HttpStatus\.' \
  | sed -E 's/^\+[[:space:]]*([A-Z][A-Z0-9_]*)\(.*/\1/' \
  | sort -u || true)"
REMOVED_ERROR_CODE_NAMES="$(git diff "$GIT_RANGE" -- '**/*ErrorCode.java' 2>/dev/null \
  | grep -E '^-[[:space:]]*[A-Z][A-Z0-9_]*\(HttpStatus\.' \
  | sed -E 's/^-[[:space:]]*([A-Z][A-Z0-9_]*)\(.*/\1/' \
  | sort -u || true)"
NEW_ERROR_CODES="$(grep -Fxvf <(printf '%s\n' "$REMOVED_ERROR_CODE_NAMES") <(printf '%s\n' "$ADDED_ERROR_CODE_LINES") 2>/dev/null || true)"
CHANGED_ERROR_CODES="$(grep -Fxf <(printf '%s\n' "$REMOVED_ERROR_CODE_NAMES") <(printf '%s\n' "$ADDED_ERROR_CODE_LINES") 2>/dev/null || true)"

# ErrorCode.getHttpStatus()와 컨트롤러 @ApiResponse(responseCode=...)의 불일치 탐지 — #75 후속(A).
# ErrorResponse.code가 String이라 컴파일 타임 연결이 없어, enum의 HttpStatus만 바꾸고 컨트롤러 쪽
# @ApiResponse 리터럴 갱신을 깜빡해도 위 신규 ErrorCode 탐지·oasdiff 스키마 diff 둘 다 못 잡는다.
# 이 저장소 컨벤션상 @ApiResponse description에 "NAME — 설명" 형태로 ErrorCode 이름이 그대로
# 적히므로(spring-boot-java.md), 그 이름을 단서로 실제 enum의 HttpStatus와 responseCode 리터럴을
# 교차검증한다. diff 기반이 아니라 현재 트리 전체를 매번 검사 — "enum은 안 바꾸고 컨트롤러 쪽만
# 잘못 손댄" 경우까지 잡으려면 GIT_RANGE만으론 부족하기 때문.
ERROR_CODE_FILES=()
while IFS= read -r f; do ERROR_CODE_FILES+=("$f"); done < <(find src/main/java -name '*ErrorCode.java' | sort)

CONTROLLER_FILES=()
while IFS= read -r f; do CONTROLLER_FILES+=("$f"); done < <(find src/main/java -name '*Controller.java' | sort)

# heredoc을 "$(perl - <<'EOF' ... EOF)" 형태로 command substitution 안에 직접 넣으면, 아래 정규식의
# 큰따옴표 개수가 줄 단위로 홀수라 bash의 $(...) 매칭 파서가 heredoc 본문까지 따옴표 균형을 추적하며
# 스캔하다 닫는 ')'를 못 찾아 통째로 syntax error가 난다(하드코딩된 delimiter가 quoted라도 발생하는
# bash 렉서 특성) — 그래서 heredoc은 별도 임시 파일에 쓰고 command substitution 밖에서 perl로 실행한다.
PERL_SCRIPT="$(mktemp)"
trap 'rm -f "$BREAKING_JSON" "$ADDITIONS_JSON" "$PERL_SCRIPT"' EXIT
cat > "$PERL_SCRIPT" <<'PERL_EOF'
my $n = shift @ARGV;
my @error_files = splice(@ARGV, 0, $n);
my @controller_files = @ARGV;

my %status_code = (
  OK => 200, CREATED => 201, ACCEPTED => 202, NO_CONTENT => 204,
  BAD_REQUEST => 400, UNAUTHORIZED => 401, FORBIDDEN => 403, NOT_FOUND => 404,
  METHOD_NOT_ALLOWED => 405, CONFLICT => 409, UNPROCESSABLE_ENTITY => 422,
  INTERNAL_SERVER_ERROR => 500, BAD_GATEWAY => 502, SERVICE_UNAVAILABLE => 503,
  GATEWAY_TIMEOUT => 504,
);

my %error_code_status;
for my $f (@error_files) {
  open(my $fh, '<', $f) or next;
  local $/;
  my $content = <$fh>;
  close $fh;
  while ($content =~ /\b([A-Z][A-Z0-9_]*)\(HttpStatus\.([A-Z_]+)\s*,/g) {
    my ($name, $status) = ($1, $2);
    $error_code_status{$name} = $status_code{$status};
  }
}

for my $f (@controller_files) {
  open(my $fh, '<', $f) or next;
  local $/;
  my $content = <$fh>;
  close $fh;
  while ($content =~ /responseCode\s*=\s*"(\d+)"\s*,\s*description\s*=\s*"([^"]*)"/g) {
    my ($declared, $desc) = ($1, $2);
    my %seen;
    while ($desc =~ /\b([A-Z][A-Z0-9_]{2,})\b/g) {
      my $name = $1;
      next if $seen{$name}++;
      next unless exists $error_code_status{$name};
      my $actual = $error_code_status{$name};
      next unless defined $actual;
      if ($actual != $declared) {
        print "$f\t$name\t$declared\t$actual\n";
      }
    }
  }
}
PERL_EOF
ERROR_CODE_MISMATCHES="$(perl "$PERL_SCRIPT" "${#ERROR_CODE_FILES[@]}" "${ERROR_CODE_FILES[@]}" "${CONTROLLER_FILES[@]}" 2>/dev/null || true)"

# 인터셉터 권한 게이트(@TripMemberOnly/@TripOwnerOnly) 추가·제거 탐지 — #75 후속(E).
# 기존 ErrorCode(AUTH_FORBIDDEN 등)를 재사용해 새 엔드포인트에 게이트를 걸거나 떼면 신규 ErrorCode
# 탐지도 안 걸리고 스키마 필드도 안 바뀌어 oasdiff diff도 0이라 가장 조용히 새는 경로였다.
# 주의: `\b`(단어 경계)는 GNU 확장이라 macOS 기본 awk(BWK awk)·mawk에서 매칭 자체가 안 된다(로컬
# 재현 시 발견). 이 컨벤션상 두 애노테이션은 항상 인자 없이 한 줄에 단독으로 쓰이므로(컨트롤러 전체
# grep 확인) `\b` 대신 줄 끝(공백만 허용)으로 경계를 잡아 awk 구현체에 안전하게 만든다.
GATE_ANNOTATION_CHANGES="$(git diff "$GIT_RANGE" -- '**/*Controller.java' 2>/dev/null | awk '
  /^\+\+\+ b\// { file = substr($0, 7); next }
  /^[+-][[:space:]]*@Trip(Member|Owner)Only[[:space:]]*$/ {
    sign = substr($0, 1, 1)
    ann = $0
    sub(/^[+-][[:space:]]*/, "", ann)
    sub(/[[:space:]]*$/, "", ann)
    label = (sign == "+") ? "추가" : "제거"
    print file ": " ann " " label
  }
' | sort -u || true)"

if [[ "$BREAKING_COUNT" -eq 0 && "$ADDITIONS_COUNT" -eq 0 && -z "$TRAILER_REASON" && -z "$NEW_ERROR_CODES" && -z "$CHANGED_ERROR_CODES" && -z "$ERROR_CODE_MISMATCHES" && -z "$GATE_ANNOTATION_CHANGES" ]]; then
  log "breaking change·필드 추가·트레일러·ErrorCode 신규/변경·ErrorCode 상태 불일치·권한 게이트 변경 없음 — 통과"
  exit 0
fi

# 주의: `grep -c . <<< "$VAR" || echo 0`는 매칭 0건이어도 grep이 "0"을 정상 출력하면서 exit code는
# 1(비매칭)을 반환해 `||`가 추가로 echo 0까지 실행 — "0\n0"이 그대로 로그에 찍히는 버그가 있었다
# (신규 ErrorCode가 항상 1건 이상이던 케이스로만 검증돼 안 드러남). 빈 문자열이면 grep을 아예 안 돌리고
# 0을 바로 쓴다.
count_lines() { if [[ -z "$1" ]]; then echo 0; else grep -c . <<< "$1"; fi; }
log "breaking change ${BREAKING_COUNT}건, 요청 필드 추가 ${ADDITIONS_COUNT}건, 트레일러 $( [[ -n "$TRAILER_REASON" ]] && echo "있음" || echo "없음" ), 신규 ErrorCode $(count_lines "$NEW_ERROR_CODES")건, 기존 ErrorCode 변경 $(count_lines "$CHANGED_ERROR_CODES")건, ErrorCode 상태 불일치 $(count_lines "$ERROR_CODE_MISMATCHES")건, 권한 게이트 변경 $(count_lines "$GATE_ANNOTATION_CHANGES")건 — Discord 알림 발송"

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

# oasdiff가 breaking·필드 추가 어느 쪽도 못 찾았는데(BREAKING_COUNT=0 && ADDITIONS_COUNT=0) 트레일러나
# 신규 ErrorCode가 있는 경우 — 스키마 diff에 안 잡히는 변경(필드 조건부 필수화·ErrorResponse.code
# 신규 값 등, #64 사고 참고). 엔드포인트별 상세는 oasdiff가 준 게 없어 만들 수 없으므로, 사람이 남긴
# 사유·신규 코드 목록만으로 별도 embed를 구성해 "PR을 직접 봐야 한다"는 사실 자체를 알린다.
# oasdiff가 breaking·필드 추가 어느 쪽도 못 찾았어도(BREAKING_COUNT=0 && ADDITIONS_COUNT=0) 트레일러·
# 신규/변경 ErrorCode 중 하나라도 있을 때만 발송 — 셋 다 없으면(예: ERROR_CODE_MISMATCHES·
# GATE_ANNOTATION_CHANGES만 있는 경우) 이 embed는 만들지 않는다. 아래 HIDDEN_SIGNAL_EMBED가 그 둘을
# 전담하므로, 여기서까지 조건 없이 만들면 "트레일러 없음" 안내문이 실제로 없는 사유인 양 같이 나가
# 중복·오해를 일으킨다.
if [[ "$BREAKING_COUNT" -eq 0 && "$ADDITIONS_COUNT" -eq 0 && ( -n "$TRAILER_REASON" || -n "$NEW_ERROR_CODES" || -n "$CHANGED_ERROR_CODES" ) ]]; then
  DISPLAY_REASON="$TRAILER_REASON"
  if [[ -z "$DISPLAY_REASON" ]]; then
    if [[ -n "$NEW_ERROR_CODES" ]]; then
      DISPLAY_REASON="⚠️ Breaking-Change-Reason 트레일러 없음 — 신규 ErrorCode가 감지됐으니 사유를 확인해 커밋 메시지에 추가해 주세요."
    else
      DISPLAY_REASON="⚠️ Breaking-Change-Reason 트레일러 없음 — 기존 ErrorCode의 HTTP 상태·메시지 변경이 감지됐으니 사유를 확인해 커밋 메시지에 추가해 주세요."
    fi
  fi
  HIDDEN_ERROR_CODE_FIELDS='[]'
  if [[ -n "$NEW_ERROR_CODES" ]]; then
    ERROR_CODE_LIST="$(sed 's/^/• `/; s/$/`/' <<< "$NEW_ERROR_CODES" | paste -sd $'\n' -)"
    HIDDEN_ERROR_CODE_FIELDS="$(jq --arg v "$ERROR_CODE_LIST" '. + [{ name: "신규 ErrorCode", value: $v, inline: false }]' <<< "$HIDDEN_ERROR_CODE_FIELDS")"
  fi
  if [[ -n "$CHANGED_ERROR_CODES" ]]; then
    CHANGED_CODE_LIST="$(sed 's/^/• `/; s/$/`/' <<< "$CHANGED_ERROR_CODES" | paste -sd $'\n' -)"
    HIDDEN_ERROR_CODE_FIELDS="$(jq --arg v "$CHANGED_CODE_LIST" '. + [{ name: "기존 ErrorCode 변경(HTTP 상태·메시지)", value: $v, inline: false }]' <<< "$HIDDEN_ERROR_CODE_FIELDS")"
  fi
  HIDDEN_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg reason "$DISPLAY_REASON" \
    --argjson errorCodes "$HIDDEN_ERROR_CODE_FIELDS" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "🚨 API Breaking Change (oasdiff 무변화 — 트레일러/ErrorCode 변경 감지)",
      url: $url,
      color: 15158332,
      fields: ($errorCodes + [
        { name: "왜 변경했는가", value: $reason, inline: false },
        { name: "참고", value: "OpenAPI 스키마 diff엔 안 잡히는 변경입니다(필드 조건부 필수화·ErrorResponse.code 신규 값 등). PR을 직접 확인해 영향 범위를 판단해 주세요.", inline: false }
      ]),
      footer: { text: $footer }
    }')"
  EMBEDS_JSON="$(jq --argjson e "$HIDDEN_EMBED" '. + [$e]' <<< "$EMBEDS_JSON")"
fi

# ErrorCode·@ApiResponse 불일치(A)·권한 게이트 변경(E) — 위 HIDDEN_EMBED와 달리 BREAKING_COUNT·
# ADDITIONS_COUNT와 무관하게 항상 확인한다. oasdiff가 이미 다른 breaking change를 찾은 김에
# 같은 PR에 이 두 신호도 섞여 있을 수 있고, 그런 경우까지 놓치지 않기 위함.
if [[ -n "$ERROR_CODE_MISMATCHES" || -n "$GATE_ANNOTATION_CHANGES" ]]; then
  HIDDEN_SIGNAL_FIELDS_JSON='[]'

  if [[ -n "$ERROR_CODE_MISMATCHES" ]]; then
    MISMATCH_LIST="$(awk -F'\t' '{ print "• `" $2 "` — @ApiResponse는 " $3 "로 문서화했지만 실제 enum은 " $4 " (" $1 ")" }' <<< "$ERROR_CODE_MISMATCHES" | paste -sd $'\n' -)"
    HIDDEN_SIGNAL_FIELDS_JSON="$(jq --arg v "$MISMATCH_LIST" '. + [{ name: "⚠️ ErrorCode·@ApiResponse HTTP 상태 불일치", value: $v, inline: false }]' <<< "$HIDDEN_SIGNAL_FIELDS_JSON")"
  fi

  if [[ -n "$GATE_ANNOTATION_CHANGES" ]]; then
    GATE_LIST="$(sed 's/^/• /' <<< "$GATE_ANNOTATION_CHANGES" | paste -sd $'\n' -)"
    HIDDEN_SIGNAL_FIELDS_JSON="$(jq --arg v "$GATE_LIST" '. + [{ name: "🔒 권한 게이트(@TripMemberOnly/@TripOwnerOnly) 변경", value: $v, inline: false }]' <<< "$HIDDEN_SIGNAL_FIELDS_JSON")"
  fi

  HIDDEN_SIGNAL_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --argjson fields "$HIDDEN_SIGNAL_FIELDS_JSON" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "🕵️ oasdiff 스키마 diff 밖 위험 신호 (ErrorCode 상태·권한 게이트)",
      url: $url,
      color: 15105570,
      fields: ($fields + [
        { name: "참고", value: "OpenAPI 스키마 diff엔 안 잡히는 변경입니다. PR을 직접 확인해 실제 영향 범위를 판단해 주세요.", inline: false }
      ]),
      footer: { text: $footer }
    }')"
  EMBEDS_JSON="$(jq --argjson e "$HIDDEN_SIGNAL_EMBED" '. + [$e]' <<< "$EMBEDS_JSON")"
fi

if [[ "$BREAKING_COUNT" -gt 0 ]]; then
  BREAKING_DOMAINS="$(jq -r "$JQ_DOMAIN_DEF"'
    [.[] | domain(.path)] | unique | join(", ")
  ' "$BREAKING_JSON")"

  REASON="$TRAILER_REASON"
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

  BREAKING_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg domains "$BREAKING_DOMAINS" \
    --argjson fields "$BREAKING_FIELDS_JSON" \
    --arg reason "$REASON" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "🚨 API Breaking Change",
      url: $url,
      color: 15158332,
      fields: ([{ name: "영향 도메인", value: $domains, inline: false }] + $fields + [
        { name: "왜 변경했는가", value: $reason, inline: false }
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

  ADDITIONS_EMBED="$(jq -n \
    --arg url "$TITLE_URL" \
    --arg domains "$ADDITIONS_DOMAINS" \
    --argjson fields "$ADDITIONS_FIELDS_JSON" \
    --arg footer "$FOOTER_TEXT" \
    '{
      title: "ℹ️ API 필드 추가 (non-breaking)",
      url: $url,
      color: 3447003,
      fields: ([{ name: "영향 도메인", value: $domains, inline: false }] + $fields + [
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
