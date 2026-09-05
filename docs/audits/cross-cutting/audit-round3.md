# Cross-Cutting Architecture Audit — Round 3 (2026-08-27, SOLID/OOP 중심)

> **선행 문서 안내**: `docs/audits/cross-cutting/audit.md`(1차, 2026-08-05)와 `audit-round2.md`(2차, 2026-08-05)가 이미 존재하며, 두 라운드가 찾은 항목은 사용자 승인 후 구현·검증까지 끝났다(`refactor-log.md`). `auth`/`user`/`user-schedule`/`trip`/`notification` 3차 감사(`docs/audits/{auth,user,user-schedule,trip,notification}/audit-round3.md`)와 동일하게, 이번 3차는 **새로 요청받은 SOLID/OOP 관점**으로 `com.tripfit.tripfit.common` 패키지 전체(main 25개 파일)를 다시 전수 검토한 결과다. 1·2차가 이미 다룬 항목(죽은 `WebConfig` 삭제, `SocialTokenCrypto.secretKey` `volatile`, `SocialIntegrationLog` PII 마스킹, `OpenApiConfig` → `auth/config/` 이동 + ArchUnit `commonPackageDoesNotDependOnOtherDomains`, `SuccessResponse.of(T,String,String)` dead code 삭제, `GlobalExceptionHandler` catch-all PII 마스킹, `JpaConfig`/`SchedulingConfig` 미통합, `CommonErrorCode` 상수 선추가 안 함, 미발생 예외 핸들러 미추가 등)은 재검토만 하고 새 판단이 없으면 반복 서술하지 않는다.
>
> **1·2차 이후 가장 크게 바뀐 지점 — `common/holiday/*`(8개 파일: `HolidayProvider`·`RedisHolidayProvider`·`client`·`config`·`controller`·`dto`·`scheduler`·`service`)가 통째로 새로 생겼다(2026-08-18, `docs/specs/user-schedule/schedule-holiday-rest.md`/`schedule-holiday-list-api.md` Implemented).** 1·2차 감사 시점에는 `common`이 `api`/`config`/`domain`/`exception`/`logging`/`security` 6개 서브패키지뿐이었는데, 지금은 `holiday`까지 7개다. 이번 3차는 이 신규 패키지를 SOLID/OOP 렌즈(특히 패키지 응집도·checklist 9번)로 처음 감사했다 — 아래 C-1 참고.
>
> 이번 세션에서 이미 저장소 전역에 반영된 두 가지 공통 변경 — 모든 Service `@RequiredArgsConstructor` 사용, 모든 Entity **클래스 레벨** `@Setter` 제거(도메인 메서드로 상태 전이) — 은 전제로 두고 재발견하지 않았다. 확인 결과 `common` 패키지의 유일한 `@Service`(`HolidayQueryService`)는 이미 `@RequiredArgsConstructor`를 쓰고 있었고, `BaseTimeEntity`/`SoftDeleteEntity` 모두 클래스 레벨 `@Setter` 없이 `@Getter` + `markDeleted()`/`clearDeleted()` 도메인 메서드로만 상태를 바꾼다. `RedisHolidayProvider`/`HolidayApiClient`/`HolidaySyncScheduler`/`SocialTokenCrypto` 등 `@Component`가 수동 생성자를 쓰는 것은 예외가 아니다 — `spring-boot-java.md`의 `@RequiredArgsConstructor` 컨벤션은 **Service 레이어**에만 적용되고, 저장소 전체(`auth.oauth`·`notification.scheduler`·`trip.config` 등 25개 `@Component`)가 동일하게 수동 생성자를 쓰는 것을 grep으로 확인했다 — `common`만의 이슈가 아니다.

## 범위

- 패키지: `com.tripfit.tripfit.common` 전체 — `api`, `config`, `domain`, `exception`, `holiday/*`(`client`, `config`, `controller`, `dto`, `scheduler`, `service`), `logging`, `security` (main 25개 파일 전수 재검토)
- 테스트: `src/test/java/com/tripfit/tripfit/common/**` 11개 파일 전수 확인(사용처 검증·회귀 테스트 성격 파악)
- 교차 확인: `docs/architecture.md`의 `common/` 패키지 트리, `docs/specs/user-schedule/{schedule-holiday-rest,schedule-holiday-list-api}.md`(holiday 패키지 위치를 명시적으로 결정한 Approved 스펙), `user.schedule.service.{ScheduleService,ScheduleAvailabilityService}`·`trip.recommendation.algorithm.RecommendationEngine`(`HolidayProvider` 소비처), `auth.security.AppConfig`(`HolidayProperties`/`SocialTokenCryptoProperties`/`FcmProperties` 등록 지점), `src/test/java/com/tripfit/tripfit/architecture/ArchitectureTest.java`(`commonPackageDoesNotDependOnOtherDomains` 룰)
- 감사자: 현재 세션(신선한 컨텍스트, 이번 대화에서 `common` 도메인 코드를 수정한 적 없음), 읽기 전용
- 기준: `audit-checklist.md` 1~15항목 + 사용자 지정 우선 렌즈(SRP·OCP·LSP·ISP·DIP·캡슐화·God class/method·feature envy·inappropriate intimacy), `core-guardrails.md` ⛔ STOP

