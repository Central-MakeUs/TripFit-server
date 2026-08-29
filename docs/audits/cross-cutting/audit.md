# cross-cutting Architecture Audit — 2026-08-05

## 범위

- 패키지: `com.tripfit.tripfit.common` (+ 하위 패키지: api, config, domain, exception, logging, security)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP
- 교차검증: `src/main/java/com/tripfit/tripfit/` 전체(auth·user·user/schedule·trip·notification) + `src/test/java/`에서 `common/` 클래스·메서드 실사용 grep, `git log` 히스토리 확인

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `WebConfig`의 CORS 등록이 완전히 죽은 코드 — `SecurityConfig`가 이미 전역 처리
- **Priority**: High
- **Category**: Dead Code / Cleanup
- **문제**: `common/config/WebConfig.java`는 `WebMvcConfigurer.addCorsMappings()`로 CORS를 등록한다. 그런데 `auth/security/SecurityConfig.java`가 `HttpSecurity.cors(cors -> cors.configurationSource(corsConfigurationSource()))`로 **Spring Security 필터 체인 안에 `CorsFilter`를 이미 등록**하고 있고, 이 필터가 `/**` 전체를 커버한다(관리 포트 분리 없음, 단일 `SecurityFilterChain`).
- **왜 문제인가**: Spring의 `DefaultCorsProcessor.processRequest()`는 응답에 `Access-Control-Allow-Origin` 헤더가 이미 있으면(Security의 `CorsFilter`가 먼저 처리) MVC HandlerMapping 단계의 CORS 처리를 스킵한다. 즉 `WebConfig`의 `addCorsMappings`는 필터 체인이 요청을 가로채는 이 프로젝트 구조에서 **한 번도 실행될 일이 없다**. 실제로 `git log`를 보면 CORS 관련 커밋(`53a9849 Fix: CORS 허용 메서드에 PATCH 추가`, `80a3087`/`bd3ddac` 로컬 IP 추가/제거)이 **매번 `WebConfig`와 `SecurityConfig` 두 파일을 동시에 수정**해왔다 — 죽은 코드를 살아있다고 믿고 이중 유지보수해온 것. 그 결과 현재 `WebConfig`의 allowedOrigins(3개: tripfit.online, www, localhost:3000)는 `SecurityConfig`(4개: 위 3개 + `https://api.tripfit.online`)와 **이미 drift**된 상태다.
- **개선 방법**: `WebConfig.java` 삭제(CORS의 SSOT는 `SecurityConfig.corsConfigurationSource()` 하나로 통일). 이후 CORS 변경은 `SecurityConfig` 한 곳만 수정.
- **API 영향**: No Impact (요청 처리 결과는 이미 `SecurityConfig`가 전담 중이므로 실제 CORS 응답 헤더에 변화 없음)
- **예상 변경 파일**: `common/config/WebConfig.java` (삭제, 24줄)
- **예상 변경 라인 수**: -24
- **위험도**: Low
- **테스트 영향도**: 현재 CORS를 직접 검증하는 테스트가 없음(grep 확인) — 회귀 위험 없음. 삭제 후 원한다면 `SecurityConfig` 대상 CORS 헤더 검증 테스트를 별도로 추가 권장(본 라운드 범위 밖).
- **예상 효과**: 이중 유지보수·drift 재발 방지. 향후 CORS 변경 시 실수로 한쪽만 고치는 사고 원천 차단.

