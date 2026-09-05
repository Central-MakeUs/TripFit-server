# Auth Refactor Log

`auth` 도메인 아키텍처 감사에서 승인된 항목을 실제로 반영한 이력이다. 라운드별로 무엇을 수정하고 어떻게 검증했는지 기록한다. 감사 결과 원본은 `audit.md` 및 `audit-round2.md` 등에 있다.

## 2026-08-15 — 3차 라운드(SOLID/OOP 중심) A-1/B-1 반영

3차 감사([`audit-round3.md`](audit-round3.md)) 기준 A(반드시 수정) 1개, B(유지보수성) 1개 전부 반영. 사용자 승인: "A-1, B-1 두 개 다 구현".

### 쉽게 설명하면 (`core-reporting.md`)

- **A-1:** 이번 세션에서 다른 모든 엔티티는 "필드를 아무 값이나 바꿀 수 있는 setter" 대신 "토큰을 폐기한다"처럼 의미가 분명한 메서드로 바꿨는데, 리프레시 토큰(로그인 상태를 유지해주는 토큰)만 이 정리에서 빠져 있었어요. `RefreshToken`에 폐기 시각(`revokedAt`)을 아무 값이나 넣을 수 있는 setter가 남아 있었고, 실제로 지금 쓰는 곳은 전부 "지금 시각"만 넣고 있었지만 코드만 봐서는 "왜 항상 지금 시각이어야 하는지"가 전혀 강제되지 않았어요. 이제 `revoke()`라는 메서드로 바꿔서, 토큰을 폐기할 때 항상 지금 시각으로만 기록되도록 코드 자체에 규칙을 박아뒀습니다. 동작 자체는 그대로예요.
- **B-1:** 애플/구글 로그인 토큰을 검증하는 두 코드가 인터넷에서 공개 열쇠를 받아오는 주소(URL) 하나만 다르고 나머지는 완전히 똑같이 복사돼 있었어요. 공통 부분을 한 곳으로 모아서, 나중에 다른 소셜 로그인(예: 네이버)이 추가돼도 주소 한 줄짜리 코드만 추가하면 되게 정리했습니다. 검증 로직 자체는 바뀌지 않았어요.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `RefreshToken.revokedAt` 필드 레벨 `@Setter` 제거, `revoke()` 도메인 메서드 신설(`this.revokedAt = LocalDateTime.now()`). `RefreshTokenService.rotate()`/`revokeFamily()`의 `setRevokedAt(...)` 호출부를 `revoke()`로 교체 | `RefreshToken.java`, `RefreshTokenService.java`, `RefreshTokenServiceTest.java` |
| B-1 | `AppleJwkVerifier`/`GoogleJwkVerifier`의 동일한 RS256 JWKS 서명 검증 로직을 `AbstractRemoteJwkVerifier`(신규)로 추출. 두 서브클래스는 각자의 JWKS URL만 `super(...)`로 전달 | `AppleJwkVerifier.java`, `GoogleJwkVerifier.java`, `AbstractRemoteJwkVerifier.java`(신규) |

### 변경 규모

- 기존 파일 수정 5개 (main 4 · test 1): `RefreshToken.java`, `RefreshTokenService.java`, `AppleJwkVerifier.java`, `GoogleJwkVerifier.java`, `RefreshTokenServiceTest.java`
- 신규 파일 1개 (main): `AbstractRemoteJwkVerifier.java`
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — Controller·DTO·`ErrorCode` enum·`@Operation`/`@Schema` 파일 전부 미변경

### 검증 결과

- `./gradlew test` (전체) — **BUILD SUCCESSFUL, 전부 통과**
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (스키마 변화 전혀 없음)

**결론: auth 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목

