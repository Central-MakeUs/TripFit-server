# 연차·반차·공휴일 휴무 설정 — 구현 규칙 (계약 변경 대응 필수)

TripFit 프론트엔드 저장소에서 연차 관련 화면(연차 일수 / 연차 신청 시점 / 반차·공휴일)을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약은 추측하지 말고 사용자에게 확인하라.

정기 일정 자체(요일·시각·슬롯)의 편집 규칙은 [`schedule-calendar-merge.md`](schedule-calendar-merge.md), 방 입장 플로우에서 이 화면이 언제 뜨는지는 [`trip-room-create-join.md`](../trip/trip-room-create-join.md) 규칙 6을 따르라.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다. 아래 예시는 `data` 안쪽만 표기했다.

## 규칙 1 — 연차 4개 값은 정기 일정이 아니라 **전용 API**로 읽고 써라 (⚠️ 계약 변경)

`maxVacationDays`·`vacationApplyPeriod`·`halfVacationAvailable`·`holidayRest` 4개 값은 **정기 일정 행이 아니라 사용자 1명에게 하나씩** 붙는다. 서버에서 저장 위치가 옮겨졌고, **정기 일정 API의 요청·응답에서 이 4개 필드가 삭제됐다.**

| | 변경 전 | 변경 후 |
|---|---|---|
| 저장 단위 | 정기 일정 **행마다** (같은 값을 모든 행에 중복 저장) | **사용자당 1개** |
| 읽기 | `GET /users/schedule/regular` 응답의 `items[0]`에서 역산 | **`GET /users/schedule/vacation-policy`** |
| 쓰기 | `POST`/`PATCH /users/schedule/regular`에 매 행마다 실어 보냄 | **`PATCH /users/schedule/vacation-policy`** 1회 |

**정기 일정 API에 이 4개 필드를 계속 보내면 무시된다(에러는 나지 않는다).** 저장된 줄 알고 넘어가면 값이 조용히 사라지니, 아래 규칙 4의 마이그레이션 체크리스트를 반드시 훑어라.

## 규칙 2 — `PATCH`는 부분 수정이 아니라 **전체 교체**다. 4개를 매번 다 보내라

생략한 필드는 "그대로 유지"가 아니라 **기본값으로 덮어써진다.**

| 필드 | 타입 | 생략 시 | 비고 |
|---|---|---|---|
| `maxVacationDays` | `int` | **2** | 허용 0~10. 벗어나면 `400 INVALID_INPUT` |
| `vacationApplyPeriod` | enum \| `null` | **`null`**(미설정) | `ANY` \| `ONE_WEEK_BEFORE` \| `TWO_WEEKS_BEFORE` \| `ONE_MONTH_BEFORE` |
| `halfVacationAvailable` | `boolean` | **`false`** | 반차 사용 가능 여부 |
| `holidayRest` | `boolean` | **`true`** | 공휴일에 쉬는지 여부 — 기본값이 `true`인 점에 주의 |

```json
{
  "maxVacationDays": 2,
  "vacationApplyPeriod": "ONE_WEEK_BEFORE",
  "halfVacationAvailable": false,
  "holidayRest": true
}
```

요청·응답 모양이 동일하다(`GET` 응답 = `PATCH` 요청 = `PATCH` 응답). 화면에서 "연차 안 씀"을 표현하려면 `maxVacationDays: 0`을 보내라 — 필드를 생략하면 2로 되돌아간다.

`vacationApplyPeriod`의 `ANY`(상관없음)와 `null`(미설정)은 **서로 다른 값**이다. 사용자가 "상관없음"을 고른 것과 아직 안 고른 것을 같은 값으로 보내지 마라.

## 규칙 3 — 연차 화면은 **정기 일정과 한 덩어리로만** 노출하라

정기 일정을 입력·수정하는 경로에서만 연차 3문항을 띄운다. "정기 일정 없어요"를 고른 사용자에게는 **연차를 묻지 마라** — 회원가입·방 입장 두 플로우 모두 동일하다.

- 회원가입 "없어요" → 곧바로 개별 일정 화면 (`SignupFlow`가 이미 이렇게 동작)
- **방 입장 "없어요" → 곧바로 개별 일정 화면** ← 현재 연차 3문항으로 넘어가고 있어 수정 필요 (규칙 4)

**서버는 이 제한을 강제하지 않는다.** `PATCH /vacation-policy`는 정기 일정이 0건이어도 그대로 저장된다 — 정기 일정 유무를 선행 조건으로 거는 게이트는 의도적으로 두지 않았다(정기를 전부 지웠다 다시 등록해도 연차 설정이 보존되도록). 따라서 **화면 노출 제어는 전적으로 프론트 책임**이다.