### A-2. `SocialTokenCrypto.secretKey` — `volatile` 누락으로 Double-Checked Locking 깨짐
- **Priority**: Medium
- **Category**: Security / Concurrency
- **문제**: `common/security/SocialTokenCrypto.java`의 `secretKey()`는 전형적인 double-checked locking(`if (secretKey != null) return; synchronized { if (secretKey != null) return; ...; secretKey = decodeKey(...); }`)을 쓰지만, 필드 선언이 `private SecretKeySpec secretKey;`로 **`volatile`이 아니다**.
- **왜 문제인가**: Java Memory Model상 `volatile` 없는 double-checked locking은 다른 스레드가 완전히 초기화되지 않은(또는 stale) 참조를 관측할 수 있는 고전적 버그다. `SocialTokenCrypto`는 `@Component`(싱글턴)이고 `auth.service.GoogleLoginCredentialService`, `auth.service.AppleCredentialService`, `user.googlecalendar.service.GoogleCalendarService`, `user.service.UserWithdrawalService` 4개 도메인에서 동시다발적으로 `encrypt`/`decrypt`를 호출하므로 동시 요청 초기 구간에 이론상 재현 가능한 실제 버그다.
- **개선 방법**: `private volatile SecretKeySpec secretKey;`로 1단어만 추가.
- **API 영향**: No Impact
- **예상 변경 파일**: `common/security/SocialTokenCrypto.java`
- **예상 변경 라인 수**: 1
- **위험도**: Low
- **테스트 영향도**: 기존 동작과 100% 동일(정상 경로에서는 눈에 띄는 차이 없음) — 회귀 테스트 불필요. A-3와 묶어 `SocialTokenCrypto` 단위 테스트를 새로 만들 때 함께 커버 권장.
- **예상 효과**: JMM 위반 제거, 잠재적 초기 구간 레이스 컨디션 제거.

