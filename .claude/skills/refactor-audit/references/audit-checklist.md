# Audit Checklist

`refactor-audit` 스킬의 1단계(Audit) 서브에이전트 프롬프트에 그대로 포함하는 점검 항목 15개. 절대 원칙·A/B/C/D 분류·산출물 포맷은 [`SKILL.md`](../SKILL.md)·[`audit-template.md`](audit-template.md)가 SSOT — 여기서는 중복하지 않는다.

## 1. 중복 코드 제거

중복 validation · 중복 try-catch · 중복 logging · 중복 DTO 변환 · 중복 Exception 생성 · 중복 Repository 호출 · 중복 Stream 코드 · 중복 Optional 처리.

가능하면 private method · Utility · Component · AOP · 공통 Service로 개선. **과도한 추상화는 금지.**

## 2. Dead Code 제거

사용되지 않는 클래스 · 메서드 · DTO · Entity Field · Repository Method · Config · Bean · Enum · Constant. 삭제 가능하면 이유를 설명.

## 3. Legacy 코드 제거

deprecated 로직 · 임시 코드 · TODO · FIXME · 더 이상 호출되지 않는 분기.

## 4. Spring Best Practice

`@Transactional` 위치 · `readOnly` 적용 여부 · 생성자 주입 · Bean Scope · Optional 사용 · Stream 남용 여부 · N+1 가능성 · Fetch 전략 · Entity 직접 반환 여부 · DTO 변환 위치 · Mapper 개선 · Validation 위치 · Exception 처리 · Controller/Service/Repository 책임.

## 5. AOP 적용 가능성

반복되는 Logging · Execution Time · Validation · Authorization · Audit가 있으면 AOP 분리 가능성을 평가한다. 무조건 적용하지 말고 **"적용 가치가 있는 경우"에만** 제안.

## 6. 성능 최적화

불필요한 객체 생성 · 불필요한 Stream · 중복 DB 조회 · 동일 데이터 반복 조회 · 불필요한 Optional · Collection 순회 중복 · O(n²) 코드 · 메모리 낭비.

## 7. JPA 점검

Lazy/Eager · Cascade · orphanRemoval · equals/hashCode · Entity 변경 감지 · 불필요한 `save()` 호출 · `flush()` 남용 · JPQL 최적화 · Projection 가능 여부.

## 8. Exception 구조

RuntimeException 남용 · Exception 중복 · GlobalExceptionHandler · ErrorCode 설계 · Logging 레벨.

## 9. 패키지 구조

책임이 맞는 위치인가 · 너무 큰 Service · God Object · Utility 남용 · Config 분리.

## 10. 보일러플레이트 제거

반복 Builder · 반복 생성자 · 반복 Mapping · 반복 null 체크 · 반복 if.

## 11. Naming

클래스명 · 메서드명 · 변수명 · 패키지명이 Spring 관례에 맞게 개선 가능한지.

## 12. 불필요한 의존성 제거

사용되지 않는 Gradle Dependency · 라이브러리 · Starter.

## 13. Security

민감정보 로그 · JWT 처리 · Validation 누락 · SQL Injection 위험 · Path Traversal · Null 처리.

## 14. 테스트 가능성

Mocking하기 어려운 구조인지, DI 개선이 필요한지.

## 15. 백엔드 아키텍처 개선 제안 (선택 — 실제 적용 가치가 있는 경우만)

"최신 기술이라서"·"많은 회사가 쓰니까"는 금지. 아래 카테고리 중 **이 도메인에 실제 효과가 있다고 판단되는 것만**, 각각 왜 필요한지 · 도입 시 장단점 · 구현 난이도 · **Now/Later/Never** + 이유를 포함해 제안한다.

| 카테고리 | 예시 기술 |
|----------|-----------|
| Concurrency | Optimistic/Pessimistic Lock, Distributed Lock(Redis), Version Column |
| Redis | Cache, Session, Refresh Token, Rate Limiting, Distributed Lock, Pub/Sub |
| Event Architecture | Domain/Application/Transaction Event, Outbox Pattern |
| Async Processing | `@Async`, Message Queue, Kafka, RabbitMQ |
| Database | Index 개선, Query 최적화, Projection, Batch 처리 |
| Monitoring | Micrometer, Prometheus, Grafana, Loki, OpenTelemetry, Distributed Tracing |
| Resilience | Retry, Circuit Breaker, Timeout, Bulkhead |
| Security | Rate Limiting, Idempotency Key, CSRF, XSS, Audit Log |
| API | Cursor Pagination, ETag, Conditional Request, Compression |
