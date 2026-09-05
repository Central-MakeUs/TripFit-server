# Cross-cutting Architecture Audit — 2차 라운드 (2026-08-05)

`com.tripfit.tripfit.common` 패키지를 대상으로 1차 리팩토링 반영 이후 진행한 2차 아키텍처 감사 문서다. 반드시 수정해야 하는 A 항목 2건과 유지보수성 리팩토링 B 항목 2건(참고 C 3건, 비권장 D 1건)을 도출했다. 도출된 항목은 승인 대기 상태로 정리되었으며, 이후 반영 내역은 `refactor-log.md`에 기록되었다.

## 범위

- 패키지: `com.tripfit.tripfit.common` (api, config, domain, exception, logging, security 전부)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `core-guardrails.md` ⛔ STOP
- 1차 감사 대비:
  - **1차에서 반영 완료 확인**: `WebConfig` 삭제(A-1), `SocialTokenCrypto.secretKey` `volatile`(A-2), `SocialIntegrationLog` PII 마스킹(A-3), `OpenApiConfig` → `auth/config/` 이동 + ArchUnit `commonPackageDoesNotDependOnOtherDomains`(B-1), `common` 보안 클래스 단위테스트 4종 신설(B-2) — 코드·테스트 모두 현재 상태와 일치함을 재확인했다.
  - **1차 C/D 재확인**: `JpaConfig`/`SchedulingConfig` 분리(둘 다 실제 사용 중 — `@Async`는 `NotificationEventListener`, `@Scheduled`는 3개 스케줄러에서 확인), `ConstraintViolationException`/`AccessDeniedException`/`HttpRequestMethodNotSupportedException`/`NoResourceFoundException` 미추가(여전히 던지는 코드 0건, `@Validated` 미사용 확인), `CommonErrorCode` 2종 유지(타 도메인 ErrorCode가 중복 정의 안 함 확인), `SocialTokenCrypto.decrypt()` 방어코드 미추가, `PiiMasker` 이메일 전용 스코프, `common/domain` Lombok vs 룰 문서 drift — **전부 상황 변화 없어 그대로 유지**, 재상정하지 않음.
  - **이번 라운드 새 시각**: (1) `common/api`의 `SuccessResponse` 팩토리 메서드 중 전체 코드베이스에서 단 한 번도 호출되지 않는 오버로드 발견(파라미터 순서 함정 포함), (2) 1차 자신의 변경(A-1/B-1) 이후 `docs/architecture.md`·`docs/specs/auth/auth-social-login.md`의 `common/` 패키지 트리 주석이 갱신되지 않아 drift, (3) `docker-compose.yml`의 `logging: driver: loki`가 컨테이너 stdout 전체를 캡처한다는 사실을 근거로 `GlobalExceptionHandler`의 catch-all 로깅이 A-3 마스킹 정책 밖에 있음을 확인(단, 이미 존재하는 스펙의 명시적 Out-of-Scope 결정과 충돌 여지가 있어 B로 신중 분류), (4) `SocialLogContext.withProviderError()`의 마스킹 강제 계약과 MDC 필드 매핑이 1차 테스트 신설(B-2)에서도 빠져 있었음을 확인.

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `SuccessResponse.of(T, String, String)` — 전체 코드베이스에서 미사용 + 파라미터 순서 함정을 가진 Dead Code