### A-3. `SocialIntegrationLog`가 Throwable을 그대로 로깅해 PiiMasker 마스킹을 우회함
- **Priority**: High
- **Category**: Security
- **문제**: `common/logging/PiiMasker.java`의 클래스 주석은 "provider 에러 바디 등 외부 응답 문자열에서 이메일을 마스킹 — **로그·last_sync_error 컬럼 저장 전 공통으로 거친다**"고 명시한다. 그러나 실제로 마스킹이 강제되는 경로는 `SocialLogContext.withProviderError()`(MDC `providerErrorMessage` 필드)와 `GoogleCalendarService.applySyncError()`(DB 컬럼) 뿐이다. `SocialIntegrationLog.warn(logger, context, message, throwable)` / `.error(...)`처럼 **원본 `Throwable` 객체를 그대로 SLF4J에 넘기는 호출이 18곳** (`AppleTokenVerifier`, `AppleNotificationVerifier`, `GoogleCalendarService.sync()` 등)에 있는데, 이 경로는 마스킹을 전혀 거치지 않는다.
- **왜 문제인가**: `logback-spring.xml`은 `com.tripfit.tripfit.auth.oauth` / `auth.service` / `user.googlecalendar` / `user.client` 패키지에 `LogstashEncoder` 기반 `STRUCTURED_JSON` appender를 붙여 Loki로 보낸다. `logger.warn(message, throwable)` 호출 시 LogstashEncoder는 `throwable.getMessage()` + 전체 스택트레이스를 `stack_trace` JSON 필드로 그대로 직렬화한다 — provider 응답 바디에 담긴 이메일 등 PII가 마스킹 없이 구조화 로그(Loki)에 영구 적재될 수 있다. `GoogleCalendarService.sync()`의 두 catch 블록이 정확히 이 패턴이다: 같은 `exception`을 라인 247/256에서 `SocialIntegrationLog.warn(..., exception)`(마스킹 없음)으로 로깅한 뒤, 라인 261에서만 `PiiMasker.mask(exception.getMessage())`를 거쳐 DB에 저장한다 — **DB는 마스킹, 로그는 미마스킹**인 비일관 상태.
- **개선 방법**: `SocialIntegrationLog`의 `Throwable`을 받는 오버로드에서, 로깅 직전에 예외 메시지를 `PiiMasker.mask()`로 감싼 뒤(스택트레이스·cause 체인은 보존) 로깅하도록 내부에서 처리. 호출부(18곳) 변경 없이 `common/logging/SocialIntegrationLog.java` 한 파일만 고쳐서 전체 마스킹을 강제할 수 있다. (구현 시 원본 예외 클래스명이 로그에 그대로 남는지 vs `Throwable`로 래핑되며 유실되는지는 트레이드오프이므로, 구현 라운드에서 방식 확정 필요 — 예: 메시지만 마스킹한 새 `Throwable(maskedMessage, cause).setStackTrace(original.getStackTrace())`로 감싸는 방식.)
- **API 영향**: No Impact (로그 포맷 내부 변경일 뿐 HTTP 응답·DB 컬럼과 무관)
- **예상 변경 파일**: `common/logging/SocialIntegrationLog.java`
- **예상 변경 라인 수**: 15~25
- **위험도**: Medium (로그 가독성·예외 클래스명 노출 방식이 바뀌므로 구현 시 신중한 설계 필요; 기능적 회귀는 없음)
- **테스트 영향도**: `SocialIntegrationLog`에 대한 기존 테스트가 전무함(아래 B-2) — 이 변경과 함께 "이메일이 포함된 예외 메시지가 마스킹되어 로깅된다"는 단위 테스트 신설 필요.
- **예상 효과**: PiiMasker의 문서화된 보장("로그 저장 전 공통 마스킹")이 실제로 지켜짐. Loki 등 로그 집계 플랫폼으로의 PII 유출 경로 차단.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `OpenApiConfig`(common)이 `auth` 도메인에 역방향 의존 — 패키지 구조 위반
- **Priority**: Medium
- **Category**: Architecture
- **문제**: `common/config/OpenApiConfig.java`가 `import com.tripfit.tripfit.auth.jwt.AuthorizedUser;`로 `auth` 도메인의 어노테이션을 직접 참조한다(`publicEndpointSecurityCustomizer()`가 파라미터에 `@AuthorizedUser`가 있는지로 JWT 필요 여부를 판단).
- **왜 문제인가**: `docs/architecture.md`는 `common`을 "도메인 간 공유 설정·예외·베이스 엔티티"로 정의한다 — 즉 **다른 도메인이 common에 의존하는 단방향** 구조가 전제다. `common`이 거꾸로 `auth`라는 특정 비즈니스 도메인을 import하면 이 전제가 깨진다. `@AuthorizedUser`는 이미 auth·user·trip·notification 4개 도메인 컨트롤러에서 널리 쓰이는 사실상 cross-cutting 개념인데 `auth/jwt/`에 위치해, `common`이 다시 그걸 참조하는 순환적 결합이 생겼다. 이 방향의 의존을 막는 ArchUnit 규칙도 현재 없다(`ArchitectureTest`에 `common`↔domain 방향 검증 부재).
- **개선 방법**: `OpenApiConfig.java`를 `auth/config/`로 이동(Bearer JWT 스킴·`@AuthorizedUser` 커스터마이저가 본질적으로 인증 도메인 관심사이므로). `@Configuration` 클래스는 컴포넌트 스캔 베이스 패키지(`com.tripfit.tripfit`)만 유지되면 서브패키지 이동은 스프링 부팅에 영향 없음(다른 파일에서 `OpenApiConfig` 클래스를 직접 import하는 곳 없음 — grep 확인 완료, `BEARER_JWT` 상수도 내부에서만 사용). 이동과 함께 `ArchitectureTest`에 "`common` 패키지는 다른 도메인 패키지에 의존하지 않는다" ArchUnit 룰 추가 권장(재발 방지).
- **API 영향**: No Impact (Swagger 산출물·Bearer 스킴·자물쇠 로직 100% 동일, 파일 위치만 이동)
- **예상 변경 파일**: `common/config/OpenApiConfig.java` → `auth/config/OpenApiConfig.java` (이동), `architecture/ArchitectureTest.java` (룰 추가, 선택)
- **예상 변경 라인 수**: 파일 이동(패키지 선언 1줄) + ArchUnit 룰 10줄 내외
- **위험도**: Low
- **테스트 영향도**: `OpenApiSpecExportTest`가 `/v3/api-docs` 응답을 그대로 export하므로 산출물 불변 검증됨. 컴파일만 통과하면 회귀 없음.
- **예상 효과**: `common`이 실제로 "도메인 무관 공유 계층"이라는 문서화된 설계 의도를 코드로 되찾음. 향후 다른 개발자가 "common은 뭘 import해도 되는 계층"이라고 오인해 같은 패턴을 반복하는 것을 ArchUnit이 막아줌.

