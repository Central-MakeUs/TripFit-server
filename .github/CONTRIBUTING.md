# GitHub 워크플로

Issue · PR · Milestone · CI를 TripFit 하네스(`docs/`, `.claude/`)와 연결합니다.

계획·우선순위 SSOT: [`docs/product/development-wave.md`](../docs/product/development-wave.md) · 요약: [`waves.md`](../docs/product/waves.md)

## 브랜치 전략

**에이전트 주의:** 새 이슈·새 브랜치·새 PR을 만들지는 사용자가 명시적으로 요청하지 않는 한 항상 먼저 확인한다 — `.claude/rules/harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절.

```
main  ←  {type}/{issue-number}-{description}
```

| 항목 | 규칙 |
|------|------|
| **기본 브랜치** | `main` — merge 시 CI test + GHCR deploy |
| **작업 브랜치** | `main`에서 분기, PR로 `main`에 merge |
| **네이밍** | `{type}/{issue-number}-{description}` |
| **type** | `feat`, `fix`, `chore`, `docs`, `refactor`, `test` (브랜치명은 소문자) |

예: `feat/12-trip-room-create`, `fix/34-auth-token-expiry`

## 커밋 메시지

**형식:** `{Type}: {한글 설명}` — **Type 첫 글자 대문자** (PascalCase)

| Type | 용도 |
|------|------|
| `Feat` | 새 기능·API |
| `Fix` | 버그 수정 |
| `Refactor` | 동작 변경 없는 구조·코드 정리 |
| `Docs` | 문서·스펙·주석 |
| `Chore` | 빌드·설정·템플릿·의존성 |
| `Test` | 테스트 추가·수정 |

예: `Feat: 소셜 로그인 API 구현`, `Refactor: 도메인 기반 레이어드 패키지 구조로 재구성`

`./scripts/install-git-hooks.sh`로 설치되는 `commit-msg` 훅이 이 형식을 로컬에서 기계적으로 검증한다(형식 위반 시 커밋 차단). Merge·Revert 커밋은 검사 대상에서 제외.

### `Breaking-Change-Reason:` 트레일러

프론트가 **조금이라도 대응해야 하는** API 계약 변경(필드 추가·삭제·이름변경·타입변경·필수화 — optional 추가 포함, enum 값 추가·삭제, `ErrorCode` 신규·변경·삭제, 경로·메서드 변경 등)이 포함된 커밋은 본문에 `Breaking-Change-Reason: <한 줄 사유>` 트레일러를 추가한다. "필드 하나 추가일 뿐"·"optional이라 안전함"은 생략 사유가 아니다 — CI의 `oasdiff breaking`은 스키마 파괴적 변경만 잡아내므로, 그보다 넓은 실제 프론트 영향은 커밋 시점에 직접 남긴다.

```
Fix: 마이페이지 응답 필드명 정리

Breaking-Change-Reason: 프론트 요청으로 name → nickname 통일 (디자인 시스템 용어 정합)
```

상세 기준·Discord 알림 흐름: [`docs/api/README.md`](../docs/api/README.md) · [`harness-workflow.md`](../.claude/rules/harness-workflow.md) STOP §5.

### 커밋 분할 (에이전트)

사용자가 **커밋을 요청**했을 때, staged되지 않은 전체 변경을 **주제별로 나눠 최대 3개** 커밋으로 만든다.

| 원칙 | 내용 |
|------|------|
| **최대 개수** | 3개 — 더 쪼개지 않음 |
| **분할 기준** | 독립된 주제 (예: 기능 구현 / 테스트 / 문서·하네스·설정) |
| **1개로 충분할 때** | 변경이 한 주제면 1커밋 |
| **금지** | 의미 없는 파일 단위 쪼개기, 빌드 깨지는 중간 커밋, 사용자 요청 없는 커밋 |

분할 순서 예: (1) 핵심 구현 → (2) 테스트 → (3) 문서·규칙·설정. 각 커밋은 `{Type}: {한글}` 형식을 따른다.

## Pull Request

**에이전트 주의:** `gh pr create` 실행 전 사용자에게 먼저 확인한다 — 구현·커밋까지 요청받았어도 PR 생성은 별도 승인 필요(위 "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절과 동일).

| 항목 | 규칙 |
|------|------|
| **base** | `main` |
| **제목** | `{Type}: {한글 설명}` (Type 첫 글자 대문자) |
| **본문** | [`pull_request_template.md`](pull_request_template.md) |
| **이슈 연결** | `Closes #n` |
| **스펙** | DB·인증·다파일 변경 시 `docs/specs/` 링크 |
| **merge** | **Create a merge commit** — PR 브랜치 커밋 히스토리 유지 |
| **merge 후** | 작업 브랜치 삭제 — 원격(`git push origin --delete {branch}`) + 로컬(`git branch -d {branch}`). GitHub PR 화면 "Delete branch" 버튼도 동일 |

### Merge 정책 (금지: Squash merge)

| 허용 | 금지 |
|------|------|
| **Create a merge commit** | **Squash merge** |
| Rebase merge (리뷰 후 rebase 정리한 경우만, 팀 합의) | Squash and merge |

**Squash merge 금지 이유:** `main`과 feature 브랜치에 **동일 작업이 이중 히스토리**로 남고, author date·잔디·커밋 추적이 깨짐. PR merge 시 GitHub UI에서 **Squash and merge 버튼 사용 금지**.

저장소 설정: Settings → General → Pull Requests → **Allow squash merging** 끄기.

## 코드 리뷰 — N 룰

리뷰 코멘트 등급 (**wave와 무관**).

| 등급 | 의미 |
|------|------|
| **N1** | 필수 반영 |
| **N2** | 권장 |
| **N3** | 웬만하면 반영 |
| **N4** | 선택 |
| **N5** | 사소 |

예: `N2: prod에서 ddl-auto update인데 엔티티 컬럼 삭제 시 운영 DB에 orphan column이 남을 수 있습니다.`

## 라벨 · 마일스톤

```bash
./scripts/github-bootstrap.sh      # 라벨 + 마일스톤
./scripts/github-sync-issues.sh    # 열린 이슈 wave 정렬 (선택)
```

### 라벨

| prefix | 값 | 용도 |
|--------|-----|------|
| `wave:` | 1, 2, 3, 4 | **유일한 계획 축** |
| `kind:` | feature, bug, chore, docs | 이슈 종류 |
| `area:` | api, domain, deploy, docs, infra | 코드 위치 |
| `meta:` | blocked, duplicate, wontfix | 상태 |

Nice/Must 구분은 **Wave Backlog Issue** 본문 + 실행 Issue **비고** — `priority:` 라벨은 사용하지 않음.

이슈당 **wave 1개** + kind 1개 + area 1개 권장.

### `[미정]` 항목 처리

중앙 트래커(구 `#2`)는 폐지됐다(2026-08-19) — 기획·스펙·BR의 `[미정]` 항목은 해당 문서에 표기만 남긴다. 상세: `.claude/rules/harness-wave.md`.

### 마일스톤 (= wave)

| 마일스톤 | wave |
|----------|------|
| Wave 1 — 소셜 로그인 | 1 |
| Wave 2 — MVP 로직 | 2 |
| Wave 3 — 외부 API 연동 | 3 |
| Wave 4 — 리팩토링·성능·런칭 후 UX | 4 |

## Agent 예시

> "wave 1에 JWT 필터 이슈 만들어줘. area api, 스펙 링크 포함."

## CI

`workflows/ci-cd.yml` — PR·`main` push 시 test, `main` push 시 GHCR deploy.
