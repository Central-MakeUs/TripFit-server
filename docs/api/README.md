# API 계약 스펙 (`docs/api/`)

`docs/api/openapi.json`은 springdoc이 생성하는 OpenAPI 3.1 스펙의 **`main` 기준 스냅샷**입니다. **손으로 편집하지 마세요** — `main`에 push될 때마다 CI(`api-contract-check` job)가 자동으로 최신화·커밋합니다.

설계 배경·결정 과정: [`docs/specs/api-contract-diff-ci.md`](../specs/api-contract-diff-ci.md)

## 이게 왜 있나

프론트는 별도 저장소([`docs/decisions/002-domain-split-vercel-api.md`](../decisions/002-domain-split-vercel-api.md))라 백엔드 DTO 변경을 수동으로 전달해야 합니다. `docs/api/openapi.json`은:

1. **프론트의 codegen 소스**: repo가 public이라 `https://raw.githubusercontent.com/Central-MakeUs/TripFit-server/main/docs/api/openapi.json`을 orval 등으로 인증 없이 바로 fetch 가능
2. **CI breaking-change 비교의 base**: PR·push마다 이 파일과 현재 코드의 실제 스펙을 `oasdiff`로 비교

## Breaking Change 감지 흐름

```
push / PR → OpenApiSpecExportTest → oasdiff breaking → 있으면 Discord #frontend 알림 + job 실패
                                                        → deploy는 막지 않음(이미 병합된 뒤라서)
```

oasdiff의 영어 breaking-change 문구는 알려진 `id`(`request-property-removed` 등) 기준으로 한글 템플릿에 매핑해 보냅니다(`notify-api-breaking-change.sh`의 `translate` 함수). 매핑 안 된 `id`는 추측 번역 없이 영어 원문 그대로 노출됩니다. footer에는 이번 변경에 포함된 커밋 short SHA가 `Commit ID: 9e1c878, 6e9df7e`처럼 전부 나열됩니다.

## "왜 변경했는가" — 커밋 트레일러 컨벤션

Breaking change를 만드는 커밋에는 본문에 `Breaking-Change-Reason:` 트레일러를 추가하세요. Discord 알림의 "왜 변경했는가"란에 그대로 노출됩니다.

```
Fix: 마이페이지 응답 필드명 정리

Breaking-Change-Reason: 프론트 요청으로 name → nickname 통일 (디자인 시스템 용어 정합)
```

트레일러가 없으면 "⚠️ 사유 미기재" 안내문이 대신 노출됩니다(하드코딩된 고정 문구가 아니라 트레일러 유무에 따라 동적으로 채워짐).

## 알림 봇 이름·아바타

기본값은 `DISCORD_BOT_USERNAME="TripFit CI"` · `DISCORD_BOT_AVATAR_URL=https://github.com/{organization}.png`(레포 소속 조직의 GitHub 아바타)입니다. 바꾸고 싶으면 `.github/workflows/ci-cd.yml`의 `api-contract-check` job에 같은 이름의 env를 추가하세요.

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
| `src/test/java/.../OpenApiSpecExportTest.java` | 현재 코드 기준 스펙을 `build/openapi/openapi.json`으로 export |
| `scripts/notify-api-breaking-change.sh` | oasdiff 실행 → breaking이면 Discord 알림 + 실패 |
| `.github/workflows/ci-cd.yml` `api-contract-check` job | 위 과정을 CI에 연결 |

## PR 단계에서 merge를 실제로 막고 싶다면

워크플로만으로는 `api-contract-check` 실패가 merge 버튼을 막지 않습니다. GitHub 저장소 **Settings → Branches → Branch protection rules → Require status checks to pass**에 `api-contract-check`를 추가해야 합니다(이 저장소 범위 밖 — 필요 시 별도 진행).
