# 플랫폼 · 클라이언트 맥락

> **이 저장소는 백엔드 API 서버**입니다. UI·스토어 빌드는 **React 프론트 저장소**(별도)에서 다룹니다.  
> Cursor Agent는 API·인증·도메인 설계 시 아래 맥락을 전제로 동작합니다.

## 조직 · 제품 목표

| 항목 | 내용 |
|------|------|
| 팀 | 프론트 React **2명** + 백엔드(이 저장소) |
| 성격 | **수익형 서비스**를 목표로 하는 동아리 프로젝트 |
| 최종 배포 | **Google Play · Apple App Store** 앱 형태 |
| MVP 초점 | 스토어 심사·결제보다 **핵심 여행방·일정·추천 플로우 검증** |

## 시스템 구성

```
[tripfit.online]          [api.tripfit.online]
  Vercel (React/Next)  ──HTTPS REST──▶  EC2 A: Nginx + Spring Boot (이 repo)
       │                                        │
       └─ Play / App Store (최종)                └─ EC2 B: MySQL (deploy/)
```

- **프론트**: Vercel — `tripfit.online` (별도 저장소, EC2 frontend 컨테이너 없음)
- **API**: `https://api.tripfit.online` — 이 repo `deploy/app/`
- 결정 근거: [`docs/decisions/002-domain-split-vercel-api.md`](../decisions/002-domain-split-vercel-api.md)
- 앱 패키징 기술(RN/Capacitor 등): **`[미정]`** — `docs/decisions/`에 기록
- 이 repo의 `deploy/`는 **API 서버만**. 스토어 제출·앱 서명은 프론트 파이프라인

## 클라이언트 런타임 환경 (확정 — 2026-07-22)

**대전제:** TripFit은 **모바일 앱(환경 A)** 으로도, **카카오톡 인앱 브라우저·모바일 웹(환경 B)** 으로도 열린다.  
**어느 환경이든 소셜 로그인 필수** — 비회원·게스트 없음 ([BR-USER-002](business-rules/user.md), [`mvp.md`](mvp.md)).

유저가 TripFit UI에 들어오는 표면은 다음 **두 환경**이다. 패키징 기술(RN/Capacitor 등)은 별도 `[미정]`이지만, **런타임 축은 확정**.

| 코드 | 환경 | 설명 |
|------|------|------|
| **A** | **순수 모바일 앱** (iOS / Android) | Play·App Store 앱. React UI를 앱 껍데기(WebView 등)에 올리되, **Google 로그인·Calendar 연동은 네이티브 SDK** |
| **B** | **카카오톡 인앱 브라우저 · 모바일 웹** | 카카오 공유 링크 등으로 열린 인앱 WebView, 또는 `tripfit.online` 모바일 브라우저 |

### Google OAuth (로그인 · Calendar) — 공통 금지 / 권장

| | 규칙 |
|---|------|
| **금지** | 서버 **302 리다이렉트**로 Google 동의 화면을 여는 방식 (PC 웹용·구식). 인앱/WebView에서 `disallowed_useragent`로 실패하기 쉬움 |
| **환경 A 권장** | 네이티브 Google SDK → `id_token`(로그인) 또는 `authorizationCode`(Calendar) → `POST` REST |
| **환경 B 권장** | 인앱 안에서 Google UI를 열지 않음. **시스템 브라우저(Safari/Chrome)로 탈출**해 동의 후, 앱/웹이 code·토큰을 받아 동일 REST로 전달 |
| **백엔드** | 환경 A/B **동일 API**. code/token만 검증·교환. 서버가 Google로 302 하지 않음 |

상세: 로그인 [`auth-social-login.md`](../specs/auth-social-login.md) · Calendar [`google-calendar-oauth.md`](../specs/google-calendar-oauth.md) · [`decisions/001`](../decisions/001-auth-mobile-token-verification.md)

## 클라이언트 배포 형태 — 하이브리드 앱 (WebView)

프론트는 React로 웹 화면을 만든 뒤, **Play·App Store 앱 껍데기 안에 WebView로 같은 화면을 띄우는** 방식을 목표로 한다. (= 위 **환경 A**의 UI 층)

```
[스마트폰 앱 껍데기]          ← 환경 A
  └── WebView → React 화면 (tripfit.online 또는 번들된 웹 에셋)
        └── HTTPS REST → api.tripfit.online (이 repo)

[카카오 인앱 / 모바일 사파리·크롬]  ← 환경 B
  └── 동일 React (tripfit.online) → 동일 API
```

| 항목 | 내용 |
|------|------|
| 장점 | 기존 React UI 재사용, 개발 기간 단축 |
| 주의 | Google 등 OS·스토어 정책에 민감한 기능은 **환경 A/B에서 획득 경로만 다름** (API는 동일) |
| 백엔드 전제 | **302 OAuth 금지** · **클라이언트 SDK/시스템 브라우저 → REST** (`decisions/001`) |

