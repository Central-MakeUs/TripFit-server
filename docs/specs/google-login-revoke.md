# Google 로그인 Revoke 확보

> 상태: Implemented (`./gradlew build` 통과 — PR 대기, FE 배포 순서 조율·실계정 수동 검증 별도 진행)
> MVP: In scope (소셜 로그인은 이미 MVP In scope — 이 스펙은 그 위에 revoke를 보강)
> 관련 BR: 해당 없음 — [`#64`](https://github.com/Central-MakeUs/TripFit-server/issues/64)(Release Gate) 후속, Wave와 무관
> 선행: [`auth-social-login.md`](auth-social-login.md), [`user-account-withdrawal.md`](user-account-withdrawal.md)
> 참고 패턴: Apple 구현(`AppleCredential`/`AppleCredentialService`/`AppleOAuthClient`) — 동일 구조 재사용
> deferred: 로그인·캘린더 Client ID 분리 → [`google-calendar-client-id-separation.md`](google-calendar-client-id-separation.md)(#78)
> 정정 (2026-07-31): [`google-login-native-sdk-decision.md`](google-login-native-sdk-decision.md)(#77)는 "결정 필요"가 아니라 FE 확인 결과 **이미 네이티브 SDK로 구현·배포 구조까지 완료**된 것으로 밝혀져 Resolved 처리 — 아래 "클라이언트(FE) 변경 요건" 절에 정확한 두 경로(네이티브/브라우저)를 반영

## 목표

Google로 로그인한 유저가 탈퇴하면 TripFit 내부 데이터 삭제와 함께 Google 쪽 로그인 동의(consent)도 실제로 철회(revoke)되게 한다. 지금은 캘린더 연동 유저에 한해서만 revoke가 호출되는데, 캘린더 연동이 아직 FE에 구현된 적이 없어 사실상 전체 Google 유저가 이 gap의 영향을 받는다.

## 배경

- `#64` 재오픈 코멘트에서 확인된 근본 원인: 로그인 시 Google ID Token을 로컬 JWKS로만 검증(`GoogleTokenVerifier`)하고 Google 서버로 토큰 교환을 하지 않아, 서버가 revoke에 쓸 access/refresh token을 가져본 적이 없음.
- `UserWithdrawalService.revokeGoogleCalendarIfConnected()`는 `GoogleCalendarCredential`(캘린더 연동 시에만 생기는 row)이 있을 때만 실행되는데, 캘린더 연동 자체가 FE 미구현(Wave 4)이라 이 경로를 타는 실사용자가 없음.
- 해결 방향은 이미 구현된 Apple 패턴과 동일: 로그인 시 authorization code를 받아 access/refresh token으로 교환·암호화 저장하고, 탈퇴 시 그 refresh token으로 Google `/revoke`를 호출.
- **사용자 결정 사항 (2026-07-31)**:
  1. authorizationCode 누락 처리는 Apple의 "처음엔 best-effort → 나중에 강제"를 반복하지 않고 **처음부터 400 강제**.
  2. 재로그인 시 `prompt=consent`를 강제하지 않음 — 탈퇴 시 revoke가 실제로 성공하면 Google이 알아서 다음 로그인에 동의 화면을 다시 보여주므로, 정상 재로그인 UX(동의 화면 생략)는 그대로 유지.
  3. **dev 환경, 상용 데이터 없음** — 이 스펙 적용 이전에 가입한 기존 유저(이미 implicit flow로 동의 완료)에 대한 소급 대응은 고려하지 않는다.

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/specs/auth-social-login.md` | wave 1 로그인 계약 — Google `id_token`만 전제 (이번 스펙으로 amend 필요) |
| `docs/specs/user-account-withdrawal.md` | `#64` provider revoke SSOT (이번 스펙으로 Google 절 amend 필요) |
| `docs/decisions/001-auth-mobile-token-verification.md` | 모바일 토큰 검증 결정 — Google WebView 차단·네이티브 SDK 전제 |
| Apple 참고 구현 | `AppleCredential`(엔티티) · `AppleCredentialService`(저장/revoke) · `AppleOAuthClient`(교환/revoke HTTP) · `AuthService.login()`의 APPLE 분기 |

## 요구사항

### Must Have

- [x] `AuthErrorCode.AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED`(400) 신규 — GOOGLE 로그인인데 `authorizationCode` 누락·공백이면 소셜 토큰 검증 전 즉시 거부(Apple과 동일 패턴, 처음부터 강제)
- [x] `LoginRequest.authorizationCode` — 기존 필드(현재 "APPLE 전용") 재사용. `@Schema` description을 "APPLE 또는 GOOGLE 로그인 시 필수, KAKAO는 안 씀"으로 amend. 신규 필드 추가 아님
- [x] 신규 엔티티 `auth/domain/GoogleLoginCredential` — `AppleCredential`과 동일 최소 구조: `user_id`(FK, UNIQUE) · `refresh_token_ciphertext`(AES-256-GCM, `GoogleCalendarTokenCrypto` 재사용 — 신규 AES 키 없음) · `BaseTimeEntity`. `apple_client_id`류 컬럼은 불필요(아래 "설계 노트" 참고)
- [x] 신규 `auth/oauth/GoogleOAuthClient` — 로그인 전용 authorization code 교환 + revoke. `AppleOAuthClient`와 동일하게 `auth` 도메인 안에 두어 `user/googlecalendar` 패키지에 대한 역방향 의존을 만들지 않음(아래 "설계 노트" 참고). `OAuthProperties.getGoogleClientId()`/`getGoogleClientSecret()`(기존 Calendar용 값 재사용, 신규 env 없음)
  - 교환: `POST https://oauth2.googleapis.com/token` — refresh_token이 응답에 없어도 예외를 던지지 않고 정상 처리(재로그인은 Google이 최초 1회만 refresh_token을 내려주므로 이게 정상 케이스)
  - revoke: `POST https://oauth2.googleapis.com/revoke?token=...` — client_id/secret 불필요(Google revoke 엔드포인트는 토큰만 요구, Apple과 다름)
- [x] 신규 `auth/service/GoogleLoginCredentialService` — `AppleCredentialService`와 동일 구조
  - `saveIfAuthorizationCodePresent(User user, String authorizationCode)`: 교환 시도 → refresh_token이 있으면 credential upsert(없으면 skip, 기존 값 유지) → 실패해도 로그인 흐름은 계속 진행(best-effort, try/catch)
  - `revokeAndDeleteIfPresent(UUID userId)`: credential 있으면 복호화한 refresh token으로 revoke 호출(best-effort) 후 **항상** row 삭제
- [x] `AuthService.login()` — GOOGLE 분기 추가: authorizationCode 없으면 400 즉시 거부 → 검증 통과 후 `googleLoginCredentialService.saveIfAuthorizationCodePresent(user, authorizationCode)` 호출(APPLE 분기와 나란히)
- [x] `UserWithdrawalService.withdraw()` — `revokeGoogleCalendarIfConnected(userId)`와 나란히 `googleLoginCredentialService.revokeAndDeleteIfPresent(userId)` 호출 추가
- [x] 신규 Repository `GoogleLoginCredentialRepository`(`findByUser_Id`, `deleteByUser_Id`) — 신규 네이티브 쿼리 없이 derived method만
- [x] `docs/architecture/erd.md`에 `google_login_credential` 테이블 반영
- [x] `docs/specs/auth-social-login.md`, `docs/specs/user-account-withdrawal.md` Google 절 amend
- [ ] 커밋 시 `Breaking-Change-Reason` 트레일러 — `LoginRequest.authorizationCode` 의미 확장(조건부 필수화 대상에 GOOGLE 추가) + 신규 `ErrorCode`

### Nice to Have

- (해당 없음)

### Out of Scope (이번 스펙에서 하지 않음)

- **기존(스펙 적용 전) 가입 유저의 소급 커버** — dev 환경, 상용 데이터 없음이라 불필요(사용자 확정)
- ~~iOS/Android 네이티브 Google Sign-In 대응(client_id 이원화·PKCE)~~ — **정정(2026-07-31): Out of Scope 아님, 이미 해당 없는 걱정이었음.** FE 확인 결과 `@react-native-google-signin/google-signin`이 이미 전 provider 네이티브 SDK로 구현·배포돼 있고(`nativeBridge.ts` 환경 분기), 이 라이브러리의 `serverAuthCode`(offlineAccess 옵션)는 iOS/Android 구분 없이 **항상 webClientId로 교환**되도록 설계돼 있어 Apple 같은 client_id 이원화·PKCE 이슈 자체가 발생하지 않는다. 이 스펙의 백엔드 구현은 수정 없이 그대로 유효 — 상세는 "클라이언트(FE) 변경 요건" 절
- **`prompt=consent` 강제** — 사용자 결정에 따라 이번 스펙에서 채택하지 않음
- **로그인·캘린더 Client ID 분리** — [`google-calendar-client-id-separation.md`](google-calendar-client-id-separation.md)로 분리(deferred). Calendar Wave 4 착수 시 진행
- 소셜 계정 다중 연결·개별 해제 → `#6`

## 설계 노트 (구현 전 참고)

- **`GoogleCalendarOAuthClient` 재사용 안 함**: Calendar용 클라이언트는 `user/googlecalendar/client/` 패키지 소속이라, `auth` 도메인이 이걸 호출하면 `auth → user.googlecalendar` 역방향 의존이 생김(레이어 원칙 위반). Apple도 자체 `auth/oauth/AppleOAuthClient`를 뒀던 것과 동일하게, 로그인용 Google 교환/revoke는 `auth/oauth/GoogleOAuthClient`로 새로 둔다. `TOKEN_URL`/`REVOKE_URL` 상수·교환 로직이 Calendar 클라이언트와 일부 겹치지만, 도메인 경계를 지키는 쪽을 우선한다. 암호화 유틸(`GoogleCalendarTokenCrypto`)만 기존처럼 재사용(Apple도 동일 패턴).
- **client_id 컬럼이 필요 없는 이유(2026-07-31 FE 확인으로 확정)**: Apple은 Bundle ID/Services ID 두 client_id가 **같은 교환·revoke 호출에 실제로 다르게 쓰여야 해서** 어느 걸 썼는지 저장이 필수였다. Google은 (a) 네이티브 앱(`@react-native-google-signin/google-signin`)이 iOS든 Android든 `serverAuthCode`를 **항상 webClientId로만** 교환하도록 설계돼 있고(라이브러리 자체 계약), (b) 브라우저 경로도 동일한 Web Client ID를 쓰며, (c) revoke 엔드포인트 자체가 client_id를 요구하지 않는다(토큰만 필요). 즉 Apple과 달리 "어느 client_id를 썼는지"가 애초에 갈리지 않아 저장할 이유가 없다 — 추측이 아니라 확인된 사실.
- **refresh_token 부재를 에러로 취급하지 않음**: 기존 `GoogleCalendarOAuthClient.parseTokenResponse(response, requireRefresh=true)`는 Calendar 연동 실패로 간주해 예외를 던지지만, 로그인 컨텍스트에서는 재로그인마다 refresh_token이 없는 게 정상이다(Google이 최초 1회만 내려줌). `GoogleOAuthClient`의 교환 메서드는 refresh_token 유무와 무관하게 정상 응답으로 처리하고, 값이 있을 때만 credential을 upsert한다.

## API / 인터페이스

기존 `POST /api/v1/auth/login` 엔드포인트의 **계약 확장**(신규 API 없음).

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| `POST` | `/api/v1/auth/login` | 없음 | `provider=GOOGLE`일 때 `authorizationCode` 필수로 전환 |

### 요청 예시 (GOOGLE)

```json
{
  "provider": "GOOGLE",
  "token": "eyJhbG... (id_token)",
  "authorizationCode": "4/0Ab... (Google authorization code)"
}
```

### 실패 (400, authorizationCode 누락)

```json
{
  "code": "AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED",
  "message": "Google 로그인에는 authorizationCode가 필요합니다."
}
```

## 클라이언트(FE) 변경 요건 — 필독

이 스펙은 백엔드만으로 끝나지 않는다. **FE 실제 구조 확인(2026-07-31) 결과, 로그인 경로가 두 개다** — `apps/app/apis/nativeBridge.ts`가 `isReactNativeWebView()`로 분기해 네이티브 앱과 브라우저를 서로 다른 방식으로 처리한다. `authorizationCode`를 채우는 방법이 두 경로에서 **서로 다르다**.

### 경로 ① 네이티브 앱(WebView 안, 실제 앱스토어 배포 버전) — `requestNativeSocialLogin`

- FE는 이미 `@react-native-google-signin/google-signin`으로 네이티브 Google 로그인을 구현해뒀다(`GOOGLE_WEB_CLIENT_ID`/`GOOGLE_IOS_CLIENT_ID` 상수 존재).
- **필요한 변경은 단 하나**: 기존 `GoogleSignin.configure({ webClientId: GOOGLE_WEB_CLIENT_ID, ... })` 호출에 **`offlineAccess: true`** 를 추가한다.
- 이렇게 하면 로그인 결과에 기존 `idToken`과 함께 **`serverAuthCode`** 가 새로 포함된다 — 이 값을 그대로 `authorizationCode`로 실어 보내면 된다.
- **client_id 걱정 없음**: `serverAuthCode`는 iOS/Android 구분 없이 항상 `webClientId`(=`GOOGLE_WEB_CLIENT_ID`)로 교환되도록 라이브러리가 설계돼 있다 — Apple의 Bundle ID/Services ID 이원화 같은 문제가 애초에 생기지 않는다.
- **선행 조건**: FE의 `GOOGLE_WEB_CLIENT_ID` 값이 백엔드 env `GOOGLE_CLIENT_ID`(=`tripfit.oauth.google-client-id`)와 **정확히 동일**해야 교환이 성공한다 — 배포 전 상호 확인 필요.

### 경로 ② 일반 브라우저(웹, `tripfit.online` 직접 접속) — `redirectToGoogleAuthorize`

- FE의 `utils/googleAuth.ts`는 `https://accounts.google.com/o/oauth2/v2/auth`에 `response_type=id_token`(implicit flow)으로 리다이렉트해 id_token만 받는다.
- **변경 필요**: `response_type=code id_token`(hybrid flow) + `access_type=offline` 추가
  - `nonce`는 FE가 이미 생성하고 있어(`createOAuthNonce`) hybrid flow의 OIDC 요구사항(nonce 필수)을 그대로 충족
  - `scope`는 그대로(`openid email profile`) — 캘린더 권한을 추가로 요청하는 게 **아님**
  - 응답 fragment에 `id_token`과 `code`가 함께 실려 옴 — 기존처럼 `id_token`은 `token`으로, 새로 받은 `code`는 `authorizationCode`로 로그인 요청에 실어 보내면 됨
  - `prompt=consent`는 추가하지 않음(위 사용자 결정)

### 공통

- `prompt=consent`는 두 경로 모두 추가하지 않음(위 사용자 결정 — 정상 재로그인 UX 유지)
- **배포 순서**: Apple 때와 동일한 리스크 — 백엔드가 먼저 강제(400)를 배포하면 FE가 아직 `authorizationCode`를 안 보내는 순간(두 경로 중 하나라도 미전환) **그 경로의 Google 로그인 전체가 즉시 실패**한다. FE가 두 경로 모두 `authorizationCode`를 보내기 시작한 뒤에 백엔드를 배포해야 함
- Kakao(`@react-native-seoul/kakao-login`)는 Admin Key 기반 unlink라 이 필드와 무관, Apple(`expo-apple-authentication`)은 네이티브 Sign In 결과가 이미 `authorizationCode`를 포함해 기존 구현 그대로 — 두 provider 모두 이번 변경의 영향을 받지 않음

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` — 신규 테이블 1개 추가 예정

```
google_login_credential (신규)
- id                       UUID v4, PK
- user_id                  UUID, FK → users.id, UNIQUE (user당 1행)
- refresh_token_ciphertext TEXT, AES-256-GCM (GoogleCalendarTokenCrypto 재사용)
- created_at / updated_at  BaseTimeEntity
```

- `google_calendar_credential`(기존, Calendar 전용)과는 **별개 테이블** — 목적·라이프사이클이 다름(로그인 credential은 계정 활성 기간 내내 유지, 탈퇴 시에만 삭제 / 캘린더 credential은 연동 해제 시점에도 삭제)
- hard delete 대상: 탈퇴 시 `revokeAndDeleteIfPresent()`가 항상 삭제(기존 `apple_credential`·`google_calendar_credential`과 동일 패턴)
- **⚠️ 두 credential이 같은 Google Client ID를 공유함**: 캘린더 전용 Client ID가 별도로 없어(FE 미구현, Wave 4), 로그인·캘린더 둘 다 현재 유일한 Web Client ID로 인증한다. Google의 "연결된 앱" 동의는 scope가 아니라 **client_id 단위**로 묶이므로, 실제로는 두 refresh token이 Google 쪽에서 하나의 통합된 grant일 가능성이 높다 — 아래 리스크 참고

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| (BR 없음, `#64` 후속) | 탈퇴 시 Google 로그인 동의 자체를 revoke | `UserWithdrawalService` + `GoogleLoginCredentialService` + `GoogleOAuthClient` |

## 검증 시나리오

### 정상

- [x] GOOGLE 로그인 시 `authorizationCode` 포함 → 로그인 성공 + `GoogleLoginCredential` 저장(refresh_token 있는 경우) — mock `verify()` (`AuthServiceTest#login_whenGoogleWithAuthorizationCode_savesCredential`)
- [x] `GoogleLoginCredential`이 있는 유저가 탈퇴 → `GoogleOAuthClient.revokeRefreshToken()` 호출 후 credential hard delete — mock `verify()` (`GoogleLoginCredentialServiceTest`, `UserWithdrawalServiceTest#withdraw_callsGoogleLoginCredentialRevokeAndDelete`)
- [ ] 캘린더 연동 상태에서 탈퇴 → 캘린더 revoke + 로그인 revoke **둘 다** 호출됨 (`#64` 시나리오 3) — 각각 별도 테스트로 검증됨(캘린더: 기존 테스트, 로그인: 신규 테스트), 두 revoke가 **같은** withdraw() 호출 안에서 함께 도는 통합 테스트는 아직 없음

### 엣지 · 실패

- [x] GOOGLE 로그인인데 `authorizationCode` 누락·공백 → 소셜 토큰 검증 전 즉시 400(`AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED`) (`AuthServiceTest#login_whenGoogleWithoutAuthorizationCode_throwsAuthorizationCodeRequired`·`#login_whenGoogleWithBlankAuthorizationCode_throwsAuthorizationCodeRequired`)
- [x] 코드 교환 응답에 `refresh_token`이 없음(재로그인 등 정상 케이스) → 예외 없이 로그인 성공, credential은 기존 값 유지(신규 생성 안 함) (`GoogleLoginCredentialServiceTest#saveIfAuthorizationCodePresent_whenRefreshTokenAbsent_skipsSaveAndKeepsExisting`)
- [x] 코드 교환 자체가 실패(네트워크·invalid code) → 로그인은 그대로 성공, credential 저장만 스킵(best-effort) (`GoogleLoginCredentialServiceTest#saveIfAuthorizationCodePresent_whenExchangeFails_doesNotThrowAndSkipsSave`)
- [x] revoke 호출이 Google 쪽 오류(4xx·네트워크 실패)로 예외를 던져도 탈퇴 자체는 성공 — 기존 `catch(Exception ignored)` 패턴과 동일하게 로그만 남김 (`GoogleOAuthClientTest#revokeRefreshToken_providerFailure_doesNotThrow`, `GoogleLoginCredentialServiceTest#revokeAndDeleteIfPresent_whenDecryptThrows_doesNotThrowAndStillDeletes`)
- [x] Kakao/Apple 로그인은 영향 없음(이 분기 자체를 안 탐) (`AuthServiceTest#login_whenNotApple_neverCallsAppleCredentialService` 등 기존 테스트로 회귀 확인)

### 수동 / 통합 (해당 시)

- [ ] 실제 테스트 Google 계정으로 가입 → 탈퇴 → `myaccount.google.com` 연결된 앱 목록에서 TripFit 삭제 확인 → 같은 계정으로 재가입 → 동의 화면 재표시 확인 (FE hybrid flow 전환 후)

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공(Spotless 포함)
- [x] `docs/specs/auth-social-login.md`, `docs/specs/user-account-withdrawal.md` amend
- [x] `docs/architecture/erd.md`에 `google_login_credential` 반영
- [x] OpenAPI(`LoginRequest`·`AuthErrorCode`·`AuthController` `@ApiResponses`) 반영
- [ ] 커밋에 `Breaking-Change-Reason` 트레일러
- [ ] `#64` 이슈에 진행 상황 반영
- [ ] (코드 밖) FE가 두 경로 모두 `authorizationCode`를 보내기 시작한 뒤 배포 — 네이티브 앱은 `offlineAccess: true` 추가, 브라우저는 hybrid flow 전환. 순서 조율 필수
- [ ] (코드 밖) `GOOGLE_WEB_CLIENT_ID`(FE) = `GOOGLE_CLIENT_ID`(백엔드 env) 값 일치 확인
- [ ] (코드 밖) 실제 Google 테스트 계정으로 네이티브 앱·브라우저 두 경로 각각 수동 검증

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 기존 가입 유저 소급 커버 | 확정(Out of Scope) | dev 환경·상용 데이터 없음(사용자 결정) |
| iOS/Android 네이티브 Google Sign-In 시 client_id 이원화 | **해소(2026-07-31, FE 확인)** | `@react-native-google-signin/google-signin`의 `serverAuthCode`는 플랫폼 무관하게 항상 webClientId로 교환됨 — Apple과 달리 애초에 발생하지 않는 리스크였음. [`google-login-native-sdk-decision.md`](google-login-native-sdk-decision.md)(#77)도 Resolved로 정정 |
| `prompt=consent` 미강제로 인해 revoke가 실패(네트워크 등)했던 유저의 재가입 시 refresh_token 재획득 실패 가능성 | 확정(수용) | best-effort 정책과 일관 — Google 쪽에서 동의가 실제로 안 지워졌으면 다음 로그인도 동의 화면 없이 code만 오고 refresh_token은 없을 수 있음 |
| FE 배포 순서 조율 | `[진행 필요]` | Apple 때와 동일한 리스크 — FE가 hybrid flow 전환 완료 후 백엔드 강제(400) 배포 |
| **로그인·캘린더가 같은 Client ID 공유** | `[미정]` — [`google-calendar-client-id-separation.md`](google-calendar-client-id-separation.md)로 분리 | (1) 기술적 리스크: Google이 client_id 단위로 동의를 묶는다면 `GoogleCalendarService.disconnect()`(탈퇴 아닌 단순 캘린더 해제)의 revoke가 같은 Google 계정으로 로그인도 한 유저의 로그인 grant까지 지울 수 있음. (2) 개념적 근거: 캘린더 연동은 KAKAO/APPLE/GOOGLE 로그인 유저 **전부**가 쓸 수 있는, 로그인 provider와 무관한 기능이라("인증"과 "외부 연동"은 별개 관심사) 애초에 같은 Client ID를 공유할 이유가 약함. Calendar Wave 4 착수 시 진행 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-31 | 초안 (Draft) — `#64` 재발견 gap 대응 |
| 2026-07-31 | 구현 완료(Implemented) — Apple 패턴 재사용, `./gradlew build` 통과 |
| 2026-07-31 | **정정** — FE 확인 결과 네이티브 SDK(`@react-native-google-signin/google-signin` 등)가 이미 구현돼 있었음. client_id 이원화 리스크 해소, FE 변경 요건을 네이티브(`offlineAccess`+`serverAuthCode`)/브라우저(hybrid flow) 두 경로로 재작성. `google-login-native-sdk-decision.md`(#77) Resolved 처리 |
