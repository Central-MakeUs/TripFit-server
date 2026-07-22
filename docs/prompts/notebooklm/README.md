# NotebookLM 프롬프트 (01~04)

노트북 **기획 자료만**으로 repo 형식의 마크다운을 생성합니다.  
**repo·GitHub 내용은 NotebookLM에 넣지 않습니다.**

실행 순서·저장 경로: 상위 [../README.md](../README.md)

## 프롬프트 목록

| # | 파일 | 생성 대상 |
|---|------|-----------|
| 01 | [01-product-foundation.md](01-product-foundation.md) | `prd.md`, `mvp.md`, `glossary.md` |
| 02 | [02-business-rules-and-flows.md](02-business-rules-and-flows.md) | `business-rules/*`, `flows/*` |
| 03 | [03-erd.md](03-erd.md) | `architecture/erd.md` |
| 04 | [04-design-wireframe.md](04-design-wireframe.md) | `design/figma-wireframe-v1.md` (선택) |

## 입력 규칙

| 허용 | 불가 |
|------|------|
| 노트북에 업로드된 기획 자료 | repo `docs/` 파일 |
| **이전 단계 NotebookLM 출력** (2·3단계) | `decisions/`, `specs/` 전문 |
| Figma URL·화면 설명 (노트북에 있는 것) | 구현 코드·JPA·SQL |

## 공통 출력 규칙

- 업로드 자료에 없음 → `[미정]` / `[기획 자료에 없음]`
- 자료 간 충돌 → 채택 근거 + `## 기획 메모 (NotebookLM)`
- 파일 상단 → `> NotebookLM 기획 자료 정리본.`
- 출력 구분 → `## docs/...` 경로 제목
- Java·Spring·REST 상세 스펙 작성 금지

## waves · 엔지니어링 경계

- `mvp.md`: [`waves.md`](../../product/waves.md) **링크 + wave↔기능 매핑 표**만. wave 1~4 정의 문구 복사 금지.
- ERD(03): 프롬프트에 적힌 **백엔드 확정 사항**(refresh_token 등)은 기획서에 없어도 포함. 그 외 인프라·TTL·배포는 [import-checklist](../agent-import-checklist.md)에서 검증.

## 에이전트 후속

NotebookLM 출력 저장 후 → [../agent-import-checklist.md](../agent-import-checklist.md)
