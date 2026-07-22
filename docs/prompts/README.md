# TripFit 문서 프롬프트

기획자료(NotebookLM) → repo `docs/` 반영 워크플로.

## 전제

- NotebookLM 노트북 = **기획자가 올린 자료만** (GitHub·repo 연동 없음)
- **repo 파일은 NotebookLM에 붙여넣지 않음**
- NotebookLM **출력**만 AI 코딩 에이전트에 가져와 `docs/`에 저장
- repo 반영·충돌 점검은 [agent-import-checklist.md](agent-import-checklist.md) (AI 코딩 에이전트에서)

## NotebookLM 실행 순서

| 순서 | 파일 | NotebookLM에 넣는 것 | 생성물 |
|:----:|------|----------------------|--------|
| **1** | [notebooklm/01-product-foundation.md](notebooklm/01-product-foundation.md) | 프롬프트만 | `prd.md`, `mvp.md`, `glossary.md` |
| **2** | [notebooklm/02-business-rules-and-flows.md](notebooklm/02-business-rules-and-flows.md) | 프롬프트 + **1단계 출력** | `business-rules/*`, `flows/*` |
| **3** | [notebooklm/03-erd.md](notebooklm/03-erd.md) | 프롬프트 + **1·2단계 출력** | `architecture/erd.md` |
| **4** | [notebooklm/04-design-wireframe.md](notebooklm/04-design-wireframe.md) | 프롬프트만 (또는 + 1단계 PRD) | `design/figma-wireframe-v1.md` |

### 각 단계 방법

1. 해당 `.md` 파일을 연다.
2. ` ``` ` 코드 블록 **전체**를 복사한다.
3. NotebookLM **새 채팅**(또는 같은 노트북)에 붙여넣는다.
4. 2·3단계: 이전 단계 **NotebookLM이 방금 출력한 마크다운**을 프롬프트 안 `[선택: …]` 자리에 붙여넣는다. (repo 파일 아님)
5. 출력에서 `## docs/...` 블록별로 파일을 나눠 로컬에 저장해 둔다.
6. 4단계는 Figma·화면 자료가 노트북에 있을 때만. 없으면 생략.

### 2·3단계에서 이전 출력 붙이는 이유

NotebookLM은 **같은 채팅에 붙인 텍스트**만 추가 컨텍스트로 씁니다. 1단계 PRD·MVP와 2단계 BR·플로우가 맞물리게 하려면, 2·3단계 프롬프트에 **직전 NotebookLM 출력**을 넣으면 됩니다.

## 에이전트 실행 순서 (NotebookLM 끝난 뒤)

1. NotebookLM 출력을 `docs/product/`, `docs/architecture/erd.md` 등 **해당 경로에 저장**
2. [agent-import-checklist.md](agent-import-checklist.md) 따라 엔지니어링 SSOT와 충돌·갭 확인
3. `[충돌]`·P0 `[미정]` → 사용자 확인 후 문서 amend
4. DB·인증 등 구현 영향 있으면 `specify` → `docs/specs/` → 구현

## NotebookLM이 다루지 않는 문서

| 경로 | 담당 |
|------|------|
| `docs/product/waves.md` | 개발팀 SSOT — NotebookLM 출력에 wave **정의** 복사 금지, 링크만 |
| `docs/product/platform.md` | 인프라·API 전제 (확정) |
| `docs/decisions/*`, `docs/specs/*` | 엔지니어링 — AI 코딩 에이전트에서 충돌만 검사 |
| `docs/architecture.md`, `api-response.md` | 레이어·API envelope |

상세: [notebooklm/README.md](notebooklm/README.md)
