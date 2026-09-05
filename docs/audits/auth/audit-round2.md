# Auth Architecture Audit — Round 2 (2026-08-05)

`com.tripfit.tripfit.auth` 패키지를 대상으로 1차 리팩토링 반영 이후 진행한 2차 아키텍처 감사 문서다. 반드시 수정해야 하는 A 항목 1건과 유지보수성 리팩토링 B 항목 1건(참고 C 2건, 비권장 D 2건)을 도출했다. 도출된 항목은 승인 대기 상태로 정리되었으며, 이후 반영 내역은 `refactor-log.md`에 기록되었다.

## 범위

- 패키지: `com.tripfit.tripfit.auth` (+ 하위 패키지 `config`, `controller`, `dev/controller`, `dev/service`, `domain`, `dto`, `exception`, `jwt`, `oauth`, `repository`, `security`, `service`)
- 감사자: 서브에이전트 (읽기 전용) — 1차 반영(커밋 `0905d19` Refactor, `09def02` Test, `481c3de` Docs) 이후 재검사
- 기준: `audit-checklist.md` 1~15항목, `core-guardrails.md` ⛔ STOP, `spring-boot-java.md`
- main 49개 파일(dev 포함) 전수 재검토 + 관련 호출부(`user/service/UserWithdrawalService`, `UserWithdrawalPersistenceService`, `User.reviveIfWithdrawn()`, `notification/controller/*`의 `@AuthorizedUser` 사용처) 교차 확인
- `docs/audits/auth/audit.md`(1차 A 4·B 5·C 3·D 4)와 `refactor-log.md`(반영 diff) 원문 대조, `git log 0905d19 09def02 481c3de`로 실제 반영 이력 확인

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `AppleCredentialService`/`GoogleLoginCredentialService` — authorizationCode 교환·revoke HTTP 호출이 여전히 자체 `@Transactional` 안에 있음 (1차 A-1 원칙이 두 서비스에는 미적용)

- **Priority**: Critical
- **Category**: Architecture / Performance
- **문제**: 1차 A-1은 `AuthService.login()`의 트랜잭션에서 `SocialTokenVerifier.verify()`(소셜 토큰 검증 HTTP)를 빼내 `AuthLoginPersistenceService.persist()`라는 짧은 `@Transactional`로 DB 쓰기만 분리했다. 그런데 `AuthService.login()`은 `persist()` 이후 바로 `appleCredentialService.saveIfAuthorizationCodePresent(...)`(APPLE) 또는 `googleLoginCredentialService.saveIfAuthorizationCodePresent(...)`(GOOGLE)를 호출한다(`AuthService.java:89-99`). 이 두 메서드는 **각자 자체 `@Transactional`**(`AppleCredentialService.java:42`, `GoogleLoginCredentialService.java:43`)이며, 그 트랜잭션 **내부**에서 `appleOAuthClient.exchangeAuthorizationCodeForRefreshToken(...)`/`googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(...)` — Apple/Google 토큰 엔드포인트로의 실제 HTTP POST(A-3에서 설정한 connect 3초·read 5초 타임아웃 대상, 공용 `RestClient` 재사용) — 를 수행한 뒤에야 `repository.findByUser_Id`/`save()`를 호출한다. `AuthController.java` Javadoc과 `AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED`/`AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED`가 보여주듯 APPLE·GOOGLE 로그인은 **매번(최초·재로그인 모두) authorizationCode가 필수**이므로, 이 경로는 예외 케이스가 아니라 **모든 Apple·Google 로그인 요청마다** 실행되는 주 경로다. 회원 탈퇴 시 호출되는 `revokeAndDeleteIfPresent()`(`AppleCredentialService.java:74`, `GoogleLoginCredentialService.java:84`)도 동일 구조 — `@Transactional` 안에서 `appleOAuthClient.revokeRefreshToken(...)`/`googleOAuthClient.revokeRefreshToken(...)` HTTP 호출 후 `deleteByUser_Id()`를 실행한다.
- **왜 문제인가**: 1차 A-1의 근거("HikariCP 등 커넥션 풀은 유한하다. Kakao/Google/Apple 중 하나라도 응답이 느려지거나 장애가 나면... 커넥션 풀이 고갈되고, 이는 auth와 무관한 다른 모든 API까지 연쇄적으로 마비시킬 수 있다")가 그대로 재현된다. `AuthLoginPersistenceService.persist()`로 DB 쓰기를 분리한 것과 별개로, 곧바로 이어지는 `saveIfAuthorizationCodePresent()`가 **또 다른 DB 커넥션을 열어둔 채** Apple/Google 토큰 엔드포인트 응답을 최대 8초(connect 3초+read 5초)까지 기다린다 — 1차에서 "고쳤다"고 검증(oasdiff·전체 테스트)한 로그인 흐름에 사실상 같은 문제가 형태만 바뀌어 남아 있는 것이다. `saveIfAuthorizationCodePresent()`는 실제로 GOOGLE/APPLE 로그인마다(Kakao 제외) 항상 실행되는 hot path이고, `revokeAndDeleteIfPresent()`는 탈퇴 시에만 실행되는 cold path라 상대적으로 영향은 작지만 같은 아키텍처 결함이다. 기존 테스트(`AppleCredentialServiceTest`, `GoogleLoginCredentialServiceTest`)는 Mockito 기반 단위 테스트라 트랜잭션 경계 자체를 검증하지 못해 이 문제가 그린 상태로 방치돼 있었다(1차 A-1의 "테스트 영향도" 절이 이미 명시했던 것과 동일한 사각지대).
  - **부분 실패 케이스 확인(참고, 문제는 아님)**: HTTP 교환이 성공한 뒤 `repository.save()`가 실패하는 경우는 이미 같은 `try { exchange → save } catch (Exception)` 블록 안에 있어 로그만 남기고 조용히 스킵된다(best-effort 설계가 의도한 대로 동작) — Apple/Google이 발급한 authorizationCode(1회용)가 소비된 뒤 credential 저장만 실패하는 상황은 이미 안전하게 흡수되고 있다. 반대로 `AuthLoginPersistenceService.persist()`(user upsert+refresh token) 자체가 실패하면 `AuthService.login()`이 그대로 예외를 던져 요청이 실패하고 부분 커밋도 없다 — 이 부분은 정상.
