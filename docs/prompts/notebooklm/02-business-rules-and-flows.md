# NotebookLM 프롬프트 02 — 비즈니스 규칙 · 플로우

> **생성 대상:** `docs/product/business-rules/*.md`, `docs/product/flows/*.md`  
> **선행 권장:** [01-product-foundation.md](01-product-foundation.md) 실행 후, PRD·MVP·용어집 출력을 채팅에 함께 붙여넣기  
> **다음 단계:** [03-erd.md](03-erd.md)

---

아래 블록 전체를 NotebookLM에 복사해 붙여넣으세요.

```
당신은 TripFit 제품의 테크니컬 PM입니다.
이 노트북의 기획 자료와, 아래에 붙여넣은 PRD·MVP·용어집(있는 경우)을 근거로 비즈니스 규칙과 사용자 플로우 문서를 작성해 주세요.

[선택: 01 프롬프트 결과인 prd.md, mvp.md, glossary.md 내용을 여기에 붙여넣기]

## 공통 규칙

1. 업로드 자료·붙여넣은 문서에 없는 내용은 `[미정]` 또는 `[기획 자료에 없음]`으로 표시하세요.
2. 자료 충돌 시 충돌 내용과 채택 이유를 명시하세요.
3. 구현 코드(Java, API 상세, SQL)는 작성하지 마세요.
4. 한국어로 작성하세요.
5. 출력은 `## docs/...` 경로 제목으로 파일을 구분하세요.

---

## 작성할 파일

### docs/product/business-rules/trip.md

트립·일정 관련 규칙. 규칙 ID: `BR-TRIP-001`, `BR-TRIP-002`, …

각 규칙:
- **조건:** 언제 적용되는지
- **동작:** 시스템이 어떻게 해야 하는지
- **위반 시:** 에러·거부·대체 동작

기획서의 제약, 한도, 검증, 예외 케이스를 규칙으로 변환하세요.

### docs/product/business-rules/ (추가 도메인)

트립 외 도메인(예: 사용자, 예약, 결제, 알림) 규칙이 있으면
`docs/product/business-rules/{도메인}.md`를 추가 작성하세요.
규칙 ID: `BR-{DOMAIN}-{번호}` (예: `BR-USER-001`).

해당 도메인이 없으면 trip.md에만 통합하고 이유를 한 줄 적으세요.

### docs/product/flows/

주요 사용자 플로우를 기능별 파일로 작성하세요.
파일명 예: `trip-create.md`, `trip-join.md`, `schedule-edit.md`

MVP In Scope 기능마다 최소 1개 플로우. 각 파일 구조:
- **목적**
- **액터** (누가)
- **사전 조건**
- **단계** (번호 목록)
- **성공 종료 조건**
- **예외 / 분기** (관련 BR-ID 참조)
- **MVP 포함 여부** (In / Out)

플로우가 1~2개뿐이면 `docs/product/flows/README.md` 하나에 통합해도 됩니다.

---

## 출력 형식

---
## docs/product/business-rules/trip.md
(전체 마크다운)

---
## docs/product/business-rules/{도메인}.md
(해당 시)

---
## docs/product/flows/{flow-name}.md
(플로우별)

---

## 마지막 요약 (짧게)

1. `[미정]` 처리한 규칙·플로우
2. 기획자 확인이 필요한 BR-ID 목록
3. MVP Out of Scope인데 플로우에 포함된 항목 (있으면)
4. 생성한 business-rules 파일 목록과 flows 파일 목록
```
