# GitHub 워크플로

Issue · PR · Milestone · CI를 TripFit 하네스(`docs/`, `.cursor/`)와 연결합니다.

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
- `gh pr create` — PR 생성 + `Closes #n`

**예시 요청:**

> "MVP P0 여행방 생성 API 이슈랑 마일스톤 MVP-1에 넣어줘. 스펙도 docs/specs에 써줘."

## CI

`workflows/ci-cd.yml` — PR·main push 시 test, main push 시 GHCR deploy.
