# 정기·개별 일정 병합 달력 — 구현 규칙

TripFit 프론트엔드 저장소에서 마이페이지 일정(정기·개별) 화면과 달력 조회 로직을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약은 추측해서 채우지 말고 사용자에게 확인하라.

Google Calendar 연동 병합·해제는 별도 문서 [`google-calendar-merge.md`](../user/google-calendar-merge.md)를 따르라 — 이 문서는 그 이전 단계인 정기(`regular`)·개별(`personal`) 자체의 병합·편집 규칙만 다룬다.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다. 아래 예시는 `data` 안쪽만 표기했으니, 실제 파싱 코드에는 한 단계 더 감싸야 한다.

## 규칙 1 — 캘린더 조회는 항상 "정기+개별을 합친 값"만 온다. 정기만/개별만 보여주는 조회는 없다

`GET /api/v1/users/schedule/calendar`, `GET /api/v1/trips/{tripId}/members/schedule-calendar` 두 API는 날짜마다 다음을 그대로 합쳐서 내려준다:

- 그 날짜에 개별(personal) 오버라이드가 **있으면** → 그 오전/오후/저녁 슬롯 + `uncertain`을 그대로 씀 (정기는 무시)
- 없으면 → 그 요일에 매칭되는 정기(regular)를 펼친 값 (정기가 여러 개 겹치면 슬롯별로 하나라도 `IMPOSSIBLE`이면 `IMPOSSIBLE`)
- 둘 다 없으면 → 그 날짜는 응답에서 아예 생략된다(sparse) — "미입력"으로 렌더링하라, "가능(POSSIBLE)"으로 표시하지 마라

**개별(personal)을 조회 전용으로 따로 보여주는 API는 없다** — `GET /users/schedule/personal` 같은 조회 API는 존재하지 않는다(제거됨). 개별 일정만 훑어보고 싶어도 위 두 캘린더 API를 써라. 반대로 "정기만 반영된 상태"를 보여주는 조회도 없다 — 캘린더는 언제나 병합 결과다.

## 규칙 2 — 날짜를 클릭하면 항상 "그 날짜 하나"의 개별 일정 편집이다. 건드린 필드 그룹만 보내라

마이페이지 달력에 찍히는 점(예: 빨간 점)이 정기에서 나온 값이든 이미 저장된 개별 오버라이드든 상관없다. **날짜를 클릭했을 때 항상 그 날짜 1건의 개별 일정 편집 바텀시트를 띄우고, 저장은 항상 `PATCH /api/v1/users/schedule/personal`로만 하라.** `PATCH /users/schedule/regular/{id}`를 여기서 호출하지 마라 — 그건 정기 패턴 자체(예: 매주 평일)를 바꾸는 API라서, 하루만 고치려는 의도와 다르게 그 패턴이 매칭되는 **모든 날짜**가 한꺼번에 바뀐다.

**핵심 계약(O1.4):** `items`의 한 항목(`scheduleDate` 1개)은 아래 두 필드 그룹을 **각각 독립적으로** 선택해서 보낸다. 그룹 전체를 생략하거나 그룹 전체를 채우거나 둘 중 하나만 가능하고, "일부만" 채우는 중간 형태는 없다.

- **`slots`** — 아침/오후/저녁 슬롯 오버라이드를 담는 객체. **키 자체를 생략**하면 슬롯을 안 건드림(정기+구글 계산값 유지). **보내면 `morningStatus`/`afternoonStatus`/`eveningStatus` 3개 전부 필수** — 하나라도 빠지면 `400`. "슬롯 하나만 `null`로 보내 그 슬롯만 복원" 같은 부분 편집은 없다.
- **`uncertain`** — 그 날짜 전체 불확실 여부(boolean). **키 자체를 생략**하면 안 건드림.

한 항목 안에 `slots`와 `uncertain`을 **동시에** 넣어도 된다 — 둘 다 바뀌었다고 항목을 2개로 쪼갤 필요 없다(오히려 같은 `scheduleDate`가 배열에 중복되면 `400`).

