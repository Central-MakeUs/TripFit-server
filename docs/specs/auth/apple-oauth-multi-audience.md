# Apple Sign In — Bundle ID / Services ID 이원화 대응 (#64 amend)

> 상태: Implemented (2026-07-31, `fix/64-apple-oauth-multi-audience` — `./gradlew build` 통과)
> MVP: In scope (소셜 로그인은 `mvp.md` Must)
> 관련 BR: 해당 없음 (Apple Sign In 사양 자체의 제약)

## 목표

Apple 로그인이 **iOS 네이티브 앱(Bundle ID)** 과 **모바일 웹브라우저(Services ID)** 두 경로로 동시에 들어올 때, 로그인 `aud` 검증·S2S 웹훅 `aud` 검증·탈퇴 시 revoke 호출이 각 유저가 실제로 로그인한 경로에 맞는 client_id로 동작하게 한다.

## 배경

- `docs/specs/auth/auth-social-login.md`에 `Apple aud 값 (bundle ID vs Services ID)`가 `[미정]`으로 남아 있었음. `.env.example`이 `APPLE_CLIENT_ID` 단일 값에서 `APPLE_BUNDLE_ID`+`APPLE_SERVICE_ID`로 분리되면서 이 결정이 실질적으로 시작됨 — 이 스펙이 그 결정을 확정하고 코드에 반영한다.
- 2026-07-31 프론트 확인 결과:
  - **iOS 네이티브**: `expo-apple-authentication`의 `AppleAuthentication.signInAsync()`(`ASAuthorizationAppleIDProvider` 래핑) — `app.json`의 `bundleIdentifier: "com.tripfit.app"`가 자동으로 id_token `aud`에 찍힘.
  - **모바일 웹브라우저(RN WebView 아님)**: `AppleID.auth.init()`(Apple JS SDK) — `clientId`가 Services ID(`NEXT_PUBLIC_APPLE_CLIENT_ID`)여야만 동작. 이 경로의 id_token `aud`는 Services ID.
  - **RN WebView + Android**: `SocialLoginStep.tsx`가 `showApple = !isReactNativeWebView() || isIOS()`로 분기해 Apple 버튼 자체를 숨김 — 이 조합만 Apple 로그인 자체가 발생하지 않음.
  - 즉 "네이티브 앱"이라는 좁은 의미로는 iOS만, 하지만 **일반 모바일 브라우저로 열면 OS 무관하게 Services ID 경로가 발생** — Android 유저도 브라우저로 로그인하면 Services ID 경로를 탄다.
- 기존 `#64`(소셜 provider revoke, 2026-07-31 Implemented) 구현은 **단일 `APPLE_CLIENT_ID`** 를 아래 4곳에 그대로 재사용하도록 짜여 있었다:
  1. `AppleTokenVerifier` — 로그인 id_token `aud` 검증
  2. `AppleNotificationVerifier`(S2S 웹훅, `#5`) — 알림 outer JWT `aud` 검증
  3. `AppleOAuthClient.exchangeAuthorizationCodeForRefreshToken()` — 토큰 교환 `client_id` form 파라미터
  4. `AppleOAuthClient.buildClientSecretJwt()` — client_secret JWT의 `sub` 클레임
- 문제: `AppleCredential`은 로그인 시점에 **어떤 client_id로 발급됐는지 저장하지 않는다.** Services ID 경로로 로그인한 유저의 refresh token을 나중에 탈퇴 시 Bundle ID로 revoke 시도하면 Apple이 거부 — 웹 경로로 가입한 유저의 revoke가 **조용히 실패**한다(현재 코드는 실패를 삼키는 best-effort라 알아차리기 어려움).
- `#64` 이슈는 사용자가 2026-07-31 재오픈함 — 이 스펙은 그 amend 작업.

## 요구사항

### Must Have