- **Priority**: Low
- **Category**: Dead Code
- **문제**: `common/api/SuccessResponse.java`는 팩토리 메서드를 2개 갖는다 — `of(T data)`(전 코드베이스 27개 호출부 전부 이걸 씀, grep 확인 완료)와 `of(T data, String code, String message)`. 후자는 `auth`·`user`·`trip`·`notification`·`user/schedule`·`user/googlecalendar` 등 전 도메인 컨트롤러를 grep해도 **호출부가 0건**이다.
- **왜 문제인가**: 단순히 안 쓰이는 코드라는 것 외에, 이 메서드는 시그니처 `(data, code, message)`이지만 내부에서 `new SuccessResponse<>(data, message, code)`로 **record 필드 순서(`data, message, code`)에 맞춰 인자를 뒤바꿔** 반환한다. 즉 메서드 파라미터 나열 순서와 실제 필드 순서가 반대라 — 지금은 아무도 안 써서 드러나지 않았지만, 향후 "성공 메시지·코드 포함"(`api-response.md` 문서화된 패턴) 케이스에서 누군가 이 메서드를 처음 호출하는 순간 `of(data, "COMMON_SUCCESS", "조회가 완료되었습니다")`처럼 자연스러운 순서로 넘기면 이미 내부에서 한 번 뒤바꿔 처리하므로 결과 자체는 맞지만, 코드를 읽는 사람은 이 스왑을 모르면 오해하기 쉬운 상태로 방치되어 있다. YAGNI 관점에서도 실사용 없는 오버로드를 유지할 이유가 없다.
- **개선 방법**: `of(T data, String code, String message)` 오버로드를 삭제한다. 문서화된 "메시지·코드 포함 성공" 패턴이 필요해지면 그때 `new SuccessResponse<>(data, message, code)` 캐노니컬 생성자를 직접 쓰거나, 실사용 필요 시점에 파라미터 순서를 필드와 동일하게 맞춰 다시 추가한다.
- **API 영향**: No Impact (호출부가 없으므로 컴파일·런타임 어떤 API 응답도 변하지 않음)
- **예상 변경 파일**: `common/api/SuccessResponse.java`
- **예상 변경 라인 수**: -4
- **위험도**: Low
- **테스트 영향도**: 없음(테스트도 이 오버로드를 참조하지 않음)
- **예상 효과**: `common/api` SSOT에서 사용되지 않는 · 파라미터 순서가 헷갈리는 코드 제거.

### A-2. `docs/architecture.md`·`docs/specs/auth/auth-social-login.md`의 `common/` 패키지 트리가 1차 라운드 자체 변경 이후 갱신되지 않음

- **Priority**: Low
- **Category**: 문서 SSOT 정합성
- **문제**: `docs/architecture.md` 18~22행은 여전히 `common/config/` 주석을 `# JPA, Web, OpenAPI`로 적고 있다. 그러나 1차 라운드에서 `WebConfig`는 삭제됐고(A-1), `OpenApiConfig`는 `auth/config/`로 이동했다(B-1) — 실제로 `common/config/`에는 `JpaConfig`·`SchedulingConfig` 2개만 남아있다(grep·`ls` 확인). 같은 트리는 `common/logging/`·`common/security/` 서브패키지(`PiiMasker`, `SocialTokenCrypto`, `SocialIntegrationLog` 등, 이번 감사 대상 절반)를 통째로 누락하고 있다. `docs/specs/auth/auth-social-login.md` 491행도 동일하게 `# JpaConfig, WebConfig, OpenApiConfig`로 똑같이 stale하다(이 문서는 스스로 "SSOT: docs/architecture.md"라고 명시).
- **왜 문제인가**: 두 문서 모두 "패키지 구조 SSOT"를 자처하는데, 실제로는 1차 감사가 만든 변경조차 반영이 안 된 채로 방치되어 있다. 신규 개발자·다음 라운드 Agent가 이 트리만 보고 `WebConfig`/`OpenApiConfig`가 여전히 `common/config/`에 있다고 오인하거나, `common/logging`·`common/security`의 존재 자체를 놓칠 위험이 있다(실제로 이번 라운드에서 `SocialTokenCrypto`·`PiiMasker` 등은 문서 트리에 전혀 나타나지 않아 처음 보면 존재를 알기 어렵다).
- **개선 방법**: `docs/architecture.md`의 `common/` 트리에 `logging/`(`PiiMasker`, `SocialIntegrationLog`, `SocialLogContext`, `SocialIntegrationAction`), `security/`(`SocialTokenCrypto`, `SocialTokenCryptoProperties`) 항목을 추가하고 `config/` 주석을 `# JpaConfig, SchedulingConfig`로 정정. `auth-social-login.md`의 동일 라인도 함께 정정(이 문서는 이미 `auth` 쪽 트리도 실제 구조(`auth/jwt`, `auth/oauth`, `auth/security` 등)와 다르게 오래된 상태이지만, 그 부분은 `auth` 도메인 감사 범위이므로 이번엔 `common/` 관련 3줄만 수정 대상으로 한정).
- **API 영향**: No Impact (문서 전용)
- **예상 변경 파일**: `docs/architecture.md`, `docs/specs/auth/auth-social-login.md`
- **예상 변경 라인 수**: 5~8
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 패키지 구조 SSOT 문서가 실제 코드와 다시 일치, 1차 라운드가 만든 drift가 2차에서 누적되지 않음.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `GlobalExceptionHandler`의 catch-all이 A-3 마스킹 정책 밖에 있음 — Loki 파이프라인 일관성 갭 (신중 검토 필요)