**요청(REQUEST)과 응답(RESPONSE)의 슬롯 모양이 다르다는 점에 주의:** 요청은 위처럼 `slots` 객체로 묶여 있지만, `PATCH` 응답과 `GET /calendar` 응답은 여전히 `morningStatus`/`afternoonStatus`/`eveningStatus`가 최상위 평면 필드다(규칙 3 표 참고) — 요청 보낼 때 쓴 모양을 그대로 응답 파싱에 재사용하면 안 된다.

### 구현 순서 (예: 매주 평일 9~18시 근무자가 특정 하루만 오후를 비움)

1. 달력에서 날짜를 클릭하면, 바텀시트를 `GET .../calendar` 응답의 그 날짜 값으로 프리필하라(화면에 현재 값을 보여주기 위함일 뿐, 저장 시 안 바꾼 필드 그룹까지 다시 보낼 필요는 없다).
2. 유저가 슬롯 일부(예: 오후)만 바꾼다.
3. 저장 시 **그 날짜 하나에 대해서만** `PATCH .../personal`을 호출한다. 슬롯을 하나라도 바꿨다면 `slots`에 **화면에 지금 표시된 아침/오후/저녁 3개를 전부** 채워 보낸다. `uncertain`을 이번에 안 건드렸다면 `uncertain` 키 자체를 생략한다.

```json
{
  "items": [
    { "scheduleDate": "2026-06-19", "slots": { "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "IMPOSSIBLE" } }
  ]
}
```

4. 저장 후에는 그 날짜의 오후만 (의미상) 바뀐다 — 아침·저녁은 정기 계산값을 그대로 재전송했을 뿐이고, 같은 요일의 다른 날짜(정기 패턴)는 그대로다.

### 아래 상황을 가정하고 UI를 짜라

- 이미 개별 오버라이드가 있는 날짜를 다시 클릭해도 프리필 소스는 동일하게 `GET .../calendar` 값이다 — 별도 분기 없이 항상 같은 흐름으로 열어라.
- **⚠️ 개별 오버라이드를 삭제·초기화하는 방법은 없다.** `slots`에 어떤 값 조합을 보내도(정기 계산값과 우연히 같은 값이어도) 그 날짜의 개별 일정 row는 **절대 삭제되지 않는다** — 삭제 코드 경로 자체가 없다. "슬롯을 이렇게 저렇게 보내면 초기화된다"는 예전 가정을 하지 마라. 한 번 저장한 날짜는 이후 정기 패턴이 바뀌어도 그 값 그대로 고정되며, 되돌리려면 유저가 그 날짜를 다시 열어 원하는 값으로 재저장하는 수밖에 없다("기본값으로 되돌리기" 전용 버튼도 없다).
- 정기가 아예 없는 날짜(주말 등, 캘린더에서 생략된 날)에도 개별 일정만 단독으로 등록할 수 있다 — 정기 일정이 하나도 없어도 막히지 않는다.
- 슬롯 오버라이드를 하나도 안 걸고 `uncertain`만(`slots` 키 생략) 보낼 수도 있다 — "이 날 일정이 바뀔 수도 있어요"만 표시하고 슬롯은 정기+구글 계산값 그대로 둔다. `uncertain`이 켜져 있는 동안에도 이미 저장된 슬롯 오버라이드는 그대로 보존되고, 다시 끄면 그대로 다시 노출된다(초기화되지 않는다).
- `PATCH .../personal`의 `items`는 여러 날짜를 한 번에 보낼 수 있지만(bulk), 단일 날짜 편집 화면에서는 그 날짜 1건만 담아 보내라 — 다른 날짜까지 같이 보내면 그 날짜들도 함께 덮어써진다. **같은 `scheduleDate`를 배열에 중복으로 넣으면 `400`이다** — `slots`용 항목과 `uncertain`용 항목을 따로 만들어 같은 날짜로 2개 보내지 마라, 한 항목에 같이 담아라.
- **⚠️ 저장 가능한 날짜에 구간 제한이 생겼다 (2026-09-13).** `scheduleDate`는 달력 조회와 **같은 구간** 안이어야 한다 — `오늘 ~ 오늘+2년−1`, 단 참여 중인 진행 여행방의 희망 기간 종료일이 그보다 뒤면 그 날짜까지. 지난 날짜이거나 상한을 넘으면 `400 INVALID_INPUT`이고, `items` 중 **하나라도** 구간 밖이면 요청 전체가 거부된다(부분 저장 없음). 예전에는 저장은 되고 조회만 막혀서, 저장했는데 달력에서 다시 볼 수 없는 일정이 생길 수 있었다. 달력 UI에서 고를 수 없는 날짜를 직접 만들어 보내지 않는 한 이 오류를 볼 일은 없다.