- [x] `OAuthProperties.appleClientId` 제거 → `appleBundleId`/`appleServiceId` 두 필드로 분리 (env: `APPLE_BUNDLE_ID`/`APPLE_SERVICE_ID` — `.env.example`·prod env·GitHub Secrets는 이미 갱신 완료, 코드만 따라가면 됨)
- [x] `OAuthProperties`에 `getAppleAudiences()` 추가 — `Google.getGoogleClientIds()`와 동일 패턴(blank 필터링된 리스트)
- [x] `AppleTokenVerifier` — `aud` 검증을 `[bundleId, serviceId]` 리스트 중 하나만 맞아도 통과하도록 변경(Google `hasValidAudience` 패턴 재사용), **실제로 매칭된 client_id 값**을 검증 결과에 실어 반환
- [x] `OAuthProfile`에 nullable 필드 추가(`appleMatchedClientId` 등) — Apple 로그인일 때만 채워지고, Google/Kakao는 항상 null. verifier→AuthService 경계 DTO를 그대로 재사용(신규 타입 도입 안 함)
- [x] `AppleNotificationVerifier`(S2S 웹훅) — `aud` 검증을 동일하게 `[bundleId, serviceId]` 리스트 중 하나만 맞아도 통과
- [x] `apple_credential` 테이블에 `apple_client_id`(varchar, NOT NULL) 컬럼 추가 — 로그인 시 매칭된 client_id 원문을 저장, 이후 재교환·revoke 호출에 그대로 재사용. `AppleCredential` 엔티티·`docs/architecture/erd.md` 갱신
- [x] `AppleOAuthClient.exchangeAuthorizationCodeForRefreshToken/revokeRefreshToken/buildClientSecretJwt` — 내부에서 항상 `oAuthProperties.getAppleClientId()`를 읽던 것을 **호출부가 넘기는 `clientId` 파라미터**로 변경
- [x] `AppleCredentialService.saveIfAuthorizationCodePresent` — `AuthService.login()`이 넘긴 매칭 client_id를 받아 교환 호출·`AppleCredential` 저장에 사용
- [x] `AppleCredentialService.revokeAndDeleteIfPresent` — 저장된 `apple_client_id` 컬럼 값을 읽어 revoke 호출에 사용(로그인 시점과 항상 동일한 값 재사용 — Bundle ID로 로그인했으면 Bundle ID로, Services ID로 로그인했으면 Services ID로 revoke)
- [x] `docs/specs/auth/auth-social-login.md` — `[미정]` 항목(Apple aud) 확정 반영 + env 표 갱신(`APPLE_CLIENT_ID` → `APPLE_BUNDLE_ID`/`APPLE_SERVICE_ID`)
- [x] `docs/specs/user/user-account-withdrawal.md` — `#64` Apple 섹션에 이 amend 반영(멀티 client_id 대응)
- [x] `docs/specs/auth/auth-apple-server-notifications.md` — `APPLE_CLIENT_ID` 참조를 멀티 audience 검증으로 갱신
- [x] `deploy/README.md` env 표 — `APPLE_CLIENT_ID` 행 제거, `APPLE_BUNDLE_ID`/`APPLE_SERVICE_ID` 행 추가
- [x] `.github/workflows/ci-cd.yml` — secrets 참조·`envs:` 목록·export 스크립트에서 `APPLE_CLIENT_ID` → `APPLE_BUNDLE_ID`+`APPLE_SERVICE_ID`로 교체(레거시 이름 잔존 금지 — harness STOP §4). 추가로 root `docker-compose.yml`·`deploy/app/docker-compose.yml`·`deploy/app/.env.example`·`AuthErrorCode.java` `@Schema`도 동일하게 갱신
- [x] 테스트 갱신: `AppleTokenVerifierTest`, `AppleOAuthClientTest`, `AppleNotificationVerifierTest`, `AppleCredentialServiceTest`, `AuthServiceTest`, `application-test.yml`
- [x] GitHub 이슈 `#64` 본문에 이 amend 내용 반영

### Nice to Have

- (없음)

### Out of Scope (이번 스펙에서 하지 않음)

- 실제 Apple 테스트 계정으로 두 경로(Bundle ID·Services ID) 각각 수동 검증 — `user-account-withdrawal.md`의 기존 "코드 밖" 체크리스트에 위임, 이 스펙에서 새로 만들지 않음
- `docs/product/platform.md` 환경 A/B 설명에 "모바일 브라우저 Apple 로그인은 OS 무관하게 Services ID 경로를 탄다"는 프론트 디테일을 반영하는 것 — 원하면 별도 후속으로 분리

## API / 인터페이스

API 계약 변경 없음. `POST /api/v1/auth/login`의 요청(`{provider, token, authorizationCode}`)·응답 스키마는 그대로 — 서버 내부에서 허용하는 `aud` 값 개수만 1개→2개로 늘어남. 클라이언트가 관찰 가능한 차이가 없어 `Breaking-Change-Reason` 트레일러 대상 아님.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md`
- 변경 테이블: `apple_credential`

```
apple_credential
  + apple_client_id  varchar  NOT NULL   -- 로그인 시 검증된 Bundle ID 또는 Services ID 원문값
