# 정기·개별 일정 병합 달력 — 구현 규칙

TripFit 프론트엔드 저장소에서 마이페이지 일정(정기·개별) 화면과 달력 조회 로직을 구현·수정할 때 아래 규칙을 따르라. 여기 없는 세부 계약은 추측해서 채우지 말고 사용자에게 확인하라.

Google Calendar 연동 병합·해제는 별도 문서 [`google-calendar-merge.md`](google-calendar-merge.md)를 따르라 — 이 문서는 그 이전 단계인 정기(`regular`)·개별(`personal`) 자체의 병합·편집 규칙만 다룬다.

모든 API 응답은 `{ "data": {...}, "message": "...", "code": "..." }` envelope로 온다. 아래 예시는 `data` 안쪽만 표기했으니, 실제 파싱 코드에는 한 단계 더 감싸야 한다.

## 규칙 1 — 캘린더 조회는 항상 "정기+개별을 합친 값"만 온다. 정기만/개별만 보여주는 조회는 없다

`GET /api/v1/users/schedule/calendar`, `GET /api/v1/trips/{tripId}/members/schedule-calendar` 두 API는 날짜마다 다음을 그대로 합쳐서 내려준다:

- 그 날짜에 개별(personal) 오버라이드가 **있으면** → 그 오전/오후/저녁 슬롯 + `uncertain`을 그대로 씀 (정기는 무시)
- 없으면 → 그 요일에 매칭되는 정기(regular)를 펼친 값 (정기가 여러 개 겹치면 슬롯별로 하나라도 `IMPOSSIBLE`이면 `IMPOSSIBLE`)
- 둘 다 없으면 → 그 날짜는 응답에서 아예 생략된다(sparse) — "미입력"으로 렌더링하라, "가능(POSSIBLE)"으로 표시하지 마라

**개별(personal)을 조회 전용으로 따로 보여주는 API는 없다** — `GET /users/schedule/personal` 같은 조회 API는 존재하지 않는다(제거됨). 개별 일정만 훑어보고 싶어도 위 두 캘린더 API를 써라. 반대로 "정기만 반영된 상태"를 보여주는 조회도 없다 — 캘린더는 언제나 병합 결과다.

## 규칙 2 — 날짜를 클릭하면 항상 "그 날짜 하나"의 개별 일정 편집이다. 건드린 슬롯만 보내라

마이페이지 달력에 찍히는 점(예: 빨간 점)이 정기에서 나온 값이든 이미 저장된 개별 오버라이드든 상관없다. **날짜를 클릭했을 때 항상 그 날짜 1건의 개별 일정 편집 바텀시트를 띄우고, 저장은 항상 `PATCH /api/v1/users/schedule/personal`로만 하라.** `PATCH /users/schedule/regular/{id}`를 여기서 호출하지 마라 — 그건 정기 패턴 자체(예: 매주 평일)를 바꾸는 API라서, 하루만 고치려는 의도와 다르게 그 패턴이 매칭되는 **모든 날짜**가 한꺼번에 바뀐다.

**핵심 변경(O1):** `morningStatus`/`afternoonStatus`/`eveningStatus`는 이제 **각각 선택(nullable) 필드**다. 유저가 건드리지 않은 슬롯은 `null`로 보내라 — 서버가 그 슬롯을 "오버라이드 없음"으로 이해하고 정기+구글 계산값을 그대로 쓴다. **슬롯 3개를 항상 다 채워 보내야 한다는 예전 규칙은 폐기됐다.**

### 구현 순서 (예: 매주 평일 9~18시 근무자가 특정 하루만 오후를 비움)

1. 달력에서 날짜를 클릭하면, 바텀시트를 `GET .../calendar` 응답의 그 날짜 값으로 프리필하라(화면에 현재 값을 보여주기 위함일 뿐, 저장 시 안 바꾼 슬롯까지 다시 보낼 필요는 없다).
2. 유저가 슬롯 일부(예: 오후)만 바꾼다.
3. 저장 시 **그 날짜 하나에 대해서만** `PATCH .../personal`을 호출한다. **유저가 실제로 바꾼 슬롯만 값을 채우고, 나머지는 `null`로 보내라.**

```json
{
  "items": [
    { "scheduleDate": "2026-06-19", "morningStatus": null, "afternoonStatus": "POSSIBLE", "eveningStatus": null, "uncertain": false }
  ]
}
```

4. 저장 후에는 그 날짜의 오후만 바뀐다 — 아침·저녁은 정기+구글 계산값을 계속 따르고, 같은 요일의 다른 날짜(정기 패턴)도 그대로다.

