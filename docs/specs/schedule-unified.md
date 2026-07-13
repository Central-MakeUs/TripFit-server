# schedule 분리 — 정기 일정 + 개인 일정

> wave: 2  
> implements: BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-TRIP-006, BR-USER-008  
> deferred: Google Calendar OAuth → [#44](https://github.com/Central-MakeUs/TripFit-server/issues/44) [`google-calendar-oauth.md`](google-calendar-oauth.md) (wave 4), 연차 복수 행 집계 (#13)  
> 상태: Approved  
> supersedes: A안 `schedule`; `Availability` → `PersonalSchedule`

## 목표

- **정기** `regular_schedule` — 시계 구간 → `TimeSlot`별 POSSIBLE/IMPOSSIBLE 계산  
- **개인** `personal_schedule` — **특정 날짜**에 오전/오후/저녁 가능·불가 + **날짜 단위 불확실(`uncertain`)**  
- 슬롯 경계·상태 컬럼은 공통 `TimeSlot` + `SlotStatuses` embeddable

## 패키지

`user` 도메인 feature 하위 패키지 (`docs/decisions/003-architecture-guide.md`):

```
user/schedule/
├── controller/   UserScheduleController
├── dto/
├── service/      ScheduleService
├── domain/       RegularSchedule, PersonalSchedule
├── repository/
└── exception/    ScheduleErrorCode
```

`TimeSlot` / `ScheduleStatus` / `SlotStatuses`는 trip 공용으로 `trip/domain/` 유지. 프로필 ErrorCode는 `user/exception/UserErrorCode`.

## TimeSlot (공통 · 확정)

| 슬롯 | 반개구간 |
|------|----------|
| MORNING | `[00:00, 13:00)` |
| AFTERNOON | `[13:00, 18:00)` |
| EVENING | `[18:00, 24:00)` |

슬롯 status: **`POSSIBLE` | `IMPOSSIBLE`만** (슬롯에 TBD 없음).

## 개인 일정 (`PersonalSchedule`)

- 행 단위: `(user_id, schedule_date)` UNIQUE — **날짜당 1행**
- `morningStatus` / `afternoonStatus` / `eveningStatus` — 가능/불가  
- `uncertain` (boolean) — **그 날짜 전체** 불확실 여부 (슬롯별 아님). 추천 시 미정(TBD)으로 취급 `[제안]`

```json
{
  "items": [
    {
      "scheduleDate": "2026-08-03",
      "morningStatus": "IMPOSSIBLE",
      "afternoonStatus": "POSSIBLE",
      "eveningStatus": "POSSIBLE",
      "uncertain": true
    }
  ],
  "deletedDates": ["2026-08-04", "2026-08-05"]
}
```

- **`items`:** `(user, date)` insert/update  
- **`deletedDates`:** 해당 날짜 row 삭제 (CLEAR · #22). regular도 0이면 `is_all_free=true`  
- `items` ∩ `deletedDates` 비공집합 → 400 · 둘 다 비어 있으면 400

## 정기 일정 (`RegularSchedule`)

- **생성:** `startTime`~`endTime` 입력 → `SlotStatuses.fromTimeRange`로 슬롯 계산
- **수정 (PATCH):** create와 동일 필드 전체 갱신. start/end 변경 시 슬롯 재계산
- **연차 필드 기본값·제약:**
  - `maxVacationDays` — default **2**, 허용 **0~10**
  - `vacationApplyPeriod` — enum `ANY` \| `ONE_WEEK_BEFORE` \| `TWO_WEEKS_BEFORE` \| `ONE_MONTH_BEFORE`, default **null**
  - `halfVacationAvailable` — default **false** (N)
  - `holidayRest` — default **true** (Y)

## API

| Method | Path | 설명 |
|--------|------|------|
| GET/POST | `/api/v1/users/schedule/regular` | 목록 / 생성 |
| PATCH/DELETE | `/api/v1/users/schedule/regular/{id}` | 전체 수정 / 삭제 |
| GET/PATCH | `/api/v1/users/schedule/personal` | 조회 / **upsert + `deletedDates`** (#22 Hidden **1단계 해제**) |
| GET | `/api/v1/users/schedule/calendar` | effective 달력 · **today~+2년** (#37) · Hidden **1단계 해제** |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | 멤버 전원 effective · **OpenAPI 공개** · ~~personal-summary~~ **삭제** |

> 폐기: `/schedule/availability`, per-slot TBD, `note` · ~~BR-USER-006 regular 선행 403~~ (#22 D-BR006-5) · ~~`members/personal-summary`~~

## 잔여

- `uncertain=true`일 때 추천에서 슬롯 무시 여부 (#13) — calendar는 U1(슬롯 그대로 노출) 가정
- ~~그룹 `members/schedule-calendar` OpenAPI Hidden~~ — **공개 완료**
- ~~`members/personal-summary`~~ — **삭제 완료**

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | **#22** — personal/calendar Hidden 해제 · `deletedDates` CLEAR · BR-USER-006 게이트 폐기 반영 |
| 2026-07-14 | personal GET/PATCH에 BR-USER-006 `REGULAR_SCHEDULE_REQUIRED` 게이트 |
| 2026-07-14 | 병합 S1 확정 링크 (`schedule-calendar-resolve.md`) |
| 2026-07-13 | calendar resolve Draft 링크 (`schedule-calendar-resolve.md`) |
| 2026-07-13 | PersonalSchedule · 날짜단위 uncertain · SlotStatuses 통합 |
| 2026-07-13 | 정기 start/end 생성 전용(readonly) · PUT은 슬롯 3개만 |
| 2026-07-13 | 슬롯·개인 일정 수정 HTTP 메서드 PUT → PATCH |
| 2026-07-13 | `user/schedule/` feature 패키지 · `ScheduleErrorCode` 분리 |
| 2026-07-13 | 경로 `/users/me/schedule/*` → `/users/schedule/*` |
| 2026-07-13 | 연차: default 2·max 10, `VacationApplyPeriod` enum, 반차 N·공휴일 Y default |
| 2026-07-13 | 정기 PATCH: 슬롯만 → 전체 수정 (`UpdateRegularScheduleRequest`) |