`audit-round3.md`의 C 3개(credential 서비스 구조적 유사성 미통합, `OAuthProfile.appleMatchedClientId` sealed 미전환, credential 저장 if/else 미승격), D 5개(`AuthController`·`AuthService`·`SocialTokenVerifier`·`JwtClaimsVerificationSupport` 현행 유지, `TokenRevocationChecker` 문서 최신화만) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round3.md` 해당 절 참고.

## 2026-08-04 — A-1~4, B-1~5 반영

감사([`audit.md`](audit.md)) 기준 A(반드시 수정) 4개, B(유지보수성) 5개 전부 반영. 사용자 승인: "A/B 전부".

### 쉽게 설명하면 (`core-reporting.md`)

- **A-1 (가장 중요):** 카카오/구글/애플 로그인 버튼을 눌렀을 때, 우리 서버가 "이 사람 진짜 맞아?"를 그 회사들에 확인받는 동안(느려질 수 있음) DB 접속 자리를 하나 계속 붙잡고 있었어요. 그 회사 서버가 느려지면 로그인과 상관없는 다른 기능(여행방 만들기 등)까지 DB 접속 자리가 부족해져 앱 전체가 느려질 위험이 있었는데, 이제 확인부터 먼저 받고 저장은 그다음에 짧게 하도록 순서를 바꿔서 그 위험을 줄였어요.
- **A-2:** 구글 로그인은 매번 "구글이 진짜 발급한 서명인지" 확인용 공개 열쇠를 새로 인터넷에서 받아왔어요(애플 로그인은 이미 한 번 받은 걸 재사용하도록 돼 있었는데 구글만 빠뜨렸던 것). 이제 구글도 애플처럼 한 번 받은 걸 재사용해서, 구글 로그인마다 불필요하게 느려지던 부분을 없앴어요.
- **A-3:** 카카오/구글/애플 서버에 요청을 보낼 때 "언제까지 기다릴지" 제한이 없었어요. 그 서버들이 응답을 아예 안 주면 우리 서버가 무한정 기다릴 수 있었는데, 이제 "3초 안에 연결 안 되면·5초 안에 응답 안 오면 포기"하도록 제한을 걸었어요.
- **A-4:** 로그인에 쓰는 비밀 값(JWT 서명 키, 구글/애플/카카오 비밀 키)이 시스템 설정을 문자열로 출력하면 그대로 노출될 수 있는 상태였어요(지금 실제로 노출되는 곳은 없었지만, 나중에 누군가 실수로 로그를 찍으면 위험). 이제 그 값들은 문자열로 출력해도 안 보이게 막았어요.
- **B-1~5:** 기능 변화는 없고 코드 정리예요 — 아무도 안 쓰는 함수 삭제, 같은 로직이 두 군데(회원/테스트 계정) 복사돼 있던 걸 한 곳으로 합침, 세 파일에 흩어져 있던 "만료됐는지 확인하는 로직"을 한 곳으로 모음, 의미 없는 1줄짜리 중간 함수 제거, 자리가 잘못된 주석 이동.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `AuthService.login()` — 소셜 provider HTTP 호출(토큰 검증·authorizationCode 교환)을 DB 쓰기 트랜잭션 밖으로 분리. `AuthLoginPersistenceService`(신규)가 upsert+refresh token 발급만 담당하는 짧은 `@Transactional`을 가짐 (self-invocation 회피를 위해 별도 빈으로 분리) | `AuthService.java`, `AuthLoginPersistenceService.java`(신규) |
| A-2 | `GoogleTokenVerifier` — 매 호출마다 `RemoteJWKSet`을 새로 만들어 캐싱이 무력화되던 문제. `AppleJwkVerifier`와 동일 패턴으로 `GoogleJwkVerifier`(신규) 도입, 인스턴스 재사용 | `GoogleTokenVerifier.java`, `GoogleJwkVerifier.java`(신규) |
| A-3 | 공용 `RestClient`(Apple/Google/Kakao 공유)에 connect 3초·read 5초 타임아웃 명시 설정 — provider 무응답 시 스레드·커넥션 무한 대기 방지 | `AppConfig.java` |
| A-4 | `JwtProperties.secret`, `OAuthProperties`의 `googleClientSecret`/`googleCalendarClientSecret`/`applePrivateKey`/`kakaoAdminKey`에 `@ToString.Exclude` — Lombok `@Data`의 `toString()`을 통한 시크릿 로그 유출 잠재 위험 차단 | `JwtProperties.java`, `OAuthProperties.java` |
| B-1 | `JwtService.parseUserId()` 프로덕션 미사용 dead code 제거 (`parseAccessToken(token).userId()`로 대체) | `JwtService.java`, `JwtServiceTest.java` |
| B-2 | `AuthService`/`DevAuthService`에 중복돼 있던 "탈퇴 계정 부활" 로직을 `User.reviveIfWithdrawn()` 도메인 메서드로 SSOT화 | `User.java`, `AuthLoginPersistenceService.java`, `DevAuthService.java` |
| B-3 | Apple/Google/Kakao 3곳에 중복된 만료 메시지 판별 로직을 `SocialErrorMessages.containsExpired()`(신규)로 추출 | `AppleTokenVerifier.java`, `GoogleTokenVerifier.java`, `KakaoTokenVerifier.java`, `SocialErrorMessages.java`(신규) |
| B-4 | `AppleTokenVerifier`/`GoogleTokenVerifier`의 값어치 없는 1줄 `processToken()` 위임 래퍼 인라인 (A-2와 함께 처리) | `AppleTokenVerifier.java`, `GoogleTokenVerifier.java` |
| B-5 | `SecurityConfig` — `permitAll` 근거 주석이 `corsConfigurationSource()`가 아닌 `securityFilterChain()` 위로 이동, 줄바꿈 정리 | `SecurityConfig.java` |

### 변경 규모

- 기존 파일 수정 14개 (main 11 · test 3): +109 / -291줄
- 신규 파일 5개 (main 3 · test 2): +311줄 — `AuthLoginPersistenceService`(+테스트), `GoogleJwkVerifier`, `SocialErrorMessages`, `UserTest`(신규 `reviveIfWithdrawn()` 커버)
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — Controller·DTO·`ErrorCode` enum·`@Operation`/`@Schema` 파일 전부 미변경

### 검증 결과 (2026-08-05 재검증 — Docker 연결 문제 해결 후 전체 재실행)

첫 보고 시점엔 이 세션의 Docker 연결이 일시적으로 끊겨 통합 테스트 32개가 전부 실패로 나왔었다. 원인(Docker Desktop 소켓 연결 지연으로 추정 — 재시도 시 정상 연결됨)을 확인하고 **전체를 처음부터 다시 돌렸다.**

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test --rerun` (캐시 무시 강제 재실행, Testcontainers 실제 MySQL 8 컨테이너 포함) — **373개 전체 통과, 0개 실패** (`AuthSecurityIntegrationTest`·`TripfitApplicationTests`·모든 Swagger consistency 테스트·`ArchitectureTest` 포함 전부 포함)
- **`oasdiff` API 계약 검증 (완료):**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (breaking 여부를 넘어 **어떤 종류의 스키마 변화도 전혀 없음** — 가장 엄격한 확인)