### 아래 상황을 가정하고 UI를 짜라

- 이미 개별 오버라이드가 있는 날짜를 다시 클릭해도 프리필 소스는 동일하게 `GET .../calendar` 값이다 — 별도 분기 없이 항상 같은 흐름으로 열어라.
- 유저가 바텀시트에서 슬롯 3개를 **전부 안 건드린 상태(모두 `null`)로 `uncertain=false`** 저장하면, 서버는 이를 "오버라이드 없음" 상태로 보고 그 날짜의 개별 일정 row를 **삭제**한다 — 이후 그 날짜는 다시 정기+구글 계산값을 따른다. 다만 제품에 "기본값으로 되돌리기" 전용 버튼은 없다 — 이건 어디까지나 CLEAR 조건의 부수 효과다.
- 정기가 아예 없는 날짜(주말 등, 캘린더에서 생략된 날)에도 개별 일정만 단독으로 등록할 수 있다 — 정기 일정이 하나도 없어도 막히지 않는다.
- 슬롯 오버라이드를 하나도 안 걸고 `uncertain=true`만 보낼 수도 있다 — "이 날 일정이 바뀔 수도 있어요"만 표시하고 슬롯은 정기+구글 계산값 그대로 둔다.
- `PATCH .../personal`의 `items`는 여러 날짜를 한 번에 보낼 수 있지만(bulk), 단일 날짜 편집 화면에서는 그 날짜 1건만 담아 보내라 — 다른 날짜까지 같이 보내면 그 날짜들도 함께 덮어써진다.

## 규칙 3 — `PATCH .../personal` 응답과 `GET .../calendar` 응답을 같은 타입으로 파싱하지 마라

둘 다 "날짜 + 슬롯 3개 + uncertain"을 담지만 DTO 모양이 다르다.

| | `PATCH .../personal` 응답 | `GET .../calendar` 응답 |
|---|---|---|
| 최상위 | `{ "items": [...] }` | `{ "startDate", "endDate", "days": [...] }` |
| 날짜 필드명 | `scheduleDate` | `date` |
| `id` | 있음 — 단, 그 날짜에 오버라이드가 하나도 안 남아 있으면(정기+구글 값만 내려가는 날짜) `null`일 수 있다 | **없음** — 그 값이 정기 유래일 수 있어 단일 row가 없다 |
| 슬롯 값의 의미 | 항상 **최종 확정값**(POSSIBLE/IMPOSSIBLE로 확정) — 저장된 원본(`null` 포함)이 아니다 | 동일 |
| 슬롯 필드명 | `morningStatus`/`afternoonStatus`/`eveningStatus`/`uncertain` | 동일 |

`scheduleDate`와 `date`를 같은 키로 매핑하면 날짜가 `undefined`가 되니 주의하라.

## 규칙 4 — 관련 API는 아래 표만 사용하라

| Method | Path | 역할 |
|---|---|---|
| `GET/POST` | `/api/v1/users/schedule/regular` | 정기 패턴 목록/생성 |
| `PATCH/DELETE` | `/api/v1/users/schedule/regular/{id}` | 정기 패턴 전체 수정/삭제 — **하루만 바꾸는 용도 아님** |
| `PATCH` | `/api/v1/users/schedule/personal` | 날짜별 개별 일정 upsert(조회 없음, 이 응답이 곧 조회 결과) — **날짜 하나만 고칠 때 이 API** |
| `GET` | `/api/v1/users/schedule/calendar?startDate=&endDate=` | 본인 정기+개별 합친 달력 (조회 구간: 오늘~오늘+2년-1, 단 참여 중인 ONGOING 여행 희망 기간 종료일이 그보다 뒤면 그 날짜까지 허용) |
| `GET` | `/api/v1/trips/{tripId}/members/schedule-calendar` | 여행방 멤버 전원의 정기+개별 합친 달력 (조회 구간: 여행 희망 기간) |

에러 코드를 처리할 때 아래 표에 없는 코드가 오면 임의로 의미를 추측하지 말고 사용자에게 물어라.

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | `personal` upsert의 `items` 비어 있음·`scheduleDate` 누락·슬롯 값이 `POSSIBLE`/`IMPOSSIBLE`/`null` 외의 값, `regular` 시각/요일 값 오류, `calendar` 조회 구간이 허용 윈도우 밖 |
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
| "저장하기" 버튼 | `PATCH /users/schedule/personal`을 그 날짜 1건만 담아 호출 — 유저가 바꾼 슬롯만 값을 채우고 안 바꾼 슬롯은 `null`로 보낸다. 정기 일정은 절대 수정하지 않는다(규칙 2) |
