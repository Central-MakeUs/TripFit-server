# 사용자 온보딩 · 프로필 (이름 + 선택 단계)

> wave: 1  
> implements: BR-USER-001 (이름 완료 후 핵심 API)  
> 결정: [`docs/decisions/007-user-profile-onboarding.md`](../../decisions/007-user-profile-onboarding.md)  
> 선행: [`auth-social-login.md`](../auth/auth-social-login.md)  
> deferred: trip join 일정 게이트(D-JOIN-TRIP-FLOW) · `hasCompletedPreSchedule` 필드 SSOT → [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md)  
> 상태: Approved (이름 API) · **재진입·이름 게이트 2026-07-20 amend** (#22 D-NAME-1, D-REENTRY-2) · **선택 온보딩 boolean(`isScheduleRegistered`/`isOptionalOnboardingCompleted`)과 `PATCH /users/onboarding`은 2026-07-20 삭제 — 대체: `hasCompletedPreSchedule` (`schedule-participation-onboarding.md` D-BR006-C) — 구 `isAllFree`는 2026-08-18 `#113`으로, 구 `hasPreSchedule`/`hasRegularSchedule`은 2026-08-19로 삭제**  

> ## ⚠️ 2026-08-19 amend — 회원가입에서 사전 일정 단계 제거
>
> **회원가입 온보딩은 이제 「이름 → Google 캘린더」까지다.** 사전 일정(정기·개별·연차) 입력은 회원가입에서 받지 않고, 실제로 필요한 시점 — **여행방 입장 · 여행방 내 수정 · 마이페이지** — 에서만 받는다. 모든 사전 일정 입력 플로우에 **건너뛰기 버튼이 없다**.
>
> **왜:** QA에서 실제 앱을 써보니 가입 단계에 일정 입력을 몰아넣는 것보다, 그 정보가 쓰이는 자리에서 받는 편이 사용성이 좋았다(2026-08-19 기획 결정).
>
> 아래 본문의 「② 사전 일정 단계」·건너뛰기 서술은 **폐지된 계약**이며 이력으로만 남긴다. 현행 플로우 SSOT: [`../user-schedule/pre-schedule-entry-flow.md`](../user-schedule/pre-schedule-entry-flow.md)

## 목표

소셜 login으로 **회원가입(JWT 발급)** 후, 필수 **성/이름** 입력과 선택 **Google 캘린더** 연동 온보딩을 프론트가 boolean·이름 null로 분기할 수 있게 한다. 사전 일정 입력은 회원가입 범위 밖이다(2026-08-19).

## 확정 정책 요약

| # | 정책 |
|---|------|
| 1 | 네이버 캘린더 **제외** — Google만 |
| 2 | 이름 = **성(`lastName`) + 이름(`firstName`)** 분리, **필수·건너뛰기 없음** |
| 3 | **회원가입 = 소셜 login upsert + JWT** (이름 전에도 토큰 발급) |
| 4 | `isGoogleCalendarConnected` — OAuth 연동 시만 `true`; 미연동·건너뛰기 = `false` |
| 5 | **`hasCompletedPreSchedule`** — login/me **조회 시 파생** (`users.vacation_apply_period IS NOT NULL`). 구 `hasPreSchedule`·`hasRegularSchedule`은 2026-08-19 삭제. D-BR006-C |
| 6 | ~~`isScheduleRegistered`~~ · ~~`isOptionalOnboardingCompleted`~~ · ~~`PATCH /users/onboarding`~~ — **2026-07-20 제거** (#22) |
| 7 | `onboarding_step` **미사용** |
| 8 | **D-NAME-1** — Kakao / Google / Apple 동일: login JWT 후 이름 필수, 핵심 API 403, login·refresh·me·온보딩 이름 PATCH 차단 금지 |

## UI 흐름

> **Amend 2026-07-20:** 재진입 SSOT = 이름 완료 → 메인 ([`007`](../../decisions/007-user-profile-onboarding.md) D-REENTRY-2)

```text
[소셜 로그인] — Kakao / Google / Apple 동일 (D-NAME-1)
       ↓
POST /api/v1/auth/login → JWT + user (firstName/lastName may null)
       ↓
firstName 또는 lastName null?
  YES → [성/이름 입력] → PATCH /users/onboarding/name
        (Routing Guard: replace/stack reset, 건너뛰기·뒤로가기 없음, BackHandler 차단)
  NO  ↓
[메인]  ← 재진입(재로그인)도 동일 — 선택 온보딩 강제 재노출 없음
```

**첫 세션 선택 온보딩 (soft prompt, 이탈 가능):**

```text
이름 완료 직후 (첫 세션만)
       ↓
[선택] Google 캘린더 (연동 또는 건너뛰기)
       ↓
메인 홈  ← 사전 일정은 여기서 받지 않는다 (2026-08-19)
(중간 이탈 → 재진입 시 메인, D-REENTRY-2)
```

**사전 일정 입력 진입 경로 (2026-08-19 확정):** 여행방 입장 · 여행방 내 수정 · 마이페이지. 어느 경로든 건너뛰기가 없고, 최초/갱신 판정은 `사전 신청일` 저장 여부 하나다 — [`../user-schedule/pre-schedule-entry-flow.md`](../user-schedule/pre-schedule-entry-flow.md)

<details>
<summary>구 「② 사전 일정 단계」 확정 (2026-08-16 — 2026-08-19 폐지, 이력)</summary>

**② 사전 일정 단계 확정 (2026-08-16 — 구 `[미정]` #22 해소, Figma Wireframe v1 대조):**

| 항목 | 확정 |
|------|------|
| 순서 | **① Google 캘린더 → ② 사전 일정** (캘린더가 먼저) |
| 사전 일정 분기 | 첫 화면이 **"정기 일정이 있나요?"** — 예/없어요 |
| **연차 3문항 위치** | **정기 일정과 한 덩어리** — "예"로 정기를 입력할 때만 노출. "없어요"면 **묻지 않음** (2026-08-19 폐기 — 지금은 두 갈래 모두 노출) |
| 건너뛰기 | **가능** (회원가입은 일정 입력을 강제하지 않음) — 방 입장 플로우와 다른 점 |
| 서버 호출 | 입력한 것만 호출. 온보딩은 어떤 입장 플래그도 설정하지 않는다 (~~`is_all_free`~~ 컬럼은 2026-08-18 `#113`으로 삭제) |

> "없어요" + 개별 일정 미입력으로 온보딩을 끝내면 일정 0행 상태로 남는다. 서버는 이 상태에 아무 플래그도 세우지 않는다 (2026-08-18 `#113` — `is_all_free`·BR-USER-011 폐지). 방 입장 판정은 그 방의 `trip_member.status = ACTIVE` 하나다.

</details>

연차 4개 필드의 저장 위치·전용 API는 [`vacation-policy-user-migration.md`](../user-schedule/vacation-policy-user-migration.md)(#52)가 SSOT. 방 입장 플로우는 [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md) D-JOIN-TRIP-FLOW가 SSOT.

**전역 403:** 핵심 API `PROFILE_NAME_REQUIRED` → 클라이언트 `/onboarding/name` 강제 이동

### 단계별 상세

| 단계 | UI | 서버 상태 변화 |
|------|-----|----------------|
| 소셜 login | SDK 로그인 | user row upsert, JWT 발급, boolean 기본값 `false` |
| 이름 | 성·이름 입력 (소셜 `nickname`은 인풋 prefill만) | `PATCH onboarding/name` → `first_name`, `last_name` |
| 캘린더 | 연동 또는 건너뛰기 | 연동 성공 시 `isGoogleCalendarConnected=true` (별도 스펙). **건너뛰기 = `false` 유지** |
| ~~사전 일정~~ | **2026-08-19 회원가입에서 제거** — 여행방 입장·여행방 내 수정·마이페이지에서만 입력받는다 | 상세 SSOT: [`../user-schedule/pre-schedule-entry-flow.md`](../user-schedule/pre-schedule-entry-flow.md) |
| 온보딩 종료 | (선택) 캘린더 단계 완료 | 별도 "완료" API·컬럼 없음 (`PATCH /users/onboarding` 2026-07-20 삭제). 사전 일정 입력 완료 여부는 `hasCompletedPreSchedule`(조회 시 파생)로 판단 |

> **재진입 (D-REENTRY-2):** `firstName` + `lastName` 완료 → **메인 직행**. 선택 온보딩 완료 여부와 무관하게 **재강제 없음**.

> **중간 이탈 (구 정책 폐기):** ~~`isOptionalOnboardingCompleted=false`이면 재로그인 시 선택 온보딩 처음부터~~ → **2026-07-20 amend:** 이름만 있으면 메인.

## 요구사항

### Must Have (wave 1 — 본 스펙)

- [x] `user` 컬럼: `first_name`, `last_name`, `is_google_calendar_connected`
- [x] `nickname` — 소셜 값만, **fallback 폐기** ([`007`](../../decisions/007-user-profile-onboarding.md))
- [x] login / `GET /auth/me` 응답 `user`에 위 필드 + `hasCompletedPreSchedule`(파생, SSOT: [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md)) 포함
- [x] `PATCH /api/v1/users/onboarding/name` — `{ firstName, lastName }` (JWT 필수)
- [x] `first_name`/`last_name` 없으면 여행방 생성·join 등 핵심 API **403** `PROFILE_NAME_REQUIRED` (D-NAME-1)
- [x] login, refresh, `GET /auth/me`, `PATCH /users/onboarding/name` — 이름 미완료여도 **허용** (D-NAME-1)
- [x] `./gradlew test` 통과

**삭제됨 (2026-07-20, #22):** `is_schedule_registered`/`is_optional_onboarding_completed` 컬럼, `PATCH /api/v1/users/onboarding` 엔드포인트. 코드에 없음(`UserController`에 `/onboarding` 매핑 없음) — 현행 대체값은 `hasCompletedPreSchedule`(파생). 당시 대체값이던 `hasPreSchedule`·`user.is_all_free`는 각각 2026-08-19·2026-08-18에 삭제됐다.

### Deferred (별도 스펙 — wave 1 본문 구현 안 함)

- [x] Google Calendar OAuth 연동 API·토큰 저장 → [#44](https://github.com/Central-MakeUs/TripFit-server/issues/44) [`google-calendar-oauth.md`](google-calendar-oauth.md) **Implemented**
- [x] 정기·개별 일정 — [`schedule-unified.md`](../user-schedule/schedule-unified.md) (wave 2, #11)
- [ ] 마이페이지 이름 수정 — [`user-my-page.md`](user-my-page.md) (`PATCH /users/profile`)
- [ ] 네이버 캘린더

## API

### `user` 요약 DTO (login · `GET /auth/me` 공통) — 실제 `UserSummaryResponse`

```json
{
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
```

| 필드 | nullable | 설명 |
|------|----------|------|
| firstName | Y | 미입력 시 null → 이름 화면 |
| lastName | Y | 미입력 시 null → 이름 화면 |
| nickname | Y | 소셜 provider 값. prefill용 |
| isGoogleCalendarConnected | N | default `false`. **연동 성공 시만** `true` |
| hasCompletedPreSchedule | N | DB 컬럼 없음, 조회 시 파생(`users.vacation_apply_period IS NOT NULL`). 최초 입력 vs 갱신 입력 분기용 — 일정 row 수는 보지 않는다. 상세: [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md) D-BR006-C |
| notificationEnabled | N | `user.notification_enabled` 저장값(default `true`, BR-USER-005). 온보딩 API는 변경하지 않음 — `PATCH /users/profile` 전용 |

### `PATCH /api/v1/users/onboarding/name`

| 항목 | 값 |
|------|-----|
| Auth | Bearer JWT **필수** |

**Request**

```json
{
  "firstName": "길동",
  "lastName": "홍"
}
```

| 필드 | 필수 | 설명 |
|------|------|------|
| firstName | Y | 이름 (공백 불가) |
| lastName | Y | 성 (공백 불가) |

**Response `200`** — 갱신된 `user` 요약 (위 DTO)

**에러**

| HTTP | code | 상황 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | blank 이름·성 |
| 401 | `AUTH_EXPIRED` 등 | JWT 없음·만료 |

### `PATCH /api/v1/users/onboarding` — 삭제됨 (2026-07-20, #22)

선택 온보딩 boolean을 별도 API로 갱신하는 설계였으나 채택되지 않았다. `isGoogleCalendarConnected`는 Google Calendar OAuth 연동 API가 직접 갱신하고([`google-calendar-oauth.md`](google-calendar-oauth.md)), 사전 일정 입력 완료 여부는 `hasCompletedPreSchedule`(파생)로 대체됐다(구 `isAllFree`는 2026-08-18 `#113`, 구 `hasPreSchedule`·`hasRegularSchedule`은 2026-08-19에 삭제) — 상세: [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md) D-BR006-C. 이 엔드포인트를 참고해 구현하지 말 것.

## 데이터 모델 (`user` 추가 컬럼)

| 컬럼 | 타입 | Default | 설명 |
|------|------|---------|------|
| first_name | varchar | null | 유저 입력 이름 |
| last_name | varchar | null | 유저 입력 성 |
| is_google_calendar_connected | boolean | false | Google Calendar 연동 |

`hasCompletedPreSchedule` 파생 규칙은 [`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md) D-BR006-C가 SSOT — 여기서 중복 정의하지 않는다.

`nickname` — 소셜 전용, fallback 없음. 상세 [`erd.md`](../../architecture/erd.md).

## AuthService upsert 정책 (구현 시)

| 상황 | nickname | first/last |
|------|----------|------------|
| 신규 login | 소셜 값 또는 null | null |
| 재로그인, 이름 미입력 | 소셜 값 갱신 가능 | null 유지 |
| 재로그인, 이름 입력 완료 | 소셜 값 갱신 가능 | **덮어쓰기 금지** |

## 검증 시나리오

- [x] 최초 login → JWT + `firstName`/`lastName` null + `isGoogleCalendarConnected`/`hasCompletedPreSchedule` 전부 false
- [x] profile PATCH → first/last 저장
- [x] 재login → first/last non-null면 **메인 분기** (선택 온보딩 완료 여부 무관, D-REENTRY-2)
- [x] 이름 null 상태에서 trip 생성·join 시도 → 403 `PROFILE_NAME_REQUIRED` (D-NAME-1)
- [ ] `hasCompletedPreSchedule` 시나리오는 [`../user-schedule/pre-schedule-entry-flow.md`](../user-schedule/pre-schedule-entry-flow.md) 검증 시나리오가 SSOT

## 관련 문서

| 문서 | 변경 |
|------|------|
| [`auth-social-login.md`](../auth/auth-social-login.md) | login 응답·nickname fallback 폐기·Out of Scope 정리 |
| [`erd.md`](../../architecture/erd.md) | `user` 컬럼 |
| [`figma-wireframe-v1.md`](../../product/design/figma-wireframe-v1.md) | 네이버 캘린더 제거 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-16 | **② 사전 일정 단계 `[미정]`(#22) 해소** — Figma Wireframe v1 대조로 확정: 캘린더→사전 일정 순서, "정기 일정이 있나요?" 예/없어요 분기, **연차 3문항은 정기 일정과 한 덩어리**("없어요"면 미노출), 회원가입은 건너뛰기 가능(방 입장은 불가 — `schedule-participation-onboarding.md` D-JOIN-TRIP-FLOW) |
| 2026-07-28 | API 경로 리네이밍 — `PATCH /users/profile`(온보딩) → `PATCH /users/onboarding/name`. 마이페이지 수정 API가 `/users/profile`을 대신 사용(`user-my-page.md` 참고) — 두 API가 "UI 의도만 다른 동일 계약"으로 오해되는 문제 해소 |
| 2026-07-23 | **문서 정정** — 본문이 여전히 `is_schedule_registered`/`is_optional_onboarding_completed`/`PATCH /users/onboarding`을 Must Have·API로 서술하고 있었으나, 이는 2026-07-20에 이미 삭제된 설계(§확정 정책 요약 참고). 코드·`schedule-participation-onboarding.md`와 일치하도록 전면 수정 |
| 2026-07-20 | **Amend** D-NAME-1 (Kakao=Google=Apple 이름 게이트), D-REENTRY-2 (재진입 → 메인). 선택 온보딩 boolean 3개·`PATCH /users/onboarding` 삭제 |
| 2026-07-08 | Approved — boolean 3개 + 이름, PATCH onboarding (이후 2026-07-20에 boolean·API 삭제) |
| 2026-07-09 | 마이페이지 이름 수정은 [`user-my-page.md`](user-my-page.md)로 분리 |
| 2026-07-13 | 경로 `/users/me/*` → `/users/*` |