**결론: 리팩토링 전/후로 API 응답·요청·에러코드·엔드포인트 스펙이 문자 그대로 100% 동일함을 실제 실행으로 증명함.** 앱 심사 제출 상태에서 안전하게 반영 가능.

### 남겨둔 C/D 항목

`audit.md`의 C 3개(iss 클레임 미검증 TODO, URL 쿼리 파라미터 결합, Apple webhook 미사용 필드), D 4개(revocation 인터페이스, credential 서비스 분리 유지, 소셜 검증 Strategy 패턴, 별도 credential 엔티티 유지) — 이번 라운드에서 변경하지 않음. 이유는 `audit.md` 해당 절 참고.

## 2026-08-05 — 2차 라운드 A-1/B-1 반영

2차 감사([`audit-round2.md`](audit-round2.md)) 기준 A(반드시 수정) 1개, B(유지보수성) 1개 전부 반영. 사용자 승인: "A/B 전부".

### 쉽게 설명하면 (`core-reporting.md`)

- **A-1 (가장 중요):** 1차 때 "카카오/구글/애플 로그인 확인을 받는 동안 DB 접속 자리를 붙잡고 있던" 문제를 고쳤었는데, 이번에 다시 살펴보니 그 확인이 끝난 바로 다음 단계 — 구글/애플 로그인 시 나중에 회원 탈퇴할 때 쓸 갱신 토큰을 저장하는 과정 — 에 똑같은 문제가 남아 있었어요. 이 저장 과정도 구글/애플 서버에 요청을 보내고 응답을 최대 8초까지 기다리는데, 그동안 DB 접속 자리를 계속 붙잡고 있었습니다. 이건 구글/애플로 로그인할 때마다 항상 거치는 과정이라, 1차에서 없애려던 위험이 형태만 바뀌어 그대로 남아 있던 셈이에요. 이제 이 저장 과정도 "먼저 확인 받고, DB 저장은 그다음에 짧게" 순서로 바꿔서 완전히 해결했습니다.
- **B-1:** 기능 변화는 없고 코드 정리예요 — 구글/애플 로그인 검증 실패 시 에러를 처리하는 코드가 두 파일에 거의 그대로 복사돼 있던 걸 한 곳으로 합쳤습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `AppleCredentialService`/`GoogleLoginCredentialService` — Apple/Google 토큰 엔드포인트 HTTP 호출(교환·revoke)을 DB 쓰기 트랜잭션 밖으로 분리. `AppleCredentialPersistenceService`/`GoogleLoginCredentialPersistenceService`(신규)가 조회·저장·삭제만 담당하는 짧은 `@Transactional`을 가짐 (self-invocation 회피를 위해 별도 빈으로 분리, `AuthLoginPersistenceService`와 동일 패턴) | `AppleCredentialService.java`, `GoogleLoginCredentialService.java`, `AppleCredentialPersistenceService.java`(신규), `GoogleLoginCredentialPersistenceService.java`(신규) |
| B-1 | `AppleTokenVerifier`/`GoogleTokenVerifier`의 `verify()` 예외 매핑 catch 블록 5종(TripFitException/BadJWTException/BadJOSEException/ParseException/JOSEException/RuntimeException) 중복을 `JwtClaimsVerificationSupport`(신규)로 추출. 두 클래스는 JWKS 서명 검증 + audience 매칭·프로필 생성만 람다로 넘기고 예외→`AuthErrorCode` 매핑은 공용 로직에 위임 | `AppleTokenVerifier.java`, `GoogleTokenVerifier.java`, `JwtClaimsVerificationSupport.java`(신규) |