- **Priority**: Medium
- **Category**: Security / Logging 일관성
- **문제**: `deploy/app/docker-compose.yml`의 `app` 서비스는 `logging: driver: loki`로 **컨테이너 stdout 전체**를 Loki로 전송한다(EC2 A → EC2 C). `logback-spring.xml`은 `auth.oauth`/`auth.service`/`user.googlecalendar`/`user.client` 4개 로거만 `STRUCTURED_JSON`(LogstashEncoder) appender로 보내고, 그 외(`common.exception.GlobalExceptionHandler` 포함)는 `base.xml`의 기본 텍스트 `CONSOLE` appender를 쓴다 — 두 appender 모두 결국 같은 컨테이너 stdout에 출력되므로, **JSON이든 텍스트든 상관없이 stdout에 찍히는 모든 로그 라인은 동일하게 Loki에 적재된다.** `GlobalExceptionHandler.handleUnexpectedException()`은 앱 전 도메인(`auth`/`user`/`trip`/`notification`)의 처리되지 않은 예외를 전부 받아 `log.error("Unhandled exception reached GlobalExceptionHandler", exception)`로 **마스킹 없이** 원본 메시지+스택트레이스를 로깅한다.
- **왜 문제인가**: A-3는 `SocialIntegrationLog`(소셜 로그인·캘린더 4개 패키지 전용) 경로만 마스킹하도록 고쳤다. 하지만 `GlobalExceptionHandler`는 이 4개 패키지에 속하지 않는 **모든 도메인의 "예상 못한" 예외**(NPE, DB 예외 등)를 받는 SSOT 안전망이고, 이 로거의 출력도 동일하게 Loki로 나간다 — 즉 provider 에러 바디처럼 확실히 PII가 있는 경로는 막았지만, "우연히 예외 메시지에 사용자 입력(이메일 등)이 섞여 들어간 나머지 모든 경로"는 그대로 열려 있다.
  다만 **주의**: `docs/specs/cross-cutting/social-integration-structured-logging.md`(원 스펙)는 "Out of Scope"에 "전사(trip·notification 등 다른 도메인) 로깅 정책 확장"을 명시적으로 못박아 뒀다. 이 스펙의 의도는 "다른 도메인에도 구조화 JSON 로깅·`action`/`provider` 필드를 새로 깔지 않는다"는 것이지, "이미 존재하는 공통 catch-all 핸들러가 PII를 마스킹 없이 로깅해도 된다"를 명시적으로 승인한 것은 아니다 — 이 gap은 스펙이 분석하지 않은 영역(Docker Loki 드라이버가 인코더 종류와 무관하게 stdout 전체를 수집한다는 사실)에서 발생한다. 따라서 A-3처럼 "이미 증명된 PII 유출"은 아니고(구체적 재현 사례 없음), 방어적 개선에 가까워 A가 아닌 B로 분류한다.
- **개선 방법**: `GlobalExceptionHandler.handleUnexpectedException()`의 `log.error(...)` 호출에서 `exception.getMessage()`만 `PiiMasker.mask()`로 감싼 새 예외(스택트레이스 보존)로 로깅하거나, 최소한 이 트레이드오프를 `docs/specs/cross-cutting/social-integration-structured-logging.md` 리스크·미결정 표에 "catch-all 핸들러는 스코프 밖" 여부를 명시적으로 기록해 다음 라운드가 같은 고민을 반복하지 않게 한다. 코드 변경을 원치 않으면 최소한 문서화만이라도 권장.
- **API 영향**: No Impact (로깅만 변경, HTTP 응답 바디는 이미 `CommonErrorCode.INTERNAL_ERROR` 고정 메시지라 불변)
- **예상 변경 파일**: `common/exception/GlobalExceptionHandler.java` (+ 선택: `docs/specs/cross-cutting/social-integration-structured-logging.md` 리스크 표 갱신)
- **예상 변경 라인 수**: 5~10
- **위험도**: Low (수정 시) / 없음(문서만 갱신 시)
- **테스트 영향도**: `GlobalExceptionHandlerTest`에 "이메일이 포함된 예외 메시지가 마스킹되어 로깅된다" 케이스 추가 필요(수정 시).
- **예상 효과**: 전 도메인 대상 500 안전망도 A-3와 동일한 마스킹 보장을 갖게 되어, "구조화 로그 4개 패키지만 마스킹, 나머지는 무방비"라는 비일관성 해소. 단, 실제 착수 여부는 사용자가 스펙의 Out-of-Scope 결정과 비교해 판단 필요.

