# Auth Architecture Audit — Round 3 (2026-08-15, SOLID/OOP 중심)

> **선행 문서 안내:** `docs/audits/auth/audit.md`(1차, 2026-08-04)와 `audit-round2.md`(2차, 2026-08-05)가 이미 존재하며, 두 라운드의 A(9개)·B(6개) 전부 사용자 승인 후 구현·검증까지 끝났다(`refactor-log.md`). 이번 3차는 **새로 요청받은 SOLID/OOP 관점**으로 현재 코드를 다시 전수 검토한 결과이며, 1·2차가 이미 다룬 항목(트랜잭션 경계, JWKS 캐싱, 타임아웃, dead code, 예외 매핑 중복 등)은 재검토만 하고 새 판단이 없으면 반복 서술하지 않는다. 1·2차 이후 코드베이스가 추가로 바뀐 점도 확인했다 — 2차 D 섹션이 언급한 `NoOpTokenRevocationChecker`는 현재 `RedisTokenRevocationChecker`(실제 Redis 블랙리스트)로 교체돼 있어 2차 §15의 "Redis — Later" 제안은 사실상 **완료**된 상태다(아래 §15 갱신 참고).

## 범위

- 패키지: `com.tripfit.tripfit.auth` (`config`, `controller`, `domain`, `dto`, `exception`, `jwt`, `oauth`, `repository`, `security`, `service`) — 전체 49개 main 파일 전수 재검토(`dev` 하위 패키지는 현재 코드베이스에 존재하지 않음)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목 + 사용자 지정 우선 렌즈(SRP·OCP·LSP·ISP·DIP·캡슐화·God class/method·feature envy·inappropriate intimacy), `core-guardrails.md` ⛔ STOP
- 이번 세션에서 이미 반영된 두 가지 리포지토리 공통 변경(모든 Service `@RequiredArgsConstructor`, 모든 Entity 클래스 레벨 `@Setter` 제거 → 도메인 메서드)은 전제로 두고 재발견하지 않음

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `RefreshToken.revokedAt` — 필드 레벨 `@Setter`가 도메인 불변식을 우회 가능하게 노출함