### B-2. `common` 패키지 테스트 커버리지 공백 — 특히 보안 핵심 클래스
- **Priority**: Medium
- **Category**: Testability
- **문제**: `src/test/java/com/tripfit/tripfit/common/`에는 `OpenApiSpecExportTest`(스펙 export용)와 `TestcontainersConfig`(테스트 헬퍼) 외에 **단위 테스트가 하나도 없다**. `PiiMasker`, `SocialTokenCrypto`(암복호화), `SocialIntegrationLog`/`SocialLogContext`(MDC 마스킹), `GlobalExceptionHandler`, `ErrorResponse`/`SuccessResponse`/`FieldError` 모두 직접 테스트가 없고, 다른 도메인 테스트(`AppleCredentialServiceTest`, `GoogleCalendarServiceTest` 등)에서는 `SocialTokenCrypto`가 전부 **mock**되어 실제 암복호화 왕복(round-trip)·잘못된 키 길이·키 미설정 예외 경로가 한 번도 실행되지 않는다.
- **왜 문제인가**: `SocialTokenCrypto`는 소셜 OAuth refresh token을 암호화해 DB에 저장하는 보안 핵심 로직이다. IV 유일성, GCM 태그 검증, 32바이트 키 검증, `SOCIAL_TOKEN_AES_KEY` 미설정 시 예외 등 실패 경로가 전혀 검증되지 않은 채 운영 중이다. `PiiMasker`도 이메일 정규식 경계 조건(로컬파트 1~2자, 여러 이메일 포함 문자열 등)이 테스트 없이 유지되고 있다.
- **개선 방법**: `common/security/SocialTokenCryptoTest`(암복호화 round-trip, 키 검증 실패, 프로필별 분기), `common/logging/PiiMaskerTest`(경계값), `common/exception/GlobalExceptionHandlerTest`(`@WebMvcTest` 슬라이스로 4개 핸들러 각각의 envelope·status 검증) 신설. A-3 구현 시 `SocialIntegrationLogTest`(마스킹 검증)도 함께.
- **API 영향**: No Impact (테스트 전용 추가)
- **예상 변경 파일**: 신규 테스트 파일 3~4개
- **예상 변경 라인 수**: 150~250 (신규)
- **위험도**: Low
- **테스트 영향도**: 순수 추가
- **예상 효과**: 보안 핵심 유틸의 회귀 방지망 확보. 다음 리팩토링(A-2, A-3) 안전망으로도 기능.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`SocialTokenCryptoProperties`/`FcmProperties`가 `JwtProperties`/`OAuthProperties`와 달리 Lombok `@Data` 대신 수기 getter/setter를 씀.** `JwtProperties`·`OAuthProperties`는 `@Data` + `@ToString.Exclude`로 시크릿이 `toString()`에 노출되는 걸 명시적으로 막는 패턴을 쓰는데, `SocialTokenCryptoProperties`(AES 키)와 `FcmProperties`(FCM 자격증명, notification 도메인의 기존 패턴)는 수기 클래스라 스타일이 갈린다. 다만 수기 클래스는 `Object.toString()` 기본 구현이라 애초에 필드를 노출하지 않으므로 **현재 유출 위험이 없고**, 오히려 `@Data`로 바꾸면 `@ToString.Exclude`를 빠뜨릴 새로운 위험이 생긴다. `FcmProperties`에 이미 동일 전례가 있어(notification 도메인 감사에서 다뤄졌을 가능성) 이번 라운드에서 손대지 않음.
- **`SocialLogContext`의 `withUserId`/`withHttpStatus`/`withProviderError`/`withTrigger`/`withGrantedScope` 5개 wither 메서드가 8개 필드를 매번 전부 나열하는 보일러플레이트.** Java record는 네이티브 wither가 없어 이 패턴이 불가피하고, 필드 8개·메서드 5개 규모에서 Builder로 바꾸면 도리어 auth/oauth·user/googlecalendar·user/client의 15곳 호출부까지 건드리는 과잉 리팩토링(YAGNI 위반)이 됨 — 규모가 실제로 커질 때(필드 12개 이상 등) 재검토.
- **`SocialTokenCrypto.decrypt()`가 손상된 `ciphertextBase64`(길이 < 12바이트)에 대해 `ArrayIndexOutOfBoundsException`을 던짐(전용 예외 아님).** 실제로는 `encrypt()`가 생성한 값만 `decrypt()`에 들어오므로 발생 가능성이 낮고, 발생해도 `GlobalExceptionHandler`의 catch-all(`Exception.class`)이 500 INTERNAL_ERROR로 안전하게 통일 처리한다(A-3 로깅 마스킹 수정 후에는 로그도 안전). 실사용 경로가 없는 방어 코드 추가는 지금 필요 없음.
- **`common/domain/BaseTimeEntity`·`SoftDeleteEntity`가 Lombok `@Getter`/`@Setter`를 사용하는데, `.claude/rules/spring-boot-java.md`는 "Lombok 미사용"이라고 명시함.** 실제로는 `auth`·`user`·`trip`·`notification` 도메인 엔티티·Properties 18개 파일에서 이미 Lombok을 광범위하게 쓰고 있어(`build.gradle`에도 정식 의존성으로 존재) `common`만의 문제가 아니라 **프로젝트 룰 문서 자체가 코드와 어긋난 상태**다. 코드 변경 사항이 아니라 문서 드리프트이므로 이번 코드 리팩토링 범위 밖 — 별도로 문서 업데이트가 필요함을 참고로 남김.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`JpaConfig`/`SchedulingConfig`를 하나로 합치지 않음.** 각각 `@EnableJpaAuditing`, `@EnableScheduling`+`@EnableAsync`라는 단일 관심사만 담당하는 마커 설정 클래스다. 합치면 클래스명에서 "무엇을 켜는 설정인지" 즉시 알기 어려워지고, `spring-boot-java.md`의 "공통 설정은 관심사별로 분리" 관례와도 맞지 않는다. 현재도 각 파일 5~11줄로 충분히 작다.
- **`BaseTimeEntity`/`SoftDeleteEntity`에 `equals`/`hashCode`를 오버라이드하지 않음.** 이 프로젝트는 `@ManyToOne` LAZY 연관관계를 자유롭게 쓰는데(풀 DDD 미적용), 여기에 필드 기반 `equals`/`hashCode`를 추가하면 프록시 초기화·컬렉션(`HashSet` 등) 사용 시 흔한 JPA 함정(프록시 vs 실제 엔티티 불일치, 지연로딩 강제 트리거)을 유발하기 쉽다. 현재처럼 식별자 동일성(Object identity) 기본 동작을 유지하는 것이 UUID PK + 트랜잭션 스코프 내 사용 패턴에서 더 안전하다.
- **`ErrorCode` 인터페이스에 `default ErrorResponse toErrorResponse()`를 추가하지 않음.** `GlobalExceptionHandler`에서 `new ErrorResponse(errorCode.getCode(), errorCode.getMessage())` 패턴이 4번 반복되긴 하지만, 이를 `ErrorCode`(exception 레이어)에 얹으면 `common.exception` 패키지가 `common.api`(응답 DTO) 포맷을 알아야 하는 역방향 결합이 생긴다. 4줄 중복 제거보다 레이어 경계 유지가 더 가치 있다.
- **`CommonErrorCode`에 상수를 미리 추가하지 않음.** 현재 `INVALID_INPUT`/`INTERNAL_ERROR` 2개만 있고 실제로 이 2개만 쓰인다. "나중에 필요할 것 같은" 공통 에러 코드(예: `FORBIDDEN`, `NOT_FOUND` 등)를 지금 추론해서 추가하는 것은 YAGNI 위반이며, 각 도메인은 이미 자체 `{Domain}ErrorCode`로 필요한 코드를 충분히 표현하고 있다.
- **`GlobalExceptionHandler`에 `ConstraintViolationException`/`AccessDeniedException`/`HttpRequestMethodNotSupportedException`/`NoResourceFoundException` 핸들러를 추가하지 않음.** grep으로 전수 확인한 결과 이 예외들을 던지는 코드가 현재 어디에도 없다(`@Validated` 메서드 파라미터 검증 미사용, Security의 인가 실패는 필터 단계에서 `AuthErrorResponseWriter`가 별도 처리). 발생하지 않는 예외를 위한 핸들러를 미리 만드는 것은 추측성 코드이며, 실제로 해당 예외를 던지는 첫 호출부가 생길 때 함께 추가하는 것이 맞다.
- **`PiiMasker`가 이메일만 마스킹하고 전화번호·주민번호 등 다른 PII 패턴을 다루지 않음.** 클래스 주석·`docs/specs/social-integration-structured-logging.md`가 명시한 스코프가 "provider 에러 바디의 이메일"로 좁게 정의되어 있고, 실제 사용처(Google/Apple/Kakao 에러 바디)도 이메일 외 PII가 노출될 소지가 확인되지 않았다. 스코프를 넓히는 건 실제 필요가 생겼을 때(예: 새 provider가 전화번호를 에러 바디에 포함) 논의할 사안.

