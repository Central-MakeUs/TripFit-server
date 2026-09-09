# schedule 분리 — 정기 일정 + 개인 일정

> wave: 2  
> implements: BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-TRIP-006, BR-USER-008  
> deferred: 연차 복수 행 집계 (#13) — Google Calendar OAuth는 [`google-calendar-oauth.md`](../user/google-calendar-oauth.md) **Implemented** (#44)  
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

## 개인 일정 (`PersonalSchedule`) — 슬롯 단위 오버라이드 (O1.4, [`schedule-slot-override.md`](schedule-slot-override.md) #67)

- 행 단위: `(user_id, schedule_date)` UNIQUE — **날짜당 1행**
- `morningStatus` / `afternoonStatus` / `eveningStatus` — **각각 nullable.** 값이 있으면 그 슬롯을 오버라이드, `null`이면 그 슬롯은 손대지 않고 정기+구글 계산값을 그대로 따른다(구 S1 "행 있으면 그 날 전체 대체"는 폐기)
- `uncertain` (boolean) — **그 날짜 전체** 불확실 여부 (슬롯별 아님). 슬롯 오버라이드가 하나도 없어도 이 값만 `true`로 둘 수 있음. 추천 시 미정(TBD)으로 취급 `[제안]`
- **요청 DTO(`PersonalScheduleItem`, O1.4)는 슬롯 3개를 `slots`(nullable 중첩 객체, 있으면 3필드 전부 필수)로 묶고 `uncertain`은 별도 nullable `Boolean`으로 둔다 — 저장 컬럼 자체(위 3개 + `is_uncertain`)와 요청 DTO 모양은 다르다.** 상세 필드 조합·에러 표는 `schedule-slot-override.md` 참고.

```json
{
  "items": [
    {
      "scheduleDate": "2026-08-03",
      "slots": { "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "IMPOSSIBLE" }
    },
    {
      "scheduleDate": "2026-08-04",
      "uncertain": true
    }
  ]
}
```

위 예시: 8/3은 슬롯 3개를 명시 오버라이드(아침·저녁도 정기 계산값과 같은 값을 재전송한 것일 뿐 여전히 오버라이드), 8/4는 `slots` 키 자체를 생략해 슬롯은 안 건드리고 "불확실"만 표시.

- **`items`:** `(user, date)` find-or-create 후 부분 업데이트 — `slots`가 있으면 슬롯 3개 갱신, `uncertain`이 있으면 그 값만 갱신(둘 다 있으면 둘 다). **`personal_schedule` row를 삭제하는 코드 경로는 없다** — 한 번 생성된 행은 이후 어떤 요청으로도 삭제되지 않는다(구 CLEAR 시맨틱은 "개별 오버라이드가 항상 정기를 이긴다"는 규칙을 깨는 버그로 판명돼 O1.4에서 완전히 제거됨, `schedule-slot-override.md` "계약 개정 이력 — O1.4" 참고)
- `items`가 비어 있거나, 한 항목에 `slots`/`uncertain`이 둘 다 없거나, 같은 `scheduleDate`가 중복되면 400
- 응답(`PATCH` 결과)은 저장된 원본이 아니라 정기+개별+구글까지 합친 **최종 확정값**(POSSIBLE/IMPOSSIBLE로 확정, null 없음)으로 내려간다 — 응답 필드는 요청과 달리 `slots` 중첩 없이 평면(`morningStatus`/`afternoonStatus`/`eveningStatus`)이다

## 정기 일정 (`RegularSchedule`)

- **생성:** `startTime`~`endTime` 입력 → `SlotStatuses.fromTimeRange`로 슬롯 계산
- **수정 (PATCH):** create와 동일 필드 전체 갱신. start/end 변경 시 슬롯 재계산
- **연차·반차·공휴일 휴무 필드는 `RegularSchedule`에 없다** — `#52`(2026-08-16)로 `User`(사람 1명당 하나)로 이동, 전용 `GET`/`PATCH /users/schedule/vacation-policy`로 별도 조회·수정. 상세: [`vacation-policy-user-migration.md`](vacation-policy-user-migration.md)

## API

| Method | Path | 설명 |
|--------|------|------|
| GET/POST | `/api/v1/users/schedule/regular` | 목록 / 생성 |
| PATCH/DELETE | `/api/v1/users/schedule/regular/{id}` | 전체 수정 / 삭제 |
| GET/PATCH | `/api/v1/users/schedule/vacation-policy` | 연차·반차·공휴일 휴무 설정 조회 / 전체 교체 (`#52`, `User` 소유) |
| PATCH | `/api/v1/users/schedule/personal` | **슬롯 단위 오버라이드 upsert(`slots`/`uncertain` 각각 선택, 삭제 경로 없음)**, 반영된 날짜들의 최종 확정값 반환 |
| GET | `/api/v1/users/schedule/calendar` | 정기+개별 합친 달력 · **today~+2년** (#37) · Hidden **1단계 해제** |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | 멤버 전원 정기+개별 합친 달력 · **OpenAPI 공개** · ~~personal-summary~~ **삭제** |

> 폐기: `/schedule/availability`, per-slot TBD, `note` · ~~BR-USER-006 regular 선행 403~~ (#22 D-BR006-5) · ~~`members/personal-summary`~~ · ~~GET `/schedule/personal`~~ (조회 API 삭제, PATCH 응답으로 대체)

**정기 유래 날짜를 클릭해 하루만 고치는 UX**(프리필·엣지케이스·`PATCH .../personal` vs `GET .../calendar` DTO 차이)는 [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) "마이페이지 개별 일정 편집 UX" 절 참고.

## 잔여

- `uncertain=true`일 때 추천에서 슬롯 무시 여부 (#13) — calendar는 U1(슬롯 그대로 노출) 가정
- ~~그룹 `members/schedule-calendar` OpenAPI Hidden~~ — **공개 완료**
- ~~`members/personal-summary`~~ — **삭제 완료**

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-30 | **O1.4 반영** — `PersonalScheduleItem` 요청 DTO를 `slots`(nullable 중첩 객체, 있으면 3필드 전부 필수)/`uncertain`(nullable `Boolean`) flat 구조로 갱신, **삭제(CLEAR) 경로 전면 삭제**(더 이상 어떤 값 조합으로도 row가 삭제되지 않음) — `schedule-slot-override.md` O1.4 계약과 동기화 |
| 2026-07-29 | **개인 일정 = 슬롯 단위 오버라이드(O1)로 전환** (#67, [`schedule-slot-override.md`](schedule-slot-override.md)) — 슬롯 3개 nullable, 삭제(CLEAR) 신호 "전부 POSSIBLE" → "전부 null", `PATCH` 응답은 정기+구글까지 합친 최종 확정값. 구 S1(행 있으면 그 날 전체 대체) 폐기 |
| 2026-08-05 | **Amend** — personal `deletedDates` 필드 제거. `items`에서 슬롯 3개 모두 POSSIBLE·uncertain=false인 항목을 삭제(CLEAR) 신호로 통합 |
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