상세 인증 API·검증 방식: [`docs/specs/auth-social-login.md`](../specs/auth-social-login.md)

## 소셜 로그인 — 프론트·백엔드 계약 (요약)

역할은 **프론트가 소셜 토큰을 받고, 백엔드가 검증 후 TripFit JWT를 발급**한다.

| 담당 | 할 일 |
|------|--------|
| **프론트** | Google / Kakao / Apple SDK(또는 동등 브릿지)로 로그인 → `id_token` 또는 `access_token` 획득 |
| **백엔드** | `POST /api/v1/auth/login` 한 엔드포인트로 `{ provider, token }` 수신 → 검증 → `accessToken` + `refreshToken` 발급 |

provider별 **별도 URL**(`/auth/kakao`, `/auth/google` 등)은 사용하지 않는다. `provider` enum으로 분기한다.

| provider | 프론트가 넘기는 `token` | 백엔드 검증 |
|----------|-------------------------|-------------|
| KAKAO | `access_token` | 카카오 API `GET /v2/user/me` |
| GOOGLE | `id_token` (JWT) | Google 공개키로 JWT 서명·`aud` 검증 |
| APPLE | `id_token` (identity token) | Apple JWK로 JWT 서명·`aud` 검증 |

구현 전 프론트와 맞출 문장 (스펙 SSOT):

> 소셜 로그인 버튼을 누르면 SDK에서 토큰을 받아 `POST /api/v1/auth/login`에 `{ provider, token }`으로 보내 주세요. 백엔드가 검증 후 TripFit JWT를 돌려줍니다.

## 앱 스토어 심사 — 백엔드·프론트가 알아둘 점

### Apple 로그인

| 상황 | 심사 |
|------|------|
| 자체 ID/PW만 사용 | Apple 로그인 없어도 무방 |
| 카카오·구글 등 **타사 소셜** 포함 | **Apple 로그인도 함께** 지원 권장 (UI에서 동등한 비중). 없으면 거절 위험 |
| 2024년 이후 | “타사 소셜이 있으면 Apple 로그인 **필수**” 문구는 가이드에서 완화됐으나, 실무에서는 여전히 함께 넣는 편이 안전 |