## ✅ A. 반드시 수정해야 하는 사항

이번 라운드에서 A 항목 없음 — Critical/High급 구조적 결함(버그·성능 회귀·보안 문제·명백한 SOLID 위반)을 찾지 못했다. 1·2차가 고친 CORS 이중 유지보수·JMM 위반·PII 마스킹 누락은 재발 없이 그대로 유지되고 있고, 신규 `holiday/*` 패키지도 `HolidayProvider` 인터페이스에 `RedisHolidayProvider` 구현체 1개만 의존하는 DIP·fail-open 계약이 건전하며, `RecommendationEngine`(trip)·`ScheduleService`/`ScheduleAvailabilityService`(user-schedule) 등 소비처는 전부 인터페이스(`HolidayProvider`)로만 의존해 `common`이 다른 도메인에 역방향 의존하지 않는 구조(ArchUnit `commonPackageDoesNotDependOnOtherDomains` 여전히 통과)를 확인했다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `GlobalExceptionHandler` — 400 INVALID_INPUT 매핑 핸들러 3개가 완전히 동일한 로직을 반복

- **Priority**: Low
- **Category**: Cleanup (중복 코드, checklist 1번)
- **문제**: `handleMessageNotReadable`(`HttpMessageNotReadableException`), `handleTypeMismatch`(`MethodArgumentTypeMismatchException`), `handleMissingParameter`(`MissingServletRequestParameterException`) 3개 `@ExceptionHandler` 메서드(`GlobalExceptionHandler.java:44-67`)는 파라미터로 받는 예외 타입만 다를 뿐, 본문이 3줄 그대로 완전히 동일하다 — `ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;` → `ResponseEntity.badRequest().body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()))`. 셋 다 예외 파라미터 자체를 전혀 사용하지 않는다(로깅도, 메시지 추출도 없음).
- **왜 문제인가**: 세 메서드가 "이 예외 타입이면 무조건 공통 INVALID_INPUT 400"이라는 완전히 동일한 규칙을 각자 따로 표현하고 있어, 네 번째로 같은 부류(예: 다음에 추가될 `UnsatisfiedServletRequestParameterException` 등)가 생길 때도 또 같은 3줄짜리 메서드가 복제될 위험이 크다. Spring의 `@ExceptionHandler`는 배열로 여러 예외 타입을 한 메서드에 묶는 것을 표준으로 지원하므로, 지금 상태는 프레임워크가 이미 제공하는 그룹화 기능을 안 쓰고 있는 것에 가깝다.
- **개선 방법**: 세 메서드를 `@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class}) ResponseEntity<ErrorResponse> handleClientInputError(Exception exception)` 하나로 합친다(세 예외 타입의 공통 상위 타입이 `Exception`뿐이라 파라미터 타입은 `Exception`으로 두되, 기존과 동일하게 미사용).
- **API 영향**: No Impact — 세 예외 각각에 대한 HTTP 상태(400)·응답 바디(`INVALID_INPUT`)는 변화 없음, 어떤 예외가 어떤 핸들러에 걸리는지도 Spring 입장에서 동일(더 구체적인 타입이 없어졌을 뿐 매칭 결과는 같음).
- **예상 변경 파일**: `common/exception/GlobalExceptionHandler.java`, `common/exception/GlobalExceptionHandlerTest.java`(3개 테스트 메서드가 `handler.handleMessageNotReadable(...)`/`handleTypeMismatch(...)`/`handleMissingParameter(...)`를 각각 직접 호출 중이라, 합쳐진 `handleClientInputError(...)` 호출로 변경 필요)
- **예상 변경 라인 수**: 약 -20 (프로덕션 코드), 테스트는 호출부 3곳 메서드명 치환
- **위험도**: Low — 순수 통합, 분기 로직 변화 없음.
- **테스트 영향도**: 기존 3개 테스트 케이스는 그대로 유지하되 호출 대상 메서드명만 바뀜 — 검증 내용(상태 코드·`code` 필드)은 동일.
- **예상 효과**: 같은 규칙(예외 타입 → 공통 INVALID_INPUT)을 표현하는 코드가 1곳으로 줄어, 다음에 같은 부류 예외가 추가될 때 기존 배열에 타입 하나만 추가하면 되는 구조가 됨.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`common/holiday/*`(8개 파일 — `client`/`config`/`controller`/`dto`/`scheduler`/`service` 전 레이어를 갖춘 완결된 미니 도메인)가 `common`(`docs/architecture.md`: "도메인 간 공유 **설정·예외·베이스 엔티티**") 아래 들어가 있어, `spring-boot-java.md`의 패키지 규칙("도메인 안 기능이 커지면 `{domain}/{feature}/`에 동일 레이어 세트를 둘 수 있다... **최상위 도메인으로 승격하지 않는 한 소유 도메인 안에 둔다**")이 상정하는 두 선택지(소유 도메인에 귀속 / 최상위 도메인으로 승격) 중 어느 쪽도 아닌 제3의 자리에 있다. `HolidayProvider`는 `user.schedule`(`ScheduleService`·`ScheduleAvailabilityService`)과 `trip.recommendation`(`RecommendationEngine`) 양쪽에서 소비되므로(grep 확인) 특정 도메인 하나가 "소유"한다고 보기 어렵고, `docs/architecture.md`의 `common/` 트리 예시(`api`/`config`/`domain`/`exception`/`logging`/`security`)에는 `holiday/`가 아예 나열조차 안 돼 있다(1·2차 라운드 시점엔 없던 패키지라 반영 자체가 안 됨). 다만 이 위치는 **우발적 drift가 아니라 Approved 스펙의 명시적 결정**이다 — `docs/specs/user-schedule/schedule-holiday-rest.md`가 "신규 패키지 `common/holiday/`"라고 못박고 그 이유(`BR-USER-008`: "일정은 User 전역 — 공휴일 판정도 trip과 무관하게 동일 적용")까지 문서에 남겼다. 즉 "어느 한 도메인 것도 아니니 공유 자리에 둔다"는 판단을 이미 사람이 내렸던 것 — 개선 방향은 (a) 최상위 `holiday/` 도메인으로 승격(패키지 선언·import 변경만으로 가능한 컴파일 수준 이동, 소비처 4개 main + 10개 test 파일 영향) 또는 (b) 최소한 `docs/architecture.md`의 `common/` 트리에 `holiday/` 서브트리를 추가해 drift만 해소, 둘 중 하나다. 어느 쪽이든 Approved 스펙 2건의 "신규 패키지 `common/holiday/`" 문구를 amend해야 하는 문서 정합성 이슈가 함께 따라와(`core-guardrails.md` STOP §1) 코드 리팩토링만으로 끝나지 않으므로, 사용자 승인 없이 이번 라운드에서 임의로 착수하지 않는다.
- **1·2차 `audit.md`/`audit-round2.md`의 C 항목 재검증 결과 — 대부분 여전히 유효, 일부는 이미 해결.** `SocialTokenCryptoProperties`(및 이번에 새로 생긴 `HolidayProperties`도 동일 스타일)가 `@Data` 대신 수기 getter/setter를 쓰는 것(유출 위험 없음, 단일 필드 `@ConfigurationProperties` 전례 유지), `SocialLogContext`의 5개 wither 메서드 보일러플레이트(8필드 규모에서 Builder 전환은 과잉), `SocialTokenCrypto.decrypt()`가 손상된 입력에 전용 예외 대신 `ArrayIndexOutOfBoundsException`을 던지는 것(실사용 경로 없음, `GlobalExceptionHandler` catch-all이 500으로 안전 처리) — 모두 코드를 다시 읽어 판단이 그대로 유효함을 확인했다. 반면 **`common/domain` Lombok 사용과 `spring-boot-java.md` "Lombok 미사용" 문서 간 drift**(1차 C, 2차 C에서 재확인)는 2026-08-08 반영(`refactor-log.md`)으로 이미 해소되어 지금은 문서·코드가 일치한다 — 더 이상 유효한 C 항목이 아니라서 이번엔 목록에서 제외.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`GlobalExceptionHandler`가 예외 타입이 늘 때마다 계속 커지는 구조(OCP) — 인터페이스·레지스트리로 대체하지 않는다.** `@RestControllerAdvice` + `@ExceptionHandler`는 Spring이 예외 타입별 디스패치(가장 구체적인 타입 우선 매칭)를 프레임워크 레벨에서 이미 구현해 제공하는 표준 관용구다. 이를 `ExceptionMapper` 인터페이스 + `Map<Class<? extends Exception>, ExceptionMapper>` 레지스트리로 감싸면, Spring이 내부적으로 이미 하는 "가장 구체적인 예외 타입 매칭" 로직(예: `MethodArgumentTypeMismatchException`이 상위 `TypeMismatchException`보다 우선 매칭)을 애플리케이션 코드로 재구현해야 하는데, 이 프로젝트의 예외 핸들러는 현재 6개뿐이고(B-1 반영 시 4개) 전부 `CommonErrorCode`/`TripFitException`이라는 좁은 계약만 다뤄 실질적으로 얻는 확장성 이득이 없다. 새 예외 타입이 10개 이상으로 늘거나 핸들러별 로직이 각각 복잡해질 때(현재는 전부 3줄 이내) 재검토할 사안이다.
- **1·2차 D 항목 전부 재확인 — 상황 변화 없음, 그대로 유지.** `JpaConfig`/`SchedulingConfig` 통합 안 함(각각 `@EnableJpaAuditing`·`@EnableScheduling`+`@EnableAsync` 단일 관심사, 실사용 확인됨), `BaseTimeEntity`/`SoftDeleteEntity`에 `equals`/`hashCode` 미추가(LAZY 연관관계·프록시 함정 회피, Object identity 유지가 더 안전), `ErrorCode.toErrorResponse()` 미추가(`exception` 패키지가 `api` 응답 포맷을 알아야 하는 역방향 결합 방지), `CommonErrorCode` 상수 선추가 안 함(현재 2개만 실사용, 도메인별 `{Domain}ErrorCode`가 이미 충분히 커버 — grep 재확인), `ConstraintViolationException`/`AccessDeniedException`/`HttpRequestMethodNotSupportedException`/`NoResourceFoundException` 핸들러 미추가(이 예외들을 던지는 코드가 `main`에 여전히 0건 — `@Validated` 미사용 재확인), `PiiMasker` 이메일 전용 스코프 유지(스펙이 정의한 범위 밖 PII 패턴이 실사용처에서 관측되지 않음) — 각 사유는 `audit.md`/`audit-round2.md` D절과 동일하며 이번 라운드에서 이를 뒤집을 새 근거를 찾지 못했다.

