# 연차·휴일 정보 — 구현 규칙 (계약 변경 대응 필수)

TripFit 프론트엔드 저장소에서 연차 관련 화면(연차 일수 / 연차 신청 시점 / 반차·공휴일)을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약은 추측하지 말고 사용자에게 확인하라.

> **이 문서는 배포된 Swagger를 대체한다.** 백엔드는 이미 배포된 앱과의 계약을 지키기 위해 이 API가 배포 전에는 라이브 Swagger로 확인 불가능하다 — 아래 "API 명세" 절이 필드 타입·제약·기본값·에러 케이스까지 Swagger와 동등한 정보를 담고 있으니 이 문서만으로 구현하라. 배포 후에는 `https://api.tripfit.online/swagger-ui/index.html`(또는 로컬 `/swagger-ui/index.html`)로 교차 확인해도 된다.

정기 일정 자체(요일·시각·슬롯)의 편집 규칙은 [`schedule-calendar-merge.md`](schedule-calendar-merge.md), 방 입장 플로우에서 이 화면이 언제 뜨는지는 [`trip-room-create-join.md`](../trip/trip-room-create-join.md) 규칙 6을 따르라.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다(`SuccessResponse`). 성공 시 `message`·`code`는 응답 body에 아예 나타나지 않는다(`null` 필드는 직렬화에서 생략됨 — `undefined`로 취급). 아래 예시는 실제로 온 그대로(`data`까지 포함) 표기한다.

## API 명세

### `GET /api/v1/users/schedule/vacation-policy` — 연차 4개 값 조회

| | |
|---|---|
| 인증 | `Authorization: Bearer <accessToken>` 필수 |
| Request Body | 없음 |
| 성공 응답 | `200 OK` |

**응답 스키마 (`data`)**

| 필드 | 타입 | nullable | 설명 |
|---|---|---|---|
| `maxVacationDays` | `int` | ✗ | 여행당 최대 연차 일수. 0~10, 저장 안 했으면 `2` |
| `vacationApplyPeriod` | `string` (enum) | ✓ | 아래 enum 표 참고. **저장 안 했으면 `null` = 사전 일정 입력 미완료(최초 입력)** |
| `halfVacationAvailable` | `boolean` | ✗ | 반차 사용 가능 여부. 저장 안 했으면 `false` |
| `holidayRest` | `boolean` | ✗ | 공휴일 휴무 여부. 저장 안 했으면 `true` |

```json
// 200 — 한 번도 저장한 적 없는 신규 사용자 (기본값)
{
  "data": {
    "maxVacationDays": 2,
    "vacationApplyPeriod": null,
    "halfVacationAvailable": false,
    "holidayRest": true
  }
}
```

```json
// 200 — 저장된 값이 있는 사용자
{
  "data": {
    "maxVacationDays": 9,
    "vacationApplyPeriod": "ONE_WEEK_BEFORE",
    "halfVacationAvailable": true,
    "holidayRest": false
  }
}
```

**에러**

| HTTP | `code` | 발생 조건 | 응답 예시 |
|---|---|---|---|
| 401 | `AUTH_INVALID_TOKEN` | `Authorization` 헤더 없음·형식 오류·서명 불일치 | `{"code":"AUTH_INVALID_TOKEN","message":"유효하지 않은 인증 토큰입니다."}` |
| 401 | `AUTH_EXPIRED` | 액세스 토큰 만료 | `{"code":"AUTH_EXPIRED","message":"액세스 토큰이 만료되었습니다."}` |

---

### `PATCH /api/v1/users/schedule/vacation-policy` — 연차 4개 값 전체 교체

| | |
|---|---|
| 인증 | `Authorization: Bearer <accessToken>` 필수 |
| 시맨틱 | **전체 교체** (부분 patch 아님) — 아래 규칙 2 참고 |
| 성공 응답 | `200 OK` |

**요청 스키마 (Request Body)**

| 필드 | 타입 | 필수 | 제약 | 생략(누락/`null`) 시 |
|---|---|---|---|---|
| `maxVacationDays` | `integer` | **예** | `0` ≤ x ≤ `10` | **400** |
| `vacationApplyPeriod` | `string` (enum) | **예** | 아래 enum 표 값 중 하나 | **400** |
| `halfVacationAvailable` | `boolean` | **예** | — | **400** |
| `holidayRest` | `boolean` | **예** | — | **400** |

> **2026-08-19 계약 변경 — 4개 필드가 전부 필수가 됐다.** 예전에는 생략하면 기본값으로 대체됐지만, 지금은 하나라도 빠지면 400이다. 이 API는 **부분 수정이 아니라 전체 교체**라, 예전 방식대로 바뀐 값만 보내면 나머지가 기본값으로 덮여 쓰였다 — 특히 `vacationApplyPeriod`가 지워지면 그 사용자는 **사전 일정 입력을 한 적 없는 상태로 되돌아간다**(아래 규칙 참고). 폼의 4개 값을 항상 함께 보내라.

