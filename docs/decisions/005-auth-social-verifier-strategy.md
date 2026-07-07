# 005 — 소셜 토큰 검증 Strategy + Registry

- **상태:** 확정
- **날짜:** 2026-07-07
- **관련:**
  - [`001-auth-mobile-token-verification.md`](001-auth-mobile-token-verification.md) — 모바일 토큰 검증 + JWT (안 B)
  - [`003-architecture-guide.md`](003-architecture-guide.md) — 도메인 기반 레이어드·레이어 규칙
  - [`docs/specs/auth-social-login.md`](../specs/auth-social-login.md) — wave 1 인증 API 스펙

## 맥락

TripFit 소셜 로그인은 **서버 리다이렉트 OAuth2가 아니라**, 클라이언트 SDK가 받은 토큰을 `POST /api/v1/auth/login` 한 곳으로 보내고 백엔드가 검증하는 방식이다 ([`001`](001-auth-mobile-token-verification.md)).

provider마다 검증 방식이 다르다.

| provider | 클라이언트 token | 서버 검증 |
|----------|------------------|-----------|
| GOOGLE | `id_token` (JWT) | Google JWK 서명 + `aud` |
| KAKAO | `access_token` | 카카오 `GET /v2/user/me` |
| APPLE | `id_token` (JWT) | Apple JWK 서명 + `aud` |

이 차이를 `AuthService`·`AuthController`에 `if (provider == …)` 로 풀면, login 유스케이스가 provider 추가·변경마다 커지고 테스트·리뷰 범위가 넓어진다. **검증 방식만 교체 가능한 구조**가 필요했다.

## 설계 의도

1. **단일 login API 유지** — URL은 `/api/v1/auth/login` 하나, body의 `provider` enum으로만 분기한다. 프론트·앱 계약을 단순하게 유지한다.
2. **provider별 검증 로직 격리** — Google/Kakao/Apple의 HTTP·JWT·설정 차이를 `auth/client/` 아래에만 둔다.
3. **login 유스케이스는 provider-agnostic** — `AuthService.login`은 “검증 → user upsert → TripFit JWT 발급”만 담당하고, 소셜 API 세부는 모른다.
4. **확장 용이 (Open/Closed)** — 새 provider는 구현체 `@Component` 추가 + `SocialProvider` enum 값 추가로 registry에 자동 등록된다. `AuthService`·Controller 수정을 최소화한다.
5. **경계 타입 표준화** — 각 verifier 출력을 `OAuthProfile` record로 통일해, 이후 user upsert·JWT 발급 코드가 provider에 의존하지 않게 한다.

## 결정

**Strategy 패턴 + Registry + Spring List injection** 을 채택한다.

```
AuthController.login(provider, token)
        ↓
AuthService.login()
        ↓
SocialTokenVerifierRegistry.getVerifier(provider)   ← Registry
        ↓
SocialTokenVerifier.verify(token) → OAuthProfile      ← Strategy
        ↓
User upsert → JwtService / RefreshTokenService
```

### 구성 요소

| 역할 | 타입 | 패키지 | 책임 |
|------|------|--------|------|
| Strategy (인터페이스) | `SocialTokenVerifier` | `auth/client/` | `verify(token)` → `OAuthProfile` |
| Concrete Strategy | `GoogleTokenVerifier`, `KakaoTokenVerifier`, `AppleTokenVerifier` | 동일 | provider별 토큰 검증 |
| Registry | `SocialTokenVerifierRegistry` | 동일 | `SocialProvider` → verifier 매핑, 미지원 provider 시 `AUTH_INVALID_REQUEST` |
| Context | `AuthService` | `auth/service/` | registry에서 strategy 선택 후 공통 login 플로우 실행 |
| Adapter DTO | `OAuthProfile` | `auth/client/` | verifier → service 경계. `provider`, `providerUserId`, `email`, `nickname`, `profileImageUrl` (nullable, **A안** provider URL passthrough — [`006`](006-profile-image-url-storage.md)) |

Registry는 생성 시 Spring이 주입한 `List<SocialTokenVerifier>`를 `EnumMap<SocialProvider, SocialTokenVerifier>`에 적재한다. **수동 bean 등록·switch 분기 없음.**

