# 일정 달력 통합 조회 (Resolved Calendar)

> wave: 2  
> implements: BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-USER-006, BR-USER-008  
> related: [`schedule-unified.md`](schedule-unified.md), [`trip-room-api.md`](trip-room-api.md), [`trip-recommendation.md`](trip-recommendation.md)  
> deferred: sparse day 의미(가능 vs 미입력) → **[#22](https://github.com/Central-MakeUs/TripFit-server/issues/22)** · **A1 → 마이페이지 today+2년** [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) (**[#37](https://github.com/Central-MakeUs/TripFit-server/issues/37)** C1) · **CONFIRMED/TERMINATED snapshot** [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md) (**[#38](https://github.com/Central-MakeUs/TripFit-server/issues/38)**)  
> 상태: **Implemented** (#17) — S1·R2=A · sparse. **A1:** #37에서 today~+2년 윈도우로 amend (**Implemented** on feat/37)  
> MVP: In scope (일정 응답·추천 입력 데이터) / 그룹 달력 UX는 wave 3

## 목표

클라이언트가 **기간 내 날짜×오전/오후/저녁**을 달력·일정 시트·추천에 바로 쓸 수 있도록,  
`regular_schedule`(패턴)과 `personal_schedule`(날짜 예외)를 서버에서 **펼치고 합친(effective) 조회 API**를 정의한다.

---

## 확정 사항 (2026-07-14)

| # | 항목 | 결정 |
|---|------|------|
| 1 | 병합 모델 | **S1** — personal 행이 있으면 그날 슬롯 3개 + `uncertain` **전부**가 effective |
| 2 | personal 쓰기 계약 | 추가/수정 시 **오전·오후·저녁 3필드 필수** (현행 API 유지, 스키마 변경 없음) |
| 3 | empty day | **sparse** — regular·personal 모두 없으면 `days`에서 **omit** |
| 4 | 응답 깊이 | 본인·그룹 **모두 effective만** (원본 레이어 없음, 납작한 day) |
| 5 | 용어 | 합친 결과 = **`effective`** (중첩 wrapper 없음) |
| 6 | 동일 요일 regular 복수 | **R2=A** — 슬롯별 **IMPOSSIBLE 우선** |
| 7 | calendar 기간 상한 **A1** | **현행 코드:** `DAYS.between(start,end) ≤ 730`. **제품 재확정(#37 C1):** 요청 구간 ⊆ **`today`~`today+2y−1`**. 여행방 기간은 희망 기간(#37 C2) — 본 A1과 무관 |

### S1이란 (클라이언트 관점)

```text
PATCH /users/schedule/personal  시
  morningStatus, afternoonStatus, eveningStatus  → 전부 필수 (@NotNull)
  “오후만 연차”도 오전·저녁 값을 클라이언트가 채워 보냄
  (보통 오전·저녁은 regular와 같은 값으로 복사해 저장)

GET /users/schedule/calendar  시
  그 날짜에 personal 행이 있으면 → effective = 그 행의 3슬롯 + uncertain
  없으면 → regular를 요일에 맞게 펼친 값
  둘 다 없으면 → 응답에 날짜 없음
```

**S2(슬롯 null = regular 유지)는 채택하지 않음.** DB·API 변경 없음.

### 납작한 effective day 예시

```json
{
  "date": "2026-08-05",
  "morningStatus": "POSSIBLE",
  "afternoonStatus": "POSSIBLE",
  "eveningStatus": "POSSIBLE",
  "uncertain": false
}
```

문서에서 “effective” = 위 필드들의 **의미**(합친 결과).  
응답에 `"effective": { ... }` 중첩 객체를 두지 않는다.

---

## 배경 — 무엇이 문제인가

### 현재 데이터 모델 (이미 구현됨)

| 테이블 | 의미 | 저장 단위 |
|--------|------|-----------|
| `regular_schedule` | 출근·수업 등 **반복 패턴** | user당 N행. 요일 + 시각→슬롯 |
| `personal_schedule` | **특정 날짜** 가능/불가 | `(user_id, schedule_date)` 1행, 슬롯 3개 필수 |

- 일정은 **User 전역** (trip FK 없음, BR-USER-008).
- 슬롯: `POSSIBLE` \| `IMPOSSIBLE`. `uncertain`은 날짜 단위.

상세: [`schedule-unified.md`](schedule-unified.md), [`erd.md`](../architecture/erd.md).

### 현재 조회 API의 한계

| API | 반환 | 달력에 바로 쓰나? |
|-----|------|------------------|
| `GET .../regular` | 규칙 목록 | ❌ |
| `GET .../personal?start&end` | **저장된 personal만** | ❌ 정기만 있는 날 누락 |
| ~~`GET .../trips/{id}/members/personal-summary`~~ | ~~멤버 personal만~~ | **삭제** — `members/schedule-calendar` 사용 |

**해결:** 서버가 기간 → 날짜별 **effective** 를 한곳에서 계산하는 calendar API.

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
   → effective = personal의 `morning` / `afternoon` / `evening` / `uncertain` **전부**  
2. personal 없고, 요일에 매칭되는 **regular ≥1**  
   → effective = regular 슬롯 합성 (R2) + `uncertain=false`  
3. 둘 다 없음  
   → **omit** (sparse)

### R2 — 같은 요일 regular 복수 = **A 확정** (슬롯별 IMPOSSIBLE 우선)

같은 요일에 regular가 2행 이상이면, 슬롯마다:

```text
하나라도 IMPOSSIBLE → IMPOSSIBLE
그 외 (모두 POSSIBLE 또는 유효 상태만 POSSIBLE) → POSSIBLE
null(미설정) 슬롯 → 합성에서 무시 (의견 없음). 해당 슬롯에 유효값이 하나도 없으면 POSSIBLE로 두지 않고 null 유지 후, 세 슬롯 모두 null이면 그날 omit 후보 — 정상 생성 경로에서는 슬롯이 채워짐
```

예: 출근(수) I/I/P + 수업(수) P/P/I → 수요일 effective **I/I/I**.

폐기안: B(최신 1행만), C(요일 겹침 저장 금지).

### R3 — `uncertain` 표시 (가정 U1, 추천은 #13)

- 달력 effective: personal이 있으면 `uncertain` 그대로, regular만이면 `false`  
- 슬롯 status는 가리지 않음 (U1). 추천 가중치는 별도 스펙.

### R4 — 레이어 (확정: 없음)

본인·그룹 calendar 모두 **effective(납작한 day)만**.  
`fromRegular` / `fromPersonal` / 중첩 `effective` 객체 **Out of Scope**.

---

## R2=A 예시 (regular 복수)

| 정기 | 수요일 슬롯 |
|------|-------------|
| 출근 | I / I / P |
| 저녁 수업 | P / P / I |

→ calendar 수요일 effective = **I / I / I** (슬롯별 불가 우선).

같은 수요일에 personal이 있으면 **S1이 먼저** → A 결과는 그날 사용하지 않음.

## 예시 (S1 + sparse)

**DB**

- Regular: 월~금, 오전·오후 IMPOSSIBLE, 저녁 POSSIBLE  
- Personal 8/5 (화, “오후만 연차”를 S1로 저장):  
  오전 IMPOSSIBLE, 오후 POSSIBLE, 저녁 POSSIBLE, uncertain=false  
  ← 클라이언트가 오전·저녁을 regular와 같게 **채워서** 보냄

**`GET .../calendar?startDate=2026-08-01&endDate=2026-08-07`**

| date | effective | 비고 |
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
| 응답 | `days[]` (effective) | `members[]` → `days[]` (effective만) |

공통 resolve: `user/schedule/service`.

### ① 본인 calendar

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/users/schedule/calendar` | JWT |

**Query:** `startDate`, `endDate` (ISO date, 필수). `end < start` → 400.  
**기간 상한:** `DAYS.between(start,end) ≤ 730` (약 2년). 초과 → 400 `INVALID_INPUT`.

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

## 요구사항

### Must Have (사전 작업 · 문서)

- [x] S1 병합·personal 3슬롯 필수·sparse·effective-only shape **문서 확정**
- [x] R2=A (슬롯별 IMPOSSIBLE 우선) **문서 확정**
- [x] `schedule-unified.md` 동기화
- [x] A1 기간 상한 **730일(약 2년)** 확정
- [ ] (선택) T1/T2/T3, `source` 여부

### Must Have (구현 — 스펙 Approved 후)

- [x] resolve 로직 + 단위 테스트 (S1, R2=A, omit, weekday expand)
- [x] `GET /api/v1/users/schedule/calendar`
- [x] OpenAPI `@Schema`
- [x] 기간 상한 730일 검증
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
| BR-TRIP-002 | effective = 날짜×슬롯 가능/불가; personal 쓰기는 슬롯 3개 필수 |
| BR-TRIP-003 | uncertain 날짜 단위 |
| BR-TRIP-004 | 그룹/타인 = effective만 |
| BR-USER-008 | User 전역 데이터, trip은 조회 컨텍스트만 |

---

## 검증 시나리오 (구현 시)

### 정상

- [x] regular만 → 매칭 요일만 days, 주말 omit  
- [x] personal 있으면 그날 슬롯·uncertain이 personal과 **완전 일치** (S1)  
- [x] personal만(요일 regular 없음) → 그날만 포함  
- [x] “오후만 연차” 저장 = 클라이언트가 3슬롯 채움 → calendar effective가 그 3슬롯  
- [x] 동일 요일 regular 2행 → 슬롯별 IMPOSSIBLE 우선 (R2=A)

### 실패

- [x] `endDate < startDate` → 400  
- [x] 기간 내 무데이터 → `days: []`  
- [ ] trip calendar 비멤버 → 403 — **#12**

---

## 완료 기준

### 사전 작업 (문서)

- [x] S1·sparse·effective-only·**R2=A** 문서 확정  
- [x] 관련 스펙 인덱스·`schedule-unified` 동기화  
- [x] A1=730일 확정  

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
| **C3** | **Personal 프리필을 regular 1행만으로 함** | S1이 잘못된 POSSIBLE을 DB에 고정 → 다른 정기(수업 등) 불가 무시 | 프론트: 일정 시트는 **`GET .../calendar` effective 복사** 후 편집. API 가이드·이슈에 명시 |
| **C4** | **`daysOfWeek` 파싱 불일치** | 생성은 성공·resolve는 매칭 실패(또는 반대) → 특정 요일만 조용히 omit | 서버 단일 파서·정규화(대문자·trim). 잘못된 토큰은 400 |

### 협의 필요 (Agreement) — 구현 전/직후 제품·프론트와 합의

| ID | 항목 | 선택지 / 질문 | 제안 | 상태 |
|----|------|---------------|------|------|
| **A1** | calendar **기간 상한** | **#37 C1 Approved·구현:** 구간 ⊆ today~+2y−1 | `CALENDAR_WINDOW_YEARS=2` | **Implemented (#37)** |
| **A2** | `personal-summary` vs `schedule-calendar` | T1 대체 | **T1 확정** (#12) · **personal-summary 삭제** |
| **A3** | **타임존·날짜 경계** | `LocalDate` only vs zone 포함 | **캘린더 일자(존 없음)** — 요청/응답 모두 date | 합의 권장 (문서에 박기) |
| **A4** | **`holidayRest`를 calendar에 반영?** | wave 2 Out / 공휴일 테이블 후 반영 | **wave 2 Out** — 요일만. 공휴일은 #13·후속 | 확정 방향(문서) · 프론트에 “공휴일≠휴무 자동” 고지 |
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
| 1 | A1 기간 상한 | **확정 730일** |
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
| 2026-07-14 | S1·sparse·effective-only 확정 |
| 2026-07-14 | R2=A 확정 · 리스크 표 |
| 2026-07-14 | **Approved** · A1=730일 · 본인 calendar · #17 |
| 2026-07-17 | A2 T1 확정 · members calendar → #12 |
| 2026-07-21 | deferred — #37 조회 윈도우 · #38 TERMINATED snapshot (A1 현행 유지) |
| 2026-07-21 | **제품 재확정** — A1→마이페이지 today+2년(#37 C1) · 방=희망 기간 · #38 CONFIRMED∪TERMINATED |