## 규칙 3 — `PATCH .../personal` 응답과 `GET .../calendar` 응답을 같은 타입으로 파싱하지 마라

둘 다 "날짜 + 슬롯 3개 + uncertain"을 담지만 DTO 모양이 다르다.

| | `PATCH .../personal` 응답 | `GET .../calendar` 응답 |
|---|---|---|
| 최상위 | `{ "items": [...] }` | `{ "startDate", "endDate", "days": [...] }` |
| 날짜 필드명 | `scheduleDate` | `date` |
| `id` | **항상 있음(non-null)** — `items`에 담기는 날짜는 이번 호출로 upsert된 날짜뿐이라 개별 일정 row가 항상 존재한다(슬롯이 정기값과 우연히 같아도 row 자체는 남는다) | **없음** — 그 값이 정기 유래일 수 있어 단일 row가 없다 |
| 슬롯 값의 의미 | 항상 **최종 확정값**(POSSIBLE/IMPOSSIBLE로 확정) — 저장된 원본(`null` 포함)이 아니다 | 동일 |
| 슬롯 필드명 | `morningStatus`/`afternoonStatus`/`eveningStatus`/`uncertain` | 동일 |

`scheduleDate`와 `date`를 같은 키로 매핑하면 날짜가 `undefined`가 되니 주의하라.

## 규칙 4 — 정기 일정 API 전체 명세

> **이 절은 배포된 Swagger를 대체한다.** 백엔드가 이미 배포된 앱과의 계약을 지키기 위해 이 계약 변경은 배포 전에는 라이브 Swagger로 확인할 수 없다 — 아래가 필드 타입·제약·기본값·에러 케이스까지 Swagger와 동등한 정보다. 배포 후에는 `/swagger-ui/index.html`로 교차 확인해도 된다.

**⚠️ 연차 필드는 정기 일정 API에서 빠졌다.** `maxVacationDays`·`vacationApplyPeriod`·`halfVacationAvailable`·`holidayRest`는 정기 일정 행이 아니라 사용자에게 하나씩 붙는 값으로 옮겨졌고, 전용 API로만 읽고 쓴다. **정기 일정 요청에 계속 실어 보내면 에러 없이 무시된다**(저장된 줄 알기 쉬우니 주의) — 마이그레이션 대상 파일 목록을 포함한 상세는 [`vacation-policy.md`](vacation-policy.md)를 따르라.

모든 요청·응답 예시는 `data` 포함 실제 envelope 그대로다.

### `GET /api/v1/users/schedule/regular` — 정기 일정 목록

| | |
|---|---|
| 인증 | Bearer JWT 필수 |
| Request Body | 없음 |
| 성공 응답 | `200 OK` — 생성 시각(`createdAt`) 오름차순 |

**응답 스키마 (`data.items[]`)**

