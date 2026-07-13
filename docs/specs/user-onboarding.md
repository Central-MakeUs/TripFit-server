# 사용자 온보딩 · 프로필 (이름 + 선택 단계)

> wave: 1  
> implements: BR-USER-001 (이름 완료 후 핵심 API)  
> 결정: [`docs/decisions/007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md)  
> 선행: [`auth-social-login.md`](auth-social-login.md)  
> 상태: Approved  
> 승인: 2026-07-08

## 목표

소셜 login으로 **회원가입(JWT 발급)** 후, 필수 **성/이름** 입력과 선택 **Google 캘린더·사전 일정(근무·연차)** 온보딩을 프론트가 boolean·이름 null로 분기할 수 있게 한다.

## 확정 정책 요약

| # | 정책 |
|---|------|
| 1 | 네이버 캘린더 **제외** — Google만 |
| 2 | 이름 = **성(`lastName`) + 이름(`firstName`)** 분리, **필수·건너뛰기 없음** |
| 3 | **회원가입 = 소셜 login upsert + JWT** (이름 전에도 토큰 발급) |
| 4 | `isGoogleCalendarConnected` — OAuth 연동 시만 `true`; 미연동·건너뛰기 = `false` |
| 5 | `isScheduleRegistered` — `regular_schedule` ≥1행 시만 `true`; 미등록·건너뛰기 = `false` |
| 6 | `isOptionalOnboardingCompleted` — 선택 온보딩 **전체** 완료 후 `true` (재진입 시 온보딩 UI 미노출) |
| 7 | `onboarding_step` **미사용** |

## UI 흐름

```text
[소셜 로그인]
       ↓
POST /api/v1/auth/login → JWT + user (firstName/lastName may null)
       ↓
firstName 또는 lastName null?
  YES → [성/이름 입력] → PATCH /users/profile
  NO  ↓
!isOptionalOnboardingCompleted?
  YES → [선택 온보딩]
          ① Google 캘린더 (연동 또는 건너뛰기)
          ② 사전 일정 / 근무·연차 (등록 또는 건너뛰기)
          → PATCH /users/onboarding { isOptionalOnboardingCompleted: true }
  NO  ↓
[메인]
```

### 단계별 상세

| 단계 | UI | 서버 상태 변화 |
|------|-----|----------------|
| 소셜 login | SDK 로그인 | user row upsert, JWT 발급, boolean 기본값 `false` |
| 이름 | 성·이름 입력 (소셜 `nickname`은 인풋 prefill만) | `PATCH profile` → `first_name`, `last_name` |
| 캘린더 | 연동 또는 건너뛰기 | 연동 성공 시 `isGoogleCalendarConnected=true` (별도 스펙). **건너뛰기 = `false` 유지** |
| 사전 일정 | 근무·연차 입력 또는 건너뛰기 | 저장 시 `isScheduleRegistered=true` (별도 스펙). **건너뛰기 = `false` 유지** |
| 온보딩 종료 | 마지막 단계 완료 | `PATCH onboarding` → `isOptionalOnboardingCompleted=true` |

> **건너뛰기 전부 후 재진입:** 캘린더·일정 온보딩 **재노출 금지** — 마지막 건너뛰기 직후 `isOptionalOnboardingCompleted=true` PATCH 필수. 두 연동 boolean은 `false` 유지.

> **중간 이탈:** `isOptionalOnboardingCompleted=false`이면 재로그인 시 선택 온보딩을 **처음부터** 다시 보여줄 수 있음 (캘린더 단계부터). 이름은 PATCH 후 유지.

## 요구사항

### Must Have (wave 1 — 본 스펙)

- [ ] `user` 컬럼: `first_name`, `last_name`, `is_google_calendar_connected`, `is_schedule_registered`, `is_optional_onboarding_completed`
- [ ] `nickname` — 소셜 값만, **fallback 폐기** ([`007`](../decisions/007-user-profile-onboarding.md))
- [ ] login / `GET /auth/me` 응답 `user`에 위 필드 포함
- [ ] `PATCH /api/v1/users/profile` — `{ firstName, lastName }` (JWT 필수)
- [ ] `PATCH /api/v1/users/onboarding` — boolean 갱신 (JWT 필수, 아래 API 참고)
- [ ] `first_name`/`last_name` 없으면 여행방 생성 등 핵심 API **403** `PROFILE_NAME_REQUIRED` `[제안]`
- [ ] `./gradlew test` 통과

### Deferred (별도 스펙 — wave 1 본문 구현 안 함)

- [ ] Google Calendar OAuth 연동 API·토큰 저장
- [x] 정기·개별 일정 — [`schedule-unified.md`](schedule-unified.md) (wave 2, #11)
- [ ] 마이페이지 이름 수정 — [`user-my-page.md`](user-my-page.md) (`PATCH /users/my-page`)
- [ ] 네이버 캘린더

## API

### `user` 요약 DTO (login · `GET /auth/me` 공통)

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
  "isScheduleRegistered": false,
  "isOptionalOnboardingCompleted": false
}
```

| 필드 | nullable | 설명 |
|------|----------|------|
| firstName | Y | 미입력 시 null → 이름 화면 |
| lastName | Y | 미입력 시 null → 이름 화면 |
| nickname | Y | 소셜 provider 값. prefill용 |
| isGoogleCalendarConnected | N | default `false`. **연동 성공 시만** `true` |
| isScheduleRegistered | N | default `false`. **condition 저장 시만** `true` |
| isOptionalOnboardingCompleted | N | default `false`. 선택 온보딩 전체 완료 시 `true` |

### `PATCH /api/v1/users/profile`

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

### `PATCH /api/v1/users/onboarding`

선택 온보딩 boolean 갱신. **건너뛰기·연동·등록 결과를 서버에 반영**해 재진입 시 프론트만으로는 불가능한 “온보딩 완료” 상태를 저장한다.

| 항목 | value |
|------|-------|
| Auth | Bearer JWT **필수** |

**Request** (전송한 필드만 갱신 — partial update)

```json
{
  "isGoogleCalendarConnected": false,
  "isScheduleRegistered": false,
  "isOptionalOnboardingCompleted": true
}
```

| 필드 | 설명 |
|------|------|
| isGoogleCalendarConnected | Google Calendar OAuth **연동 성공** 시 `true`. 건너뛰기 시 **보내지 않거나 `false` 유지** |
| isScheduleRegistered | 정기 일정(`regular_schedule`) ≥1 저장 후 `true`. 건너뛰기 시 **보내지 않거나 `false` 유지** |
| isOptionalOnboardingCompleted | 선택 온보딩 **마지막 단계** 완료(등록·건너뛰기 모두 포함) 시 `true` |

**일반 패턴 (건너뛰기만 한 경우)**

```json
{
  "isOptionalOnboardingCompleted": true
}
```

`isGoogleCalendarConnected`·`isScheduleRegistered`는 `false`로 남음.

**Response `200`** — 갱신된 `user` 요약

## 데이터 모델 (`user` 추가 컬럼)

| 컬럼 | 타입 | Default | 설명 |
|------|------|---------|------|
| first_name | varchar | null | 유저 입력 이름 |
| last_name | varchar | null | 유저 입력 성 |
| is_google_calendar_connected | boolean | false | Google Calendar 연동 |
| is_schedule_registered | boolean | false | 사전 정기 일정(`regular_schedule`) 등록 |
| is_optional_onboarding_completed | boolean | false | 선택 온보딩 전체 완료 |

`nickname` — 소셜 전용, fallback 없음. 상세 [`erd.md`](../architecture/erd.md).

## AuthService upsert 정책 (구현 시)

| 상황 | nickname | first/last |
|------|----------|------------|
| 신규 login | 소셜 값 또는 null | null |
| 재로그인, 이름 미입력 | 소셜 값 갱신 가능 | null 유지 |
| 재로그인, 이름 입력 완료 | 소셜 값 갱신 가능 | **덮어쓰기 금지** |

## 검증 시나리오

- [ ] 최초 login → JWT + `firstName`/`lastName` null + boolean 전부 false
- [ ] profile PATCH → first/last 저장
- [ ] onboarding PATCH skip only → `isOptionalOnboardingCompleted=true`, calendar/schedule boolean false
- [ ] 재login → optional completed true면 메인 분기
- [ ] 이름 null 상태에서 trip 생성 시도 → 403 `[제안]`

## 관련 문서

| 문서 | 변경 |
|------|------|
| [`auth-social-login.md`](auth-social-login.md) | login 응답·nickname fallback 폐기·Out of Scope 정리 |
| [`erd.md`](../architecture/erd.md) | `user` 컬럼 |
| [`figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) | 네이버 캘린더 제거 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-08 | Approved — boolean 3개 + 이름, PATCH onboarding |
| 2026-07-09 | 마이페이지 이름 수정은 [`user-my-page.md`](user-my-page.md)로 분리 |
| 2026-07-13 | 경로 `/users/me/*` → `/users/*` |