```json
// Request
{
  "maxVacationDays": 9,
  "vacationApplyPeriod": "ONE_WEEK_BEFORE",
  "halfVacationAvailable": true,
  "holidayRest": false
}
```

```json
// 200 — 요청 body와 동일한 모양 그대로 응답 (저장된 최종값)
{
  "data": {
    "maxVacationDays": 9,
    "vacationApplyPeriod": "ONE_WEEK_BEFORE",
    "halfVacationAvailable": true,
    "holidayRest": false
  }
}
```

**`vacationApplyPeriod` enum 값**

| 값 | 화면 라벨 |
|---|---|
| `ANY` | 상관없음 |
| `ONE_WEEK_BEFORE` | 1주 전 |
| `TWO_WEEKS_BEFORE` | 2주 전 |
| `ONE_MONTH_BEFORE` | 한 달 전 |

**에러 (실제 서버 응답을 그대로 캡처한 값 — 메시지 텍스트까지 그대로 파싱해도 안전하다)**

| HTTP | `code` | 발생 조건 | 응답 예시 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | `maxVacationDays > 10` | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다.","errors":[{"field":"maxVacationDays","message":"must be less than or equal to 10"}]}` |
| 400 | `INVALID_INPUT` | `maxVacationDays < 0` | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다.","errors":[{"field":"maxVacationDays","message":"must be greater than or equal to 0"}]}` |
| 400 | `INVALID_INPUT` | `vacationApplyPeriod`에 enum 밖 문자열(오타 등) | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다."}` (이 케이스는 `errors` 배열이 없다 — JSON 파싱 단계 실패라 필드별 정보가 없음) |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` | 위와 동일 | 위와 동일 |

⚠️ `errors` 필드는 **있을 때도 있고 없을 때도 있다.** `@Min`/`@Max` 검증 실패(값이 범위를 벗어남)는 `errors` 배열이 오지만, enum 파싱 실패는 `errors` 없이 최상위 `message`만 온다. 폼 에러 표시 로직이 `errors` 배열 유무 둘 다 처리하게 짜라.

**⚠️ 서버는 정기 일정 존재 여부를 검사하지 않는다.** 정기 일정이 0건이어도 이 API는 그대로 저장된다 — 화면 노출 제어(규칙 3)는 전적으로 프론트 책임이다.

---

## 규칙 1 — 연차 4개 값은 정기 일정이 아니라 **전용 API**로 읽고 써라 (⚠️ 계약 변경)

`maxVacationDays`·`vacationApplyPeriod`·`halfVacationAvailable`·`holidayRest` 4개 값은 **정기 일정 행이 아니라 사용자 1명에게 하나씩** 붙는다. 서버에서 저장 위치가 옮겨졌고, **정기 일정 API의 요청·응답에서 이 4개 필드가 삭제됐다.** 정기 일정 API의 최신 계약(전체 필드 표·요청/응답 예시)은 [`schedule-calendar-merge.md`](schedule-calendar-merge.md) "규칙 4 — 정기 일정 API 전체 명세"를 따르라.

| | 변경 전 | 변경 후 |
|---|---|---|
| 저장 단위 | 정기 일정 **행마다** (같은 값을 모든 행에 중복 저장) | **사용자당 1개** |
| 읽기 | `GET /users/schedule/regular` 응답의 `items[0]`에서 역산 | **`GET /users/schedule/vacation-policy`** |
| 쓰기 | `POST`/`PATCH /users/schedule/regular`에 매 행마다 실어 보냄 | **`PATCH /users/schedule/vacation-policy`** 1회 |

**정기 일정 API에 이 4개 필드를 계속 보내면 무시된다(에러는 나지 않는다).** 저장된 줄 알고 넘어가면 값이 조용히 사라지니, 아래 규칙 4의 마이그레이션 체크리스트를 반드시 훑어라.

## 규칙 2 — `PATCH`는 부분 수정이 아니라 **전체 교체**다. 4개를 매번 다 보내라

생략한 필드는 "그대로 유지"가 아니라 **기본값으로 덮어써진다.** 위 "API 명세" 절 표를 참고하라.

화면에서 "연차 안 씀"을 표현하려면 `maxVacationDays: 0`을 보내라 — 필드를 생략하면 2로 되돌아간다.

`vacationApplyPeriod`의 `ANY`(상관없음)와 `null`(미설정)은 **서로 다른 값**이다. 사용자가 "상관없음"을 고른 것과 아직 안 고른 것을 같은 값으로 보내지 마라.

이 구분이 화면 흐름을 좌우한다. **`vacationApplyPeriod`(사전 신청일)가 저장돼 있는지가 "사전 일정 입력을 끝냈는지"의 판정 기준**이기 때문이다(2026-08-19 확정).