| 필드 | 타입 | nullable | 설명 |
|---|---|---|---|
| `id` | `string` (UUID) | ✗ | 정기 일정 ID |
| `title` | `string` | ✗ | 표시명 (출근·수업·회의 등) |
| `daysOfWeek` | `string` | ✓ | 반복 요일. `MON,TUE,...` 콤마 CSV. 미설정 시 `null` |
| `startTime` | `string` (`HH:mm:ss`) | ✓ | 시작 시각 |
| `endTime` | `string` (`HH:mm:ss`) | ✓ | 종료 시각 |
| `morningStatus` | `string` (`POSSIBLE`\|`IMPOSSIBLE`) | ✓ | 오전 `[00:00,13:00)` 슬롯 — `startTime`/`endTime`에서 서버가 파생 계산 |
| `afternoonStatus` | 동일 | ✓ | 오후 `[13:00,18:00)` |
| `eveningStatus` | 동일 | ✓ | 저녁 `[18:00,24:00)` |

```json
{
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "title": "출근",
        "daysOfWeek": "MON,TUE,WED,THU,FRI",
        "startTime": "09:00:00",
        "endTime": "18:00:00",
        "morningStatus": "IMPOSSIBLE",
        "afternoonStatus": "IMPOSSIBLE",
        "eveningStatus": "POSSIBLE"
      }
    ]
  }
}
```

에러: `401 AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` 뿐.

---

### `POST /api/v1/users/schedule/regular` — 정기 일정 생성

| | |
|---|---|
| 인증 | Bearer JWT 필수 |
| 성공 응답 | `201 Created` |
| 부수효과 | 첫 정기 일정 생성 시 `hasRegularSchedule`·`hasPreSchedule`이 `true`가 됨 — `GET /auth/me` 등 재조회 필요 |

**요청 스키마**

| 필드 | 타입 | 필수 | 제약 | 비고 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 공백만으로 안 됨(`@NotBlank`) | 예: `"출근"` |
| `daysOfWeek` | `string` | ❌ | `MON`~`SUN` 콤마 CSV. 대소문자·중복·순서 무관(서버가 정규화). 알 수 없는 토큰이면 `400` | 생략하면 `null` — "요일 무관, 시간만" 패턴 |
| `startTime` | `string` (`HH:mm:ss`) | ✅ | `@NotNull` | |
| `endTime` | `string` (`HH:mm:ss`) | ✅ | `@NotNull`, **`endTime`이 `startTime`보다 뒤여야 함**(같거나 앞이면 `400`) | 자정 넘김(예: 22:00~02:00) 미지원 |

응답 스키마·예시는 위 `GET` 목록의 `items[]` 항목 1개와 동일 모양(`data`가 배열이 아니라 객체 1개).

```json
// Request
{ "title": "출근", "daysOfWeek": "MON,TUE,WED,THU,FRI", "startTime": "09:00:00", "endTime": "18:00:00" }
```
```json
// 201
{
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "출근",
    "daysOfWeek": "MON,TUE,WED,THU,FRI",
    "startTime": "09:00:00",
    "endTime": "18:00:00",
    "morningStatus": "IMPOSSIBLE",
    "afternoonStatus": "IMPOSSIBLE",
    "eveningStatus": "POSSIBLE"
  }
}
```

**에러 (실제 서버 응답 그대로 캡처)**

