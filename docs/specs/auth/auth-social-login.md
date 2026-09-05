# 소셜 로그인 · JWT 인증

> implements: BR-USER-001
> deferred: BR-USER-003
> 상태: Approved
> 승인: 2026-06-30 (팀 — DB 변경 허용 조건 포함)
> 범위: login / refresh / logout baseline (RTR·Redis **미포함**)
> 결정: [안 B] [`docs/decisions/001-auth-mobile-token-verification.md`](../../decisions/001-auth-mobile-token-verification.md)
> 후속 (출시 이후): RTR + Redis — [`docs/decisions/004-auth-token-rotation.md`](../../decisions/004-auth-token-rotation.md), [`auth-token-rotation.md`](auth-token-rotation.md)
> 프로필 이미지: MVP 출시 **A안** URL passthrough — [`docs/decisions/006-profile-image-url-storage.md`](../../decisions/006-profile-image-url-storage.md) · 출시 이후 B안(S3) — [`user-profile-image-s3-mirror.md`](../user/user-profile-image-s3-mirror.md)
> 온보딩·이름: [`docs/decisions/007-user-profile-onboarding.md`](../../decisions/007-user-profile-onboarding.md) · [`user-onboarding.md`](../user/user-onboarding.md)

## 목표

React 앱(최종 Play·App Store)에서 Google / Kakao / Apple 로그인 후 TripFit 서비스 유저로 등록·식별하고, Access JWT로 이후 API를 호출할 수 있게 한다.

## 배경

- **클라이언트**: React 프론트 2명, 최종 네이티브 앱 배포 (`docs/product/platform.md`)
- **자체 회원가입 없음** — 소셜 로그인만 허용 (Figma 와이어프레임 v1)
- **현재 서버**: `User(provider, social_id)` 엔티티만 존재, Security·인증 API 없음
- **배포 형태**: 하이브리드 앱(WebView) — React 화면을 앱 껍데기에 띄움 (`docs/product/platform.md`)
- **인증 플로우**: 앱 SDK가 id_token / access_token을 획득 → **백엔드가 토큰을 검증**하는 REST API (서버 리다이렉트 OAuth2 플로우 아님)
- **API 형태**: provider별 엔드포인트 분리 **하지 않음** — `POST /api/v1/auth/login` 단일 엔드포인트 + `provider` enum
- **확장 예정**: Google 캘린더 연동 — OAuth·토큰 저장은 **별도 스펙** (온보딩 UI·boolean만 [`user-onboarding.md`](../user/user-onboarding.md))
- **계정 연결**(BR-USER-003, Kakao+Google 통합): 출시 이후 — **Out of Scope**
- **Apple S2S Notification**: MVP 로그인과 별도 — 스토어 제출 전 [`auth-apple-server-notifications.md`](auth-apple-server-notifications.md)

### 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/product/platform.md` | 하이브리드 앱·스토어 심사·프론트 계약 요약 |
| `docs/product/mvp.md` | 인증 In scope, 계정 연동 출시 이후 |
| `docs/product/business-rules/user.md` | BR-USER-001, BR-USER-003 |
| `docs/architecture/erd.md` | `user` 테이블 정의 |
| `docs/product/design/figma-wireframe-v1.md` | Google / Kakao / Apple 로그인 화면 |
| `docs/specs/auth/auth-apple-server-notifications.md` | Apple 계정 변경 webhook (스토어 제출 전) |
| `docs/decisions/004-auth-token-rotation.md` | **확정** — RTR + Redis (출시 이후) |
| `docs/decisions/005-auth-social-verifier-strategy.md` | **확정** — `SocialTokenVerifier` Strategy + Registry 설계 |
| `docs/decisions/006-profile-image-url-storage.md` | **확정** — 프로필 이미지 A안(MVP 출시 URL passthrough) · B안(출시 이후 S3) |
| `docs/specs/auth/auth-token-rotation.md` | 출시 이후 구현 스펙 (Draft) |
| `docs/specs/user/user-profile-image-s3-mirror.md` | 출시 이후 프로필 이미지 S3 미러링 (Draft) |
| `docs/decisions/007-user-profile-onboarding.md` | **확정** — 이름(성/이름)·boolean 온보딩 상태 |
| `docs/specs/user/user-onboarding.md` | MVP 출시 프로필·온보딩 PATCH API |

## MVP 출시 vs 출시 이후

> **2026-09-15 갱신:** 아래 "출시 이후" 열은 이미 **구현 완료**됐고, 그 과정에서 설계가 한 번 뒤집혔다 — Redis는 access JWT 블랙리스트가 아니라 **refresh token 저장소**가 됐고(HttpOnly 쿠키 전달), access는 블랙리스트 없이 TTL 15분으로만 만료된다. 현행 SSOT: [`auth-refresh-redis-cookie.md`](auth-refresh-redis-cookie.md). 이 표는 당시 계획 비교 기록으로 남긴다.

