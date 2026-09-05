# API 계약 스펙 (`docs/api/`)

`docs/api/openapi.json`은 springdoc이 생성하는 OpenAPI 3.1 스펙의 **`main` 기준 스냅샷**입니다. **손으로 편집하지 마세요** — `main`에 push될 때마다 CI(`api-contract-check` job)가 자동으로 최신화·커밋합니다.

설계 배경·결정 과정: [`docs/specs/cross-cutting/api-contract-diff-ci.md`](../specs/cross-cutting/api-contract-diff-ci.md)

## 이게 왜 있나

프론트는 별도 저장소([`docs/decisions/002-domain-split-vercel-api.md`](../decisions/002-domain-split-vercel-api.md))라 백엔드 DTO 변경을 수동으로 전달해야 합니다. `docs/api/openapi.json`은:

1. **프론트의 codegen 소스**: repo가 public이라 `https://raw.githubusercontent.com/Central-MakeUs/TripFit-server/main/docs/api/openapi.json`을 orval 등으로 인증 없이 바로 fetch 가능
2. **CI breaking-change 비교의 base**: PR·push마다 이 파일과 현재 코드의 실제 스펙을 `oasdiff`로 비교

## Breaking Change 감지 흐름

```
push / PR → OpenApiSpecExportTest → oasdiff breaking → 있으면 Discord #frontend 알림만 발송
                                   → 없어도 트레일러·ErrorCode 신규/변경 있으면 별도 알림 발송(아래 "oasdiff가 못 보는 변경" 참고)
                                   → ErrorCode·@ApiResponse 불일치·권한 게이트 변경은 위 결과와 무관하게 항상 별도 확인
                                                        → job은 항상 통과(CI 실패로 표시 안 함), deploy도 막지 않음
```

oasdiff의 breaking-change 문구는 **번역 없이 영어 원문 그대로** 노출됩니다. oasdiff breaking check `id`가 80개+라 전부 한글 템플릿으로 매핑·유지보수하는 게 비현실적이고, 일부만 매핑하면 매핑 안 된 `id`만 영어로 남아 한 필드 안에서 한글·영어가 뒤섞이는 문제가 있어(2026-07-29 실제 사고) 아예 원문만 쓰기로 함(2026-07-29 amend). footer에는 이번 변경에 포함된 커밋 short SHA가 `Commit ID: 9e1c878, 6e9df7e`처럼 전부 나열됩니다.

`GIT_RANGE`는 이번 push의 `before..after`(또는 PR의 `base..head`)라 로컬에 여러 커밋을 쌓아두고 한 번에 push하면 서로 무관한 여러 변경의 breaking change·사유가 한 알림에 뭉쳐서 나옵니다 — 가능하면 breaking change가 생긴 커밋은 바로바로 push하세요.

### merge 시 알림이 안 오는 게 정상인 이유 — 중복 방지 로직