- **Priority**: Medium
- **Category**: Architecture (캡슐화)
- **문제**: 이번 세션에서 전 Entity의 **클래스 레벨** `@Setter`는 도메인 메서드로 교체됐지만(`AppleCredential.update()`, `GoogleLoginCredential.updateRefreshToken()` 등이 그 결과물), `RefreshToken`은 `revokedAt` 필드에만 개별 `@Setter`가 남아 있다(`auth/domain/RefreshToken.java:66`). 호출부는 `RefreshTokenService.rotate()`(`:47`)에서 `current.setRevokedAt(LocalDateTime.now())`, `revokeFamily()`(`:63`)에서 `refreshToken.setRevokedAt(now)`로 사용 중이다.
- **왜 문제인가**: `setRevokedAt`은 `LocalDateTime` 아무 값이나 받는 범용 setter라, "토큰을 폐기한다"는 도메인 동작(폐기 시각은 항상 "지금"이어야 함)을 호출부가 임의의 과거·미래 시각으로도 설정할 수 있는 상태로 노출한다. 실제 호출부 2곳 모두 `LocalDateTime.now()`만 넘기고 있어 현재는 오용이 없지만, "왜 항상 now()인가"라는 불변식이 엔티티가 아니라 호출부 관례에만 의존한다 — 새 호출부가 실수로 다른 시각을 넘기면 컴파일 타임에 막을 방법이 없다. 이는 이번 세션에 다른 모든 엔티티에 적용한 "상태 전이는 도메인 메서드로만" 원칙에서 `RefreshToken`만 예외로 남은 것이기도 하다(Tell, Don't Ask 위반 · 캡슐화 결함).
- **개선 방법**: `RefreshToken`에 `revoke()` 도메인 메서드를 추가(`this.revokedAt = LocalDateTime.now();`)하고 필드의 `@Setter`를 제거한다. `RefreshTokenService.rotate()`·`revokeFamily()`의 두 호출부를 `current.revoke()` / `refreshToken.revoke()`로 교체한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/domain/RefreshToken.java`, `auth/service/RefreshTokenService.java`
- **예상 변경 라인 수**: ~10줄
- **위험도**: Low — 순수 리네이밍성 캡슐화 변경, 동작 변화 없음
- **테스트 영향도**: `RefreshTokenServiceTest.java:103`가 `reusedToken.setRevokedAt(...)`으로 폐기된 토큰 픽스처를 직접 만들고 있어 `reusedToken.revoke()`(또는 과거 시각이 필요하면 테스트 전용 팩토리)로 갱신 필요. 그 외 케이스는 영향 없음.
- **예상 효과**: `RefreshToken`도 나머지 엔티티와 동일하게 "상태 전이는 이름 있는 도메인 메서드로만" 원칙을 따르게 되어 일관성·캡슐화 향상.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `AppleJwkVerifier` / `GoogleJwkVerifier` — 서명 검증 로직이 URL 상수 하나만 다르고 완전히 동일

- **Priority**: Low
- **Category**: Cleanup
- **문제**: 두 클래스(`auth/oauth/AppleJwkVerifier.java`, `auth/oauth/GoogleJwkVerifier.java`)는 `RemoteJWKSet` 필드 캐싱, `verify(String token)`의 `DefaultJWTProcessor`+`JWSVerificationKeySelector`(RS256) 구성까지 **한 글자도 다르지 않게 동일**하다. 다른 부분은 static 초기화 블록의 URL 리터럴(`https://appleid.apple.com/auth/keys` vs `https://www.googleapis.com/oauth2/v3/certs`)뿐이다.
- **왜 문제인가**: `audit-round2.md` C 섹션은 이 둘을 합치지 않는 근거로 "audience 매칭 로직(Apple은 매칭값 반환, Google은 boolean만 필요)이 달라 조건 분기가 늘어난다"를 들었는데, 실제로 다시 읽어보면 audience 매칭은 이 두 `JwkVerifier` 클래스가 아니라 이들을 호출하는 `AppleTokenVerifier`/`GoogleTokenVerifier`의 `buildProfile()`에 있다. `JwkVerifier` 두 클래스 자체는 서명 검증만 하고 audience 판단을 전혀 하지 않으므로, 2차 감사의 "합치면 조건 분기가 늘어난다"는 우려는 이 두 클래스에는 적용되지 않는다 — 실제로는 순수하게 URL 하나만 다른 완전 중복이다. 새 provider(예: Naver)가 같은 RS256 JWKS 패턴을 쓰게 되면 이 복붙이 세 번째로 반복된다.
- **개선 방법**: 공통 로직을 담은 `abstract class AbstractRemoteJwkVerifier`(필드 `keySource`, `verify(String)` 메서드)를 만들고, `AppleJwkVerifier`/`GoogleJwkVerifier`는 각자의 no-arg 생성자에서 `super(APPLE_JWK_URL)` / `super(GOOGLE_JWK_URL)`만 호출하도록 축소한다. 두 클래스는 별도 Spring 빈 타입으로 유지되므로(단일 파라미터화 컴포넌트로 합치는 것과 달리) `AppleTokenVerifier`/`GoogleTokenVerifier`/`AppleNotificationVerifier`의 타입 기반 생성자 주입은 `@Qualifier` 추가 없이 그대로 동작한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/oauth/AppleJwkVerifier.java`, `auth/oauth/GoogleJwkVerifier.java`, (신규) `auth/oauth/AbstractRemoteJwkVerifier.java`
- **예상 변경 라인 수**: 신규 ~30줄, 기존 두 파일 각 ~25줄 → ~10줄로 축소(순감 ~20줄)
- **위험도**: Low — 서명 검증 동작 자체는 변경 없음, 순수 구조 이동
- **테스트 영향도**: `AppleTokenVerifierTest`/`GoogleTokenVerifierTest`/`AppleNotificationVerifierTest`가 `AppleJwkVerifier`/`GoogleJwkVerifier` 타입으로 mock을 만들고 있다면(타입 이름 자체는 유지되므로) 그대로 통과 예상 — 서브클래스가 부모의 `verify()`를 상속만 받고 오버라이드하지 않으므로 Mockito 목킹 대상 타입도 변하지 않음.
- **예상 효과**: 새 RS256 JWKS provider 추가 시 URL 한 줄만 다른 파일을 매번 복붙하지 않고 5줄짜리 서브클래스만 추가하면 됨(OCP).

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`AppleCredentialService`/`GoogleLoginCredentialService`(+ 각각의 PersistenceService) 구조적 유사성 — 공통 상위 타입으로 묶지 않음.** 두 서비스 모두 "authorizationCode 존재 확인 → 교환(best-effort try-catch+로그) → persistence 위임"과 "조회 → 복호화 → revoke(best-effort) → 삭제" 골격은 같지만, 세부 정책이 미묘하게 다르다 — Apple은 재로그인마다 refreshToken·clientId를 **항상 함께 덮어씀**, Google은 refreshToken이 없으면(재로그인 시 정상) **조용히 스킵**(`GoogleLoginCredentialService.java:46-49`에만 있는 이 null 체크가 대표적 비대칭). `saveIfAuthorizationCodePresent`의 세 번째 파라미터도 타입은 둘 다 `String`이지만 의미가 다르다(Apple=`clientId`, Google=`redirectUri`). Template Method로 강제 통합하면 이 비대칭 분기가 상위 클래스 안으로 들어가 오히려 "Apple 전용 분기"·"Google 전용 분기"가 뒤섞인 한 파일이 되어 가독성이 떨어진다 — `audit-round2.md` D 섹션의 "엔티티를 하나로 합치지 않는" 판단과 같은 이유로, Service 레벨도 지금 구조가 낫다고 판단. provider가 3개 이상으로 늘고 정책이 수렴하면 재검토.
- **`OAuthProfile.appleMatchedClientId`(nullable) — APPLE 전용 필드가 공용 record에 섞여 있음(경미한 ISP 냄새).** `SocialTokenVerifier` 3개 구현체가 공유하는 경계 DTO인데 필드 하나는 GOOGLE/KAKAO에서 항상 `null`이다(record 자체 `@Schema`에 "GOOGLE/KAKAO는 항상 null"이라고 이미 명시돼 있음). sealed interface + provider별 서브타입으로 분리하면 이 필드는 Apple 변형에만 존재하게 되지만, `AuthService.login()`이 어차피 provider로 분기해 이 필드를 쓰는 지점이 딱 1곳(`profile.appleMatchedClientId()`, `AuthService.java:80`)뿐이라 다형성 도입 비용이 절감 효과보다 크다. 필드 1개·사용처 1곳 수준에서는 지금처럼 문서화된 nullable 필드가 sealed 계층보다 단순하다.
- **`AuthService.login()`의 provider별 credential 저장 `if/else`(APPLE→Apple, GOOGLE→Google, 그 외 skip) — Strategy로 승격하지 않음.** `SocialTokenVerifierRegistry`(토큰 검증)는 3개 provider가 동일한 계약(`verify(String) → OAuthProfile`)을 갖기 때문에 Strategy+Registry가 실익이 있었다(`audit-round2.md` D 섹션이 이미 "실사용으로 값을 증명한 패턴"이라 평가). 반면 credential 저장은 **3개 중 2개만** 필요하고, 그 2개조차 세 번째 파라미터의 의미가 다르다(위 항목 참고) — 공통 인터페이스를 만들려면 `Map<String,String>` 같은 타입 소거된 컨텍스트를 받아야 해 지금의 타입 안전한 오버로드보다 오히려 후퇴한다. 실제로 필요한 새 동작이 아니라 "패턴을 맞추기 위한" 추상화라 보류.
- **`GoogleOAuthClient.revokeRefreshToken()`의 `REVOKE_URL + "?token=" + refreshToken` 문자열 결합** — 1·2차 감사에서 이미 다룬 항목(`audit-round2.md` C). `refreshToken`이 사용자 입력이 아니라 Google이 발급한 opaque 토큰이라 인코딩 위험이 낮다는 판단은 재확인해도 유효 — 새로 추가할 사유 없음.
- **`AppleTokenVerifier`/`GoogleTokenVerifier`의 `iss` 클레임 미검증 TODO** — 1·2차에서 이미 다룬 항목. 검증 조건 추가는 보안 로직 변경이라 "API 계약·로직 100% 유지" 전제의 이번 감사 범위 밖이라는 판단 유지.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`AuthController`(264줄) — 실제 로직은 4개 메서드의 얇은 위임뿐, God Controller 아님.** 줄 수만 보면 커 보이지만 본문을 읽으면 각 메서드는 3~5줄로 `xxxService.xxx(...)`를 호출해 `SuccessResponse`로 감싸는 것이 전부고, 나머지는 `@ApiResponses`의 Swagger 예시 JSON·Javadoc이다(`spring-boot-java.md` OpenAPI 컨벤션이 요구하는 성공+에러 케이스 전체 문서화). 로직을 쪼갤 대상 자체가 없다 — 분리하면 오히려 Swagger 어노테이션과 실제 메서드 시그니처가 파일 두 개로 흩어져 추적이 어려워진다.
- **`AuthService`의 생성자 의존성 9개 — God Service 아니라 정당한 유스케이스 오케스트레이터.** `verifierRegistry`·`authLoginPersistenceService`·`jwtService`·`refreshTokenService`·`userSummaryService`·`userLookupService`·`appleCredentialService`·`googleLoginCredentialService`·`tokenRevocationChecker` 9개가 한 클래스에 모여 있지만, `login()`/`refresh()`/`logout()`/`getCurrentUser()` 네 메서드 각각은 자신이 쓰는 협력자 서브셋만 짧게 호출하고 실제 작업은 전부 위임한다(`AuthService` 자신은 분기·검증만 최소로 수행). 의존성 개수는 "로그인"이라는 유스케이스가 실제로 걸치는 관심사(소셜 검증·영속화·JWT·revoke·2개 provider의 credential)가 넓다는 사실을 반영할 뿐이며, 이를 억지로 쪼개면 조율 로직이 여러 얇은 파사드로 흩어져 `login()` 하나의 흐름을 이해하려면 파일을 더 많이 오가야 한다.
- **`SocialTokenVerifier` + `SocialTokenVerifierRegistry`(Strategy + `EnumMap`) — 1·2차 판단 재확인, 여전히 유효.** provider 3개뿐이라 과해 보일 수 있으나 이미 실사용으로 값을 증명한 확장점이다(Kakao 추가 시 registry·`AuthService` 코드를 전혀 안 건드리고 `@Component` 하나만 추가).
- **`JwtClaimsVerificationSupport`(2차 B-1로 도입) — Kakao 쪽 예외 매핑과 더 묶지 않음.** `AppleTokenVerifier`/`GoogleTokenVerifier`는 JWT 서명·클레임 검증 실패(nimbus 예외 계층)를 다루고, `KakaoTokenVerifier`는 REST 응답 실패(`RestClientException`/HTTP status)를 다룬다 — 실패 도메인 자체가 달라(하나는 JOSE/JWT 파싱, 하나는 HTTP 통신) 억지로 하나의 매핑 함수로 합치면 두 실패 종류를 모두 아는 거대한 매핑 헬퍼가 되어 오히려 응집도가 떨어진다. 지금처럼 "JWT 2종은 공유, REST 1종은 자체 보유"가 실패 종류의 경계와 정확히 일치한다.
- **2차 D 섹션의 `TokenRevocationChecker` 관련 서술은 최신화 필요(정보 갱신, 재작업 아님)** — 2차 문서는 "`NoOpTokenRevocationChecker` 단일 구현체"를 전제로 D 판단을 서술했으나, 현재 코드는 `RedisTokenRevocationChecker`(실제 Redis 블랙리스트, `auth/oauth/RedisTokenRevocationChecker.java`)로 이미 교체돼 있다. 인터페이스+구현체 분리 구조 자체(D 판단의 핵심)는 여전히 유효 — `JwtAuthenticationFilter`가 인터페이스에만 의존하므로 구현체 교체가 무손실로 끝난 것이 오히려 이 분리의 실익을 증명한다. 문서만 stale했던 것으로, 별도 코드 변경 대상 아님.

## 15. 백엔드 아키텍처 개선 제안 (갱신)

1·2차 §15의 4개 제안을 재확인한 결과, 세 개는 판단·상태 그대로이고 하나는 이미 구현됐다.

- **Redis jti revocation 블랙리스트 — ✅ 구현 완료.** 1·2차에서 "Later"로 제안했던 항목이 `RedisTokenRevocationChecker`로 이미 구현·배포돼 있다(fail-open 정책까지 포함). 더 이상 제안 대상이 아니다.
- **Resilience — Circuit Breaker: 여전히 미구현, 판단 유지(Later).** `grep` 재확인 결과 Resilience4j 등 관련 의존성 없음. A-3(타임아웃, 1차 완료)만으로 현재 트래픽 규모에서는 충분하다는 1·2차 판단이 유효.
- **Async — 탈퇴 시 Apple/Google revoke 호출: 여전히 미구현, 판단 유지(Later).** `@Async` 사용처 없음. best-effort 설계상 사용자 응답에 영향이 없어 급하지 않다는 판단 유지.
- **Security — 로그인 Rate Limiting: 여전히 미구현, 판단 유지(Later, 단 방치 금지 권고 유지).** 관련 라이브러리·구현 없음. 공개 엔드포인트라는 성격상 우선순위는 여전히 낮지 않다.

새로 추가할 카테고리는 없음 — auth 도메인의 외부 I/O·동시성 프로파일은 1·2차 이후 근본적으로 바뀌지 않았다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다(A-1: `RefreshToken.revoke()` 도메인 메서드, B-1: `JwkVerifier` 공통 베이스 클래스 추출). C/D는 이번 라운드에서 수정하지 않습니다.
