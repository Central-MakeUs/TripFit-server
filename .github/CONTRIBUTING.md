# GitHub 워크플로

Issue · PR · Milestone · CI를 TripFit 하네스(`docs/`, `.claude/`)와 연결합니다.

계획·우선순위 SSOT: [`docs/product/development-wave.md`](../docs/product/development-wave.md) · 요약: [`waves.md`](../docs/product/waves.md)

## 브랜치 전략

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

### `[미정]` chore 트래커

기획·스펙·BR의 **`[미정]`** 항목은 **[#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)** 에 모은다 (`kind: chore`). 상세: `.claude/rules/harness-wave.md`.

### 마일스톤 (= wave)

| 마일스톤 | wave |
|----------|------|
| Wave 1 — 준비 | 1 |
| Wave 2 — 핵심 MVP | 2 |
| Wave 3 — 출시 UX | 3 |
| Wave 4 — 운영·확장 | 4 |

## Agent 예시

> "wave 1에 JWT 필터 이슈 만들어줘. area api, 스펙 링크 포함."

## CI

`workflows/ci-cd.yml` — PR·`main` push 시 test, `main` push 시 GHCR deploy.