| HTTP | code | 발생 조건 | 응답 예시 |
|---|---|---|---|
| 400 | `INVALID_INPUT` | `title` 공백/누락 | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다.","errors":[{"field":"title","message":"must not be blank"}]}` |
| 400 | `INVALID_INPUT` | `startTime`/`endTime` 누락 | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다.","errors":[{"field":"startTime","message":"must not be null"}]}` (필드명은 누락된 쪽) |
| 400 | `INVALID_INPUT` | `endTime` ≤ `startTime` | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다."}` (⚠️ `errors` 배열 없음 — 서비스 레벨 검증이라 필드 정보가 안 실린다) |
| 400 | `INVALID_INPUT` | `daysOfWeek`에 `MON`~`SUN`(또는 `MONDAY` 등 영문 전체 표기) 외 토큰 | `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다."}` (`errors` 없음) |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` | 위와 동일 | 위와 동일 |

⚠️ `errors` 배열은 **Bean Validation(`@NotBlank`/`@NotNull`) 실패에만** 붙는다. 서비스 레벨 검증(`endTime`≤`startTime`, 잘못된 요일 토큰)은 `errors` 없이 최상위 `message`만 온다 — 폼 에러 표시 로직이 이 차이를 처리하게 짜라 (필드별 인라인 에러가 필요하면 `errors` 유무로 분기).

---

### `PATCH /api/v1/users/schedule/regular/{id}` — 정기 일정 전체 수정

요청 스키마·검증 규칙은 `POST`와 **완전히 동일**(4개 필드 전부 다시 보내야 함 — 부분 수정 아님). 성공 응답은 `200 OK` + 동일 모양.

**⚠️ 하루만 바꾸는 용도가 아니다.** `daysOfWeek`가 매칭되는 **모든 날짜**가 한꺼번에 바뀐다 — 특정 하루만 고치려면 `PATCH /users/schedule/personal`(규칙 2)을 써라.

**에러:** `POST`의 400 표에 더해 아래가 추가된다.

| HTTP | code | 발생 조건 | 응답 예시 |
|---|---|---|---|
| 404 | `REGULAR_SCHEDULE_NOT_FOUND` | `{id}`가 존재하지 않거나 본인 소유가 아님 | `{"code":"REGULAR_SCHEDULE_NOT_FOUND","message":"정기 일정을 찾을 수 없습니다."}` |

---

### `DELETE /api/v1/users/schedule/regular/{id}` — 정기 일정 삭제

| | |
|---|---|
| 인증 | Bearer JWT 필수 |
| Request Body | 없음 |
| 성공 응답 | `204 No Content` (body 없음) |
| 부수효과 | 정기가 0건이 되면 `hasRegularSchedule`이, 정기·개별이 모두 0건이 되면 `hasPreSchedule`까지 `false`가 됨 — `GET /auth/me` 재조회 필요 |

**에러**

| HTTP | code | 발생 조건 |
|---|---|---|
| 404 | `REGULAR_SCHEDULE_NOT_FOUND` | `{id}`가 존재하지 않거나 본인 소유가 아님 |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` | 토큰 없음·무효·만료 |

---

### 관련 API 요약표

| Method | Path | 역할 |
|---|---|---|
| `GET/POST` | `/api/v1/users/schedule/regular` | 정기 패턴 목록/생성 — 위 전체 명세 참고 |
| `PATCH/DELETE` | `/api/v1/users/schedule/regular/{id}` | 정기 패턴 전체 수정/삭제 — **하루만 바꾸는 용도 아님** |
| `GET/PATCH` | `/api/v1/users/schedule/vacation-policy` | 연차·반차·공휴일 휴무 4개 값(사용자당 1개) — [`vacation-policy.md`](vacation-policy.md) |
| `PATCH` | `/api/v1/users/schedule/personal` | 날짜별 개별 일정 upsert(조회 없음, 이 응답이 곧 조회 결과) — **날짜 하나만 고칠 때 이 API** |
| `GET` | `/api/v1/users/schedule/calendar?startDate=&endDate=` | 본인 정기+개별 합친 달력 (조회 구간: 오늘~오늘+2년-1, 단 참여 중인 ONGOING 여행 희망 기간 종료일이 그보다 뒤면 그 날짜까지 허용) |
| `GET` | `/api/v1/trips/{tripId}/members/schedule-calendar` | 여행방 멤버 전원의 정기+개별 합친 달력 (조회 구간: 여행 희망 기간) |

에러 코드를 처리할 때 아래 표에 없는 코드가 오면 임의로 의미를 추측하지 말고 사용자에게 물어라.

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | `personal` upsert의 `items` 비어 있음·`scheduleDate` 누락·한 항목에 `slots`도 `uncertain`도 없음·`slots`를 보냈는데 3필드 중 일부 누락·슬롯 값이 `POSSIBLE`/`IMPOSSIBLE` 외의 값·같은 `scheduleDate`가 `items`에 중복, `regular` 시각/요일 값 오류(위 전체 명세), `calendar` 조회 구간이 허용 윈도우 밖 |
| 404 | `REGULAR_SCHEDULE_NOT_FOUND` | 존재하지 않거나 본인 소유가 아닌 정기 일정 ID로 수정/삭제 시도 |
| 401 | `AUTH_INVALID_TOKEN` / `AUTH_EXPIRED` | 토큰 없음·무효·만료 — 재로그인 플로우로 보내라 |