## 설계 원칙

### 1. Controller / Service / Verifier 책임 분리

[`003-architecture-guide.md`](003-architecture-guide.md) 레이어 규칙을 따른다.

| 레이어 | 소셜 로그인에서 할 일 | 하지 말 것 |
|--------|----------------------|------------|
| `AuthController` | `{ provider, token }` 수신, `AuthService` 위임 | provider별 검증, JWT 파싱 |
| `AuthService` | verifier 선택, user upsert, TripFit 토큰 발급 | Google JWK URL, Kakao REST path 하드코딩 |
| `*TokenVerifier` | 외부 토큰 1회 검증, `OAuthProfile` 반환 | DB 접근, refresh token 생성 |

### 2. 단일 엔드포int, 다형적 검증

- **API 표면:** provider별 URL (`/auth/kakao` 등) **사용 안 함**.
- **다형성:** Strategy로 provider별 구현만 갈라진다.
- **클라이언트 계약:** 항상 `{ "provider": "GOOGLE"|"KAKAO"|"APPLE", "token": "..." }`.

### 3. `OAuthProfile`은 DB Entity가 아니다

- verifier 출력용 **경계 record**이다. JPA Entity로 승격하지 않는다.
- `providerUserId` → `user.social_id` 매핑은 `AuthService`가 담당한다.
- `email`은 provider가 줄 때만 채워지며 nullable이다. **UNIQUE·로그인 식별 키로 쓰지 않는다** (식별은 `(provider, social_id)`).

### 4. 실패는 verifier에서 `TripFitException`으로 통일

- 검증 실패 → `AuthErrorCode.AUTH_INVALID_TOKEN` (또는 설정 누락 등 명시적 메시지).
- Registry에 없는 provider → `AUTH_INVALID_REQUEST`.
- Controller·Service는 provider별 HTTP status를 분기하지 않는다. `GlobalExceptionHandler`가 envelope로 변환한다.

### 5. 설정은 verifier 또는 `OAuthProperties`에만

- Google/Apple client ID (`aud` 검증): `tripfit.oauth.*` → `OAuthProperties`.
- Kakao: 서버 env **불필요** (클라이언트 `access_token`으로 profile API 호출).
- verifier 구현체는 `@Component` 단일 bean; factory 클래스 남발하지 않는다.

### 6. wave 4 확장과의 정렬

동일 auth 슬라이스에서 **인터페이스 + NoOp 구현** 패턴을 재사용한다.

| 관심사 | wave 1 | wave 4 (예정) |
|--------|--------|---------------|
| 소셜 검증 | `SocialTokenVerifier` | 동일 |
| access JWT 폐기 | `TokenRevocationChecker` NoOp | Redis 구현체로 교체 |
| refresh | DB row 유지 | RTR — [`004-auth-token-rotation.md`](004-auth-token-rotation.md) |

소셜 verifier Strategy와 JWT revocation Strategy는 **서로 다른 인터페이스**로 유지한다. 하나의 God interface로 합치지 않는다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택 |
|------|------|------|------|
| `AuthService` 내 `switch (provider)` | 파일 수 적음 | provider 추가마다 service·테스트 비대화 | ✗ |
| provider별 Controller (`/auth/google` …) | REST가 직관적일 수 있음 | 프론트 계약 3배, 공통 login 후처리 중복 | ✗ |
| Spring Security OAuth2 Client | Spring 표준 | 브라우저 리다이렉트 전제, 네이티브 앱 부적합 | ✗ ([`001`](001-auth-mobile-token-verification.md)) |
| Strategy + `@Bean` 수동 Map | 명시적 wiring | provider 추가 시 config 클래스 수정 | ✗ |
| **Strategy + Registry + List injection** | OCP, 테스트 용이, wiring 자동 | 클래스 파일 수 소폭 증가 | **✓** |

## 패키지 배치

[`003`](003-architecture-guide.md) 도메인 기반 레이어드 규칙:

```
auth/
├── controller/          AuthController
├── dto/                 LoginRequest, LoginResponse, …
├── service/
│   ├── AuthService.java           ← Context (유스케이스)
│   ├── JwtService.java
│   └── RefreshTokenService.java
├── client/
│   ├── SocialTokenVerifier.java
│   ├── SocialTokenVerifierRegistry.java
│   ├── OAuthProfile.java
│   ├── GoogleTokenVerifier.java
│   ├── KakaoTokenVerifier.java
│   ├── AppleTokenVerifier.java
│   ├── TokenRevocationChecker.java
│   └── NoOpTokenRevocationChecker.java
├── domain/              RefreshToken
├── repository/          RefreshTokenRepository
├── config/              JwtProperties, OAuthProperties, SecurityConfig
└── exception/           AuthErrorCode
```

`User` Entity는 **`user/domain/`** — auth 도메인이 user 영속화를 orchestration만 한다.

## provider 추가 절차 (체크리스트)

1. `SocialProvider` enum에 값 추가 (`user/domain/`).
2. `SocialTokenVerifier` 구현체 작성 + `@Component`.
3. `getProvider()`가 enum과 일치하는지 확인.
4. `verify()`는 반드시 `OAuthProfile` 반환; 식별자는 `providerUserId`에만.
5. 필요 시 `OAuthProperties`·env·`deploy/app/.env.example` 보완.
6. 구현체 단위 테스트 + `AuthServiceTest`(registry mock) 보강.
7. `docs/specs/auth-social-login.md` API·provider 표 동기화.

**수정하지 않아도 되는 것 (원칙):** `AuthController`, `AuthService.login` 본문, Registry 클래스.

## 테스트 전략

| 대상 | 방식 |
|------|------|
| `*TokenVerifier` | 단위 테스트 — invalid token, aud 불일치, 설정 누락 |
| `SocialTokenVerifierRegistry` | (선택) 미등록 provider → 예외 |
| `AuthService.login` | Mockito — registry·verifier mock, upsert·JWT 발급 orchestration |
| `AuthController` | `@WebMvcTest` / standalone MockMvc — HTTP·DTO·에러 envelope |

provider별 **실토큰 E2E**는 스테이징·수동; CI는 mock·fixture 위주.

## 트레이드오프 · 주의

- **Registry는 기동 시점에 고정** — enum에 없는 provider bean이 있거나, enum은 있는데 bean이 없으면 login 시 런타임 실패. enum·bean 쌍을 PR에서 함께 리뷰한다.
- **Kakao만 token 종류가 다름** (`access_token` vs `id_token`) — 클라이언트 계약 문서(`platform.md`, 스펙)와 반드시 맞출 것. verifier 내부에 숨기고 API는 통일 `{ provider, token }`.
- **Spring Security OAuth2 Client 미사용** — verifier가 직접 JWK·RestClient를 호출한다. 키 rotation(JWK refetch)은 각 구현체 책임.
- **계정 연결(BR-USER-003)** — Strategy는 wave 4 `user_identity` 스펙과 별개. 현재 upsert는 `(provider, social_id)` 1:1.

## 관련 코드 (SSOT)

| 파일 | 역할 |
|------|------|
| `auth/client/SocialTokenVerifier.java` | Strategy 인터페이스 |
| `auth/client/SocialTokenVerifierRegistry.java` | Registry |
| `auth/service/AuthService.java` | login 유스케이스 Context |
| `auth/controller/AuthController.java` | 단일 `POST /api/v1/auth/login` |

## 후속 작업

- [ ] provider 추가 시 본 문서 체크리스트 + 스펙 동기화
- [ ] wave 4: `TokenRevocationChecker` Redis 구현 — 본 Strategy 문서와 별도 ([`004`](004-auth-token-rotation.md))
- [ ] wave 4: 계정 연결 시 `user_identity` — verifier 출력·upsert 정책 재검토
- [ ] wave 4: 프로필 이미지 B안(S3 미러) — [`006`](006-profile-image-url-storage.md), [`user-profile-image-s3-mirror.md`](../specs/user-profile-image-s3-mirror.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-07 | 초안 — Strategy + Registry 설계 의도·원칙·확장 절차 |