## 15. 백엔드 아키텍처 개선 제안

- **Concurrency (A-2 volatile 수정)**: **Now** — 이미 A-2로 반영. 별도 인프라 도입 없이 1줄 수정으로 해결되는 사안이라 이 섹션에서 추가로 제안할 게 없음.
- **Security 로깅 파이프라인 (A-3 PII 마스킹)**: **Now** — 이미 A-3로 반영. Loki 등 로그 집계로 PII가 새는 구체적 경로가 확인됐으므로 다음 라운드로 미룰 이유가 없음.
- **Redis/캐싱**: **Never** — `common` 패키지에는 캐싱할 만한 반복 조회·연산이 없다(`SocialTokenCrypto`는 키를 이미 인스턴스 필드에 캐싱, `PiiMasker`는 정규식 컴파일을 static으로 이미 캐싱). 도입 근거 없음.
- **Event Architecture**: **Never** — `common`은 이벤트 발행 주체가 아니고(이벤트는 `notification` 도메인의 관심사), 여기에 이벤트 버스를 도입하는 것은 과설계.
- **Monitoring/Tracing**: **Later** — `SocialTokenCrypto`/`PiiMasker` 실패율을 메트릭으로 노출하면 진단에 도움될 수 있으나, 현재 4개 도메인에서 발생하는 실패는 이미 A-3의 구조화 로그(Loki)로 조회 가능하다. Micrometer 커스텀 메트릭 추가는 실제 운영 중 로그 기반 진단이 부족하다고 판명될 때 재검토.
- **Resilience(Retry/CircuitBreaker)**: **Never** — `SocialTokenCrypto`는 로컬 AES 연산(외부 I/O 없음)이라 재시도 대상이 아니고, 외부 provider 호출의 재시도는 이미 `GoogleCalendarSyncScheduler`(30분 폴링)가 도메인 레이어에서 담당 중이라 `common`에 별도로 넣을 이유가 없다.
- **API 계약**: **Never** — 이번 감사에서 발견된 모든 이슈는 내부 구현/로깅/패키지 구조 문제이며 API 계약(Request/Response, HTTP Status, ErrorCode, Endpoint, Swagger)에 영향을 주는 사항은 없음.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
