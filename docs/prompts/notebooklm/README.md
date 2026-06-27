# NotebookLM 기획 문서 생성 프롬프트

TripFit 기획 자료(NotebookLM 노트북 업로드본)를 repo 문서로 정리할 때 사용합니다.

## 실행 순서

| 순서 | 프롬프트 파일 | 생성 대상 | 붙여넣을 경로 |
|------|---------------|-----------|---------------|
| 1 | [01-product-foundation.md](01-product-foundation.md) | PRD, MVP, 용어집 | `docs/product/` |
| 2 | [02-business-rules-and-flows.md](02-business-rules-and-flows.md) | 비즈니스 규칙, 플로우 | `docs/product/business-rules/`, `docs/product/flows/` |
| 3 | [03-erd.md](03-erd.md) | ERD | `docs/architecture/erd.md` |

## 사용 방법

1. NotebookLM에 기획 관련 문서를 모두 업로드한다.
2. **01** 프롬프트 전체를 복사해 새 채팅에 붙여넣고 실행한다.
3. 출력을 해당 `docs/product/` 파일에 복사한다.
4. **02**, **03**도 같은 방식으로 순서대로 실행한다.
5. **03 실행 전** (권장): 01·02에서 작성된 내용을 채팅에 함께 붙여넣으면 ERD 정합성이 높아진다.

## 공통 규칙 (모든 프롬프트에 포함)

- 업로드 자료에 없는 내용은 추측하지 않고 `[미정]` 또는 `[기획 자료에 없음]` 표시
- 자료 충돌 시 충돌 내용과 채택 이유 명시
- 구현 코드(Java 등)는 작성하지 않음
- 한국어로 작성
- 출력은 `## docs/...` 경로 제목으로 파일 구분

## 후속 작업 (Cursor)

문서 반영 후 기능 구현 시:

- `docs/product/mvp.md`로 범위 확인
- `docs/architecture/erd.md`로 엔티티·관계 참조
- `specify` 스킬로 `docs/specs/{feature}.md` 작성 후 구현