- 백엔드: `AppleTokenVerifier` + `POST /api/v1/auth/login` (`provider: APPLE`) — [`auth-social-login.md`](../specs/auth-social-login.md)
- 가이드: [App Store Review Guidelines — Login Services](https://developer.apple.com/kr/app-store/review/guidelines/#login-services)

### Google 로그인 · Calendar — WebView / 인앱 차단

구글은 보안상 **WebView·카카오 인앱 브라우저에서 구글 OAuth UI를 여는 것을 차단**하는 경우가 많다 (`403 disallowed_useragent`).

- **환경 A**: 네이티브 Google SDK · 시스템 계정 선택 (`id_token` / Calendar `authorizationCode`)
- **환경 B**: 인앱 내 Google UI 금지 → **시스템 브라우저 탈출** 후 동일 REST
- **백엔드**: 토큰·code 검증/교환만 (WebView·인앱 UX는 프론트 영역). **서버 302 리다이렉트 OAuth 구현 금지**
- Calendar 상세: [`google-calendar-oauth.md`](../specs/google-calendar-oauth.md)

### Apple Server-to-Server Notification (스토어 제출 전)

한국 앱에서 Sign in with Apple을 지원할 경우, **유저 애플 계정 변경 이벤트**를 애플 서버가 우리 백엔드로 push한다. 미구현 시 심사에 영향을 줄 수 있다.

- 목적: 이메일 전달 설정 변경, 앱 내 계정 삭제, 애플 계정 영구 삭제 등 → TripFit `user` 데이터 동기화
- 백엔드: 전용 webhook 엔드포인트 + JWT 검증 + 이벤트 처리
- Apple Developer Console에 엔드포인트 URL 등록
- **MVP 로그인 스펙과 별도** — [`docs/specs/auth-apple-server-notifications.md`](../specs/auth-apple-server-notifications.md)
- 참고: [Apple News — Sign in with Apple account change notifications](https://developer.apple.com/news/?id=j9zukcr6)

## Agent가 API 설계할 때 전제

### 모바일·앱 스토어를 고려한 백엔드

| 영역 | Agent 행동 |
|------|------------|
| **API 형태** | JSON REST, `/api/v1/...` prefix. 브라우저 전용 가정 금지 |
| **인증 · Google** | **환경 A/B** 전제. 서버 **302 OAuth 금지**. 클라이언트가 토큰/code → REST. 세부는 `docs/specs/` + `docs/decisions/001` |
| **초대 링크** | 카카오 공유·딥링크 — Universal Link / App Link fallback은 스펙·프론트와 합의. 임의 URL 스킴 구현 금지 |
| **CORS** | Vercel(`https://tripfit.online`) 등 웹 origin — API는 `api.tripfit.online`. 네이티브 앱은 CORS 없음 |
| **에러 응답** | [`api-response.md`](../architecture/api-response.md) **초안** — Body: `data`, `message`, `code` (HTTP status는 헤더만) |
| **푸시 알림** | wave 3(리마인드). FCM/APNs는 **별도 스펙** 없으면 토큰 테이블·발송 로직 추가 금지 |
| **결제·수익화** | MVP Out unless `mvp.md`·스펙에 명시. Agent가 임의 결제 API 추가 금지 |

### 프론트와의 계약

- **API 응답 envelope (초안)**: [`docs/architecture/api-response.md`](../architecture/api-response.md) — **프론트에 아직 규약 없음**. 백엔드 제안안으로 합의 후 확정
- API 요청/응답·`data` shape·`code`는 **`docs/specs/`** + 프론트 2명과 맞출 것
- 화면·한글 라벨은 `docs/product/design/`, `glossary.md` — 백엔드 enum 이름과 혼동 금지
- OpenAPI(springdoc)는 **첫 API 공개·envelope 확정 후** 동기화
- **여행방 멤버십:** `JOINED`≠일반 멤버 일정미완 — **방장 create 직후만**. 멤버 join=`RESPONDED` 즉시. 초대 공유=방장∧RESPONDED. create에 `inviteCode` 없음 — Swagger Info · [`glossary.md`](glossary.md) · [`trip-room-api.md`](../specs/trip-room-api.md) 필독 절

### 프론트 합의 제안 — API 응답 (논의용 5줄)

아래는 **백엔드 제안 초안**. 프론트 피드백 반영 후 `api-response.md` 상태를 `확정`으로 바꾼다.

1. Body 후보: `data`, `message`, `code` — **Body에 `status`/`success` 없음** (성공 여부는 `response.ok`).
2. 성공 시 **`body.data`** 사용. 단순 조회는 `{ "data": ... }` 만으로도 OK (`message` 선택).
3. 실패 시 **`body.message`** 표시, **`body.code`** 로 분기 (`TRIP_NOT_FOUND`, `TOKEN_EXPIRED` 등).
4. 400 검증: `INVALID_INPUT` + **`errors: [{ field, message }]`**.
5. 목록(제안): `data.items` + `data.pageInfo`. Base URL: `https://api.tripfit.online`, `/api/v1/...`.

## wave별 인증·플랫폼 (Agent)

계획 축: [`waves.md`](waves.md)

| wave | 작업 |
|------|------|
| **1** | [`auth-social-login.md`](../specs/auth-social-login.md) — login/refresh/logout |
| **4** | [`auth-token-rotation.md`](../specs/auth-token-rotation.md) — RTR + Redis |
| **4** | Apple S2S webhook, 딥링크, 앱 버전 호환 (스토어 직전) |
| **3~4** | 푸시, 분석, 결제 등 — 각각 스펙 + decisions |

## 관련 문서

| 문서 | 용도 |
|------|------|
| `waves.md` | **계획·우선순위 SSOT** (wave 1~4) |
| `mvp.md` | 기능 In/Out |
| `prd.md` | 앱 설치·온보딩 등 사용자 시나리오 |
| `design/figma-wireframe-v1.md` | 화면·상태 |
| `architecture.md` | 서버 도메인 기반 레이어드·Controller/Service/Repository |
| `architecture/api-response.md` | REST JSON envelope **초안** (프론트 합의용) |
| `specs/auth-social-login.md` | wave 1 소셜 로그인·JWT |
| `specs/google-calendar-oauth.md` | wave 4 Google Calendar · 환경 A/B · code→POST |
| `specs/auth-token-rotation.md` | wave 4 RTR + Redis (Draft) |
| `specs/auth-apple-server-notifications.md` | Apple 계정 변경 S2S webhook |
| `decisions/001-auth-mobile-token-verification.md` | 모바일 토큰 검증 + JWT (안 B) |
| `decisions/004-auth-token-rotation.md` | RTR + Redis 확정 |
| `.cursor/rules/client-platform.mdc` | API·인증 작업 시 자동 규칙 |

## 미정 항목

- 앱 패키징 기술 스택 (RN / Capacitor / 기타) — **환경 A** 브릿지 상세에 영향. 런타임 환경 A/B 자체는 **확정**
- 스토어 계정·심사 일정
- 환경 B에서 Google 동의 후 복귀 URL(커스텀 스킴 / Universal Link / 웹 콜백) 상세 — 프론트·Calendar 스펙과 합의

확정되면 이 파일 또는 `docs/decisions/`를 업데이트합니다.