| 상태 | 판정 | 여행방 입장 시 첫 화면 |
|---|---|---|
| `null` | **최초 입력** | `정기 일정이 있나요?` |
| enum 값(=`ANY` 포함) | **갱신 입력** | `일정 변경이 있나요?` |

- 요약 응답(`/auth/login`·`/auth/me` 등)의 **`hasCompletedPreSchedule`** 이 같은 판정을 boolean으로 알려준다 — 이 API를 따로 부르지 않아도 첫 화면을 정할 수 있다.
- **정기·개별 일정 건수는 판정과 무관하다.** 일정이 하나도 없어도 이 값이 있으면 갱신이고, 정기 일정이 잔뜩 있어도 이 값이 없으면 최초다.
- 이 값을 지우는 경로는 **탈퇴뿐**이다. 일정을 전부 지워도 갱신 상태는 유지된다.
- `activate`(여행방 입장 완료)는 이 값이 없으면 **403 `PRE_SCHEDULE_REQUIRED`**로 거부된다.

## 규칙 3 — 연차·휴일 정보 화면은 **모든 사전 일정 입력 경로에서** 노출하라 (2026-08-19 전면 개정)

> **구 규칙 폐기:** 예전에는 "정기 일정과 한 덩어리라 '없어요'를 고른 사용자에게는 연차를 묻지 마라"였다. **정반대가 됐다.**

`정기 일정이 있나요? → 없어요`를 고른 사용자에게도 연차·휴일 정보 화면을 띄워라. 이 화면을 저장해야 **사전 신청일**이 채워지고, 그 값이 "사전 일정 입력을 끝냈다"는 유일한 표시다. 여기서 건너뛰면 그 사용자는 다음에도 최초 입력으로 들어오고, `activate`가 403 `PRE_SCHEDULE_REQUIRED`로 막는다.

| 경로 | 연차·휴일 정보 화면 |
|---|---|
| 최초 입력 · `있어요` | 정기 일정 입력 **다음에** 노출 |
| 최초 입력 · `없어요` | 정기 화면을 건너뛰고 **바로** 노출 |
| 갱신 입력 | 정기 일정 수정 **다음에** 노출 (기존 값 프리필) |
| 마이페이지 `기본정보 관리` | 정기 일정 **다음에** 노출 |

**서버는 이 화면의 노출 순서를 강제하지 않는다.** `PATCH /vacation-policy`는 정기 일정이 0건이어도 그대로 저장된다 — 정기 유무를 선행 조건으로 거는 게이트는 의도적으로 두지 않았다(정기를 전부 지웠다 다시 등록해도 연차 설정이 보존되도록). 서버가 검사하는 건 딱 하나, **`activate` 시점에 사전 신청일이 있는가**다.

정기 일정을 전부 삭제해도 연차 값은 서버에 그대로 남는다(지우는 API도 없다). 이때 값은 연차 시뮬레이션에 쓰이지 않지만, **입력 완료 표시로는 계속 유효하다** — "지워야 한다"고 생각하지 마라.

## 규칙 4 — 아래 지점을 전부 고쳐라 (마이그레이션 체크리스트)

현재 클라이언트는 정기 일정 목록에서 연차 값을 역산하고 있다. 그 필드들이 응답에서 사라지므로 **아래 지점은 반드시 수정해야 하며, 안 고치면 연차 값이 항상 기본값으로 보인다.**

| 파일 | 현재 코드 | 수정 방향 |
|---|---|---|
| `app/room/[roomId]/_components/RoomDetailSection.tsx` | `annualLeaveCount: savedItems[0]?.maxVacationDays`, `leaveNoticeDays: getLeaveNoticeDaysFromRegularSchedules(savedItems)`, `includeHalfDayHoliday: getIncludeHalfDayHolidayFromRegularSchedules(savedItems)` | `GET /users/schedule/vacation-policy` 응답으로 채워라 |
| `app/room/[roomId]/_components/group-calendar/GroupCalendarSection.tsx` | 동일 3줄 | 동일 |
| `app/my-schedule/_components/MyScheduleSection.tsx` | 동일 패턴 | 동일 |
| `utils/mapRegularSchedule.ts`(`getLeaveNoticeDaysFromRegularSchedules` 등) | 정기 응답에서 연차 역산 | **함수 자체 제거** — 역산할 원본이 없어졌다 |
| `hooks/useSaveRegularSchedule.ts` | 저장 시 모든 정기 행에 연차 값 중복 전송 | 정기는 `title`/`daysOfWeek`/`startTime`/`endTime`만, 연차는 `PATCH /vacation-policy` **1회** 별도 호출 |
| `components/basic-info/index.tsx:184` | `confirmDirectInputOnNoRegularSchedule`가 "없어요" 선택 시 연차 스텝을 건너뛰게 함 | **제거하라 (2026-08-19).** "없어요" 경로도 연차·휴일 정보를 거쳐야 한다 — 이 스킵이 남아 있으면 사전 신청일이 저장되지 않아 사용자가 영원히 최초 입력에 갇히고 `activate`가 403으로 막힌다 (규칙 3) |
| "없어요" 선택 시 | (없음) | `DELETE /api/v1/users/schedule/regular` **즉시 호출** — 남아 있던 정기 일정 전체 삭제 (`trip/trip-room-create-join.md` 규칙 6) |
| 연차 저장 요청 | 바뀐 필드만 전송 | **4개 필드를 항상 함께** — 하나라도 빠지면 400 (규칙 2) |

