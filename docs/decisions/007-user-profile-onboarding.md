# 007 — 사용자 프로필·온보딩 상태 (boolean + 이름)

- **상태:** 확정
- **날짜:** 2026-07-08
- **관련:**
  - [`docs/specs/user-onboarding.md`](../specs/user-onboarding.md) — wave 1 API·UI SSOT
  - [`docs/specs/auth-social-login.md`](../specs/auth-social-login.md) — 소셜 login·JWT
  - [`docs/decisions/006-profile-image-url-storage.md`](006-profile-image-url-storage.md)

## 맥락

회원가입(소셜 login) 직후 JWT를 발급하고, 이후 **이름(필수)** · **Google 캘린더(선택)** · **사전 일정/근무·연차(선택)** 온보딩을 단계별로 진행한다.

- `onboarding_step` enum/integer **사용 안 함**
- 네이버 캘린더 **제외** (Google만)
- `isGoogleCalendarConnected`만 사용 — 별도 dismiss/skipped 플래그 **없음**. 미연동·건너뛰기 = `false`

## 결정

### 회원가입 vs 온보딩

| 구분 | 정의 | 완료 조건 |
|------|------|-----------|
| **회원가입** | TripFit 계정 생성 | 소셜 login upsert 성공 → **JWT 즉시 발급** |
| **이름 등록** | 필수 프로필 | `first_name` + `last_name` 저장 (건너뛰기 **없음**) |
| **선택 온보딩** | 캘린더·사전 일정 UI | `is_optional_onboarding_completed = true` (연동/등록/건너뛰기 후 PATCH) |

### `user` 필드 (이름 + boolean 3개)

| 컬럼 (DB) | API (camelCase) | 의미 |
|-----------|-----------------|------|
| `first_name` | `firstName` | 유저 입력 **이름** (필수, nullable until PATCH) |
| `last_name` | `lastName` | 유저 입력 **성** (필수, nullable until PATCH) |
| `nickname` | `nickname` | 소셜 provider 표시명 (prefill·참고용). **표시 SSOT 아님** |
| `is_google_calendar_connected` | `isGoogleCalendarConnected` | Google Calendar OAuth **실제 연동** 시에만 `true`. 미연동·건너뛰기 = `false` |
| `is_schedule_registered` | `isScheduleRegistered` | `user_condition` **실제 저장** 시에만 `true`. 미등록·건너뛰기 = `false` |
| `is_optional_onboarding_completed` | `isOptionalOnboardingCompleted` | 선택 온보딩 **기록용** (2026-07-20 amend: **재진입 라우팅 SSOT 아님**, D-REENTRY-2) |

**이름 완료 판별:** `first_name IS NOT NULL AND last_name IS NOT NULL` (별도 boolean 없음)

### 프론트 화면 분기 (SSOT)

> **Amend 2026-07-20 (D-REENTRY-2):** 재진입 라우팅 SSOT는 **이름 완료 여부**만. `isOptionalOnboardingCompleted`는 재진입에 **사용하지 않음**.

```
login 성공 (JWT 보유)
  ↓
firstName 또는 lastName null?  → [이름 입력] (필수, 건너뛰기·뒤로가기 없음 — D-NAME-1)
  ↓
[메인]  ← 이름 완료 시 재진입도 동일 (선택 온보딩 강제 재노출 없음)
```

**첫 세션 선택 온보딩 (soft prompt):**

```
이름 완료 후 (첫 세션만)
  ↓
[선택] Google 캘린더 (연동 또는 건너뛰기)
  ↓
[선택] 사전 일정 / 근무·연차 (등록 또는 건너뛰기)
  ↓
(중간 이탈 가능 — 재진입 시 메인, D-REENTRY-2)
```

- 캘린더 단계: `isGoogleCalendarConnected`는 **연동 성공 시에만** `true`. 건너뛰기는 `false` 유지.
- 사전 일정 단계: `isScheduleRegistered`는 **저장 성공 시에만** `true`. 건너뛰기는 `false` 유지.
- `isOptionalOnboardingCompleted`: **재진입 라우팅 SSOT 아님** (역할 축소). PATCH는 analytics·상태 기록용으로 유지 가능.

### D-NAME-1 — 이름 게이트 (2026-07-20, Kakao = Google = Apple)

| 항목 | 확정 |
|------|------|
| HTTP | login + 이름을 **한 트랜잭션으로 묶지 않음** — `POST /auth/login` → JWT → 이름 null이면 이름 화면 |
| Provider | **Kakao / Google / Apple 동일** — 필수 이름·게이트 동일 |
| 클라이언트 | Routing Guard (`replace` / stack reset), 건너뛰기·뒤로가기 없음, `BackHandler` 차단 |
| 서버 | `requireProfileNameComplete()` — trip 생성·join 등 핵심 API **403** `PROFILE_NAME_REQUIRED` |
| 서버 예외 | login, refresh, `GET /auth/me`, `PATCH /users/profile` — **차단 금지** |
| 클라이언트 | 전역 403 → `/onboarding/name` |

