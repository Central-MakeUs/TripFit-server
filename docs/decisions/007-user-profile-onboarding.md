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
| `is_pre_schedule_registered` | `isPreScheduleRegistered` | `user_condition` **실제 저장** 시에만 `true`. 미등록·건너뛰기 = `false` |
| `is_optional_onboarding_completed` | `isOptionalOnboardingCompleted` | 선택 온보딩 **전체 완료** (마지막 단계까지 진행 후 PATCH). `true` 이후 앱 진입 시 온보딩 UI **재노출 안 함** |

**이름 완료 판별:** `first_name IS NOT NULL AND last_name IS NOT NULL` (별도 boolean 없음)

### 프론트 화면 분기 (SSOT)

```
login 성공 (JWT 보유)
  ↓
firstName 또는 lastName null?  → [이름 입력] (필수)
  ↓
!isOptionalOnboardingCompleted?  → [선택 온보딩] (캘린더 → 사전 일정, 각 건너뛰기 가능)
  ↓
메인
```

- 캘린더 단계: `isGoogleCalendarConnected`는 **연동 성공 시에만** `true`. 건너뛰기는 `false` 유지.
- 사전 일정 단계: `isPreScheduleRegistered`는 **저장 성공 시에만** `true`. 건너뛰기는 `false` 유지.
- 온보딩 **전체** 재노출 방지: 마지막 단계(등록 또는 건너뛰기) 후 `PATCH /users/me/onboarding`으로 `isOptionalOnboardingCompleted=true`

### 확정 UX — 건너뛰기 전부 후 재진입

캘린더·사전 일정을 **모두 건너뛴** 뒤에는 `isGoogleCalendarConnected`·`isPreScheduleRegistered`는 `false`로 남지만, 마지막에 `isOptionalOnboardingCompleted=true` PATCH가 되어 있어야 한다.

**이후 앱 재진입(재로그인 포함):** 캘린더 온보딩·일정 등록 온보딩을 **다시 띄우지 않음** → 이름 입력 완료 시 **바로 메인**.

```
캘린더 건너뛰기 → 사전 일정 건너뛰기
  → PATCH { isOptionalOnboardingCompleted: true }
  → (isGoogleCalendarConnected=false, isPreScheduleRegistered=false 유지)
재진입 → isOptionalOnboardingCompleted=true → 메인 only
```

마이페이지·여행방에서 캘린더 연동·조건 입력은 **나중에** 가능 (별도 API).

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
| `PATCH /users/me/profile` (first/last) | `user_condition` CRUD 전체 |
| `PATCH /users/me/onboarding` (boolean 갱신) | 네이버 캘린더 |

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
| 2026-07-08 | 초안 — boolean 3개 + 이름, 네이버 제외, JWT login 직후 |
