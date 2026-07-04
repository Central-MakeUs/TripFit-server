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

## 클라이언트 배포 형태 — 하이브리드 앱 (WebView)

프론트는 React로 웹 화면을 만든 뒤, **Play·App Store 앱 껍데기 안에 WebView로 같은 화면을 띄우는** 방식을 목표로 한다.

```
[스마트폰 앱 껍데기]
  └── WebView → React 화면 (tripfit.online 또는 번들된 웹 에셋)
        └── HTTPS REST → api.tripfit.online (이 repo)
```

| 항목 | 내용 |
|------|------|
| 장점 | 기존 React UI 재사용, 개발 기간 단축 |
| 주의 | 로그인 등 OS·스토어 정책에 민감한 기능은 **웹과 앱에서 동작이 다를 수 있음** |
| 백엔드 전제 | 브라우저 리다이렉트 OAuth가 아니라 **앱 SDK 토큰 → REST 검증** (`docs/decisions/001-auth-mobile-token-verification.md`) |

상세 인증 API·검증 방식: [`docs/specs/auth-social-login-mvp.md`](../specs/auth-social-login-mvp.md)

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

- 백엔드: `AppleTokenVerifier` + `POST /api/v1/auth/login` (`provider: APPLE`) — [`auth-social-login-mvp.md`](../specs/auth-social-login-mvp.md)
- 가이드: [App Store Review Guidelines — Login Services](https://developer.apple.com/kr/app-store/review/guidelines/#login-services)

### Google 로그인 — WebView 차단

구글은 보안상 **앱 내 WebView에서 구글 로그인 페이지를 여는 것을 차단**한다 (`403 disallowed_useragent`).

- **프론트**: 구글 로그인은 WebView 버튼이 아니라 **네이티브 SDK·시스템 계정 선택**으로 `id_token`을 받아야 함
- **백엔드**: 받은 `id_token`만 검증하면 됨 (WebView 이슈는 프론트 영역)

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
| **인증** | 소셜 로그인·토큰은 **모바일 OAuth/딥링크** 가능하게 스펙에 명시. 세부는 S 유형 스펙 + 필요 시 `docs/decisions/` |
| **초대 링크** | 카카오 공유·딥링크 — Universal Link / App Link fallback은 스펙·프론트와 합의. 임의 URL 스킴 구현 금지 |
| **CORS** | Vercel(`https://tripfit.online`) 등 웹 origin — API는 `api.tripfit.online`. 네이티브 앱은 CORS 없음 |
| **에러 응답** | [`api-response.md`](../architecture/api-response.md) **초안** — Body: `data`, `message`, `code` (HTTP status는 헤더만) |
| **푸시 알림** | MVP P1(리마인드). FCM/APNs는 **별도 스펙** 없으면 토큰 테이블·발송 로직 추가 금지 |
| **결제·수익화** | MVP Out unless `mvp.md`·스펙에 명시. Agent가 임의 결제 API 추가 금지 |

### 프론트와의 계약

- **API 응답 envelope (초안)**: [`docs/architecture/api-response.md`](../architecture/api-response.md) — **프론트에 아직 규약 없음**. 백엔드 제안안으로 합의 후 확정
- API 요청/응답·`data` shape·`code`는 **`docs/specs/`** + 프론트 2명과 맞출 것
- 화면·한글 라벨은 `docs/product/design/`, `glossary.md` — 백엔드 enum 이름과 혼동 금지
- OpenAPI(springdoc)는 **첫 API 공개·envelope 확정 후** 동기화

### 프론트 합의 제안 — API 응답 (논의용 5줄)

아래는 **백엔드 제안 초안**. 프론트 피드백 반영 후 `api-response.md` 상태를 `확정`으로 바꾼다.

1. Body 후보: `data`, `message`, `code` — **Body에 `status`/`success` 없음** (성공 여부는 `response.ok`).
2. 성공 시 **`body.data`** 사용. 단순 조회는 `{ "data": ... }` 만으로도 OK (`message` 선택).
3. 실패 시 **`body.message`** 표시, **`body.code`** 로 분기 (`TRIP_NOT_FOUND`, `TOKEN_EXPIRED` 등).
4. 400 검증: `INVALID_INPUT` + **`errors: [{ field, message }]`**.
5. 목록(제안): `data.items` + `data.pageInfo`. Base URL: `https://api.tripfit.online`, `/api/v1/...`.

## 단계별 우선순위 (Agent)

1. **지금 (MVP)**: 도메인·REST·[`auth-social-login-mvp.md`](../specs/auth-social-login-mvp.md) 기준 소셜 로그인, 초대/참여 플로우 API
2. **스토어 직전**: Apple S2S Notification webhook, 딥링크, 앱 버전 호환, 환경별 API base URL 문서화
3. **런칭 후**: 푸시, 분석, 결제 등 — 각각 스펙 + decisions

## 관련 문서

| 문서 | 용도 |
|------|------|
| `mvp.md` | 기능 In/Out |
| `prd.md` | 앱 설치·온보딩 등 사용자 시나리오 |
| `design/figma-wireframe-v1.md` | 화면·상태 |
| `architecture.md` | 서버 레이어·DDD |
| `architecture/api-response.md` | REST JSON envelope **초안** (프론트 합의용) |
| `specs/auth-social-login-mvp.md` | 소셜 로그인·JWT (단일 `POST /auth/login`) |
| `specs/auth-apple-server-notifications.md` | Apple 계정 변경 S2S webhook (스토어 제출 전) |
| `decisions/001-auth-mobile-token-verification.md` | 모바일 토큰 검증 + JWT (안 B) |
| `.cursor/rules/client-platform.mdc` | API·인증 작업 시 자동 규칙 |

## 미정 항목

- 앱 패키징 기술 스택 (RN / Capacitor / 기타) — WebView 브릿지 방식에 따라 SDK 연동 상세가 달라짐
- 스토어 계정·심사 일정
- 웹-only MVP 여부 vs 앱-first

확정되면 이 파일 또는 `docs/decisions/`를 업데이트합니다.