## 규칙 5 — 여행 칩은 `/calendar` 파라미터가 아니라 `GET /trips?scope=ongoing`으로 만들어라

마이페이지 화면 상단의 여행 칩(제주도 여행 / 나트랑 여행 / 전주 여행)을 `/calendar`에 트립 필터 파라미터를 넘겨서 만들려고 하지 마라 — 그런 파라미터는 없다. 대신:

- `GET /api/v1/trips?scope=ongoing` 응답의 각 항목(`TripHomeCardResponse`)에 `name`·`startRange`·`endRange`가 이미 들어 있다. 화면 진입 시 이 API를 1회 호출해 칩을 그리고, 각 칩에 `tripId`·`name`·`startRange`·`endRange`를 들고 있어라.
- 칩을 탭하면, 그 트립의 `startRange`를 `/calendar`의 `startDate`에, `endRange`를 `endDate`에 그대로 넣어서 호출하라 — 서버에 `tripId`를 넘기는 파라미터는 없다. 이미 1)에서 받은 값을 그대로 재사용하면 된다.
- 칩을 하나도 선택하지 않은 기본 진입(이번 달 보기, 달력 스크롤)에는 트립과 무관하게 원하는 구간을 그대로 써라.
- "방 멤버 전원 달력"이 필요한 화면(다른 화면)이라면 `GET /trips/{tripId}/members/schedule-calendar`를 써라 — 이건 트립 하나로 범위가 `startRange`~`endRange`에 고정된 완전히 다른 API다. `/users/schedule/calendar`(본인 달력, 트립 무관)와 혼동하지 마라.

## 화면 참고 — 마이페이지 「내 일정 입력하기」

| 화면 요소 | 데이터 출처 |
|---|---|
| 상단 여행 칩(제주도 여행 / 나트랑 여행 / 전주 여행) | `GET /trips?scope=ongoing`의 `name`. 선택된 칩의 `startRange`~`endRange`를 아래 달력 조회 구간으로 사용(규칙 5) |
| 달력에서 날짜 아래 점 표시 | `GET /calendar`의 `days[]`에 그 날짜가 있고 슬롯 중 하나라도 `IMPOSSIBLE`이면 표시. 정기 유래인지 개별 유래인지는 구분하지 않는다(합친 값 하나로만 판단). 응답에 없는 날짜(sparse)는 점 없음 |
| 날짜 클릭 → 바텀시트 헤더 "YYYY년 M월 D일" | 클릭한 날짜(정기 유래 날짜를 클릭해도 항상 개별 편집 바텀시트 — 규칙 2) |
| "이 날 일정이 변경될 수 있어요" 토글 | 그 날짜의 `uncertain` |
| 아침/오후/저녁 각 행 버튼("여행 가능해요" ↔ "일정이 있어요") | 각각 `morningStatus`/`afternoonStatus`/`eveningStatus` — "여행 가능해요"=`POSSIBLE`, "일정이 있어요"=`IMPOSSIBLE`. 바텀시트를 열 때는 이 3개+토글을 `GET .../calendar`의 그 날짜 값으로 프리필하라(규칙 2) |
| "저장하기" 버튼 | `PATCH /users/schedule/personal`을 그 날짜 1건만 담아 호출 — 슬롯을 바꿨다면 `slots`에 3개 전부(화면 표시값 그대로) 채우고, 안 바꿨다면 `slots` 키 자체를 생략한다. `uncertain`도 마찬가지로 안 바꿨으면 키를 생략한다. 정기 일정은 절대 수정하지 않는다(규칙 2) |