```

- dev DB, 상용 보존 데이터 없음(harness-workflow §3) — Flyway 등 마이그레이션 작성 안 함, 엔티티 최신화 후 `ddl-auto`/리셋으로 반영

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| (외부 요건) | Apple Sign In: id_token/S2S 알림의 `aud`는 로그인 시 사용한 client_id(App ID 또는 Services ID)와 일치해야 함 — Apple 사양 | `AppleTokenVerifier`, `AppleNotificationVerifier` |
| (외부 요건) | Apple 토큰 교환·revoke는 authorizationCode를 발급한 것과 **동일한 client_id**로만 성공 | `AppleOAuthClient`, `AppleCredentialService` |

## 검증 시나리오

### 정상

- [x] iOS 네이티브 로그인(authorizationCode가 Bundle ID로 발급) → `AppleCredential.apple_client_id = Bundle ID` 저장 → 탈퇴 시 Bundle ID로 revoke 호출 — `AppleCredentialServiceTest#saveIfAuthorizationCodePresent_whenNewUser_createsCredential`, `AppleOAuthClientTest`(`com.tripfit.app` 케이스)
- [x] 모바일 브라우저 로그인(Services ID로 발급) → `AppleCredential.apple_client_id = Services ID` 저장 → 탈퇴 시 Services ID로 revoke 호출 — `AppleCredentialServiceTest#saveIfAuthorizationCodePresent_whenExistingCredential_overwritesRefreshTokenAndClientId`·`#revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesWithStoredClientIdThenDeletes`, `AppleOAuthClientTest#exchangeAuthorizationCodeForRefreshToken_withServiceId_usesServiceIdAsClientId`
- [ ] S2S 웹훅 — Bundle ID·Services ID 어느 쪽으로 발급된 이벤트든 `aud` 검증 통과 — 기존 `AppleNotificationVerifierTest`가 성공 경로를 테스트하지 않는 관례(`AppleTokenVerifierTest`와 동일)라 이번에도 신규 성공 케이스 미작성. 필요 시 후속

### 엣지 · 실패

- [x] 두 client_id 다 미설정 → 기존과 동일하게 500(`INTERNAL_ERROR`, 서버 설정 누락) — `AppleTokenVerifierTest#verify_missingClientId_throwsInternalError`, `AppleNotificationVerifierTest#verify_missingClientId_throwsInternalError`
- [ ] id_token의 `aud`가 Bundle ID·Services ID 어느 쪽과도 불일치 → `AUTH_SOCIAL_TOKEN_INVALID`(기존 동일) — 별도 테스트 미작성(기존 `hasValidAudience`→`findMatchedAudience` 리팩터는 로직 동치, 실제 Apple 토큰 없이는 성공/실패 분기 테스트 불가한 기존 제약과 동일)
- [ ] 기존 `AppleCredential` row(마이그레이션 전, `apple_client_id` 없음) — dev DB 리셋으로 처리, 별도 백필 로직 없음

### 수동 / 통합 (해당 시)

- [ ] (기존 `user-account-withdrawal.md` 코드 밖 체크리스트에 위임 — 실제 Apple 테스트 계정)

## 완료 기준

- [x] `./gradlew test` 통과 (기존 `#64` 테스트 전부 통과 유지 + 신규 Services ID 경로 케이스)
- [x] `./gradlew build` 성공 (spotlessCheck 포함)
- [x] `docs/architecture/erd.md`·관련 스펙 3종·`deploy/README.md`·`ci-cd.yml`·두 `docker-compose.yml`·`deploy/app/.env.example`·`AuthErrorCode.java` 전부 `APPLE_CLIENT_ID` 잔존 없이 갱신
- [x] GitHub `#64` 본문 갱신

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `OAuthProfile`에 Apple 전용 nullable 필드를 두는 방식 | 확정 필요(구현 설계) | 대안: Apple만 별도 검증 결과 타입을 쓰거나, `AuthService`가 verifier와 별개로 aud를 한 번 더 파싱 — 후자는 중복 파싱이라 비권장. 리뷰 시 이견 있으면 조정 |
| 기존 서비스 중인 유저의 `apple_credential` row(컬럼 없음 상태) | 확정 필요 | dev·상용 보존 데이터 없음 원칙상 DB 리셋으로 충분하다고 가정 — 만약 이미 prod에 실사용 row가 있다면 별도 백필 필요 여부 확인 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-31 | 초안 — `#64` 재오픈 amend, `auth-social-login.md`의 `[미정]`(Apple aud) 확정 반영 |