조회는 정기 일정 목록과 **나란히(병렬로)** 하라 — 두 API는 서로 의존하지 않는다.

```
화면 진입 → GET /users/schedule/regular      (정기 목록)
         → GET /users/schedule/vacation-policy (연차 4개)   ← 동시에
```

## 규칙 5 — 관련 API·에러 요약

전체 명세는 위 "API 명세" 절을 따르고, 이 표는 빠른 참조용이다.

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/v1/users/schedule/vacation-policy` | JWT | 연차 4개 값 조회. 한 번도 저장 안 했으면 기본값(`2`/`null`/`false`/`true`) |
| `PATCH` | `/api/v1/users/schedule/vacation-policy` | JWT | 4개 값 **전체 교체** |

`GET /auth/me`·`POST /auth/login`·`PATCH /users/profile` 응답(`UserSummaryResponse`)에는 **연차 값 4개가 들어 있지 않다.** 일부러 넣지 않았으니(같은 값이 두 곳에 있으면 연차 저장 후 캐시가 낡는다) 거기서 찾지 마라. 다만 **저장 여부**는 `hasCompletedPreSchedule` boolean으로 실려 있고, 이 API로 처음 저장하면 그 값이 `false → true`로 바뀐다 — **저장 직후 `/auth/me`를 다시 불러 최신 값을 받아라.**

| HTTP | code | 상황 | 처리 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | **4개 필드 중 하나라도 누락**·`maxVacationDays`가 0~10 밖·`vacationApplyPeriod` 오타 | 폼 검증 에러로 표시 |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` | 토큰 없음·무효·만료 | 재로그인 플로우 |

## 규칙 6 — 이 API는 방 입장 조건을 **건드리지 않는다**. 그렇게 가정하고 짜라

연차·휴일 정보 저장은 "일정 등록"이 아니다 — 정기·개별 일정 행은 하나도 만들어지지 않는다. 다만 **`hasCompletedPreSchedule`은 이 저장으로 `true`가 된다**(사전 신청일이 채워지므로). 2026-08-19 이전에 있던 `hasRegularSchedule`·`hasPreSchedule`은 삭제됐다.

- 저장 후 `GET /auth/me`를 다시 부를 필요 없다 (정기·개별 일정 저장과 다른 점).
- 이미 방에 참여 중인 사용자가 연차만 수정해도 방에서 튕기지 않는다.

이 성질에 기대어, 연차 저장 성공을 "일정 입력 완료"로 취급하지 마라 — 방 입장은 그 방의 일정 확인 플로우를 끝내고 `activate`/`join`을 호출했는지(`myMemberStatus`)로 판정된다.

## 화면 참고 — 「기본 정보 관리」 연차 3문항

| 화면 요소 | 데이터 |
|---|---|
| "연차를 며칠까지 쓸 수 있나요?" | `maxVacationDays` (0~10) |
| "연차는 언제까지 신청해야 하나요?" (상관없음 / 1주 전 / 2주 전 / 한 달 전) | `vacationApplyPeriod` — 각각 `ANY` / `ONE_WEEK_BEFORE` / `TWO_WEEKS_BEFORE` / `ONE_MONTH_BEFORE` |
| "반차를 쓸 수 있나요?" | `halfVacationAvailable` |
| "공휴일에 쉬나요?" | `holidayRest` |
| "저장" | `PATCH /users/schedule/vacation-policy` **1회** — 4개 전부 담아서. 정기 일정 저장과 별개 호출 |

⚠️ **`maxVacationDays` 입력 UI 상한 주의:** 현재 클라이언트 `ANNUAL_LEAVE_COUNT_VALUES`(`components/basic-info/basicInfo.const.ts`)는 0~30을 선택지로 제공하지만, 서버는 **0~10만 허용**한다(위 "API 명세" 400 케이스 참고). 11 이상을 선택해 저장하면 `400 INVALID_INPUT`이 난다. 이 상한이 서버 10으로 확정인지, 프론트 30으로 서버가 확장될지는 **기획 확인 중** — 답이 나올 때까지 UI를 임의로 고치지 마라.