### B-2. `SocialLogContext.withProviderError()`의 마스킹 강제 계약 + MDC 필드 매핑이 테스트로 검증되지 않음

- **Priority**: Medium
- **Category**: Testability
- **문제**: `SocialLogContext.withProviderError(reason, rawMessage)`의 코드 주석은 "provider 에러 바디는 여기서만 채울 수 있게 해 PiiMasker 마스킹을 강제한다"고 명시하는데, `src/test/java`를 전수 grep해도 `withProviderError`/`withUserId`/`withHttpStatus`/`withTrigger`/`withGrantedScope` 호출이 **단 한 곳도 없다**. `SocialIntegrationLog.toMdcFields()`(provider/action/userId/httpStatus/providerErrorReason/providerErrorMessage/trigger/grantedScope 8개 필드를 MDC 키로 매핑하는 로직)도 마찬가지로 직접 테스트가 없다. 1차 B-2가 신설한 `SocialIntegrationLogTest`는 `Throwable` 인자를 받는 `warn`/`error`의 마스킹만 검증하고(A-3 대상), `SocialLogContext` 자체의 wither 마스킹·MDC 매핑 로직은 다루지 않는다.
- **왜 문제인가**: `withProviderError`가 "여기서만 마스킹을 강제한다"는 보안 불변식을 코드 주석으로 선언해놓고 이를 검증하는 테스트가 전무하면, 향후 리팩토링(필드 추가·순서 변경 등) 중 이 마스킹 호출이 실수로 삭제돼도 어떤 테스트도 실패하지 않는다. `toMdcFields`의 키 이름(`"provider"`, `"providerErrorMessage"` 등)도 Loki 쿼리·Grafana 대시보드가 문자열로 참조하는 계약이라 오타 하나가 조용히 필드 누락으로 이어질 수 있다.
- **개선 방법**: `common/logging/SocialLogContextTest`(신규) 또는 기존 `SocialIntegrationLogTest`에 추가로 — (1) `withProviderError`에 이메일 포함 raw 메시지를 넣었을 때 `providerErrorMessage()`가 마스킹된 값을 반환하는지, (2) `SocialIntegrationLog.info/warn` 호출 시 MDC에 `provider`/`action`/`userId`/`httpStatus`/`providerErrorReason`/`providerErrorMessage`/`trigger`/`grantedScope` 키가 정확한 이름으로 채워지고 로깅 종료 후 제거되는지(`MDC.get(...)`을 로깅 콜백 내부에서 캡처) 검증하는 테스트를 추가.
- **API 영향**: No Impact (테스트 전용 추가)
- **예상 변경 파일**: `common/logging/SocialLogContextTest.java`(신규) 또는 `SocialIntegrationLogTest.java` 확장
- **예상 변경 라인 수**: 40~70 (신규)
- **위험도**: Low
- **테스트 영향도**: 순수 추가
- **예상 효과**: A-3가 지킨 마스킹 불변식을 `SocialLogContext` 레벨에서도 회귀 방지망으로 커버, MDC 필드 계약(Loki 쿼리가 참조하는 키 이름)의 오타·누락을 컴파일 타임이 아닌 테스트로 조기 발견.

## 💡 C. 참고 사항 (이번엔 수정 안 함, 이유 필수)