| | MVP 출시 (본 스펙) | 출시 이후 (확정, 별도 스펙) |
|--|-------------------|---------------------------|
| **Refresh rotation (RTR)** | 미적용 — refresh row 유지 | refresh마다 token 교체 + reuse detection |
| **Redis** | 미사용 | ~~access JWT용~~ → **refresh token 저장소로 구현됨** (블랙리스트 폐기) |
| **프로필 이미지** | **A안** — provider URL → `profile_image_url` 그대로 저장 | **B안** — S3 미러링 후 TripFit URL ([`006`](../../decisions/006-profile-image-url-storage.md)) |
| **refresh 응답** | `accessToken`만 | + `refreshToken` (새 opaque token) |
| **준비 (MVP 출시 코드)** | `jti`, `family_id`, `TokenRevocationChecker` NoOp | 출시 이후에 Redis·RTR 구현체 교체 |

## 하이브리드 앱 (WebView) 맥락

프론트는 React 웹 UI를 Play·App Store 앱의 **WebView**에 그대로 넣는 방식을 목표로 한다.

```
[앱 껍데기 + WebView]
  React 로그인 화면 (UI)
        ↓ (구글·애플은 네이티브 SDK 브릿지 — 아래 주의사항)
  SDK / 브릿지가 소셜 토큰 획득
        ↓
POST /api/v1/auth/login  { provider, token }
        ↓
TripFit accessToken + refreshToken
```

백엔드는 **웹이냐 앱이냐와 무관하게** 동일한 login API를 제공한다. 차이는 **프론트가 토큰을 어떻게 받느냐**(WebView vs 네이티브 SDK)에 있다.

## 스토어 심사 · 클라이언트 주의사항

백엔드 구현과 별도로, 프론트·심사에서 자주 막히는 패턴이다. 구현·QA 시 함께 확인한다.

### Apple 로그인

| 상황 | 심사 |
|------|------|
| 자체 ID/PW만 | Apple 로그인 없어도 무방 |
| 카카오·구글 등 타사 소셜 포함 | **Apple 로그인도 UI·기능 모두 포함** 권장 (버튼 크기·비중 동등). 미포함 시 거절 위험 |

