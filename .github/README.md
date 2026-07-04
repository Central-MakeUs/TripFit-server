# GitHub 워크플로

Issue · PR · Milestone · CI를 TripFit 하네스(`docs/`, `.cursor/`)와 연결합니다.

## 브랜치 전략

```
main  ←  {type}/{issue-number}-{description}
```

| 항목 | 규칙 |
|------|------|
| **기본 브랜치** | `main` — merge 시 CI test + GHCR deploy |
| **작업 브랜치** | `main`에서 분기, PR로 `main`에 merge |
| **네이밍** | `{type}/{issue-number}-{description}` |
| **type** | `feat`, `fix`, `chore`, `docs`, `refactor`, `test` |

예: `feat/12-trip-room-create`, `fix/34-auth-token-expiry`, `docs/8-api-response-spec`

이슈 없이 작은 수정(T 유형)은 `fix/login-typo`처럼 번호 생략 가능 — 팀 합의 시.

## 커밋 메시지

**형식:** `{type}: {한글 설명}`

```
feat: 여행방 생성 API 추가
fix: 토큰 만료 시 401 응답 code 수정
chore: verify-deploy 스크립트 로그 정리
docs: API 응답 규약 문서 추가
```

- Agent가 커밋 제안 시 위 형식 사용 (사용자 명시 요청 시에만 커밋)
- PR **Squash merge** 시 PR 제목이 `main`의 커밋 메시지가 됨 → 제목을 커밋 규칙과 동일하게 작성

## Pull Request

| 항목 | 규칙 |
|------|------|
| **base** | `main` |
| **제목** | `{type}: {한글 설명}` (squash 후 커밋 메시지) |
| **본문** | [`pull_request_template.md`](pull_request_template.md) |
| **이슈 연결** | `Closes #n` — merge 시 이슈 자동 close |
| **스펙** | S 유형은 `docs/specs/` 링크 필수 |
| **merge** | **Squash merge** 권장 |

## 코드 리뷰 — PN 룰

리뷰 코멘트·Agent 리뷰 제안 시 태그를 붙입니다.

| 등급 | 의미 | 리뷰어 의도 |
|------|------|-------------|
| **P1** | 꼭 반영 | 중대한 오류·보안·데이터 손상 가능 |
| **P2** | 적극 고려 | 수용 또는 토론 필요 |
| **P3** | 웬만하면 반영 | 미반영 시 PR에 사유 기록 |
| **P4** | 선택 | 반영해도/안 해도 OK |
| **P5** | 사소 | 무시 가능 |

예: `P2: prod에서 ddl-auto validate인데 엔티티만 추가하면 기동 실패합니다.`

승인 정책은 팀이 정합니다 (예: 1명 Approve 후 merge). 자동 배정 워크플로는 도입 시 이 섹션에 추가.

## 템플릿

| 파일 | 용도 |
|------|------|
| `ISSUE_TEMPLATE/feature.yml` | 새 기능·API (MVP 작업) |
| `ISSUE_TEMPLATE/bug.yml` | 버그 |
| `pull_request_template.md` | PR 본문 기본 구조 |

CI·문서·리팩터는 **Feature** 이슈로 등록하거나 Agent에게 `gh issue create`로 생성하면 됩니다.

## 이슈 본문 — 누가 채우나?

| 방식 | 설명 |
|------|------|
| **직접** | GitHub UI에서 템플릿 필드 작성 |
| **Agent** | "여행방 생성 API 이슈 만들어줘" → `docs/specs/` + `gh issue create`까지 가능 |
| **혼합** | 제목·area만 적고, Agent에게 요구사항·완료 기준 초안 요청 |

**정석:** 긴 설계는 `docs/specs/`에, Issue에는 **목표 + 스펙 링크 + 완료 기준**만.

## 라벨 · 마일스톤 초기화 (1회)

저장소에 push한 뒤, 로컬에서 GitHub CLI로:

```bash
./scripts/github-bootstrap.sh
```

- 라벨: `type:*`, `area:*`, `priority:*`, `size:*`
- 마일스톤: `MVP-1` (P0 기능 묶음) — `docs/product/mvp.md` 기준

## Agent가 할 수 있는 것

`gh` CLI 인증이 되어 있으면 Agent 모드에서:

- `gh issue create` — 이슈 생성 (템플릿 필드 내용을 본문으로 조합)
- `gh api` / `gh milestone` — 마일스톤 생성·이슈 할당
- `gh pr create` — PR 생성 (`--base main`) + `Closes #n`

**예시 요청:**

> "MVP P0 여행방 생성 API 이슈랑 마일스톤 MVP-1에 넣어줘. 스펙도 docs/specs에 써줘."

## CI

`workflows/ci-cd.yml` — PR·`main` push 시 test, `main` push 시 GHCR deploy.

## 관련 문서

| 경로 | 용도 |
|------|------|
| [`docs/architecture/api-response.md`](../docs/architecture/api-response.md) | API JSON envelope 초안 (프론트 합의 전) |
| [`.cursor/rules/harness-workflow.mdc`](../.cursor/rules/harness-workflow.mdc) | S/M/T·스펙·검증 워크플로 |