`api-contract-check` job의 "Check breaking changes and notify Discord" 스텝은 `pull_request` 이벤트에서만 돌고, main으로의 merge push(커밋 메시지가 `Merge pull request #`로 시작)에서는 **의도적으로 skip**됩니다 — PR이 열릴 때 이미 같은 diff로 알림을 보냈으니 merge 시점에 또 보내면 중복이기 때문입니다. 즉 **알림은 PR을 열거나 업데이트하는 시점에 옵니다, merge 시점이 아닙니다.** PR 없이 로컬에서 직접 main에 push한 경우(#67 사고)는 이 휴리스틱이 걸리지 않아 알림이 나갑니다.

### oasdiff가 못 보는 변경 — 트레일러·ErrorCode·권한 게이트 기반 2차 감지 (2026-07-30 amend, `#64` 사고)

`oasdiff`는 **OpenAPI 스키마 diff**만 봅니다. 아래처럼 스키마는 그대로인데 실제로는 프론트가 대응해야 하는 변경은 `oasdiff breaking`도 `필드 추가`도 못 잡아서, 예전엔 알림 자체가 안 나갔습니다:

- **필드의 조건부 필수화** — 필드 자체는 그대로 `optional`(Bean Validation으로 전 provider에 `@NotBlank`를 걸 수 없는 경우 등)이지만, 서비스 레이어 로직이 특정 조건(예: `provider == APPLE`)에서 그 필드를 사실상 필수로 강제하는 경우. 스키마의 `requiredMode`는 안 바뀜.
- **신규 `ErrorCode` 추가** — `ErrorResponse.code`가 `String` 타입이라 OpenAPI 스키마에 enum 값으로 안 잡힘. 새 4xx 에러 코드가 생겨도 스키마 diff는 무변화.
- 그 외 스키마에 안 나타나는 모든 런타임 검증·정책 변화 일반.

**실제 사고(2026-07-30):** `#64`(Apple 로그인 시 `authorizationCode` 조건부 필수화 + `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED` 추가) PR이 merge됐는데 Discord 알림이 전혀 안 나갔다. 원인은 위 두 가지가 정확히 겹친 경우였고, 커밋에 `Breaking-Change-Reason` 트레일러를 달아뒀는데도 그 내용이 Discord로 전달되는 경로 자체가 없었다(트레일러는 breaking embed 안에 텍스트로 끼워 넣는 용도로만 쓰였고, oasdiff가 아무것도 못 찾으면 그 embed 자체가 안 만들어짐).

**수정(같은 날 amend):** `scripts/notify-api-breaking-change.sh`가 이제 `BREAKING_COUNT`/`ADDITIONS_COUNT`와 **무관하게** 아래 두 조건을 독립적으로 확인합니다.

1. `GIT_RANGE`의 커밋 중 하나라도 `Breaking-Change-Reason:` 트레일러가 있으면 — 사람이 이미 "이건 알려야 한다"고 표시한 것이므로 그대로 신뢰
2. `GIT_RANGE`에서 `**/*ErrorCode.java`에 신규 enum 상수(`NAME(HttpStatus...` 패턴)가 추가됐으면 — 트레일러를 깜빡해도 걸리는 2차 방어선. **주의:** 추가된 줄(`+`)만 보면 "완전히 새 상수"와 "기존 상수의 `HttpStatus`·메시지만 바뀐 경우"(diff에 `-old`/`+new`로 통째로 찍힘)를 구분 못 해 전부 "신규"로 오해할 수 있었음 — 같은 이름이 제거된 줄(`-`)에도 있으면 "기존 ErrorCode 변경"으로 따로 분류·표시하도록 수정함(2026-07-31 amend, `#75` 코멘트 지적).

둘 중 하나라도 있으면 `🚨 API Breaking Change (oasdiff 무변화 — 트레일러/ErrorCode 변경 감지)` embed를 별도로 보냅니다. oasdiff가 준 정보가 없어 엔드포인트별 상세는 못 만들고, 트레일러 사유·신규/변경 코드 목록·"PR을 직접 확인하라"는 안내만 담습니다.

**추가 수정 (2026-07-31 amend, `#75` 코멘트 후속 — 위 두 감지로도 못 잡는 경로 2개 추가):**

3. **ErrorCode·`@ApiResponse` HTTP 상태 불일치** — `ErrorResponse.code`가 `String`이라 컨트롤러의 `@ApiResponse(responseCode = "403", description = "AUTH_FORBIDDEN — ...")` 같은 리터럴과 실제 enum의 `HttpStatus`가 컴파일 타임으로 안 묶여 있습니다. enum의 `HttpStatus`만 바꾸고 컨트롤러 쪽 갱신을 깜빡하면, 런타임은 새 상태코드를 내려주는데 Swagger·oasdiff 기준 스펙은 옛 상태코드 그대로 남습니다. `@ApiResponse` description에 ErrorCode 이름이 그대로 적히는 컨벤션(`openapi-conventions.md`)을 단서로, `GIT_RANGE` diff가 아니라 **현재 트리 전체**를 매번 스캔해 실제 enum `HttpStatus`와 컨트롤러 `responseCode` 리터럴을 교차검증합니다("enum은 안 건드리고 컨트롤러만 잘못 고친" 경우까지 잡으려면 diff만으론 부족하기 때문).
4. **권한 게이트(`@TripMemberOnly`/`@TripOwnerOnly`) 추가·제거** — 기존 `ErrorCode`(예: `AUTH_FORBIDDEN`)를 재사용해 새 엔드포인트에 게이트를 걸거나 떼면 신규 ErrorCode 탐지도, 스키마 필드 diff도 안 걸리는 가장 조용한 경로입니다. `GIT_RANGE`의 컨트롤러 diff에서 이 두 애노테이션이 추가/제거된 줄만 뽑아 알립니다.

3·4 중 하나라도 있으면 `🕵️ oasdiff 스키마 diff 밖 위험 신호` embed를 별도로 보냅니다 — `BREAKING_COUNT`·`ADDITIONS_COUNT`와 **무관하게 항상** 확인합니다(oasdiff가 이미 다른 breaking change를 찾은 김에 같은 PR에 이 신호도 섞여 있을 수 있어서).

**여전히 못 잡는 것 (구조적으로 자동화 불가 — `#75` 코멘트 분석, 2026-07-31):**

- 요청 파라미터 **기본값 변경** — `@Parameter(defaultValue=...)` 컨벤션이 없어 서버 기본 페이지 크기·정렬 순서가 바뀌어도 스펙엔 애초에 안 나타남
- `String` 필드의 **허용 포맷/파싱 규칙 변경** — 예: `Weekday.daysOfWeek`의 구분자·요일 표기. 스키마엔 `string`이라고만 나오고 실제 규칙은 코드 안에만 있음(`spring-boot-java.md` Enum 절에 "계약이 String인 필드"로 의도적으로 분류된 케이스)
- **계산 필드(computed field)의 트리거 조건 변경** — 예: `hasCompletedPreSchedule`. 타입·이름은 그대로인데 "언제 true가 되는지" 로직만 바뀌면 스키마 diff는 0
- **CORS·env 값 변경** — `allowedOrigins`, `JWT_ACCESS_EXPIRATION` 등. "필드가 있다"는 보장돼도 "언제 세션이 끊기는지"·"어디서 호출 가능한지"는 OpenAPI 스펙 개념 밖
- **HTTP 헤더 추가·제거·의미 변경** — 이 저장소 DTO는 전부 바디 기준이라 헤더 기반 계약이 생기면 완전한 사각지대
- 그 외 트레일러도 안 달고 새/변경 `ErrorCode`도 안 만드는 순수 비즈니스 로직 변경(예: 검증 규칙 강화, side effect 추가/제거) 일반

이런 변경은 **`Breaking-Change-Reason` 트레일러를 다는 것**이 유일한 안전장치입니다 — 자동 탐지를 더 늘리기보다 트레일러 습관을 지키는 게 우선입니다.

## "왜 변경했는가" — 커밋 트레일러 컨벤션

**프론트가 조금이라도 대응해야 하는 API 계약 변경**(필드 추가·삭제·이름변경·타입변경·필수화, enum 값 추가·삭제, ErrorCode 신규·변경·삭제, 경로·메서드 변경 등 — optional 필드 추가도 포함)에는 본문에 `Breaking-Change-Reason:` 트레일러를 추가하세요. "필드 하나 추가일 뿐"이라는 이유로 생략하지 않습니다 — CI가 `oasdiff breaking`으로 잡아내는 것은 좁은 스키마 파괴적 변경뿐이라, 그보다 넓은 실제 영향 범위는 사람이 직접 기록해야 합니다. 상세 기준: [`core-guardrails.md`](../../.claude/rules/core-guardrails.md) STOP §5.

Discord 알림의 "왜 변경했는가"란에 커밋 short SHA와 함께 그대로 노출됩니다. oasdiff가 breaking change를 찾았으면 그 embed 안에, oasdiff는 아무것도 못 찾았지만 트레일러나 신규 `ErrorCode`가 있으면 별도 embed로 노출됩니다(위 "oasdiff가 못 보는 변경" 참고) — **트레일러가 실제로 Discord에 도달하는 유일한 두 경로**이니 생략하지 마세요.

```
Fix: 마이페이지 응답 필드명 정리

Breaking-Change-Reason: 프론트 요청으로 name → nickname 통일 (디자인 시스템 용어 정합)
```

**한 줄로 쓰세요 — 자동 줄바꿈으로 두 줄 이상 걸치게 두지 마세요.** 스크립트는 `Breaking-Change-Reason:` 다음 줄부터 빈 줄이나 다른 트레일러(`Key: value`)가 나올 때까지를 한 사유로 이어 붙이지만, 커밋 메시지 안에서 사유가 여러 줄로 wrap된 경우까지 안전하게 합치기 위한 보정일 뿐이니 애초에 한 줄로 쓰는 게 가장 안전합니다.

여러 커밋에 걸쳐 있으면 각 사유 앞에 `짧은SHA: `를 붙여 어느 커밋의 사유인지 구분합니다(예: `04e3262: ...`). 트레일러가 없으면 "⚠️ 사유 미기재" 안내문이 대신 노출됩니다(하드코딩된 고정 문구가 아니라 트레일러 유무에 따라 동적으로 채워짐).

## 알림 봇 이름·아바타

스크립트 기본값은 `DISCORD_BOT_USERNAME="TripFit CI"` · `DISCORD_BOT_AVATAR_URL=https://github.com/{organization}.png`(레포 소속 조직의 GitHub 아바타)이지만, `.github/workflows/ci-cd.yml`의 `api-contract-check` job이 `DISCORD_BOT_AVATAR_URL`을 `https://raw.githubusercontent.com/{repo}/main/docs/api/tripfit_app_icon.png`로 오버라이드해 실제로는 TripFit 앱 아이콘(`docs/api/tripfit_app_icon.png`)이 표시됩니다. repo가 public이라 별도 인증 없이 `main`에서 바로 fetch되며, `docs/api/openapi.json`과 같은 방식(raw URL)입니다. 아바타를 바꾸려면 이 파일을 교체하거나 워크플로의 env 값을 다른 URL로 바꾸세요.

## 로컬 재현

```bash
# 1) 현재 코드 기준 스펙 생성 (build/openapi/openapi.json)
./gradlew test --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"

# 2) oasdiff 설치 (최초 1회)
brew install oasdiff   # 또는: curl -fsSL https://raw.githubusercontent.com/oasdiff/oasdiff/main/install.sh | sh

# 3) main 스냅샷과 비교
oasdiff breaking docs/api/openapi.json build/openapi/openapi.json

# 4) Discord로 보내지 않고 실제 payload만 눈으로 확인 (DRY_RUN)
DRY_RUN=true \
BASE_SPEC=docs/api/openapi.json \
REVISED_SPEC=build/openapi/openapi.json \
DISCORD_WEBHOOK_URL=dummy \
GIT_RANGE="origin/main..HEAD" \
GITHUB_SHA="$(git rev-parse HEAD)" \
  ./scripts/notify-api-breaking-change.sh
```

## 관련 파일

| 경로 | 역할 |
|------|------|
| `docs/api/openapi.json` | `main` 스냅샷 (자동 갱신, 손편집 금지) |
| `docs/api/tripfit_app_icon.png` | Discord 알림 봇 아바타 — raw URL로 CI에서 참조 |
| `src/test/java/.../OpenApiSpecExportTest.java` | 현재 코드 기준 스펙을 `build/openapi/openapi.json`으로 export |
| `scripts/notify-api-breaking-change.sh` | oasdiff 실행 → breaking·필드 추가·(oasdiff 무변화 시) 트레일러·ErrorCode 신규/변경·(항상) ErrorCode·`@ApiResponse` 불일치·권한 게이트 변경이면 Discord 알림만 발송 (job은 항상 통과) |
| `.github/workflows/ci-cd.yml` `api-contract-check` job | 위 과정을 CI에 연결 |