## 15. 백엔드 아키텍처 개선 제안

1·2차 §15의 제안들을 재확인한 결과 상태 변화 없음 — 새 SOLID/OOP 렌즈에서도 이 패키지에 새로 제안할 아키텍처 카테고리는 없다.

- **Concurrency / Security 로깅 파이프라인**: 1·2차 판단(A-2 `volatile`·A-3/B-1 PII 마스킹으로 이미 반영) 유지. 추가 제안 없음.
- **Redis/캐싱**: 1·2차 판단(캐싱 대상 반복 조회 없음) 유지, 다만 `holiday/*`가 이미 Redis를 캐시 계층으로 쓰고 있음을 확인(신규 관찰 — `RedisHolidayProvider`가 연도별 Set 키 + TTL 7일 + staging-then-rename 원자 교체로 fail-open을 구현, 별도 개선 여지 없음). **Never**(추가 도입 불필요).
- **Event Architecture**: 1·2차 판단(이벤트 발행 주체 아님) 유지. **Never**.
- **Monitoring/Tracing**: 2차 판단(Loki가 이미 컨테이너 stdout 전체 수집 중, `common` 전용 Micrometer 메트릭 도입 근거 부족) 유지. **Never**.
- **Resilience(Retry/CircuitBreaker)**: 1·2차 판단(`SocialTokenCrypto`는 로컬 연산, provider 재시도는 도메인 스케줄러가 담당) 유지 — `HolidayApiClient`도 동일 논리: 실패 시 `HolidaySyncScheduler`가 해당 연도 갱신만 건너뛰고 기존 캐시를 유지하는 fail-open 설계라 재시도 인프라가 없어도 장애가 사용자에게 전파되지 않는다. **Never**.
- **API 계약**: 1·2차 판단(내부 구현·로깅·패키지 구조 문제만 발견, API 계약 영향 없음) 유지. 이번 라운드도 동일. **Never**.

## 승인 대기

사용자 승인 후 B-1(`GlobalExceptionHandler` 400 핸들러 3개 통합)만 구현합니다(A 없음). C-1(`holiday` 패키지 위치)은 Approved 스펙 2건의 명시적 결정과 맞물려 있어 착수 전 사용자 확인이 별도로 필요합니다. C/D 나머지는 이번 라운드에서 수정하지 않습니다.