### 확정 UX — 재진입 (Amend 2026-07-20, D-REENTRY-2)

**구 정책 (폐기):** `isOptionalOnboardingCompleted=false`이면 재로그인 시 선택 온보딩을 **처음부터 재노출**.

**신 정책:** `first_name` + `last_name` 완료 시 **재진입(재로그인 포함) → 바로 메인**. 선택 온보딩은 **첫 세션 soft prompt**만; 중간 이탈해도 재진입 시 **트랩하지 않음**.

```
이름 PATCH 완료
  → (선택 온보딩 중 이탈 가능)
재진입 → firstName·lastName 모두 non-null → 메인 only
  (isOptionalOnboardingCompleted 값과 무관)
```

캘린더·사전 일정을 건너뛴 경우에도 동일 — `isGoogleCalendarConnected`·`isScheduleRegistered`가 `false`여도 재진입 시 메인.

마이페이지·여행방·trip join 게이트(D-JOIN-ENTRY)에서 캘린더 연동·일정 입력은 **별도**로 유도 가능.

### JWT

- **소셜 login 직후** access·refresh JWT 발급 (이름 입력 전에도 발급).
- 이름·온보딩 API는 `Authorization: Bearer` 필수.

### nickname 정책 (기존 fallback 폐기)

- provider `nickname`은 DB에 저장 가능 (prefill용).
- `"카카오 사용자"` 등 **fallback 문자열 사용 안 함**.
- `first_name`/`last_name` 확정 후 재로그인 시 **소셜 nickname으로 덮어쓰지 않음**.

### wave 1 범위

| 포함 | 제외 (별도 스펙·이후) |
|------|----------------------|
| login 응답 필드 확장 | Google Calendar OAuth 연동 API 본체 |
| `PATCH /users/profile` (first/last) | `user_condition` CRUD 전체 |
| `PATCH /users/onboarding` (boolean 갱신) | 네이버 캘린더 |

## 고려한 대안

| 대안 | 채택 |
|------|------|
| `onboarding_step` integer | ✗ — boolean + 이름 null로 분기 |
| `isRegistered` + `isOnboarded` 2플래그 | ✗ — 이름 null + optional completed로 대체 |
| `googleCalendarOnboardingDismissed` 별도 플래그 | ✗ — `isGoogleCalendarConnected`만 (미연동=건너뛰기) |
| **`isOptionalOnboardingCompleted` 3번째 boolean** | **✓** — 선택 온보딩 재노출 방지 |

## 후속 작업

- [ ] `docs/specs/user-onboarding.md` 구현
- [ ] `erd.md`·`auth-social-login.md` 동기화
- [ ] 코드: User 엔티티·AuthService fallback 제거·PATCH API

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-20 | **Amend D-REENTRY-2** — 재진입 SSOT = 이름 완료 → 메인; `isOptionalOnboardingCompleted` 역할 축소. **D-NAME-1** — Kakao=Google=Apple 이름 게이트·클라/서버 guard |
| 2026-07-08 | 초안 — boolean 3개 + 이름, 네이버 제외, JWT login 직후 |
| 2026-07-17 | BR-USER-006 게이트·personal/calendar → **#22 `[미정]`** · OpenAPI Hidden |

## Amend (2026-07-17) — #22

`isScheduleRegistered`·사전 일정 skip·trip submit(BR-USER-007)과의 정합이 깨져 **사전 일정 단계 정책을 `[미정]`으로 되돌림**.  
재설계 SSOT: [`schedule-participation-onboarding.md`](../specs/schedule-participation-onboarding.md) · GitHub **#22**.

## Amend (2026-07-20) — #22 부분 확정

| ID | 요약 |
|----|------|
| **D-NAME-1** | 소셜 login JWT 후 이름 필수 (Kakao=Google=Apple). 핵심 API 403 `PROFILE_NAME_REQUIRED`. login/refresh/me/profile PATCH 차단 금지. 클라 Routing Guard + 전역 403 → `/onboarding/name` |
| **D-REENTRY-2** | **이전:** `isOptionalOnboardingCompleted=false` → 재진입 시 선택 온보딩 재노출. **변경:** 이름 완료 → 재진입 시 **메인 직행**. 선택 온보딩 = 첫 세션 soft prompt만 |

trip join 게이트(D-JOIN-ENTRY · CLEAR · TRIP-FLOW)는 본 decisions가 아닌 [`schedule-participation-onboarding.md`](../specs/schedule-participation-onboarding.md) SSOT.
