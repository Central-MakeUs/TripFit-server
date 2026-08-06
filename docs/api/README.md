# API 계약 스펙 (`docs/api/`)

`docs/api/openapi.json`은 springdoc이 생성하는 OpenAPI 3.1 스펙의 **`main` 기준 스냅샷**입니다. **손으로 편집하지 마세요** — `main`에 push될 때마다 CI(`api-contract-check` job)가 자동으로 최신화·커밋합니다.

설계 배경·결정 과정: [`docs/specs/api-contract-diff-ci.md`](../specs/api-contract-diff-ci.md)

## 이게 왜 있나

프론트는 별도 저장소([`docs/decisions/002-domain-split-vercel-api.md`](../decisions/002-domain-split-vercel-api.md))라 백엔드 DTO 변경을 수동으로 전달해야 합니다. `docs/api/openapi.json`은:

1. **프론트의 codegen 소스**: repo가 public이라 `https://raw.githubusercontent.com/Central-MakeUs/TripFit-server/main/docs/api/openapi.json`을 orval 등으로 인증 없이 바로 fetch 가능
2. **CI breaking-change 비교의 base**: PR·push마다 이 파일과 현재 코드의 실제 스펙을 `oasdiff`로 비교

## Breaking Change 감지 흐름

```
push / PR → OpenApiSpecExportTest → oasdiff breaking → 있으면 Discord #frontend 알림만 발송
                                                        → job은 항상 통과(CI 실패로 표시 안 함), deploy도 막지 않음
```

oasdiff의 breaking-change 문구는 **번역 없이 영어 원문 그대로** 노출됩니다. oasdiff breaking check `id`가 80개+라 전부 한글 템플릿으로 매핑·유지보수하는 게 비현실적이고, 일부만 매핑하면 매핑 안 된 `id`만 영어로 남아 한 필드 안에서 한글·영어가 뒤섞이는 문제가 있어(2026-07-29 실제 사고) 아예 원문만 쓰기로 함(2026-07-29 amend). footer에는 이번 변경에 포함된 커밋 short SHA가 `Commit ID: 9e1c878, 6e9df7e`처럼 전부 나열됩니다.

`GIT_RANGE`는 이번 push의 `before..after`(또는 PR의 `base..head`)라 로컬에 여러 커밋을 쌓아두고 한 번에 push하면 서로 무관한 여러 변경의 breaking change·사유가 한 알림에 뭉쳐서 나옵니다 — 가능하면 breaking change가 생긴 커밋은 바로바로 push하세요.

## Release Gate #65 관련 엔드포인트 콜아웃

`POST /api/v1/auth/login`(로그인)·`DELETE /api/v1/users/me`(탈퇴)는 앱 스토어 심사([`harness-wave.md`](../../.claude/rules/harness-wave.md) Release Gate 표 — [#5](https://github.com/Central-MakeUs/TripFit-server/issues/5) Apple S2S webhook · [#62](https://github.com/Central-MakeUs/TripFit-server/issues/62) OAuth 콘솔 설정 · [#64](https://github.com/Central-MakeUs/TripFit-server/issues/64) 탈퇴 시 provider revoke)와 직결돼 일반 API 변경보다 프론트와의 사전 논의가 중요하다. 이 두 엔드포인트에 breaking change·필드 추가가 생기면 Discord embed에 별도 "⚠️ Release Gate #65 관련" 필드가 추가되고, 변경 텍스트에서 `GOOGLE`/`KAKAO`/`APPLE` 언급을 스캔해 어떤 provider와 관련 있는지(특정 provider 언급이 없으면 "전체 영향") 함께 보여준다.

## "왜 변경했는가" — 커밋 트레일러 컨벤션

**프론트가 조금이라도 대응해야 하는 API 계약 변경**(필드 추가·삭제·이름변경·타입변경·필수화, enum 값 추가·삭제, ErrorCode 신규·변경·삭제, 경로·메서드 변경 등 — optional 필드 추가도 포함)에는 본문에 `Breaking-Change-Reason:` 트레일러를 추가하세요. "필드 하나 추가일 뿐"이라는 이유로 생략하지 않습니다 — CI가 `oasdiff breaking`으로 잡아내는 것은 좁은 스키마 파괴적 변경뿐이라, 그보다 넓은 실제 영향 범위는 사람이 직접 기록해야 합니다. 상세 기준: [`harness-workflow.md`](../../.claude/rules/harness-workflow.md) STOP §5.

Discord 알림의 "왜 변경했는가"란에 커밋 short SHA와 함께 그대로 노출됩니다(breaking 임베드 기준 — 아래 "Breaking Change 감지 흐름" 참고).

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
| `scripts/notify-api-breaking-change.sh` | oasdiff 실행 → breaking·필드 추가면 Discord 알림만 발송 (job은 항상 통과) |
| `.github/workflows/ci-cd.yml` `api-contract-check` job | 위 과정을 CI에 연결 |