- **개선 방법**: 1차 A-1과 동일한 패턴(self-invocation 회피를 위한 별도 빈 분리)을 두 서비스에도 적용한다. 각 서비스를 "HTTP 교환/revoke 담당"과 "DB 조회·저장만 담당하는 짧은 `@Transactional` persistence 빈"으로 나눈다 — `AuthLoginPersistenceService`와 동일한 골격(`AppleCredentialPersistenceService`/`GoogleLoginCredentialPersistenceService` 등, 명명은 기존 컨벤션에 맞춰 조정). `saveIfAuthorizationCodePresent()`는 HTTP 교환을 트랜잭션 밖에서 먼저 실행하고, 성공한 refresh token만 넘겨 persistence 빈의 짧은 `@Transactional` 메서드(암호화+find+save)를 호출하도록 재구성한다. `revokeAndDeleteIfPresent()`도 동일하게 "find+decrypt(트랜잭션 밖 또는 얇은 조회 트랜잭션) → HTTP revoke(트랜잭션 밖) → delete(짧은 트랜잭션)"로 재구성한다. best-effort try-catch 시맨틱(실패해도 로그인·탈퇴는 계속)은 그대로 유지 가능 — catch 블록의 위치만 "HTTP 실패"와 "DB 실패"를 각각 감싸도록 조정하면 된다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/service/AppleCredentialService.java`, `auth/service/GoogleLoginCredentialService.java` (+ 신규 persistence 빈 2개), `test/.../auth/service/AppleCredentialServiceTest.java`, `test/.../auth/service/GoogleLoginCredentialServiceTest.java`
- **예상 변경 라인 수**: 80~120줄 (2개 서비스 + 신규 빈 2개 + 테스트 갱신)
- **위험도**: Medium — 1차 A-1과 동일한 성격의 트랜잭션 경계 이동. Mockito 단위 테스트만으로는 회귀를 못 잡으므로 `AuthSecurityIntegrationTest`(로그인 흐름) 및 탈퇴 통합 테스트로 흐름 자체가 여전히 성공하는지 재확인 필요.
- **테스트 영향도**: 기존 `AppleCredentialServiceTest`/`GoogleLoginCredentialServiceTest`는 Mockito mock 기반이라 대부분 그대로 통과 예상되나, 클래스 분리 시 생성자 시그니처가 바뀌면 `@InjectMocks`/직접 생성 대상 갱신 필요.
- **예상 효과**: 1차 A-1이 "로그인 지연·소셜 provider 장애가 DB 커넥션 풀 고갈로 번지는 것을 차단"하려던 목표를 실제로 완결시킴 — 현재는 Apple/Google 로그인마다 이 경로로 동일 리스크가 재도입돼 있던 상태였다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `AppleTokenVerifier`/`GoogleTokenVerifier` — `verify()`의 예외 매핑 catch 블록 5종이 두 파일에 거의 그대로 중복

- **Priority**: Low
- **Category**: Cleanup
- **문제**: `AppleTokenVerifier.verify()`(`:44-118`)와 `GoogleTokenVerifier.verify()`(`:43-116`)는 JWKS 서명 검증(`appleJwkVerifier.verify`/`googleJwkVerifier.verify`) 이후 audience 매칭 로직만 다르고, 그 뒤를 감싸는 `catch (TripFitException)` / `catch (BadJWTException)` / `catch (BadJOSEException)` / `catch (ParseException)` / `catch (JOSEException)` / `catch (Exception)` 6개 블록은 로그 메시지의 "Apple"/"Google" 표기, `verifyContext()`가 반환하는 `SocialProvider` 상수만 다를 뿐 예외→`AuthErrorCode` 매핑·`SocialIntegrationLog.warn` 호출 순서·구조가 전부 동일하다(1차 B-3이 "expired 문자열 판별" 휴리스틱만 `SocialErrorMessages`로 뽑아냈고, 이 catch 블록 골격 자체는 그대로 남았다).
- **왜 문제인가**: provider 응답 포맷 변화나 새 예외 타입 추가로 이 매핑 로직을 조정해야 할 때 두 파일을 동시에 찾아 고쳐야 하고, 한쪽만 갱신하면 Apple/Google 간 에러 코드 매핑이 미묘하게 어긋나는 회귀가 생길 수 있다.
- **개선 방법**: JWTClaimsSet을 반환하는 `Supplier`(체크 예외를 던질 수 있어야 하므로 전용 `@FunctionalInterface`)와 `SocialProvider`/`Logger`/`SocialIntegrationAction`을 받아 catch 블록 5종을 대신 실행하고 매핑된 `JWTClaimsSet`(또는 예외)을 반환하는 작은 package-private 헬퍼(예: `oauth/JwtClaimsVerificationSupport`)로 추출한다. `AppleTokenVerifier`/`GoogleTokenVerifier`는 이 헬퍼로 `JWTClaimsSet`을 얻은 뒤 각자의 audience 매칭·`OAuthProfile` 생성 로직만 유지한다. Kakao는 JWT가 아닌 HTTP 응답 기반이라 이 헬퍼 대상이 아니다.
- **API 영향**: No Impact
- **예상 변경 파일**: `auth/oauth/AppleTokenVerifier.java`, `auth/oauth/GoogleTokenVerifier.java`, (신규) `auth/oauth/JwtClaimsVerificationSupport.java`
- **예상 변경 라인 수**: ~90줄 (중복 제거 -70, 신규 헬퍼 +40 내외)
- **위험도**: Low — 순수 코드 이동, 기존 `AppleTokenVerifierTest`/`GoogleTokenVerifierTest`의 예외별 케이스(만료·서명 불일치·파싱 실패·JWK 조회 실패 등)가 그대로 통과해야 무손실임을 증명.
- **테스트 영향도**: 두 테스트 클래스의 기존 케이스는 동일하게 통과해야 함 — 신규 헬퍼에 대한 별도 단위 테스트 추가는 선택.
- **예상 효과**: 두 파일에 흩어진 예외 매핑 로직 단일화, 향후 provider 응답 포맷 변경 시 1곳만 수정.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`RefreshTokenRepository.deleteAllByUserId`/`AppleCredentialRepository.deleteByUser_Id`/`GoogleLoginCredentialRepository.deleteByUser_Id` — Spring Data 파생 delete 쿼리가 벌크 DELETE가 아니라 "조회 후 개별 삭제"로 동작함** — `@Modifying @Query`가 없는 `deleteBy...` 파생 메서드는 대상 엔티티를 먼저 SELECT한 뒤 각각 `EntityManager.remove()`를 호출한다(JPA 라이프사이클 콜백을 위해 의도된 동작). `RefreshToken`은 사용자당 활성 세션 수(보통 1~수개) 만큼만, `AppleCredential`/`GoogleLoginCredential`은 `@OneToOne`이라 최대 1행만 대상이라 실질적인 성능 영향은 미미하다. `@Modifying` 벌크 삭제로 바꾸면 SELECT 1회를 아낄 수 있지만, 이번 라운드에서 발견한 A-1(트랜잭션 경계)만큼의 실익이 없어 별도로 다루지 않는다.
- **1차 `audit.md`의 C 항목 재검증 결과 — 4개 전부 여전히 유효, 변경 없음**:
  - `AppleTokenVerifier`/`GoogleTokenVerifier`의 `iss` 클레임 미검증 TODO — 코드에 TODO 그대로 남아 있고, 새 검증 조건 추가는 비즈니스/보안 로직 변경이라 "API 계약·로직 100% 유지" 전제와 여전히 맞지 않는다.
  - `GoogleOAuthClient.revokeRefreshToken()`의 `REVOKE_URL + "?token=" + refreshToken` 문자열 결합 — 여전히 동일 코드. `refreshToken`이 사용자 입력이 아닌 opaque 토큰이라 인코딩 위험이 실질적으로 낮고, 기존 테스트가 정확한 URL 문자열을 어서션하고 있어 가치 대비 변경 비용이 여전히 낮다.
  - `AppleJwkVerifier`/`GoogleJwkVerifier`(1차 A-2로 신규 도입된 `GoogleJwkVerifier` 포함) 추가 통합 여지 — 두 클래스 모두 RS256 RemoteJWKSet 캐싱 골격은 같지만 audience 매칭 로직(Apple: 매칭값 반환 필요, Google: boolean만 필요)이 달라 하나로 묶으면 오히려 조건 분기가 늘어난다. provider 2개뿐인 상황에서 과도한 추상화 우려는 여전히 유효.
  - `AppleNotificationEvent`의 `eventTime`/`email`/`isPrivateEmail` 필드 미사용 — `AppleNotificationService.handle()`은 여전히 EMAIL_ENABLED/DISABLED에서 정적 로그 문자열만 남기고 이 필드들을 읽지 않는다. Apple webhook payload 계약을 그대로 캡처해 둔 DTO라는 성격은 변하지 않았다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`AuthLoginPersistenceService`/`DevAuthService`의 `revive(User)` private 래퍼 중복 — 추가 추출하지 않음** — 1차 B-2로 "탈퇴 부활" 실질 로직은 이미 `User.reviveIfWithdrawn()`으로 SSOT화됐다. 남은 것은 `Optional.map(this::revive)` 체이닝에 필요한 `{ user.reviveIfWithdrawn(); return user; }` 2~3줄짜리 어댑터 래퍼이며, 이는 각 클래스의 upsert 흐름(하나는 신규 생성 시 프로필 갱신까지 겸함, 하나는 고정 테스트 계정 생성)에 자연스럽게 붙어 있는 지역적 코드다. 이 이상 공통 유틸로 뽑으면 2줄짜리 메서드 참조 하나를 위해 새 파일·새 의존성을 만드는 과잉 추상화가 되어 오히려 각 클래스의 응집도만 떨어뜨린다.
- **1차 `audit.md`의 D 항목 재검증 결과 — 4개 전부 여전히 유효**: `TokenRevocationChecker` 인터페이스+`NoOpTokenRevocationChecker` 단일 구현체(출시 이후 RTR 확장점, `RefreshToken.familyId`/`revokedAt` 필드가 이미 이를 위해 준비돼 있음 — 여전히 임박한 계획된 확장), `AppleCredentialService`/`GoogleLoginCredentialService`를 `AuthService`로 합치지 않음(SRP, God Service 방지 — A-1(round2) 적용 후에도 이 분리 자체는 그대로 유지할 가치), `SocialTokenVerifier` 인터페이스+`SocialTokenVerifierRegistry`(Strategy+EnumMap, 이미 3개 provider로 실사용 검증됨), `AppleCredential`/`GoogleLoginCredential` 엔티티 미통합(update() 시맨틱이 근본적으로 다름 — Apple은 매번 덮어씀, Google은 조건부 갱신) — 모두 코드를 다시 읽어 확인했으며 1차 판단이 여전히 타당하다.

**참고(감사만, 수정 대상 아님) — 도메인 외부 소비처 관찰**: `user/service/UserWithdrawalService.withdraw()`는 provider revoke 호출들(`googleLoginCredentialService.revokeAndDeleteIfPresent`, `appleCredentialService.revokeAndDeleteIfPresent`, `kakaoUnlinkClient.unlink`, Google Calendar revoke)을 **모두 `persistenceService.finalizeWithdrawal(userId)`(DB cascade+soft delete 짧은 트랜잭션) 호출 이전에** 끝내도록 이미 올바르게 구조화돼 있다(클래스 주석에 "A-2"로 명시). 즉 탈퇴 유스케이스 자체의 오케스트레이션은 1차 A-1의 원칙(외부 HTTP를 DB 쓰기 트랜잭션 밖으로)을 정확히 따르고 있다 — 다만 이번 A-1(round2)에서 지적했듯 그 안에서 호출되는 `appleCredentialService.revokeAndDeleteIfPresent()`/`googleLoginCredentialService.revokeAndDeleteIfPresent()` **자체가** 각자 `@Transactional`을 갖고 있어 HTTP 호출을 다시 트랜잭션 안으로 끌어들이고 있다는 점이 이번 A-1의 핵심이다. `UserWithdrawalService`는 `user` 도메인 코드라 이번 라운드(auth 도메인) 직접 수정 대상은 아니지만, auth 도메인의 A-1(round2)을 적용하면 이 소비처의 의도(오케스트레이션 레벨에서 HTTP를 트랜잭션 밖으로)가 실제로 완성된다.

`notification/controller/NotificationController`·`DeviceTokenController`의 `@AuthorizedUser` 사용은 표준 패턴 그대로이며 auth 코드를 잘못 쓰는 패턴은 발견되지 않았다.

## 15. 백엔드 아키텍처 개선 제안

- 1차 `audit.md` §15의 4개 제안(Redis jti revocation 블랙리스트 / Resilience Circuit Breaker / Async 탈퇴 revoke / Security 로그인 Rate Limiting)을 코드베이스에서 재확인(`grep` — Redis·Resilience4j·`@Async`·rate limiting 관련 의존성·구현 전부 미검출)한 결과 **4개 전부 여전히 미구현**이며, 판단도 1차와 동일하게 모두 **Later**로 유효하다. 트래픽·Milestone 규모상 즉시 필요한 사고가 아직 없고, 출시 이후 RTR·Redis 도입과 자연스럽게 묶어 설계하는 편이 중복 작업을 피한다.
- **Resilience — Now로 격상 검토는 아님, 다만 A-1(round2)과 연계 확인**: A-1(round2)이 반영되면 `AppleCredentialService`/`GoogleLoginCredentialService`의 HTTP 호출도 트랜잭션 밖에서 실행되므로, 이후 Circuit Breaker(1차 제안, Later) 도입 시 대상 호출 지점이 `AuthLoginPersistenceService.persist()`와 대칭적인 구조가 되어 설계가 더 단순해진다 — 별도 Now 사유는 아니고, Later 항목의 선행 조건이 하나 더 명확해졌다는 참고.
- 그 외 카테고리(Concurrency/Event/Database/Monitoring/API)는 1차와 동일하게 해당 없음(YAGNI) — auth 도메인의 외부 I/O는 소셜 provider 3종(Kakao/Google/Apple)뿐이고, A-1(round2)·기존 A-3(타임아웃) 적용만으로 성능·가용성 리스크가 충분히 해소된다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
