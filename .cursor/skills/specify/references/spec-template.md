# [Feature Name]

> 상태: Draft | Approved | Implemented  
> MVP: In scope | Out of scope | TBD  
> 관련 BR: BR-xxx (없으면 N/A)

## 목표

[한 문장 — 이 기능이 사용자/시스템에 주는 가치]

## 배경

- 왜 지금 필요한가
- 관련 문서: `docs/product/...`, Figma 화면 ID

## 요구사항

### Must Have

- [ ] 
- [ ] 

### Nice to Have

- [ ] 

### Out of Scope (이번 스펙에서 하지 않음)

- 

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| | | | |

요청/응답 예시 — envelope **초안**: [`docs/architecture/api-response.md`](../../architecture/api-response.md) (프론트 합의 전)

성공 (단순):

```json
{
  "data": {}
}
```

실패 (404):

```json
{
  "code": "TRIP_NOT_FOUND",
  "message": "여행방을 찾을 수 없습니다."
}
```

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md`
- 신규/변경 테이블·컬럼:

```
[엔티티·컬럼 개요]
```

- Soft delete / enum / FK 정책

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| | | |

## 검증 시나리오

구현·테스트·PR 리뷰의 기준. 엣지 케이스는 `[미정]` 대신 시나리오로 명시.

### 정상

- [ ] 
- [ ] 

### 엣지 · 실패

- [ ] (예: 초대 코드 중복 → 409)
- [ ] (예: 권한 없음 → 403)

### 수동 / 통합 (해당 시)

- [ ] (예: `curl` 또는 `@WebMvcTest`로 확인할 항목)

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] [기능별 수용 조건]
- [ ] OpenAPI/Swagger 반영 (API 추가 시)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| | [미정] / 확정 | |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| YYYY-MM-DD | 초안 |