- 백엔드: `provider: APPLE` + `AppleTokenVerifier` (본 스펙 Must Have)
- 가이드: [Login Services](https://developer.apple.com/kr/app-store/review/guidelines/#login-services)

### Google — WebView 로그인 차단

구글은 앱 내 WebView에서 구글 로그인 UI를 띄우는 것을 **원천 차단**한다 (`403 disallowed_useragent`).

| 잘못된 방식 | 올바른 방식 |
|------------|------------|
| WebView 안 React 구글 버튼 → 구글 로그인 페이지 | 네이티브 Google Sign-In SDK → `id_token` → `POST /auth/login` |

백엔드는 `id_token` 검증만 담당. WebView 우회는 **프론트·앱 패키징** 책임.

### 카카오

WebView에서도 비교적 안정적. 프론트가 `access_token`을 받아 login API로 전달.

### Apple Server-to-Server Notification

Sign in with Apple 지원 한국 앱은 **계정 변경 이벤트 webhook**이 스토어 심사에 영향을 줄 수 있다. 본 스펙(MVP 로그인) 범위 밖 — [`auth-apple-server-notifications.md`](auth-apple-server-notifications.md) 참고.

## 프론트·백엔드 역할 분담

### 합의 체크리스트 (구현 전)

- [ ] 로그인 API는 **`POST /api/v1/auth/login` 단일 엔드포인트** (`provider` + `token`)
- [ ] 카카오: 프론트 → `access_token` / 백엔드 → 카카오 profile API 검증
- [ ] 구글: 프론트 → `id_token` (네이티브 SDK) / 백엔드 → JWT 서명·`aud` 검증
- [ ] 애플: 프론트 → `id_token` (identity token, 네이티브 SDK) / 백엔드 → Apple JWK 검증
- [ ] 로그인 성공 응답: `accessToken`, `refreshToken`, `user` — 이후 API는 `Authorization: Bearer`
- [ ] 구글·애플은 **WebView 내 소셜 로그인 버튼만으로 처리하지 않음**
- [ ] 앱 UI에 카카오·구글 소셜이 있으면 **Apple 로그인 버튼도 동등하게** 배치 (프론트)
- [ ] 스토어 제출 전: Apple S2S webhook 스펙 검토

### 토큰 종류 정리

| 토큰 | 발급 주체 | 용도 |
|------|-----------|------|
| 카카오 `access_token` | 카카오 | login 요청 1회 — 백엔드가 카카오 API로 유저 확인 |
| 구글 `id_token` | 구글 | login 요청 1회 — 백엔드가 JWT 검증 후 `sub` 추출 |
| 애플 `id_token` | 애플 | login 요청 1회 — 백엔드가 JWT 검증 후 `sub` 추출 |
| TripFit `accessToken` | **TripFit Spring** | 로그인 이후 API 인증 (2h) |
| TripFit `refreshToken` | **TripFit Spring** | access 만료 시 재발급 (30d, DB 저장) |

## API 설계 원칙 — 단일 login 엔드포인트

provider마다 URL을 나누지 않는다 (`/auth/kakao`, `/auth/google` 등 **사용 안 함**).

| 엔드포인트 | 용도 |
|-----------|------|
| `POST /api/v1/auth/login` | 모든 소셜 로그인 — body `{ provider, token }` |
| `POST /api/v1/auth/refresh` | access JWT 재발급 |
| `POST /api/v1/auth/logout` | refresh token 무효화 |

`AuthController`는 `provider`에 따라 `SocialTokenVerifier` 구현체를 선택한다 (Strategy — [`005-auth-social-verifier-strategy.md`](../../decisions/005-auth-social-verifier-strategy.md)).

## 아키텍처 개요

```
[React 앱]
  Google / Kakao / Apple SDK 로그인
        ↓ idToken 또는 accessToken
POST /api/v1/auth/login  { provider, token }
        ↓
┌─────────────────────────────────────┐
│ SocialTokenVerifier (provider별)     │
│  - Google: id_token 서명·aud 검증    │
│  - Kakao: access_token → profile API │
│  - Apple: id_token JWK 서명 검증     │
└─────────────────────────────────────┘
        ↓ OAuthProfile (표준화)
User upsert (provider + social_id)
        ↓
Access JWT (2h) + Refresh Token (30d, DB) 발급
        ↓
이후 API: Authorization: Bearer {accessJwt}
```

### Spring Security 역할

| 구성요소 | 역할 |
|----------|------|
| `SecurityFilterChain` | `POST /api/v1/auth/{login,refresh,logout,apple/notifications}` + `/error`·`/actuator/**`·swagger·scalar permitAll, 나머지 authenticated |
| `JwtAuthenticationFilter` | Access JWT 파싱·검증 → SecurityContext 설정. `shouldNotFilter`로 위 permitAll POST 4종은 파싱 자체를 건너뜀(`JwtAuthenticationFilter.PUBLIC_AUTH_POST_PATHS`가 SecurityConfig와 공유하는 SSOT) — 아니면 클라이언트가 관성적으로 붙여 보내는 만료된 액세스 토큰 때문에 login/refresh/logout 자체가 컨트롤러 도달 전에 401로 막힘(2026-08-16 발견·수정, 아래 변경 이력) |
| `SocialTokenVerifier` | provider별 토큰 검증 (Strategy 패턴) |
| `@AuthorizedUser` | HandlerMethodArgumentResolver — 컨트롤러에 `UUID userId` 주입 (AOP 확장 기반) |

**채택하지 않음**: Spring Security OAuth2 Client 리다이렉트 플로우 (`/oauth2/authorization/{provider}`) — 모바일 앱에 부적합.

## 요구사항

### Must Have

- [ ] `POST /api/v1/auth/login` — provider + token → access JWT + refresh token
- [ ] `POST /api/v1/auth/refresh` — refresh token → 새 access JWT
- [ ] `POST /api/v1/auth/logout` — refresh token 무효화
- [ ] Google / Kakao / Apple 3종 provider 지원 (`SocialProvider` enum 기존 값 사용)
- [ ] 신규 로그인 시 `user` 레코드 생성 (`first_name`/`last_name` null, onboarding boolean default `false`) — [`user-onboarding.md`](../user/user-onboarding.md)
- [ ] 기존 `(provider, social_id)` 조합이면 동일 user 반환 (재로그인 = upsert)
- [ ] Access JWT: HS256 또는 RS256, `sub` = `user.id`, **`jti` = UUID (출시 이후 Redis 대비)**, 만료 2시간
- [ ] Refresh token: DB 저장, **`family_id` 포함 (출시 이후 RTR 대비)**, 만료 30일, 로그아웃 시 삭제
- [ ] `TokenRevocationChecker` interface + NoOp 구현 (출시 이후 Redis 교체용)
- [ ] `JwtAuthenticationFilter` + 인증 필요 API 보호
- [ ] `@AuthorizedUser` ArgumentResolver로 컨트롤러에 로그인 유저 ID 주입
- [ ] 일관된 에러 응답 body (`code` + `message`) — 앱 파싱 가능
- [ ] `./gradlew test` 통과 (단위 + `@WebMvcTest` 또는 통합 테스트)
- [ ] OpenAPI(springdoc) 반영

### Nice to Have

- [ ] 동시 기기 refresh token 상한 (예: user당 5개) — 출시 이후 [`auth-token-rotation.md`](auth-token-rotation.md) 검토
- [ ] `GET /api/v1/auth/me` — 현재 유저 프로필 조회

### Out of Scope (MVP 출시 — 출시 이후·별도 스펙)

- **Refresh Token Rotation (RTR)** — 출시 이후 확정 [`004`](../../decisions/004-auth-token-rotation.md), [`auth-token-rotation.md`](auth-token-rotation.md)
- **Redis** — 이후 **refresh token 저장소로 구현 완료**(2026-09-15). access 블랙리스트/화이트리스트는 도입하지 않기로 종결 — [`auth-refresh-redis-cookie.md`](auth-refresh-redis-cookie.md)
- 자체 이메일/비밀번호 회원가입
- 계정 연결 — BR-USER-003 (Kakao + Google → 하나의 user)
- `user_identity` 테이블 분리
- Google Calendar OAuth 연동 API — [`google-calendar-oauth.md`](../user/google-calendar-oauth.md) **Implemented** (#44). 로그인 OAuth와 별 scope·별 API
- Apple Sign In **Server-to-Server Notification** — 스토어 제출 전 별도 스펙 [`auth-apple-server-notifications.md`](auth-apple-server-notifications.md)
- FCM/APNs 디바이스 토큰
- 비회원(게스트) 세션 — **Out of Scope** (BR-USER-002 확정: 참여자 소셜 로그인 필수)

## API / 인터페이스

### 공통 에러 응답

```json
{
  "code": "AUTH_SOCIAL_TOKEN_INVALID",
  "message": "유효하지 않은 소셜 로그인 토큰입니다."
}
```

| HTTP | code | 상황 |
|------|------|------|
| 400 | `AUTH_INVALID_REQUEST` | provider 누락, 지원하지 않는 provider |
| 400 | `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED` | provider가 APPLE인데 `authorizationCode` 누락·공백 — 탈퇴 시 Apple revoke 호출에 필요(App Store 심사 요건) |
| 400 | `AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED` | provider가 GOOGLE인데 `authorizationCode` 누락·공백 — 탈퇴 시 Google 로그인 동의 revoke 호출에 필요 |
| 401 | `AUTH_INVALID_TOKEN` | 액세스 JWT(서버 발급) 무효 — 소셜 토큰 검증과 무관, `POST /auth/login` 자체는 발생시키지 않음 |
| 401 | `AUTH_SOCIAL_TOKEN_EXPIRED` | 소셜 provider가 토큰 만료로 응답 — `POST /auth/login` 전용, 재로그인으로 해결 |
| 401 | `AUTH_SOCIAL_TOKEN_INVALID` | 만료 외 소셜 토큰 무효(서명·audience·형식 오류 등) — `POST /auth/login` 전용, 기본값(catch-all) |
| 503 | `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` | 소셜 provider API 접근 실패(네트워크·타임아웃) — `POST /auth/login` 전용, 토큰 문제 아님, 재시도 유도 |
| 401 | `AUTH_EXPIRED` | access JWT 만료 |
| 401 | `AUTH_INVALID_REFRESH` | refresh token 없음·만료·폐기 |
| 403 | `AUTH_FORBIDDEN` | 인증됐으나 권한 없음 (향후 RBAC) |

세분화 배경·판별 로직: [`auth-error-code-granularity.md`](auth-error-code-granularity.md) (#57)

### `POST /api/v1/auth/login`

> **단일 엔드포인트** — Kakao / Google / Apple 모두 이 URL 사용. `provider` enum으로 분기.
> provider별 URL (`/auth/kakao` 등)은 **정의하지 않음**.

| 항목 | 값 |
|------|-----|
| Auth | 불필요 |
| Content-Type | `application/json` |

**Request**

```json
{
  "provider": "GOOGLE",
  "token": "<id_token 또는 access_token>"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| provider | enum | Y | `GOOGLE` \| `KAKAO` \| `APPLE` |
| token | string | Y | 앱 SDK에서 획득한 토큰. provider별 의미 아래 참고 |
| authorizationCode | string | provider가 APPLE·GOOGLE이면 Y, KAKAO는 안 씀 | 각 provider OAuth 플로우에서 받은 authorization code — 탈퇴 시 revoke에 쓸 refresh token 교환용(`AppleCredential`/`GoogleLoginCredential`) |
| redirectUri | string | GOOGLE 브라우저 리다이렉트 로그인이면 Y, 그 외(GOOGLE 네이티브 앱·KAKAO·APPLE)는 안 씀 | 브라우저 로그인 리다이렉트에 실제로 쓴 redirect_uri 원문 — Google authorization code 교환 시 code 발급 때 쓴 값과 정확히 일치해야 함(2026-08-01, `google-login-revoke.md` 정정 2) |

**provider별 token 의미**

| provider | token 종류 | 서버 검증 방식 |
|----------|-----------|----------------|
| GOOGLE | `id_token` (권장) | Google tokeninfo 또는 JWK 서명 검증. `aud` = 앱 client ID |
| KAKAO | `access_token` | `GET https://kapi.kakao.com/v2/user/me` 호출 후 `id` 추출 |
| APPLE | `id_token` | Apple JWK endpoint에서 공개키 fetch → `sub`, `aud` 검증 |

**authorizationCode (APPLE·GOOGLE 필수)**

두 provider 모두 로그인마다(최초·재로그인 모두) authorization code를 새로 받아 보내야 한다. 탈퇴 시 provider 쪽 연결 해제(revoke)에 쓸 refresh token을 매번 교환·갱신하기 위함 — 상세: [`user-account-withdrawal.md`](../user/user-account-withdrawal.md), [`google-login-revoke.md`](google-login-revoke.md). GOOGLE은 provider 특성상 재로그인 시 refresh_token이 응답에 없을 수 있어 credential이 갱신되지 않을 수 있다(정상 동작, 최초 동의 때만 발급).

**redirectUri (GOOGLE 브라우저 로그인 필수)**

GOOGLE 로그인이 브라우저 전체 페이지 리다이렉트 경로(네이티브 앱 `serverAuthCode` 아님)면, authorize 요청에 실제로 쓴 `redirect_uri`를 그대로 보내야 한다. Google 토큰 교환은 code 발급 때 쓴 redirect_uri와 정확히 같은 값을 요구하며, 다르거나 비어 있으면 교환 자체가 실패해 credential 저장만 조용히 스킵된다(로그인 자체는 계속 성공, best-effort — `authorizationCode` 누락과 달리 400은 아님). 상세: [`google-login-revoke.md`](google-login-revoke.md) 정정 2.

**Response `200`**

```json
{
  "data": {
    "accessToken": "<jwt>",
    "refreshToken": "<opaque-uuid-or-random>",
    "expiresIn": 7200,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "firstName": "길동",
      "lastName": "홍",
      "nickname": "홍길동",
      "profileImageUrl": "https://lh3.googleusercontent.com/...",
      "provider": "GOOGLE",
      "isGoogleCalendarConnected": false,
      "hasCompletedPreSchedule": false,
      "notificationEnabled": true
    }
  }
}
```

### `POST /api/v1/auth/refresh`

| 항목 | 값 |
|------|-----|
| Auth | 불필요 |

**Request**

```json
{
  "refreshToken": "<refresh_token>"
}
```

**Response `200` (MVP 출시)**

```json
{
  "data": {
    "accessToken": "<jwt>",
    "expiresIn": 7200
  }
}
```

> **출시 이후 (RTR):** 동일 endpoint에 `refreshToken` 필드 **추가** — [`auth-token-rotation.md`](auth-token-rotation.md). MVP 출시 클라이언트는 `accessToken`만 사용.

### `POST /api/v1/auth/logout`

| 항목 | 값 |
|------|-----|
| Auth | Bearer access JWT (권장) |

**Request**

```json
{
  "refreshToken": "<refresh_token>"
}
```

**Response `204`** — body 없음

### 인증 필요 API 공통

```
Authorization: Bearer <accessToken>
```

## 데이터 모델

### DB 변경 정책

구현 중 **인증 목적에 한해 DB 스키마 변경을 허용**한다. ERD 1단계(탐색)이며, 본 스펙 범위 안에서 합리적 변경은 승인 없이 진행 가능하다.

| 허용 | 조건 |
|------|------|
| `refresh_token` 테이블 신규 | 본 스펙 Must Have |
| `user` 컬럼 추가·수정 | 인증·프로필에 필요할 때 (예: `email` nullable, `last_login_at`) |
| 인덱스·제약 추가 | 조회·무결성에 필요할 때 |
| JPA 엔티티·`ddl-auto: update` 반영 | local/dev 프로필 |

| 금지 (별도 스펙·승인 필요) | 이유 |
|---------------------------|------|
| `user_identity` 분리·`user` 구조 대규모 개편 | BR-USER-003 계정 연결 — 출시 이후 |
| Flyway/`V*__*.sql` 마이그레이션 추가 | **작성 금지** — ddl-auto + DB 리셋 (`core-guardrails`) |

**후속**: 스키마를 바꿨으면 구현 PR 전에 `docs/architecture/erd.md` 동기화. DB는 폐기·재생성으로 맞춤.

### 기존 — 기본 유지 (`users`)

`users` 테이블 (`docs/architecture/erd.md` 기준 · Java `User`). **필요 시 위 정책에 따라 컬럼·제약 추가 가능.** 구 테이블명 `user` → **`users`** (예약어 회피, 2026-07-21).

| 컬럼 | 설명 |
|------|------|
| id | PK |
| provider | `GOOGLE` \| `KAKAO` \| `APPLE` |
| social_id | provider 고유 사용자 ID (`sub` 또는 Kakao `id`) |
| email | nullable — Apple relay 등 |
| first_name | nullable — 유저 입력 **이름** (필수, PATCH onboarding/name) |
| last_name | nullable — 유저 입력 **성** (필수, PATCH onboarding/name) |
| nickname | nullable — 소셜 provider 표시명 (prefill). **fallback 없음** — [`007`](../../decisions/007-user-profile-onboarding.md) |
| profile_image_url | nullable — **MVP 출시(A안):** Google/Kakao provider CDN URL. Apple null. **출시 이후(B안):** TripFit S3 — [`006`](../../decisions/006-profile-image-url-storage.md) |
| is_google_calendar_connected | boolean, default false — OAuth 연동 시만 true |
| is_schedule_registered | boolean, default false — `regular_schedule` ≥1행 시 true |
| is_optional_onboarding_completed | boolean, default false — 선택 온보딩 전체 완료 |
| created_at, updated_at, deleted_at | Soft delete |

**UNIQUE** `(provider, social_id)` — 한 provider당 하나의 user. 계정 연결 전까지 1 provider = 1 user.

### 신규 — `refresh_token`

| 컬럼 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| id | char(36) | N | PK | UUID v4 |
| user_id | char(36) | N | FK → `user(id)` |
| token | varchar(255) | N | opaque token (UUID v4 등). UNIQUE |
| family_id | char(36) | N | UUID — login 체인 (출시 이후 RTR). MVP 출시: login마다 신규 |
| revoked_at | datetime(6) | Y | 출시 이후 rotation용. MVP 출시 logout은 row **delete** |
| expires_at | datetime(6) | N | 만료 시각 |
| created_at | datetime(6) | N | 발급 시각 |

**인덱스**: `UNIQUE (token)`, `INDEX (user_id)`, `INDEX (family_id)`

**정책 (MVP 출시)**

- 로그아웃: 해당 refresh token row 삭제
- refresh: access JWT만 재발급, refresh row **유지** (rotation 없음)
- 만료: refresh 요청 시 401 + row 삭제
- user soft delete 시: 연관 refresh token 전부 삭제

**정책 (출시 이후 — RTR)** — [`auth-token-rotation.md`](auth-token-rotation.md)

### 내부 표준 타입 — `OAuthProfile` (코드 내, DB 아님)

| 필드 | 설명 |
|------|------|
| provider | `SocialProvider` |
| providerUserId | 식별 기준 (`social_id`에 매핑) |
| email | nullable — Apple relay email 등. **UNIQUE 키로 사용 금지** |
| nickname | nullable |
| profileImageUrl | nullable — verifier가 추출한 **provider 외부 URL** (A안 passthrough). Apple null |

### 프로필 이미지 저장 (A안 / B안)

[`docs/decisions/006-profile-image-url-storage.md`](../../decisions/006-profile-image-url-storage.md) SSOT.

| | MVP 출시 (A안, **확정**) | 출시 이후 (B안, 예정) |
|--|------------------------|---------------------|
| 저장 값 | provider URL 그대로 | TripFit S3( CDN ) URL |
| 구현 | `AuthService` upsert passthrough | `ProfileImageMirrorService` + S3 — [`user-profile-image-s3-mirror.md`](../user/user-profile-image-s3-mirror.md) |
| API 필드 | `profileImageUrl` 동일 | 동일 (값만 TripFit 도메인) |
| Apple | null (id_token에 없음) | 동일 |

**MVP 출시 upsert 규칙**

- 신규·재로그인: `OAuthProfile.profileImageUrl`이 non-blank면 `user.profile_image_url`에 **그대로** 저장
- 미러링·다운로드·S3 업로드 **하지 않음**
- provider가 URL을 바꾸면 재로그인 시 덮어씀

### 스키마 반영 (ddl-auto)

- Hibernate `ddl-auto: update`(local/dev/prod) / `create-drop`(test)로 엔티티 반영
- **SQL 마이그레이션 파일 없음·작성 금지.** 스키마 불일치 시 DB volume 삭제 후 재기동 (`core-guardrails` ⛔ DB 스키마)

## Provider별 구현 메모

### Google

- 앱에서 `id_token` 전달 권장
- 검증: `aud` = Google OAuth client ID (iOS / Android 각각 — env로 관리)
- `sub` → `social_id`
- `email` → id_token `email` (nullable)
- `name` → `nickname` (소셜 prefill, 표시 SSOT 아님)
- `picture` → `profileImageUrl` → `profile_image_url` (**A안:** URL passthrough)

### Kakao

- 앱에서 `access_token` 전달
- 서버: Kakao REST API `GET /v2/user/me` (Authorization: Bearer)
- 응답 `id` (UUID) → `social_id` (String 변환)
- nickname: `kakao_account.profile.nickname`
- profileImageUrl: `kakao_account.profile.profile_image_url` → DB **A안** passthrough

### Apple

- 앱에서 `id_token` (identity token) 전달
- JWK endpoint: `https://appleid.apple.com/auth/keys` — `kid` rotation 대응 (캐시 + miss 시 재fetch)
- **`sub` → `social_id` (email·nickname·실명 아님)** — TripFit 사용자 식별의 SSOT. Apple은 동일 Apple ID에 대해 항상 동일한 `sub`를 발급하므로, 표시 이름이 없어도 `(provider, social_id)`로 upsert·재로그인 매칭 가능
- `aud` = **확정(2026-07-31)**: Bundle ID(iOS 네이티브 앱, 예: `com.tripfit.app`) 또는 Services ID(모바일 브라우저) 중 하나 — 두 값 다 허용 목록에 두고 하나만 맞아도 통과(Google `id_token` `aud` 멀티 client ID 검증과 동일 패턴). 상세: [`apple-oauth-multi-audience.md`](apple-oauth-multi-audience.md)
- email: 최초 1회만 올 수 있음 → nullable 저장 (**UNIQUE 키로 사용 금지**)
- nickname·profileImageUrl: id_token에 **없음** → `nickname` null, profileImageUrl null. 이름은 유저 입력(`first_name`/`last_name`) — [`user-onboarding.md`](../user/user-onboarding.md)

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-USER-001 | 여행방 생성 등 — JWT + **이름(first/last) 입력 완료** | `JwtAuthenticationFilter` + trip API + [`user-onboarding.md`](../user/user-onboarding.md) |
| BR-USER-003 | 소셜 계정 연동·해제 | **Out of Scope** — 출시 이후에 `user_identity` 스펙으로 분리 |

## 패키지 구조

> SSOT: [`docs/architecture.md`](../../architecture.md), [`decisions/003-architecture-guide.md`](../../decisions/003-architecture-guide.md), [`decisions/005-auth-social-verifier-strategy.md`](../../decisions/005-auth-social-verifier-strategy.md)

```
com.tripfit.tripfit
├── common/
│   ├── api/                        # SuccessResponse, ErrorResponse, FieldError
│   ├── config/                     # JpaConfig, SchedulingConfig
│   ├── domain/                     # BaseTimeEntity, SoftDeleteEntity
│   ├── exception/                  # ErrorCode, GlobalExceptionHandler
│   ├── logging/                    # PiiMasker, SocialIntegrationLog, SocialLogContext, SocialIntegrationAction
│   └── security/                   # SocialTokenCrypto, SocialTokenCryptoProperties
├── auth/
│   ├── config/                     # JwtProperties, SecurityConfig, JwtAuthenticationFilter,
│   │                               # AuthorizedUser, AuthorizedUserArgumentResolver
│   ├── controller/                 # AuthController
│   ├── dto/                        # LoginRequest, LoginResponse, ...
│   ├── service/                    # AuthService, JwtService, RefreshTokenService
│   ├── domain/                     # RefreshToken
│   ├── repository/                 # RefreshTokenRepository
│   ├── client/                     # SocialTokenVerifier*, OAuthProfile, TokenRevocationChecker
│   └── exception/                  # AuthErrorCode
└── user/
    ├── controller/                 # UserController (profile·onboarding PATCH) — [`user-onboarding.md`](../user/user-onboarding.md)
    ├── domain/                     # User, SocialProvider
    └── repository/                 # UserRepository
```

## 환경 변수 (`.env` — 커밋 금지)

| 변수 | 용도 |
|------|------|
| `JWT_SECRET` | Access JWT 서명 키 (prod: 256bit+ random) |
| `JWT_ACCESS_EXPIRATION` | 초 단위 (기본 7200) |
| `JWT_REFRESH_EXPIRATION_DAYS` | 일 단위 (기본 30) |
| `GOOGLE_CLIENT_ID_IOS` | Google id_token `aud` 검증 |
| `GOOGLE_CLIENT_ID_ANDROID` | Google id_token `aud` 검증 |
| `KAKAO_REST_API_KEY` | (Kakao Admin Key는 서버 검증에 불필요 — access_token으로 profile API 호출) |
| `APPLE_BUNDLE_ID` | Apple id_token `aud` 검증 — iOS 네이티브 앱 경로 (App ID/Bundle ID) |
| `APPLE_SERVICE_ID` | Apple id_token `aud` 검증 — 모바일 브라우저 경로 (Services ID) |

`deploy/app/.env.example`에 placeholder 추가.

## 검증 시나리오

### 정상

- [ ] Google id_token으로 최초 로그인 → user 생성 + JWT 발급
- [ ] 동일 Google 계정 재로그인 → 기존 user, 새 refresh token
- [ ] Kakao access_token으로 로그인 → user 생성
- [ ] Apple id_token으로 로그인 → `sub` 기준 user 생성 (email 없어도 성공)
- [ ] access JWT로 인증 필요 API 호출 성공
- [ ] refresh token으로 access JWT 재발급
- [ ] logout 후 refresh token으로 재발급 시도 → 401

### 엣지 · 실패

- [ ] 만료된 access JWT → 401 `AUTH_EXPIRED`
- [ ] 위조 access JWT → 401
- [ ] 잘못된 provider enum → 400
- [ ] Google id_token `aud` 불일치 → 401
- [ ] Kakao access_token 만료 → 401
- [ ] Apple id_token 서명 불일치 → 401
- [ ] refresh token 만료 → 401 + DB row 삭제
- [ ] Authorization 헤더 없이 보호 API 호출 → 401

### 수동 / 통합

- [ ] Swagger UI에서 login → Bearer 설정 → 보호 API 호출
- [ ] `@WebMvcTest(AuthController)` — mock verifier
- [ ] `AppleTokenVerifier` — Apple 공개 JWK fixture로 단위 테스트

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] 3 provider login API 동작 (최소 Google은 통합 테스트, Kakao/Apple은 verifier 단위 테스트 + `[미정]` 실토큰은 스테이징 수동)
- [ ] refresh / logout 플로우 동작
- [ ] OpenAPI `/swagger-ui.html`에 auth 엔드포인트 노출
- [ ] `deploy/app/.env.example` 갱신
- [ ] 프론트 2명과 API 계약 공유 (요청/응답 JSON 확정)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 앱 패키징 (RN / Capacitor / Expo) | `[미정]` | SDK별 token 획득 방식은 프론트 결정 — `docs/decisions/` 확정 후 스펙 보완 |
| Google client ID (iOS vs Android) | `[미정]` | login 요청에 `platform` 필드 추가 여부 — 프론트 합의 |
| Apple `aud` 값 (bundle ID vs Services ID) | **확정 (2026-07-31)** | 둘 다(멀티 client ID 허용) — [`apple-oauth-multi-audience.md`](apple-oauth-multi-audience.md) |
| nickname 정책 | **확정 (amend)** | 소셜 prefill만, **fallback 폐기** — [`007`](../../decisions/007-user-profile-onboarding.md) |
| 온보딩 상태 | **확정** | boolean 3개 + first/last null — [`user-onboarding.md`](../user/user-onboarding.md) |
| profileImageUrl 저장 | **확정 (MVP 출시 A안)** | provider URL passthrough — [`006`](../../decisions/006-profile-image-url-storage.md) |
| profileImageUrl S3 미러 | **출시 이후 예정 (B안)** | [`user-profile-image-s3-mirror.md`](../user/user-profile-image-s3-mirror.md), Issue #9 |
| Refresh token rotation (RTR) | **출시 이후 확정** | [`004`](../../decisions/004-auth-token-rotation.md) — MVP 출시 Out |
| Redis | **Implemented (2026-09-15)** | refresh token 저장소 + 공휴일 캐시. access 블랙리스트는 **폐기** — [`auth-refresh-redis-cookie.md`](auth-refresh-redis-cookie.md) |
| JWT 서명 알고리즘 HS256 vs RS256 | `[미정]` | 단일 서버 MVP는 HS256으로 시작 가능 |
| Google Calendar OAuth API | **Implemented** | [#44](https://github.com/Central-MakeUs/TripFit-server/issues/44) [`google-calendar-oauth.md`](../user/google-calendar-oauth.md) |

## 구현 단계 제안 (이슈 분할 참고)

| 순서 | 작업 | 예상 size |
|------|------|-----------|
| 1 | Security 기반 + JWT 발급/검증 + `refresh_token` 엔티티 | S |
| 2 | Google + Kakao verifier + login API | S |
| 3 | Apple JWK verifier | S |
| 4 | `@AuthorizedUser` + 보호 API 샘플 (예: `GET /auth/me`) | M |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-06-30 | 초안 (안 B 채택) |
| 2026-06-30 | DB 변경 허용 정책 추가, Approved, decisions `001` 연결 |
| 2026-07-06 | 하이브리드 앱·스토어 심사 주의사항·단일 login 엔드포인트·프론트 합의 체크리스트 추가 |
| 2026-07-08 | 온보딩·이름(성/이름)·boolean 3개 — [`007`](../../decisions/007-user-profile-onboarding.md), [`user-onboarding.md`](../user/user-onboarding.md); nickname fallback 폐기 |
| 2026-08-16 | **버그 수정** — `JwtAuthenticationFilter`가 permitAll 엔드포인트(login/refresh/logout/apple-notifications)에도 `Authorization` 헤더를 먼저 검사해, 클라이언트가 만료된 액세스 토큰을 관성적으로 실어 보내면 컨트롤러 도달 전에 401 `AUTH_EXPIRED`로 차단되던 결함 발견·수정. 특히 `POST /refresh`가 재발급이 필요한 바로 그 시점(액세스 토큰 만료 직후)에 100% 실패해 로그인이 몇 시간 뒤 풀리는 것처럼 보이는 원인이었음(운영 로그 확인 — 48시간 동안 refresh 호출 2건 전부 401). `shouldNotFilter` 오버라이드로 해당 4개 POST 경로는 필터 파싱 자체를 건너뛰도록 수정, API 계약 변경 없음(순수 버그 수정) |