- **`SocialIntegrationAction` enum·`SocialLogContext` record에 `@Schema`가 없음.** `.claude/rules/spring-boot-java.md`의 `@Schema` 필수 규칙은 API로 노출되는 DTO·Entity·enum 대상이다. 이 둘은 순수 내부 구조화 로깅 필드(Swagger에 노출되지 않음)라 규칙 적용 대상이 아니며, 실제로 `common/logging` 하위 다른 클래스들도 동일 패턴 — 문제 없음, 그대로 유지.
- **`GlobalExceptionHandler.handleMessageNotReadable`/`handleTypeMismatch`가 서버 측 로그를 전혀 남기지 않음(파라미터 `exception` 미사용).** 두 핸들러 모두 400 클라이언트 오류(잘못된 요청 바디·타입)이고 프론트가 보낸 요청 자체의 문제이므로 서버 측 로깅 필수성이 낮다. 모든 400 요청마다 로그를 남기면 노이즈·로깅 오버헤드만 늘어난다 — 현재처럼 로그 없이 400만 반환하는 것이 적절.
- **1차 C에서 넘어온 항목(재확인, 변경 없음)**: `SocialTokenCryptoProperties`/`FcmProperties` Lombok 미사용 스타일 차이, `SocialLogContext` wither 5개 보일러플레이트, `SocialTokenCrypto.decrypt()` 방어 코드 미추가, `common/domain` Lombok 사용과 `spring-boot-java.md` "Lombok 미사용" 문서 간 drift — 전부 이번 라운드에서도 코드·문서 상태 그대로이며 1차가 남긴 사유가 여전히 유효하다. 재론하지 않음.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **1차 D 전부 재확인, 변경 없음**: `JpaConfig`/`SchedulingConfig` 통합 안 함(둘 다 실사용 확인 — `@EnableAsync`는 `NotificationEventListener`, `@EnableScheduling`은 3개 스케줄러), `BaseTimeEntity`/`SoftDeleteEntity` `equals`/`hashCode` 미추가, `ErrorCode.toErrorResponse()` 미추가, `CommonErrorCode` 상수 선추가 안 함(타 도메인이 중복 정의하지 않음을 grep으로 재확인), 미발생 예외(`ConstraintViolationException` 등) 핸들러 미추가(`@Validated` 사용처 0건 재확인), `PiiMasker` 스코프 확장 안 함 — 각 사유는 1차 `audit.md` D절과 동일하며 이번 라운드에서 이를 뒤집을 새 근거를 찾지 못했다.

## 15. 백엔드 아키텍처 개선 제안

- **Security 로깅 파이프라인 일관성 (B-1)**: **Later** — 이미 B-1로 상세히 다뤘듯, 원 스펙의 "전사 로깅 정책 확장 Out of Scope" 결정과 충돌 여지가 있어 사용자 판단이 필요하다. 실제 프로덕션 로그에서 `GlobalExceptionHandler` 경로로 PII가 새는 구체 사례가 관측되면 **Now**로 승격 권장.
- **Redis/캐싱**: **Never** — 1차와 동일한 이유로 변화 없음. `common`에는 여전히 캐싱 대상 반복 조회가 없다.
- **Event Architecture**: **Never** — `common`은 이벤트 발행 주체가 아니며 이번 라운드에서도 그 구조가 바뀔 근거를 찾지 못했다.
- **Monitoring/Tracing**: **Never** — 1차는 "Later"였으나, 이번 라운드에서 확인한바 Loki가 이미 `docker-compose`의 `logging: driver: loki`로 EC2 A/B 전 컨테이너 stdout을 수집 중이라 로그 기반 진단 인프라 자체는 이미 갖춰져 있다. 별도 Micrometer 커스텀 메트릭을 `common`에 추가할 근거가 여전히 부족해 한 단계 낮춰 Never로 재평가.
- **Resilience(Retry/CircuitBreaker)**: **Never** — 1차와 동일, `SocialTokenCrypto`는 로컬 연산이라 재시도 대상이 아니고 외부 provider 재시도는 도메인 레이어(`GoogleCalendarSyncScheduler`)가 담당.
- **API 계약**: **Never** — 이번 감사에서 발견된 모든 이슈는 로깅·문서·미사용 코드 문제이며 API 계약에 영향을 주는 사항은 없음.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다. 특히 B-1은 원 스펙의 Out-of-Scope 결정과 상충 여지가 있으므로, 착수 전 사용자 확인을 별도로 권장합니다.
