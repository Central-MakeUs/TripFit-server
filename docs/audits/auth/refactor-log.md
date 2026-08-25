# Auth Refactor Log

## 2026-08-04 — A-1~4, B-1~5 반영

감사([`audit.md`](audit.md)) 기준 A(반드시 수정) 4개, B(유지보수성) 5개 전부 반영. 사용자 승인: "A/B 전부".

### 쉽게 설명하면 (`plain-language-reporting.md`)

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
