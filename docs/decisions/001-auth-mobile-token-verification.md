# 모바일 소셜 로그인 — 토큰 검증 + JWT (안 B)

- **상태:** 확정
- **날짜:** 2026-06-30
- **관련:** [`docs/specs/auth-social-login-mvp.md`](../specs/auth-social-login-mvp.md)

## 맥락

TripFit은 React 프론트 2명이 개발하고 최종적으로 Play·App Store **하이브리드 앱(WebView)** 으로 배포한다 (`docs/product/platform.md`). 소셜 로그인(Google / Kakao / Apple)만 지원하며 자체 회원가입은 없다.

프론트는 React UI를 WebView에 띄우되, **구글·애플 로그인은 네이티브 SDK**로 토큰을 받아 백엔드에 전달한다. 백엔드는 provider별 URL이 아닌 **`POST /api/v1/auth/login` 단일 엔드포인트**로 `{ provider, token }`을 받는다.

인증 구현 방식 후보:

1. **직접 구현** — provider별 code/token/userinfo 전부 수동
2. **Spring Security OAuth2 Client** — 서버 리다이렉트 플로우
3. **모바일 토큰 검증 API** — 앱 SDK 토큰을 백엔드가 검증 후 JWT 발급
4. **Identity 분리 + 계정 연결** — `user` / `user_identity` 구조

MVP에서는 핵심 여행방·일정 플로우 검증이 우선이고, 계정 연결(BR-USER-003)은 P2다.

## 결정

**안 B: 모바일 토큰 검증 API + Access JWT + Refresh Token** 을 채택한다.

- 앱 SDK가 획득한 `id_token` / `access_token`을 `POST /api/v1/auth/login`으로 전달
- provider별 `SocialTokenVerifier`로 검증 후 `OAuthProfile` 표준화
- 기존 `user(provider, social_id)` upsert → Access JWT(2h) + Refresh Token(30d, DB) 발급
- Spring Security는 **JWT 필터·인가**에만 사용. OAuth2 Client 리다이렉트 플로우는 사용하지 않음
- **구현 중 인증 목적의 DB 변경 허용** — `refresh_token` 신규, `user` 컬럼 추가 등. 스펙 `DB 변경 정책` 참고. 변경 후 `erd.md` 동기화 검토

## 고려한 대안

| 대안 | 장점 | 단점 |
|------|------|------|
| A — Access JWT만 (refresh 없음) | 구현 최소 | 앱 UX 불량, 만료 시 재로그인 강제 |
| Spring Security OAuth2 Client | Spring 표준, 코드 적음 | 브라우저 리다이렉트 전제 — 네이티브 앱에 부적합 |
| C — Identity 분리 + 계정 연결 | BR-USER-003 즉시 대응, 캘린더 token 저장 용이 | ERD 1단계에서 `user` 구조 변경 리스크, MVP 공수 과다 |
| 직접 구현 (code → token → userinfo) | 세밀한 제어 | provider 3종 × 유지보수 부담 |

## 트레이드오프 · 후속 리스크

- **계정 연결 불가**: 한 provider = 한 user. Kakao·Google 통합은 P2 `user_identity` 스펙 필요
- **Google WebView 차단**: 구글 로그인은 WebView가 아닌 네이티브 SDK — 프론트 책임 (`platform.md`)
- **Apple 로그인 UI**: 타사 소셜과 함께 제공 시 Apple 로그인도 동등하게 — 프론트·심사 (`platform.md`)
- **Apple `aud`·Google client ID**: iOS/Android 분기 — 프론트와 env 합의 `[미정]`
- **Apple S2S Notification**: MVP login과 별도 — 스토어 제출 전 [`auth-apple-server-notifications.md`](../specs/auth-apple-server-notifications.md)
- **캘린더 연동**: 로그인 OAuth와 scope·token 저장이 다름 — MVP+1 별도 스펙
- **DB 변경 자유도**: 인증 범위 내 허용이나, `user` rename·Identity 분리는 본 결정 범위 밖

## 후속 작업

- [ ] `docs/specs/auth-social-login-mvp.md` 기준 구현 (`POST /api/v1/auth/login` 단일 엔드포인트)
- [ ] `deploy/app/.env.example` JWT·provider client ID placeholder
- [ ] 프론트와 login API 계약 공유 (token 종류·단일 login URL·WebView/SDK 분기)
- [ ] 스토어 제출 전: `docs/specs/auth-apple-server-notifications.md` 구현
- [ ] P2: 계정 연결 시 `002-...` decisions + identity 스펙 검토
