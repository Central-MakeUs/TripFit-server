# 003 — 전술 DDD (단일 모듈 레이어드)

- **상태:** 확정
- **날짜:** 2026-07-06
- **관련:** `docs/architecture.md`, `.cursor/rules/spring-boot-java.mdc`

## 맥락

TripFit 백엔드는 Spring Boot 단일 Gradle 모듈. 팀 규모·MVP 범위상 **풀 DDD 인프라**(이벤트 소싱, 모듈 분리)는 과함.  
대신 **레이어드 + 전술 DDD**로 BR-*가 Service에만 몰리는 Anemic Domain을 막고, 프론트와 맞는 API 경계를 유지한다.

## 결정

1. **바운디드 컨텍스트 1개** — TripFit 전체를 하나의 모노리스 API로 운영 (MVP).
2. **애그리거트 루트**: `User`(+`UserCondition`), `Trip`(멤버·일정·추천 포함).
3. **Domain**에 불변식·상태 전이, **Application Service**에 유스케이스·트랜잭션, **Repository**는 루트만.
4. API 계약은 **`dto/` + api-response envelope** — Domain을 HTTP 밖으로 노출하지 않음.

## 고려한 대안

| 대안 | 장점 | 단점 |
|------|------|------|
| **Anemic (Service에만 로직)** | 구현 빠름 | BR 분산·테스트 어려움 |
| **전술 DDD (채택)** | BR 응집, Agent·팀 규칙 명확 | 초기 메서드 설계 비용 |
| **멀티 모듈 DDD** | 컨텍스트 분리 | MVP 오버헤드 |

## 트레이드오프 · 후속 리스크

- `Trip` 애그리거트가 커지면 성능·동시성 이슈 — 필요 시 `TripMember` 분리 등 `decisions/` 재검토.
- 애그리거트 표는 ERD 확정에 따라 스펙에서 조정 가능 — 변경 시 `architecture.md` 동기화.

## 후속 작업

- [ ] 첫 유스케이스 API부터 Entity 메서드·Domain 예외 패턴 확립
- [ ] `GlobalExceptionHandler` + `ErrorCode`로 Domain 예외 → envelope 매핑