### 변경 규모

- 기존 파일 수정 4개 (main): `AppleCredentialService.java`, `GoogleLoginCredentialService.java`, `AppleTokenVerifier.java`, `GoogleTokenVerifier.java`
- 신규 파일 5개 (main 3 · test 2): `AppleCredentialPersistenceService.java`, `GoogleLoginCredentialPersistenceService.java`, `JwtClaimsVerificationSupport.java`, `AppleCredentialPersistenceServiceTest.java`(신규), `GoogleLoginCredentialPersistenceServiceTest.java`(신규)
- 테스트 갱신 2개: `AppleCredentialServiceTest.java`(persistenceService mock으로 전환, 조율 로직만 검증), `GoogleLoginCredentialServiceTest.java`(동일)
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — Controller·DTO·`ErrorCode` enum·`@Operation`/`@Schema` 파일 전부 미변경

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test --tests "com.tripfit.tripfit.auth.*" --tests "com.tripfit.tripfit.architecture.*"` — 전부 통과
- `./gradlew test` (전체) — **423개 전체 통과, 0개 실패**
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No breaking changes to report"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → 유일한 diff는 `trip` 도메인 `SaveRecommendationFeedbackRequest`의 `@Schema` 설명 문구("PUT" vs "PATCH")로, **이번 auth 라운드와 무관한 기존 drift**(스펙 문서가 실제 코드보다 stale). auth 관련 diff는 **0건**.

**결론: auth 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목

`audit-round2.md`의 C 2개(delete 파생 쿼리 SELECT-then-delete, 1차 C 4개 재검증), D 2개(revive 래퍼 미추출, 1차 D 4개 재검증) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round2.md` 해당 절 참고.
