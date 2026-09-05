# Auth Architecture Audit — 2026-08-04

> **상태 업데이트 (2026-08-05):** 이 문서 작성 이후 별도 리팩터 커밋들(#78·#21·#44 등)에서 아래 A·B 섹션 9개 항목이 전부 이미 코드에 반영됐으나, 이 문서는 동기화되지 않고 "승인 대기" 상태로 방치돼 있었다. 코드 재확인 후 각 항목에 **구현 상태** 줄을 추가했다. C/D 섹션과 §15(백엔드 아키텍처 개선 제안)는 재확인 결과 여전히 유효 — 수정하지 않는다.

## 범위

- 패키지: `com.tripfit.tripfit.auth` (`controller`, `dev/controller`, `dev/service`, `domain`, `dto`, `exception`, `jwt`, `oauth`, `repository`, `security`, `service`)
- 참고(감사만, 수정 대상 아님): `common/exception`, `common/logging`, `common/security` — auth가 의존하는 공용 컴포넌트 확인용
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `.claude/skills/safe-refactor/references/audit-checklist.md` 1~15항목(구 `refacotr-command.md`), `core-guardrails.md` ⛔ STOP, `spring-boot-java.md`, `testing.md`
- 총 39개 파일(main 45개 — dev 포함, test 14개) 전수 검토

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `AuthService.login()` — DB 트랜잭션이 외부 소셜 provider HTTP 호출을 감싸고 있음

- **Priority**: Critical
- **Category**: Architecture / Performance
- **구현 상태**: ✅ 완료 — `AuthService.login()`은 더 이상 `@Transactional`이 아니다. 소셜 검증(`verifier.verify()`)을 트랜잭션 밖에서 먼저 실행하고, DB 쓰기만 신규 `AuthLoginPersistenceService.persist()`(`@Transactional`)로 분리했다 (`auth/service/AuthService.java:59-107`, `auth/service/AuthLoginPersistenceService.java`).
- **문제**: `AuthService.login()`은 `@Transactional`이며, 그 트랜잭션 내부에서 순서대로 (1) `SocialTokenVerifier.verify(token)` — Kakao는 `kapi.kakao.com/v2/user/me` HTTP GET, Google/Apple은 JWKS 캐시 미스 시 각각 `googleapis.com`/`appleid.apple.com` HTTP 호출 — 그리고 APPLE/GOOGLE이면 (2) `AppleCredentialService.saveIfAuthorizationCodePresent`/`GoogleLoginCredentialService.saveIfAuthorizationCodePresent`(각각 자체 `@Transactional`이지만 REQUIRED 전파로 동일 물리 트랜잭션에 합류) 내부에서 `AppleOAuthClient`/`GoogleOAuthClient`의 authorizationCode→refresh_token 교환 HTTP POST까지 실행된다. 즉 **모든 로그인 요청마다** DB 커넥션 풀에서 커넥션 하나를 잡아둔 채로 외부 소셜 provider와의 네트워크 왕복(들)을 기다린다.
- **왜 문제인가**: HikariCP 등 커넥션 풀은 유한하다. Kakao/Google/Apple 중 하나라도 응답이 느려지거나(타임아웃 미설정 — A-3 참고) 장애가 나면, 로그인 요청들이 커넥션을 계속 물고 대기하게 되어 커넥션 풀이 고갈된다. 로그인은 이 서버에서 가장 트래픽이 몰리는 공개 엔드포인트인데, 이 엔드포인트의 지연이 **auth와 무관한 다른 모든 API**(trip, user, notification 등 동일 DB 풀을 쓰는 모든 요청)까지 연쇄적으로 마비시킬 수 있다. `@Transactional` 위치는 `audit-checklist.md` 4번 체크리스트에 명시된 항목이다.
- **개선 방법**: 외부 HTTP 호출(소셜 토큰 검증, authorizationCode 교환)을 DB 쓰기 트랜잭션 **밖**에서 먼저 수행하고, DB에 실제로 쓰는 부분(`upsertUser`, refresh token 저장, credential upsert)만 짧은 `@Transactional`로 감싸도록 재구성한다. Spring AOP는 같은 빈 안에서 `this.method()` self-invocation 시 프록시를 안 타므로, 단순히 메서드를 쪼개는 것만으로는 안 되고 (a) DB 쓰기만 담당하는 별도 빈으로 분리하거나 (b) `AuthService`를 "검증·조율"과 "영속화" 두 계층으로 나눠야 한다. best-effort 시맨틱(Apple/Google credential 저장 실패해도 로그인은 계속)은 그대로 유지 가능 — 현재도 이미 try-catch로 감싸여 있어 트랜잭션 롤백에 의존하지 않는다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/service/AuthService.java`, `auth/service/AppleCredentialService.java`, `auth/service/GoogleLoginCredentialService.java` (트랜잭션 경계 재구성을 위해 새 protected/package 헬퍼 빈이 필요할 수 있음)
- **예상 변경 라인 수**: 60~100줄 (여러 파일에 걸친 구조 변경)
- **위험도**: Medium — 트랜잭션 경계를 옮기는 작업이라, 기존 단위 테스트(Mockito 기반이라 실제 트랜잭션 범위는 검증하지 못함)만으로는 회귀를 못 잡는다. `AuthSecurityIntegrationTest` 등 통합 테스트로 로그인 흐름 자체가 여전히 성공하는지 재확인 필요.
- **테스트 영향도**: 기존 `AuthServiceTest`는 Mockito mock 기반이라 트랜잭션 경계와 무관하게 대부분 그대로 통과할 것으로 예상되나, 클래스 분리 시 생성자 시그니처가 바뀌면 테스트의 `@InjectMocks` 대상도 갱신 필요.
- **예상 효과**: 로그인 지연·소셜 provider 장애가 DB 커넥션 풀 고갈로 번지는 것을 차단 — 서비스 전체 가용성에 영향을 주는 가장 큰 리스크 제거.

### A-2. `GoogleTokenVerifier` — 매 로그인마다 JWKS를 새로 생성해 캐싱이 무력화됨

- **Priority**: High
- **Category**: Performance
- **구현 상태**: ✅ 완료 — `auth/oauth/GoogleJwkVerifier.java`가 신규 생성돼 `AppleJwkVerifier`와 동일하게 `RemoteJWKSet`을 필드로 캐싱하고, `GoogleTokenVerifier`가 이를 주입받아 재사용한다.
- **문제**: `AppleJwkVerifier`는 `RemoteJWKSet` 인스턴스를 필드로 캐싱해 재사용하며(주석: "RemoteJWKSet은 내부적으로 키를 캐시함 — 인스턴스를 재사용해야 매 검증마다 재조회하지 않음"), 이는 의도적으로 올바르게 구현돼 있다. 반면 `GoogleTokenVerifier.processToken()`은 **매 호출마다** `new RemoteJWKSet<>(GOOGLE_JWK_URL)` + `new DefaultJWTProcessor<>()`를 새로 만든다. `RemoteJWKSet`의 내부 캐시는 인스턴스 단위이므로, 매번 새 인스턴스를 만들면 캐시가 절대 재사용되지 않는다.
- **왜 문제인가**: Google 로그인(가장 많이 쓰일 소셜 provider 중 하나)마다 `https://www.googleapis.com/oauth2/v3/certs`로 매번 네트워크 왕복이 발생한다 — Apple 쪽에서 이미 올바르게 푼 문제를 Google 쪽만 놓친 것으로 보이는 명백한 성능 결함이자, provider 응답 지연이 그대로 로그인 지연으로 직결된다(A-1과 결합 시 악화).
- **개선 방법**: `AppleJwkVerifier`와 동일한 패턴으로 `GoogleJwkVerifier` `@Component`를 새로 만들어 `keySource`를 필드로 캐싱하고, `GoogleTokenVerifier`가 이를 생성자 주입받아 `processToken()`에서 재사용하도록 변경한다.
- **API 영향**: No Impact
- **예상 변경 파일**: (신규) `auth/oauth/GoogleJwkVerifier.java`, `auth/oauth/GoogleTokenVerifier.java`
- **예상 변경 라인 수**: 신규 ~30줄, 기존 파일 ~15줄 축소
- **위험도**: Low — 이미 검증된 Apple 패턴을 그대로 이식
- **테스트 영향도**: `GoogleTokenVerifierTest`가 `new GoogleTokenVerifier(oAuthProperties)`를 직접 생성하므로 생성자 시그니처 변경에 맞춰 테스트 생성자 호출부 수정 필요(동작 자체는 동일하게 통과해야 함).
- **예상 효과**: Google 로그인마다의 불필요한 JWK 네트워크 재조회 제거 — 지연 감소, Google 장애 시 영향 축소.

### A-3. 소셜 provider 호출용 공용 `RestClient`에 타임아웃 미설정

- **Priority**: High
- **Category**: Performance / Architecture
- **구현 상태**: ✅ 완료 — `AppConfig.restClient()`에 `SimpleClientHttpRequestFactory`로 connect 3초 / read 5초 타임아웃이 설정돼 있다 (`auth/security/AppConfig.java:36-39`).
- **문제**: `AppConfig.restClient()`가 `RestClient.create()`로 생성한 빈을 `AppleOAuthClient`·`GoogleOAuthClient`·`KakaoTokenVerifier`가 공유하는데, connect/read 타임아웃이 전혀 설정돼 있지 않다. JDK 기본 HTTP 클라이언트는 별도 설정이 없으면 사실상 무제한 대기한다.
- **왜 문제인가**: Kakao user/me, Google/Apple 토큰 교환·revoke 호출이 응답 없이 걸려있으면 해당 요청 스레드가 무한정 대기한다. A-1(트랜잭션 내부 호출)과 결합하면 DB 커넥션까지 함께 붙잡혀 있어 피해가 배가된다.
- **개선 방법**: `AppConfig.restClient()`에서 `ClientHttpRequestFactorySettings`(또는 `ClientHttpRequestFactory` 구현체)로 connect/read 타임아웃을 명시 설정한다(예: connect 3초, read 5초 — 정확한 값은 운영 관찰값 기준으로 결정 필요).
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/security/AppConfig.java`
- **예상 변경 라인 수**: ~10줄
- **위험도**: Low~Medium — 타임아웃 값을 너무 짧게 잡으면 정상 상황에서도 실패로 오판할 수 있어 값 선정에 주의 필요
- **테스트 영향도**: 기존 `AppleOAuthClientTest`/`GoogleOAuthClientTest`(`MockRestServiceServer` 기반)는 타임아웃과 무관하게 그대로 통과.
- **예상 효과**: 소셜 provider 장애 시 스레드/커넥션 무한 대기 방지, 장애 격리.

### A-4. `JwtProperties`/`OAuthProperties` — Lombok `@Data`가 비밀값을 `toString()`에 노출

- **Priority**: Medium
- **Category**: Security
- **구현 상태**: ✅ 완료 — `JwtProperties.secret`, `OAuthProperties.googleClientSecret`/`googleCalendarClientSecret`/`applePrivateKey`/`kakaoAdminKey` 전 필드에 `@ToString.Exclude`가 적용돼 있다.
- **문제**: `JwtProperties.secret`, `OAuthProperties.applePrivateKey`/`googleClientSecret`/`kakaoAdminKey`가 Lombok `@Data`로 생성된 `toString()`/`equals()`/`hashCode()`에 그대로 포함된다. 현재 이 객체들을 직접 로그로 남기는 코드는 발견되지 않았지만, 향후 누군가 디버깅 목적으로 `log.debug("config={}", jwtProperties)` 류를 추가하거나, 예외 메시지·assertion 실패 메시지에 이 객체가 우연히 포함되면 JWT 서명 시크릿·Apple 개인키·Google/Kakao 시크릿이 그대로 로그에 남는다.
- **왜 문제인가**: 인증 도메인은 시크릿 노출이 곧 전체 서비스 계정 탈취로 이어지는 민감 영역이다(`audit-checklist.md` 13번 Security 체크리스트: "민감정보 로그"). 현재 미발동 상태의 잠재 위험(landmine)이라도 사전 차단이 저비용·고효과다.
- **개선 방법**: `secret`/`applePrivateKey`/`googleClientSecret`/`kakaoAdminKey` 필드에 Lombok `@ToString.Exclude`(및 필요 시 `@EqualsAndHashCode.Exclude`)를 추가하거나, 클래스에 커스텀 `toString()`을 작성해 민감 필드를 마스킹한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/jwt/JwtProperties.java`, `auth/oauth/OAuthProperties.java`
- **예상 변경 라인 수**: ~10줄
- **위험도**: Low
- **테스트 영향도**: 없음 (두 클래스 모두 현재 `toString()`/`equals()`를 테스트에서 검증하지 않음)
- **예상 효과**: 향후 실수로 인한 시크릿 로그 유출을 원천 차단.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `JwtService.parseUserId()` — 프로덕션 코드에서 미사용(dead code)

- **Priority**: Medium
- **Category**: Dead Code
- **구현 상태**: ✅ 완료 — `parseUserId`는 코드베이스 전체(main·test)에서 더 이상 검출되지 않는다. 삭제됨.
- **문제**: `JwtService.parseUserId(String)`은 `parseAccessToken(token).userId()`의 단순 위임 메서드인데, 실제 호출부는 `JwtServiceTest`뿐이다. 프로덕션 경로(`JwtAuthenticationFilter`, `AuthorizedUserArgumentResolver`)는 전부 `parseAccessToken()`을 직접 쓴다.
- **왜 문제인가**: 두 개의 유사한 공개 메서드가 있으면 신규 개발자가 어느 쪽을 써야 할지 헷갈리고, 실질적으로 프로덕션에서 검증되지 않는 API 표면이 늘어난다.
- **개선 방법**: `parseUserId` 메서드를 삭제하고, `JwtServiceTest`의 2개 호출부(`parseUserId(token)`, `parseUserId("invalid-token")`)를 `parseAccessToken(token).userId()`/`parseAccessToken("invalid-token")`로 변경.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/jwt/JwtService.java`, `test/.../auth/jwt/JwtServiceTest.java`
- **예상 변경 라인 수**: ~8줄
- **위험도**: Low
- **테스트 영향도**: 테스트 2곳 호출부 변경, 검증 내용은 동일
- **예상 효과**: API 표면 축소, 혼동 제거

### B-2. `AuthService`/`DevAuthService` — "탈퇴 계정 부활" 로직 완전 중복

- **Priority**: Medium
- **Category**: Cleanup
- **구현 상태**: ✅ 완료 — `User.reviveIfWithdrawn()` 도메인 메서드로 통합됐다 (`user/domain/User.java:119`). `AuthLoginPersistenceService`(A-1 분리로 생긴 신규 클래스)와 `DevAuthService` 모두 `user.reviveIfWithdrawn()`을 호출한다.
- **문제**: `AuthService.reviveIfWithdrawn(User)`와 `DevAuthService.reviveIfWithdrawn(User)`가 `deletedAt` null 체크 → `setDeletedAt(null)` → `setAllFree(false)`로 완전히 동일한 4줄 로직을 각자 private 메서드로 복붙하고 있다.
- **왜 문제인가**: "탈퇴 부활" 정책(예: 부활 시 초기화할 필드 추가)이 바뀌면 두 곳을 항상 함께 고쳐야 하는데, 물리적으로 떨어진 두 클래스라 누락 위험이 있다.
- **개선 방법**: 이 로직을 `User` 엔티티의 도메인 메서드(예: `reviveIfWithdrawn()`)로 옮기고 두 Service가 `user.reviveIfWithdrawn()`을 호출하도록 통일한다. `User` 엔티티는 `user/domain/` 소속으로 엄밀히는 auth 도메인 밖이지만, 중복 SSOT화를 위해 최소 변경(메서드 1개 추가·auth 쪽 두 호출부 교체)이 필요하다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/service/AuthService.java`, `auth/dev/service/DevAuthService.java`, `user/domain/User.java`(auth 도메인 밖, 중복 제거를 위한 최소 변경)
- **예상 변경 라인 수**: ~15줄
- **위험도**: Low
- **테스트 영향도**: `AuthServiceTest.login_whenExistingAccountIsWithdrawn_revivesAccountAndLogsIn`가 동일하게 통과해야 함. `DevAuthServiceTest`도 확인 필요.
- **예상 효과**: 중복 제거, 정책 변경 시 단일 지점 수정

### B-3. `isExpiredMessage(String)` — Apple/Google/Kakao 3곳에 동일 로직 중복

- **Priority**: Low
- **Category**: Cleanup
- **구현 상태**: ✅ 완료 — `auth/oauth/SocialErrorMessages.java` 유틸이 신규 생성됐고, Apple/Google/Kakao 3개 Verifier 모두 `SocialErrorMessages.containsExpired(...)`를 재사용한다.
- **문제**: `AppleTokenVerifier`, `GoogleTokenVerifier`, `KakaoTokenVerifier` 세 클래스 모두 "메시지에 'expired'라는 단어가 포함돼 있는지"를 `Locale.ROOT` 소문자 변환 후 `contains("expired")`로 판별하는 거의 동일한 private 메서드를 각자 갖고 있다(Kakao는 응답 바디, Apple/Google은 예외 메시지 대상이라는 차이만 있음).
- **왜 문제인가**: 3곳에 동일 휴리스틱이 흩어져 있어, provider 응답 포맷이 바뀌어 이 판별 로직을 조정해야 할 때(코드 주석에도 "문자열로 판별"이라는 취약성이 명시돼 있음) 3곳을 다 찾아 고쳐야 한다.
- **개선 방법**: `auth/oauth/` 패키지에 작은 정적 유틸(예: `SocialErrorMessages.containsExpired(String)`)로 추출해 3곳에서 재사용한다. YAGNI 관점에서도 이미 3회 실사용 중인 중복이라 추출 정당성이 충분하다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/oauth/AppleTokenVerifier.java`, `auth/oauth/GoogleTokenVerifier.java`, `auth/oauth/KakaoTokenVerifier.java`, (신규) `auth/oauth/SocialErrorMessages.java`
- **예상 변경 라인 수**: ~20줄 (중복 제거 -9, 신규 유틸 +8 내외)
- **위험도**: Low
- **테스트 영향도**: 각 Verifier의 만료 판별 관련 테스트가 있다면(직접 테스트는 미확인 — 대부분 `verify_malformedToken_...` 케이스뿐) 그대로 통과해야 함.
- **예상 효과**: 휴리스틱 단일화, 향후 조정 시 1곳만 수정

### B-4. `AppleTokenVerifier.processToken()` — 값어치 없는 1줄 위임 래퍼

- **Priority**: Low
- **Category**: Cleanup
- **구현 상태**: ✅ 완료 — `processToken()`은 Apple·Google 양쪽에서 더 이상 검출되지 않는다(인라인됨).
- **문제**: `AppleTokenVerifier.processToken(String)`은 `return appleJwkVerifier.verify(token);` 한 줄뿐이며 호출부도 `verify()` 안 1곳뿐이다.
- **왜 문제인가**: 아무 추가 로직 없는 1줄 private 메서드는 코드를 읽을 때 불필요한 간접 참조 한 단계를 더 만든다.
- **개선 방법**: `processToken()`을 인라인해 `verify()`의 `try` 블록 안에서 `appleJwkVerifier.verify(token)`을 직접 호출한다. (A-2 적용 후 `GoogleTokenVerifier.processToken()`도 같은 모양의 1줄 위임이 되므로 함께 인라인 검토)
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/oauth/AppleTokenVerifier.java` (A-2 적용 시 `GoogleTokenVerifier.java`도 포함)
- **예상 변경 라인 수**: ~6줄
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 미세한 가독성 개선

### B-5. `SecurityConfig` — 주석 블록이 실제 설명 대상 메서드와 다른 위치에 붙어 있음

- **Priority**: Low
- **Category**: Readability
- **구현 상태**: ✅ 완료 — 주석 블록이 `securityFilterChain()` 메서드 바로 위로 이동됐다 (`auth/security/SecurityConfig.java:51-57`).
- **문제**: `permitAll` 경로별 이유(login/refresh/logout/dev-login/apple-notifications/error 등)를 설명하는 여러 줄 주석이 `corsConfigurationSource()` `@Bean` 메서드 바로 위에 붙어 있는데, 이 주석 내용은 실제로는 아래에 있는 `securityFilterChain()` 메서드의 `authorizeHttpRequests(...).permitAll()` 설정을 설명하는 내용이다. 또한 이 블록의 줄바꿈이 부자연스럽게 쪼개져 있다(예: "로그아웃은" / "만료·폐기" / "토큰도" / "body로 처리하기 위해 permitAll."이 4줄로 분절).
- **왜 문제인가**: `corsConfigurationSource()`를 읽는 개발자는 CORS와 무관한 인증 정책 설명을 보게 되고, 정작 `securityFilterChain()`의 `permitAll` 목록을 바꾸려는 개발자는 바로 위에서 그 근거를 찾지 못한다.
- **개선 방법**: 해당 주석 블록을 `securityFilterChain()` 메서드 위로 옮기고 줄바꿈을 자연스러운 문장 단위로 재정리한다.
- **API 영향**: No Impact (주석만 이동)
- **예상 변경 파일**: `auth/security/SecurityConfig.java`
- **예상 변경 라인 수**: ~8줄
- **위험도**: Low
- **테스트 영향도**: 없음
- **예상 효과**: 정책 변경 시 올바른 위치에서 근거 확인 가능, 가독성 향상

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **Apple/GoogleTokenVerifier의 `iss` 클레임 미검증 TODO** — 두 클래스 모두 "`AppleNotificationVerifier`처럼 iss까지 명시 검증하는 편이 일관적"이라는 TODO가 이미 코드에 남아 있다. JWKS 소스 자체가 provider 전용이라 실질 위험은 낮다는 원 작성자 판단에 동의하며, 무엇보다 **토큰 검증 로직에 새 검증 조건을 추가하는 것 자체가 비즈니스/보안 로직 변경**이라 "API 계약·로직 100% 유지"라는 이번 감사의 전제와 맞지 않는다. 별도 스펙·리뷰로 다룰 사안.
- **`GoogleOAuthClient.revokeRefreshToken()`의 URL 문자열 결합** — `REVOKE_URL + "?token=" + refreshToken`으로 쿼리 파라미터를 만드는데, `RestClient`의 URI 빌더(`uriBuilder.queryParam(...)`)를 쓰는 편이 더 관용적이다. 다만 `refreshToken`은 사용자 입력이 아니라 Google이 발급한 opaque 토큰이라 실질 인코딩 위험은 거의 없고, 기존 테스트가 정확한 URL 문자열(`REVOKE_URL + "?token=rt-value"`)을 어서션하고 있어 바꾸려면 테스트도 함께 손대야 한다 — 가치 대비 변경 비용이 낮아 보류.
- **`AppleJwkVerifier`/`GoogleJwkVerifier`(A-2 적용 후) 추가 통합 여지** — 둘 다 RS256 RemoteJWKSet 캐싱이라는 같은 골격이지만, audience 매칭 로직(Apple은 Bundle ID/Services ID 중 매칭값 자체를 반환해야 함, Google은 boolean만 필요)이 서로 달라 완전히 같은 코드는 아니다. provider가 2개뿐인 상황에서 이걸 하나의 파라미터화된 컴포넌트로 더 묶는 것은 과도한 추상화가 될 수 있어 지금은 보류.
- **`AppleNotificationEvent`의 `eventTime`/`email`/`isPrivateEmail` 필드 미사용** — `AppleNotificationService.handle()`은 이 필드들을 실제로 읽지 않는다(EMAIL_ENABLED/DISABLED 케이스도 정적 로그 문자열만 남김). 그러나 이 DTO는 Apple이 보내는 webhook payload의 명시적 계약을 그대로 반영한 것이고 `@Schema`로 이미 "email-enabled/disabled 전용" 등 의미가 문서화돼 있어, 완전한 dead code라기보다는 외부 계약을 있는 그대로 캡처해 둔 것에 가깝다. 실제 값 사용처가 생기기 전까지 필드를 지우는 것은 오히려 계약 문서로서의 가치를 없앤다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`TokenRevocationChecker` 인터페이스 + `NoOpTokenRevocationChecker` 단일 구현체** — 구현체가 하나뿐이고 항상 `false`만 반환해 과도한 추상화로 보일 수 있으나, `RefreshToken` 엔티티에 이미 `familyId`/`revokedAt`(출시 이후 RTR용) 필드가 준비돼 있고 TODO도 "출시 이후 RTR+Redis 도입 시 jti 블랙리스트 조회로 교체"라고 구체적으로 명시돼 있다. `JwtAuthenticationFilter`가 이미 인터페이스에만 의존하므로, 지금 이 seam을 걷어내면 출시 이후에 동일한 확장점을 다시 만들어야 한다 — 실제로 임박한 계획된 확장이라 YAGNI 위반이 아니다.
- **`AppleCredentialService`/`GoogleLoginCredentialService`를 `AuthService`로 합치지 않음** — provider별 credential 저장·revoke 책임을 별도 Service로 분리한 현재 구조가 SRP에 맞다. 합치면 `AuthService`가 로그인 오케스트레이션 + 2개 provider의 credential 영속화까지 떠안는 God Service가 되어 `audit-checklist.md` 9번(과도하게 큰 Service) 위반이 된다.
- **`SocialTokenVerifier` 인터페이스 + `SocialTokenVerifierRegistry`(Strategy + `EnumMap`)** — provider가 3개뿐이라 과도해 보일 수 있으나, 이미 실사용 중인 검증된 확장점이다(Kakao 추가 시 `AuthService`/registry 코드를 전혀 안 건드리고 `@Component` 하나만 추가해서 끝났다는 사실 자체가 이 추상화의 실익을 증명한다). YAGNI가 아니라 이미 값을 증명한 패턴.
- **`AppleCredential`/`GoogleLoginCredential` 엔티티를 하나로 합치지 않음** — 필드 구조(`id`, `user`, `refreshTokenCiphertext`)는 비슷해 보이지만 `update()` 시맨틱이 근본적으로 다르다(Apple은 재로그인마다 refreshToken·clientId를 항상 함께 덮어씀, Google은 refreshToken이 있을 때만 조건부로 덮어씀 — 코드 주석에도 이 비대칭이 명시돼 있음). 하나의 provider 판별 컬럼을 가진 테이블로 합치면 오히려 nullable 컬럼과 조건 분기가 늘어나 가독성이 떨어진다.

## 15. 백엔드 아키텍처 개선 제안

### Redis — jti revocation 블랙리스트

- **왜 필요한지 / 적용 가치**: `NoOpTokenRevocationChecker`가 이미 이 자리를 비워두고 있고, 출시 이후 RTR(refresh token rotation) reuse 탐지를 위해서도 결국 필요해질 기능이다. access JWT는 자체 만료까지 무효화할 방법이 없는데(로그아웃해도 access는 만료 시각까지 유효 — `AuthController.logout` Javadoc에 이미 명시), 탈취된 access 토큰을 즉시 차단하려면 jti 블랙리스트가 필요하다.
- **장단점**: 장점 — 로그아웃/탈퇴 즉시 access 토큰 무효화 가능, RTR reuse 탐지 기반 마련. 단점 — Redis 운영 부담 추가, 모든 인증 요청마다 Redis round-trip이 추가돼 지연 증가(로컬 캐시·TTL 설계 필요).
- **구현 난이도**: 중간 — `TokenRevocationChecker` 인터페이스가 이미 있어 구현체 교체만 하면 되지만, "폐기 시 jti를 어떻게 수집·전파할지"(로그아웃 시 jti 기록, TTL을 access 만료 시각과 맞추는 등) 설계가 필요.
- **Now / Later / Never**: **Later** — 현재 트래픽·Milestone 규모에서 access 토큰 즉시 무효화가 실제로 요구되는 사고가 아직 없고, 출시 이후 RTR 도입과 자연스럽게 묶어서 하는 편이 중복 설계를 피할 수 있다.

### Resilience — 소셜 provider 호출 타임아웃/Circuit Breaker

- **왜 필요한지 / 적용 가치**: A-3에서 지적한 타임아웃 미설정은 최소한의 방어선이고, 여기에 더해 Circuit Breaker(Resilience4j)를 붙이면 Apple/Google/Kakao 중 하나가 장애일 때 실패가 예상되는 호출을 빠르게 차단해 스레드·커넥션 소모를 더 줄일 수 있다.
- **장단점**: 장점 — provider 장애 전파 최소화, 빠른 실패로 사용자에게 즉시 503(`AUTH_SOCIAL_PROVIDER_UNAVAILABLE`, 이미 있는 에러코드) 응답 가능. 단점 — 새 의존성(Resilience4j) 추가, half-open 임계값 등 튜닝 필요, 로컬/테스트 환경에서의 mock 처리 복잡도 증가.
- **구현 난이도**: 낮음(타임아웃) ~ 중간(Circuit Breaker)
- **Now / Later / Never**: 타임아웃(A-3)은 **Now**(이번 A 항목으로 이미 포함). Circuit Breaker는 **Later** — A-1(트랜잭션 재구성)·A-3(타임아웃)가 먼저 정리되면 실제 장애 영향 범위가 크게 줄어들어, Circuit Breaker의 한계효용이 지금 당장은 크지 않다.

### Async Processing — 탈퇴 시 Apple/Google revoke 호출

- **왜 필요한지 / 적용 가치**: `AppleCredentialService.revokeAndDeleteIfPresent`/`GoogleLoginCredentialService.revokeAndDeleteIfPresent`는 이미 실패해도 로그만 남기는 best-effort 설계다(사용자 응답에 영향 없음). 이런 성격의 호출은 `@Async`로 돌려 탈퇴 요청의 응답 지연에서 완전히 분리할 수 있다.
- **장단점**: 장점 — 탈퇴 API 응답 지연 감소. 단점 — `@Async`는 별도 스레드 풀 설정·예외 처리(비동기 예외는 호출자에게 전파 안 됨 — 지금도 어차피 로그만 남기므로 큰 차이는 없음) 필요, 트랜잭션 경계가 더 복잡해짐(A-1과 함께 설계해야 함).
- **구현 난이도**: 낮음 ~ 중간(A-1 트랜잭션 재구성과 맞물려야 실익이 큼)
- **Now / Later / Never**: **Later** — A-1의 트랜잭션 경계 재구성이 선행되지 않은 채 `@Async`만 추가하면 "트랜잭션 안에서 외부 호출"이라는 근본 문제는 그대로 남고 복잡도만 늘어난다. A-1 이후 자연스럽게 재검토.

### Security — 로그인 엔드포인트 Rate Limiting

- **왜 필요한지 / 적용 가치**: `POST /api/v1/auth/login`은 `permitAll`인 공개 엔드포인트이고, 매 요청이 외부 HTTP 호출 + DB 트랜잭션(A-1)을 유발한다. 현재 이 엔드포인트에 대한 rate limiting은 Spring Security/애플리케이션 레벨 어디에도 보이지 않는다(인프라 레벨 WAF/Nginx 설정은 이 레포 범위 밖).
- **장단점**: 장점 — 위조 토큰 대량 요청으로 인한 리소스 소모(A-1·A-3와 결합 시 피해가 커짐) 방지. 단점 — 분산 환경 rate limiting은 결국 Redis 등 공유 저장소가 필요해 단순 in-memory bucket으로는 다중 인스턴스에서 한계가 있음.
- **구현 난이도**: 낮음(단일 인스턴스, in-memory) ~ 중간(분산, Redis 기반)
- **Now / Later / Never**: **Later** — 현재까지 실제 남용 사례가 관측되지 않았고, 제대로 하려면(다중 인스턴스 대응) Redis 도입과 맞물리는 편이 합리적이다. 다만 인증되지 않은 공개 엔드포인트라는 특성상 A-1/A-3보다는 후순위지만 방치할 항목은 아니라는 점은 명시해 둔다.

## 상태 (2026-08-05 갱신)

- **A/B (9개 항목)**: ✅ 전부 코드 재확인 완료 — 이미 구현됨. 각 항목 **구현 상태** 줄 참고.
- **C/D**: 재확인 결과 판단 유효 — 수정하지 않음.
- **15. 백엔드 아키텍처 개선 제안 (Redis jti 블랙리스트 / Circuit Breaker / 탈퇴 시 revoke 비동기화 / 로그인 Rate Limiting)**: grep 재확인 결과 4개 전부 **미구현**. 문서 판단대로 모두 Later — 다만 로그인 Rate Limiting은 공개 엔드포인트 보호 차원에서 방치하지 않을 것을 권장(§15 해당 항목 참고).