정기 일정을 전부 삭제해도 연차 값은 서버에 그대로 남는다. 이때 값은 어떤 계산에도 쓰이지 않으니(정기가 없으면 연차 시뮬레이션 자체를 건너뜀) "지워야 한다"고 생각하지 마라 — 지우는 API도 없다.

## 규칙 4 — 아래 지점을 전부 고쳐라 (마이그레이션 체크리스트)

현재 클라이언트는 정기 일정 목록에서 연차 값을 역산하고 있다. 그 필드들이 응답에서 사라지므로 **아래 지점은 반드시 수정해야 하며, 안 고치면 연차 값이 항상 기본값으로 보인다.**

| 파일 | 현재 코드 | 수정 방향 |
|---|---|---|
| `app/room/[roomId]/_components/RoomDetailSection.tsx` | `annualLeaveCount: savedItems[0]?.maxVacationDays`, `leaveNoticeDays: getLeaveNoticeDaysFromRegularSchedules(savedItems)`, `includeHalfDayHoliday: getIncludeHalfDayHolidayFromRegularSchedules(savedItems)` | `GET /users/schedule/vacation-policy` 응답으로 채워라 |
| `app/room/[roomId]/_components/group-calendar/GroupCalendarSection.tsx` | 동일 3줄 | 동일 |
| `app/my-schedule/_components/MyScheduleSection.tsx` | 동일 패턴 | 동일 |
| `utils/mapRegularSchedule.ts`(`getLeaveNoticeDaysFromRegularSchedules` 등) | 정기 응답에서 연차 역산 | **함수 자체 제거** — 역산할 원본이 없어졌다 |
| `hooks/useSaveRegularSchedule.ts` | 저장 시 모든 정기 행에 연차 값 중복 전송 | 정기는 `title`/`daysOfWeek`/`startTime`/`endTime`만, 연차는 `PATCH /vacation-policy` **1회** 별도 호출 |
| `components/basic-info/index.tsx:184` | `confirmDirectInputOnNoRegularSchedule`가 회원가입에만 적용 | 방 입장 경로에도 적용해 "없어요" → 개별 일정으로 직행 (규칙 3) |

조회는 정기 일정 목록과 **나란히(병렬로)** 하라 — 두 API는 서로 의존하지 않는다.

```
화면 진입 → GET /users/schedule/regular      (정기 목록)
         → GET /users/schedule/vacation-policy (연차 4개)   ← 동시에
```

## 규칙 5 — 관련 API는 아래 표만 사용하라

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/v1/users/schedule/vacation-policy` | JWT | 연차 4개 값 조회. 한 번도 저장 안 했으면 기본값(`2`/`null`/`false`/`true`) |
| `PATCH` | `/api/v1/users/schedule/vacation-policy` | JWT | 4개 값 **전체 교체** |

`GET /auth/me`·`POST /auth/login`·`PATCH /users/profile` 응답(`UserSummaryResponse`)에는 **연차 값이 들어 있지 않다.** 일부러 넣지 않았으니(같은 값이 두 곳에 있으면 연차 저장 후 캐시가 낡는다) 거기서 찾지 마라.

| HTTP | code | 상황 | 처리 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | `maxVacationDays`가 0~10 밖 | 폼 검증 에러로 표시 |
| 401 | `AUTH_EXPIRED` 등 | 토큰 없음·만료 | 재로그인 플로우 |

## 규칙 6 — 이 API는 방 입장 조건을 **건드리지 않는다**. 그렇게 가정하고 짜라

연차 설정 저장은 "일정 등록"이 아니다. `PATCH /vacation-policy`를 호출해도 `hasPreSchedule`·`isAllFree`는 변하지 않는다.

- 저장 후 `GET /auth/me`를 다시 부를 필요 없다 (정기·개별 일정 저장과 다른 점).
- 이미 방에 참여 중인 사용자가 연차만 수정해도 방에서 튕기지 않는다.

이 성질에 기대어, 연차 저장 성공을 "일정 입력 완료"로 취급하지 마라 — 방 입장 조건은 여전히 `정기 ≥1 OR 개별 ≥1 OR isAllFree`다.

## 화면 참고 — 「기본 정보 관리」 연차 3문항

| 화면 요소 | 데이터 |
|---|---|
| "연차를 며칠까지 쓸 수 있나요?" | `maxVacationDays` (0~10) |
| "연차는 언제까지 신청해야 하나요?" (상관없음 / 1주 전 / 2주 전 / 한 달 전) | `vacationApplyPeriod` — 각각 `ANY` / `ONE_WEEK_BEFORE` / `TWO_WEEKS_BEFORE` / `ONE_MONTH_BEFORE` |
| "반차를 쓸 수 있나요?" | `halfVacationAvailable` |
| "공휴일에 쉬나요?" | `holidayRest` |
| "저장" | `PATCH /users/schedule/vacation-policy` **1회** — 4개 전부 담아서. 정기 일정 저장과 별개 호출 |
