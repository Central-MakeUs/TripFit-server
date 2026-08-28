# 일정 달력 통합 조회 (Resolved Calendar)

> wave: 2  
> implements: BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-USER-006, BR-USER-008  
> related: [`schedule-unified.md`](schedule-unified.md), [`trip-room-api.md`](../trip/trip-room-api.md), [`trip-recommendation.md`](../trip/trip-recommendation.md)  
> deferred: sparse day 의미(가능 vs 미입력) → **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** · **A1 → 마이페이지 today+2년** [`trip-schedule-calendar-window.md`](../trip/trip-schedule-calendar-window.md) (**[#37](https://github.com/Central-MakeUs/TripFit-server/issues/37)** C1) · **CONFIRMED/EXPIRED snapshot** [`trip-schedule-snapshot.md`](../trip/trip-schedule-snapshot.md) (**[#38](https://github.com/Central-MakeUs/TripFit-server/issues/38)**)  
> 상태: **Implemented** (#17) — S1·R2=A · sparse. **A1:** #37에서 today~+2년 윈도우로 amend (**Implemented** on feat/37)  
> MVP: In scope (일정 응답·추천 입력 데이터) / 그룹 달력 UX는 wave 3

> ⚠️ **개별 일정 병합 규칙 S1은 폐기되었다.** [`schedule-slot-override.md`](schedule-slot-override.md)(O1, #67, **Approved**)가 "개별 행이 있으면 그 날 전체 대체"(S1)를 "슬롯 단위 오버라이드"로 대체한다 — 아래 R1·S1 관련 서술은 **이력 문서**로만 남기고, 현재 병합 계약은 O1 스펙을 SSOT로 본다. R2(정기 복수 겹침)·R3(uncertain)·R4(레이어 없음)·A1(기간 상한)·sparse 원칙은 O1에서도 그대로 유지된다.

## 목표

클라이언트가 **기간 내 날짜×오전/오후/저녁**을 달력·일정 시트·추천에 바로 쓸 수 있도록,  
`regular_schedule`(패턴)과 `personal_schedule`(날짜 예외)를 서버에서 **펼치고 합친(정기+개별) 조회 API**를 정의한다.

---

## 확정 사항 (2026-07-14)

| # | 항목 | 결정 |
|---|------|------|
| 1 | 병합 모델 | ~~**S1** — personal 행이 있으면 그날 슬롯 3개 + `uncertain` **전부**가 합친 값이 된다~~ **폐기 → O1로 대체** ([`schedule-slot-override.md`](schedule-slot-override.md)): 슬롯 단위로 개별 오버라이드(있으면) > 정기⊕구글 |
| 2 | personal 쓰기 계약 | ~~추가/수정 시 오전·오후·저녁 3필드 필수~~ **폐기** — 슬롯 3필드는 nullable(`null`=오버라이드 없음), O1 참고 |
| 3 | empty day | **sparse** — regular·personal 모두 없으면 `days`에서 **omit** |
| 4 | 응답 깊이 | 본인·그룹 **모두 정기+개별 합친 값만** (원본 레이어 없음, 납작한 day) |
| 5 | 용어 | 합친 결과 = **정기+개별 합친 값**(= "합친 달력"의 하루치). 중첩 wrapper 없이 day에 슬롯 필드 그대로 노출 |
| 6 | 동일 요일 regular 복수 | **R2=A** — 슬롯별 **IMPOSSIBLE 우선** |
| 7 | calendar 기간 상한 **A1** | **현행 (#37 C1):** 요청 구간 ⊆ **`today`~`today+2y−1`** · today 이전 포함 시 400. (구: 길이 ≤730일 — 폐기). 여행방 기간은 희망 기간(#37 C2) — 본 A1과 무관 |

### S1이란 (클라이언트 관점)

```text
PATCH /users/schedule/personal  시
  morningStatus, afternoonStatus, eveningStatus  → 전부 필수 (@NotNull)
  “오후만 연차”도 오전·저녁 값을 클라이언트가 채워 보냄
  (보통 오전·저녁은 regular와 같은 값으로 복사해 저장)

GET /users/schedule/calendar  시
  그 날짜에 personal 행이 있으면 → 합친 값 = 그 행의 3슬롯 + uncertain
  없으면 → regular를 요일에 맞게 펼친 값
  둘 다 없으면 → 응답에 날짜 없음
```

**S2(슬롯 null = regular 유지)는 채택하지 않음.** DB·API 변경 없음.

### 납작한 합친 day 예시

```json
{
  "date": "2026-08-05",
  "morningStatus": "POSSIBLE",
  "afternoonStatus": "POSSIBLE",
  "eveningStatus": "POSSIBLE",
  "uncertain": false
}
```

문서에서 “정기+개별 합친 값”은 위 필드들의 **의미**(합친 결과)를 가리키는 표현일 뿐,  
응답에 별도 이름의 중첩 객체(예: `"merged": { ... }`)를 두지 않는다 — day에 슬롯 필드가 그대로 노출된다.

---

## 배경 — 무엇이 문제인가

### 현재 데이터 모델 (이미 구현됨)

| 테이블 | 의미 | 저장 단위 |
|--------|------|-----------|
| `regular_schedule` | 출근·수업 등 **반복 패턴** | user당 N행. 요일 + 시각→슬롯 |
| `personal_schedule` | **특정 날짜** 가능/불가 | `(user_id, schedule_date)` 1행, 슬롯 3개 필수 |

- 일정은 **User 전역** (trip FK 없음, BR-USER-008).
- 슬롯: `POSSIBLE` \| `IMPOSSIBLE`. `uncertain`은 날짜 단위.

상세: [`schedule-unified.md`](schedule-unified.md), [`erd.md`](../../architecture/erd.md).

### 현재 조회 API의 한계

| API | 반환 | 달력에 바로 쓰나? |
|-----|------|------------------|
| `GET .../regular` | 규칙 목록 | ❌ |
| `GET .../personal?start&end` | **저장된 personal만** | ❌ 정기만 있는 날 누락 |
| ~~`GET .../trips/{id}/members/personal-summary`~~ | ~~멤버 personal만~~ | **삭제** — `members/schedule-calendar` 사용 |

**해결:** 서버가 기간 → 날짜별 **정기+개별 합친 값**을 한곳에서 계산하는 calendar API.

### 역할 분리

```
[저장 · 편집 SSOT]
  regular / personal CRUD  ← 변경 없음 (S1 쓰기 계약 유지)

[읽기 전용]
  calendar                 ← expand(regular) ⊕ S1 overlay(personal)
```

---

## 병합 규칙

### R1 — S1 (확정)

날짜 `date`마다:

1. `(user_id, date)` **personal 행 있음**  
   → 합친 값 = personal의 `morning` / `afternoon` / `evening` / `uncertain` **전부**  
2. personal 없고, 요일에 매칭되는 **regular ≥1**  
   → 합친 값 = regular 슬롯 합성 (R2) + `uncertain=false`  
3. 둘 다 없음  
   → **omit** (sparse)

### R2 — 같은 요일 regular 복수 = **A 확정** (슬롯별 IMPOSSIBLE 우선)

같은 요일에 regular가 2행 이상이면, 슬롯마다:

```text
하나라도 IMPOSSIBLE → IMPOSSIBLE
그 외 (모두 POSSIBLE 또는 유효 상태만 POSSIBLE) → POSSIBLE
null(미설정) 슬롯 → 합성에서 무시 (의견 없음). 해당 슬롯에 유효값이 하나도 없으면 POSSIBLE로 두지 않고 null 유지 후, 세 슬롯 모두 null이면 그날 omit 후보 — 정상 생성 경로에서는 슬롯이 채워짐
```

예: 출근(수) I/I/P + 수업(수) P/P/I → 수요일 합친 값 **I/I/I**.

폐기안: B(최신 1행만), C(요일 겹침 저장 금지).

### R3 — `uncertain` 표시 (가정 U1, 추천은 #13)

- 달력 합친 값: personal이 있으면 `uncertain` 그대로, regular만이면 `false`  
- 슬롯 status는 가리지 않음 (U1). 추천 가중치는 별도 스펙.

### R4 — 레이어 (확정: 없음)

본인·그룹 calendar 모두 **정기+개별 합친 값(납작한 day)만**.  
`fromRegular` / `fromPersonal` / 중첩 `merged` 객체 **Out of Scope**.

---

## R2=A 예시 (regular 복수)

| 정기 | 수요일 슬롯 |
|------|-------------|
| 출근 | I / I / P |
| 저녁 수업 | P / P / I |

→ calendar 수요일 합친 값 = **I / I / I** (슬롯별 불가 우선).

같은 수요일에 personal이 있으면 **S1이 먼저** → A 결과는 그날 사용하지 않음.

## 예시 (S1 + sparse)

**DB**

- Regular: 월~금, 오전·오후 IMPOSSIBLE, 저녁 POSSIBLE  
- Personal 8/5 (화, “오후만 연차”를 S1로 저장):  
  오전 IMPOSSIBLE, 오후 POSSIBLE, 저녁 POSSIBLE, uncertain=false  
  ← 클라이언트가 오전·저녁을 regular와 같게 **채워서** 보냄

**`GET .../calendar?startDate=2026-08-01&endDate=2026-08-07`**

| date | 합친 값 | 비고 |
|------|-----------|------|
| 08-01 토 | omit | |
| 08-02 일 | omit | |
| 08-03 월 | I / I / P | regular |
| 08-04 화 | I / I / P | regular |
| 08-05 화 | I / P / P | **personal 통째로** |
| 08-06 수 | I / I / P | regular |
| 08-07 목 | I / I / P | regular |

---

## API / 인터페이스

### 위치

| | ① 본인 calendar | ② 여행방 멤버 calendar |
|--|-----------------|------------------------|
| Path | `GET /api/v1/users/schedule/calendar` | `GET /api/v1/trips/{tripId}/members/schedule-calendar` |
| Controller | `UserScheduleController` | `TripMemberController` |
| 기간 | `startDate`·`endDate` query | `trip.startRange`~`endRange` |
| 권한 | JWT 본인 | JWT + trip 멤버 |
| 응답 | `days[]` (정기+개별 합친 값) | `members[]` → `days[]` (정기+개별 합친 값만) |

공통 resolve: `user/schedule/service`.

### ① 본인 calendar

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/users/schedule/calendar` | JWT |

**Query:** `startDate`, `endDate` (ISO date, 필수). `end < start` → 400.  
**기간 윈도우 (#37 C1):** 요청 구간 ⊆ `[today, today+2년−1]`. today 이전 포함·윈도우 밖 → 400 `INVALID_INPUT`.

**응답**

```json
{
  "data": {
    "startDate": "2026-08-01",
    "endDate": "2026-08-07",
    "days": [
      {
        "date": "2026-08-03",
        "morningStatus": "IMPOSSIBLE",
        "afternoonStatus": "IMPOSSIBLE",
        "eveningStatus": "POSSIBLE",
        "uncertain": false
      },
      {
        "date": "2026-08-05",
        "morningStatus": "IMPOSSIBLE",
        "afternoonStatus": "POSSIBLE",
        "eveningStatus": "POSSIBLE",
        "uncertain": false
      }
    ]
  }
}
```

- `source` 필드: **Nice / 기본 생략** (넣어도 동작 무관, Must 아님).

#### 트립 칩 → 조회 구간 선택 (마이페이지 화면)

마이페이지 "내 일정 입력하기" 화면 상단의 여행 칩(예: 제주도 여행 / 나트랑 여행 / 전주 여행)은 **본 API가 아니라 `GET /api/v1/trips?scope=ongoing`**(`TripController.listTrips`)이 제공한다. 그 응답 `TripHomeCardResponse`에 `name`·`startRange`·`endRange`가 이미 전부 있다:

1. 화면 진입 시 `GET /trips?scope=ongoing`을 1회 호출해 칩 목록을 렌더한다 (각 칩에 `tripId`·`name`·`startRange`·`endRange`를 들고 있음).
2. 칩을 선택하면, 그 트립의 `startRange`~`endRange`를 그대로 본 API의 `startDate`~`endDate`로 넘겨 호출한다. 서버가 `tripId`로 재계산하지 않는다 — FE가 1)에서 이미 받은 값을 그대로 쓴다.
3. 칩을 선택하지 않은 기본 진입(이번 달 보기, 달력 스크롤)에는 트립과 무관하게 원하는 임의 구간을 넘긴다.

본 API가 `tripId`를 직접 받지 않는 이유: **User 전역** 일정(BR-USER-008 — 트립과 무관하게 존재하는 데이터)을 보여주는 범용 API이기 때문이다. 트립 범위로 **고정된** 조회가 필요한 화면(방 멤버 전원 달력)은 완전히 다른 API(②)를 쓴다.

### ② 여행방 멤버 calendar

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | JWT + member |

기존 ~~`personal-summary`~~ 와의 관계 **T1 확정** (#12) · **API 삭제**.  
trip CRUD 전이면 **①만** 구현.

### 유지 (변경 없음)

| Path | 역할 |
|------|------|
| `.../regular` | 패턴 CRUD |
| `.../personal` | 날짜 예외 CRUD — **슬롯 3개 필수 = S1 쓰기** |

### Out of Scope

- wave 3 집계 API, Google Calendar, S2(nullable 슬롯), 원본 레이어 응답, DB 신규 테이블

---

## 응답 DTO 비교 — `PATCH .../personal` vs `GET .../calendar`

둘 다 "날짜 + 슬롯 3개 + uncertain"을 나르지만, **DTO가 서로 다르다.** FE가 같은 타입처럼 파싱하면 안 된다.

| | `PATCH .../personal` 응답 (`PersonalScheduleResponse.items[]`) | `GET .../calendar` 응답 (`ScheduleCalendarResponse.days[]`) |
|---|---|---|
| 래퍼 | `{ items: [...] }` | `{ startDate, endDate, days: [...] }` |
| 날짜 필드명 | `scheduleDate` | `date` |
| `id` | 있음 — `personal_schedule` row의 UUID | **없음** — 그날 값이 regular 유래일 수도 있어 단일 row가 없음 |
| 슬롯·`uncertain` | `morningStatus`/`afternoonStatus`/`eveningStatus`/`uncertain` | 필드명 동일 |
| 값의 의미 | **최종 확정값**(정기+개별+구글까지 합친 값, `schedule-slot-override.md` O1.4 — 저장된 원본이 아님) | **정기+개별을 합친 값** (regular 유래도 포함) |

## 마이페이지 개별 일정 편집 UX — 정기 유래 날짜를 클릭했을 때

와이어프레임(마이페이지 「내 일정 입력하기」)이 보여주는 흐름: 달력에 찍힌 빨간 점이 정기에서 나온 것이든 개별 오버라이드에서 나온 것이든, **날짜를 클릭하면 항상 그 날짜 하나의 개별 일정 편집 바텀시트가 뜬다.** 정기 패턴(`regular_schedule`)은 이 흐름에서 절대 수정되지 않는다 — 수정 대상은 언제나 `personal_schedule`의 그 날짜 1행이다.

### 시나리오 — 평일 9 to 6 근무자가 하루만 오후를 비움

1. 정기 등록: `POST /regular` — `daysOfWeek=MON,TUE,WED,THU,FRI`, `09:00~18:00` → 평일 매일 아침·오후 `IMPOSSIBLE`, 저녁 `POSSIBLE`
2. 마이페이지 달력에서 6/19(금)을 클릭 — 이 날짜는 `personal_schedule` row가 없어 6/19의 합친 값은 정기 펼침 결과(I/I/P)
3. FE는 바텀시트를 열 때 **`GET /users/schedule/calendar`의 6/19 합친 값(I/I/P)으로 프리필**한다 (C3 — 정기 row 하나만 베끼면 다른 정기와 어긋날 수 있으므로 반드시 합친 값을 써야 함)
4. 유저가 "오후" 토글을 꺼서 오후만 `POSSIBLE`로 변경
5. 저장 시 `PATCH /users/schedule/personal`을 **6/19 하루에 대해서만** 호출(O1.4 — `slots`는 건드릴 때 3필드 전부 명시): `{"items": [{"scheduleDate": "2026-06-19", "slots": {"morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE"}}]}`
6. 서버는 `personal_schedule`에 (user, 2026-06-19) 1행을 새로 만든다 — `regular_schedule`은 무엇도 바뀌지 않는다
7. 이후 `GET /calendar`에서 6/19는 I/P/P(개별 우선), 정기가 그대로인 6/20(토는 매칭 안 됨) · 6/22(월)은 여전히 I/I/P

### 엣지 케이스

| 상황 | 동작 |
|------|------|
| 이미 개별 오버라이드가 있는 날짜를 다시 클릭 | 프리필 값은 (동일하게) `GET /calendar` 합친 값 — 이 경우 정기가 아니라 그 personal row 값 그대로. 저장 시 같은 날짜 row를 **update**(insert 아님) |
| 같은 요일에 정기가 2개 이상 겹치는 날짜에 새로 오버라이드 생성 | 그 날짜는 personal 값만 적용되고, 겹쳤던 정기 슬롯 합성(R2=A) 결과는 그날에 한해 **완전히 무시**된다 |
| 유저가 바텀시트에서 슬롯을 프리필값 그대로(정기와 동일한 값) 다시 저장 | **O1.4: 값 조합과 무관하게 그대로 개별 row가 생성/유지된다** — 정기와 값이 같아 보여도 삭제되지 않는다(구 `isDeleteSignal`류 판정 자체가 없음). 정기와 값이 같은 "불필요해 보이는" override가 남을 수 있으니, FE는 **"유저가 실제로 건드린 날짜만" 요청에 담아야 한다**(아래 ⚠️ 참고) |

> **⚠️ 화면에 뜬 구간을 통째로 저장하지 말 것 (2026-08-16 추가):** 프리필은 정기+개별+구글을 **합친 계산값**이므로, 유저가 아무것도 고치지 않은 날짜까지 그대로 `PATCH /personal`에 담아 보내면 **정기 유래 계산값이 전부 개별 오버라이드 row로 굳는다.** 개별은 항상 정기를 이기고 O1.4 이후 삭제 경로가 없으므로, 이후 정기 일정을 수정해도 그 날짜들은 옛 값에 고정되며 **되돌릴 방법이 없다.** 특히 방 입장 확인 플로우는 **방에 들어갈 때마다 매번** 이 화면을 거치므로 피해가 누적된다([`schedule-participation-onboarding.md`](../trip/schedule-participation-onboarding.md) D-JOIN-TRIP-FLOW). 요청에는 **유저가 토글한 날짜만** 담는다 — 권장이 아니라 필수.
| 유저가 슬롯을 정기와 완전히 같은 값(3슬롯 POSSIBLE·`uncertain=false` 등)으로 명시 저장 | **삭제되지 않는다.** O1.3까지는 이 값 조합을 삭제(CLEAR) 신호로 오인해 정기값으로 조용히 되돌아가는 버그가 있었으나, O1.4에서 삭제 경로 자체를 제거해 근본 해결했다(`schedule-slot-override.md` "계약 개정 이력 — O1.4"). 이 값 그대로 오버라이드 row가 영구히 저장된다 |
| 정기가 전혀 없는 날짜(주말 등, sparse omit)에 개별 일정만 새로 등록 | 정상 동작 — personal만 있어도 됨(regular 선행 요구 없음). 이후 `GET /calendar`에 그 날짜가 새로 나타남 |
| 하루만 고치려고 `PATCH /regular/{id}`를 호출 | **잘못된 API 선택.** 그 정기 패턴이 매칭되는 **모든 요일**이 한꺼번에 바뀐다 — 특정 하루만 바꾸려면 반드시 `PATCH /personal` |
| FE가 두 응답을 같은 타입으로 파싱 | `scheduleDate`(personal) vs `date`(calendar) 필드명이 달라 매핑 누락 시 날짜가 `undefined`가 됨 — 위 DTO 비교 표 참고 |

### 화면 요소 ↔ API 필드 매핑 (마이페이지 「내 일정 입력하기」)

| 화면 요소 | 데이터 출처 |
|---|---|
| 상단 여행 칩(제주도 여행 / 나트랑 여행 / 전주 여행) | `GET /trips?scope=ongoing` 응답의 `name`. 선택된 칩의 `startRange`~`endRange`를 아래 달력 조회 구간으로 사용(위 "트립 칩 → 조회 구간 선택" 참고) |
| 날짜 아래 점(dot) 표시 | `GET /calendar`의 `days[]`에 그 날짜가 있고 슬롯 중 하나라도 `IMPOSSIBLE`인 경우. 정기 유래·개별 유래를 구분해 다르게 표시하지 않는다(합친 값 하나로만 판단). sparse omit된 날짜는 점 없음 |
| 바텀시트 상단 "YYYY년 M월 D일" | 클릭한 날짜 |
| "이 날 일정이 변경될 수 있어요" 토글 | 그 날짜의 `uncertain` |
| 아침/오후/저녁 각 행의 버튼("여행 가능해요" ↔ "일정이 있어요") | 각각 `morningStatus`/`afternoonStatus`/`eveningStatus` — "여행 가능해요"=`POSSIBLE`, "일정이 있어요"=`IMPOSSIBLE` |
| "저장하기" 버튼 | `PATCH /users/schedule/personal`을 그 날짜 1건만 담아 호출(3슬롯+`uncertain` 전부 포함, 위 "구현 순서" 참고) |

병합 규칙은 이 스펙이 SSOT다.

---

## 요구사항

### Must Have (사전 작업 · 문서)

- [x] S1 병합·personal 3슬롯 필수·sparse·정기+개별 합친 값만 노출하는 shape **문서 확정**
- [x] R2=A (슬롯별 IMPOSSIBLE 우선) **문서 확정**
- [x] `schedule-unified.md` 동기화
- [x] A1 기간 상한 확정 — **현행 (#37 C1): `today`~`today+2y−1`** (최초 확정치는 730일 길이였으나 #37에서 폐기·대체)
- [ ] (선택) T1/T2/T3, `source` 여부

### Must Have (구현 — 스펙 Approved 후)

- [x] resolve 로직 + 단위 테스트 (S1, R2=A, omit, weekday expand)
- [x] `GET /api/v1/users/schedule/calendar`
- [x] OpenAPI `@Schema`
- [x] 기간 상한 검증 — 현행 `today`~`today+2y−1` (#37)
- [x] `GET /api/v1/users/schedule/calendar` (#17)
- [ ] `GET /api/v1/trips/{tripId}/members/schedule-calendar` — **#12** (T1 · summary deprecate)

### Nice to Have

- [ ] `source` (`REGULAR` \| `PERSONAL`)

---

## 데이터 모델

- **스키마 변경 없음** (S1 = 현행 personal 계약).  
- Read model만 추가.

### 알고리즘 (S1)

```text
function resolveDays(userId, start, end):
  regulars = findRegularByUser(userId)
  personals = findPersonalByUserBetween(userId, start, end)  // map by date
  days = []
  for date in [start .. end]:
    personal = personals.get(date)
    if personal != null:
      // S1: 슬롯 3개 + uncertain 통째로
      days.add({ date, personal.slots..., uncertain: personal.uncertain })
      continue
    matched = regulars.filter(r => weekday(date) in r.daysOfWeek)
    if matched.isEmpty:
      continue  // sparse omit
    slots = combineRegularsImpossibleWins(matched)  // R2=A
    days.add({ date, slots..., uncertain: false })
  return days

function combineRegularsImpossibleWins(regulars):
  for each slot in MORNING, AFTERNOON, EVENING:
    values = non-null statuses from regulars for slot
    if values contains IMPOSSIBLE → IMPOSSIBLE
    else if values contains POSSIBLE → POSSIBLE
    else → null
```

---

## 비즈니스 규칙

| BR | 적용 |
|----|------|
| BR-TRIP-002 | 정기+개별 합친 값 = 날짜×슬롯 가능/불가; personal 쓰기는 슬롯 3개 필수 |
| BR-TRIP-003 | uncertain 날짜 단위 |
| BR-TRIP-004 | 그룹/타인 = 정기+개별 합친 값만 |
| BR-USER-008 | User 전역 데이터, trip은 조회 컨텍스트만 |

---

## 검증 시나리오 (구현 시)

### 정상

- [x] regular만 → 매칭 요일만 days, 주말 omit  
- [x] personal 있으면 그날 슬롯·uncertain이 personal과 **완전 일치** (S1)  
- [x] personal만(요일 regular 없음) → 그날만 포함  
- [x] “오후만 연차” 저장 = 클라이언트가 3슬롯 채움 → calendar 합친 값이 그 3슬롯  
- [x] 동일 요일 regular 2행 → 슬롯별 IMPOSSIBLE 우선 (R2=A)

### 실패

- [x] `endDate < startDate` → 400  
- [x] 기간 내 무데이터 → `days: []`  
- [ ] trip calendar 비멤버 → 403 — **#12**

---

## 완료 기준

### 사전 작업 (문서)

- [x] S1·sparse·정기+개별 합친 값만 노출·**R2=A** 문서 확정  
- [x] 관련 스펙 인덱스·`schedule-unified` 동기화  
- [x] A1 확정 — 최초 730일, **현행은 #37로 대체** (`today`~`today+2y−1`)  

### 구현

- [x] 본 스펙 **Approved** 후 코드  
- [x] `./gradlew test` (`user.schedule.*`)  
- [x] 이슈 #17 체크리스트 갱신  
- [x] `main`에 반영됨 (PR empty — calendar 커밋이 이미 main에 존재, 2026-07-15 확인)

---

## 리스크·잔여 이슈

> 구현·프론트·추천(#13)·여행방(#12)과 **맞춰야 하는 것**을 심각도별로 정리한다.

### 치명적 (Critical) — 잘못되면 제품 신뢰·추천/달력이 어긋남

| ID | 문제 | 왜 치명적인가 | 완화 / 담당 |
|----|------|---------------|-------------|
| **C1** | **추천(#13)이 resolve를 따로 구현** | 달력 색과 추천 TOP이 다른 “가능”을 씀 → 사용자 불신 | #13 Must: **본 스펙 resolve 함수 재사용**. 가중치만 #13 |
| **C2** | ~~**`personal-summary`가 personal-only로 남음**~~ | — | **해소** — API 삭제 · `members/schedule-calendar`만 |
| **C3** | **Personal 프리필을 regular 1행만으로 함** | S1이 잘못된 POSSIBLE을 DB에 고정 → 다른 정기(수업 등) 불가 무시 | 프론트: 일정 시트는 **`GET .../calendar` 정기+개별 합친 값 복사** 후 편집. API 가이드·이슈에 명시. 상세 시나리오는 아래 "마이페이지 개별 편집 UX" 절 |
| **C4** | **`daysOfWeek` 파싱 불일치** | 생성은 성공·resolve는 매칭 실패(또는 반대) → 특정 요일만 조용히 omit | 서버 단일 파서·정규화(대문자·trim). 잘못된 토큰은 400 |

### 협의 필요 (Agreement) — 구현 전/직후 제품·프론트와 합의

| ID | 항목 | 선택지 / 질문 | 제안 | 상태 |
|----|------|---------------|------|------|
| **A1** | calendar **기간 상한** | **#37 C1 Approved·구현:** 구간 ⊆ today~+2y−1 | `CALENDAR_WINDOW_YEARS=2` | **Implemented (#37)** |
| **A2** | `personal-summary` vs `schedule-calendar` | T1 대체 | **T1 확정** (#12) · **personal-summary 삭제** |
| **A3** | **타임존·날짜 경계** | `LocalDate` only vs zone 포함 | **캘린더 일자(존 없음)** — 요청/응답 모두 date | 합의 권장 (문서에 박기) |
| **A4** | **`holidayRest`를 calendar에 반영?** | 반영함 | **반영 완료** — 공휴일에 쉬는 사용자의 공휴일은 정기 일정을 적용하지 않음(개별·구글은 유지). 판정은 사람 단위(대표 행 기준) | **Implemented** ([`schedule-holiday-rest.md`](schedule-holiday-rest.md), #107) |
| **A5** | **`VacationApplyPeriod`를 calendar에 반영?** | 슬롯만 / “신청 불가 기간” 표시 | **슬롯만** (calendar). 신청 가능 여부는 제출·추천 | 확정 방향 |
| **A6** | **`uncertain` 달력 표시 vs 추천** | U1 슬롯 유지 / U2 가림 | 달력 **U1**. 추천 TBD는 #13 | 달력 확정 · 추천 `[미정]` |
| **A7** | 응답에 **`source`** | 생략 / REGULAR\|PERSONAL | **생략**(Must 아님) | Nice |
| **A8** | 본인 calendar 경로·권한 | 현행 `/users/schedule/calendar` | 유지 | 확정 |
| **A9** | Personal이 regular보다 느슨할 때 UI | 무시 / “정기와 다름” 경고 | 서버는 S1 유지. 경고는 **프론트 Nice** | 협의(UX) |

### 낮음 · 알고만 갈 것 (Low)

| 문제 | 완화 |
|------|------|
| regular 슬롯 null (비정상 데이터) | R2=A에서 null 무시; 생성 경로 슬롯 필수 |
| 대기간 조회로 CPU·payload 증가 | A1 상한 |
| S1이 의도적으로 수업 불가를 덮음 | 제품 허용. A9 |

### 잔여 `[미정]` 요약 (블로커는 아님 · Approved 전에 정하면 좋음)

| # | 항목 | 비고 |
|---|------|------|
| 1 | A1 기간 상한 | **Implemented (#37)** — today~+2y−1 (구 730일 길이 폐기) |
| 2 | A2 summary → calendar | **T1 확정** (#12) |
| 3 | A6 추천 uncertain | #13 |
| 4 | A7 source | Nice |

병합 규칙 **S1 + R2=A** 자체는 확정되어 **구현 블로커 아님**.

### GitHub 트래킹

| 이슈 | 역할 |
|------|------|
| **#11** | regular/personal **CRUD** (대부분 완료). calendar는 후속으로 분리 |
| **#17** | `GET .../schedule/calendar` (+ resolve) — 본 스펙 SSOT |
| **#12** | trip · A2(summary↔calendar) 연동 |
| **#13** | C1(resolve 재사용) · A6(uncertain 추천) |

### 구현 순서

1. 스펙 Approved (+ A1 기본값 권장)  
2. resolve 단위 테스트 (S1, R2=A, omit)  
3. `GET .../users/schedule/calendar`  
4. #12 이후 members calendar · summary 정리(A2)  
5. #13에서 resolve **공유** (C1)  

---

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-13 | Draft 초안 |
| 2026-07-14 | S1·sparse·정기+개별 합친 값만 노출 확정 |
| 2026-07-14 | R2=A 확정 · 리스크 표 |
| 2026-07-14 | **Approved** · A1=730일 · 본인 calendar · #17 |
| 2026-07-17 | A2 T1 확정 · members calendar → #12 |
| 2026-07-21 | deferred — #37 조회 윈도우 · #38 TERMINATED snapshot (A1 현행 유지) |
| 2026-07-21 | **제품 재확정** — A1→마이페이지 today+2년(#37 C1) · 방=희망 기간 · #38 CONFIRMED∪TERMINATED |
| 2026-07-29 | **용어 변경** — "effective" 표현을 전부 "정기+개별 합친 값/달력"으로 교체(Swagger·docs·내부 메서드명 `resolveEffectiveSchedule`→`resolveMergedSchedule` 동일 적용, DB·API 계약 변경 없음) · "응답 DTO 비교"·"마이페이지 개별 일정 편집 UX"(시나리오·엣지케이스) 절 추가 · FE 전달용 `schedule-calendar-merge.md` 작성 (해당 문서는 2026-09-22 `fe-context/` 폴더 전체와 함께 폐기됨 — 현행 병합 규칙 SSOT는 본 스펙) |
| 2026-07-29 | **문서 보강** — 여행 칩(`GET /trips?scope=ongoing`)과 본 API `startDate`/`endDate`의 관계 명문화("트립 칩 → 조회 구간 선택" 절, API 계약 변경 없음) · "화면 요소 ↔ API 필드 매핑" 절 추가 |
| 2026-08-16 | **A4 해소** — `holidayRest`를 근무일 판정에 반영([`schedule-holiday-rest.md`](schedule-holiday-rest.md), #107). 구 "wave 2 Out — 요일만" 방향과 프론트 "공휴일≠휴무 자동" 고지 문구 삭제 |
| 2026-07-29 | **S1 폐기 → O1로 대체** ([`schedule-slot-override.md`](schedule-slot-override.md), #67, **Approved·구현 완료**) — 개별 일정이 "그 날 전체 대체"에서 "슬롯 단위 오버라이드"로 전환. 본 문서의 S1·R1 서술은 이력 문서로 유지, 확정 사항 표 #1·#2에 폐기 표시 |
