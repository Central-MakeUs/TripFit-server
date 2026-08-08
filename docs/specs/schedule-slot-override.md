# 개별 일정 = 슬롯 단위 오버라이드 (O1)

> 상태: Approved (#67)
> MVP: In scope (`docs/product/mvp.md` — "참여자 일정 입력(오전/오후/저녁 단위, 미정 상태 포함)")
> 관련 BR: BR-TRIP-002, BR-TRIP-003, BR-TRIP-004, BR-USER-008
> supersedes: [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) **S1**(개별 존재 시 그 날 전체 대체) · R1(병합 규칙 1) — **R2(정기 복수 IMPOSSIBLE 우선)는 그대로 유지**
> related: [`schedule-unified.md`](schedule-unified.md), [`google-calendar-merge.md`](../product/fe-context/google-calendar-merge.md)

## 목표

정기 일정에 이미 반영된 정보(예: "9~18시 근무라 아침 불가능")가, 사용자가 개별 일정으로 다른 슬롯(예: 오후) 하나만 커스터마이즈하는 순간 **조용히 사라지는 문제**를 구조적으로 없앤다. 개별 일정을 "그 날 전체를 대체하는 행"이 아니라 "**정기+구글이 계산한 기본값 중, 사용자가 명시적으로 손댄 슬롯만 덮어쓰는 오버라이드**"로 재정의한다.

## 배경

### 현재(S1) 모델의 문제

`schedule-calendar-resolve.md`의 S1은 "그 날짜에 `personal_schedule` 행이 있으면 슬롯 3개+`uncertain` **전부**를 그 행 값으로 쓰고, 정기는 아예 안 본다"는 규칙이다. `personal_schedule`의 슬롯 3개 컬럼이 `NOT NULL`이라, 클라이언트는 한 슬롯만 고치고 싶어도 나머지 두 슬롯 값을 **반드시 채워서** 보내야 한다.

문제는 이 "나머지 두 슬롯 값"을 채우는 책임이 전적으로 **그 순간 그 화면을 만든 프론트 코드**에 있고, 서버는 이를 검증도 보정도 하지 않는다는 것이다. 마이페이지 캘린더 편집 화면처럼 "먼저 `GET /calendar`로 프리필 후 편집"하는 화면이라면 문제가 없지만, 정기 일정 존재를 모르는 **다른 화면**(예: 별도 설정 페이지, 온보딩 등)이 개별 일정을 저장하면 나머지 슬롯이 기본값(`POSSIBLE`)으로 채워져 정기의 `IMPOSSIBLE` 정보가 그 날짜에 한해 사라진다 — 서버 쪽에 아무 안전장치가 없다.

### 왜 지금 고치는가

- dev 환경, 상용 보존 데이터 없음 → 스키마·계약을 자유롭게 바꿀 수 있는 지금이 비용이 가장 싸다.
- 실제로 `personal_schedule`의 슬롯 컬럼(`morning_status`/`afternoon_status`/`evening_status`)은 **DB 레벨에서는 이미 nullable이다** — `SlotStatuses`(정기·개인 공용 `@Embeddable`)의 `@Column`에 `nullable = false`가 없다. NOT NULL을 강제하는 건 **애플리케이션 검증**(`validatePersonalItem`, DTO `@NotNull`)과 **병합 알고리즘(S1)** 뿐이다. 즉 **DB 마이그레이션이 전혀 필요 없고**, 애플리케이션 로직만 바꾸면 된다.

## 대안 비교

| | **A. in-place nullable (권장)** | B. 슬롯별 별도 오버라이드 테이블 |
|---|---|---|
| 구조 | `personal_schedule`은 그대로 두고, 슬롯 3개를 "값 있으면 오버라이드, `null`이면 오버라이드 없음"으로 재해석 | `schedule_slot_override(user_id, date, slot enum, status)` — 오버라이드가 있는 슬롯만 행으로 존재 |
| `uncertain`(day-level) | 기존 row에 그대로 공존 가능 | 슬롯 단위 테이블과 `uncertain`(day 단위)의 소속이 안 맞아 **테이블을 2개**로 쪼개야 함 |
| 스키마 변경 | **없음** (컬럼이 이미 nullable) | 신규 테이블 생성 + 기존 `personal_schedule`과의 관계 재정의 |
| 조회 로직 | 날짜당 최대 1행 조회는 지금과 동일 | 날짜당 0~3행을 GROUP BY로 슬롯별로 모아야 함 — 조회·매핑 복잡도 증가 |
| upsert 로직 | 슬롯 하나만 `null`로 보내면 그 슬롯만 해제 — 기존 upsert 흐름 그대로 확장 | 슬롯마다 존재 여부 판단 후 insert/update/delete 분기 — 로직·트랜잭션 증가 |
| 표현력 | B와 동일(오버라이드 유무를 완전히 표현 가능) | A와 동일 |

**결론: A.** 표현력은 동일한데 B는 테이블을 늘리고 조회·쓰기 로직만 복잡해진다 — "정규화가 이론적으로 더 낫다"는 이점이, 슬롯이 정확히 3개로 고정된 이 도메인에서는 실익이 없다. 게다가 A는 **스키마 변경이 아예 없다.**

## 요구사항

### Must Have

- [ ] `personal_schedule` 슬롯 3개(`morning_status`/`afternoon_status`/`evening_status`) 검증을 nullable 허용으로 완화 (`PersonalSchedule` 엔티티는 이미 nullable 저장 가능 — DTO·서비스 검증만 변경)
- [ ] `PATCH /users/schedule/personal`의 `PersonalScheduleItem` 슬롯 3필드: `@NotNull` 제거, `requiredMode = REQUIRED` → `NOT_REQUIRED`(nullable). `null` = "이 슬롯 오버라이드 없음/해제"
- [ ] `ScheduleCalendarResolver` 병합 알고리즘을 아래 "병합 알고리즘(O1)"대로 재작성 — 슬롯 단위로 `개별 오버라이드 > (정기 ⊕ 구글)` 우선순위 적용
- [ ] 삭제(CLEAR) 조건 재정의: 슬롯 3개 전부 `null` **그리고** `uncertain=false` → 그 날짜 row 삭제. (구 조건 "3개 다 `POSSIBLE`"은 폐기 — 이제 "3개 다 `POSSIBLE`"은 **유효한 전체-오버라이드**로 저장됨)
- [ ] `GET /calendar`, `GET /trips/{tripId}/members/schedule-calendar` 응답 **모양은 불변** (여전히 날짜별 3슬롯+`uncertain`, 값은 항상 `POSSIBLE`/`IMPOSSIBLE`로 확정돼 내려감 — nullable은 저장 계층에서만 의미가 있고 응답에는 노출 안 함)
- [ ] `ScheduleCalendarResolveService` 단위 테스트 갱신 — 부분 오버라이드·전체 오버라이드·오버라이드 해제·구글 병합 조합 케이스 추가
- [ ] `docs/architecture/erd.md`의 `personal_schedule` 컬럼 nullable 표기(N→Y) + 의미 갱신
- [ ] `schedule-unified.md`, `schedule-calendar-resolve.md` 개정(S1 폐기 반영, 본 스펙 링크)
- [ ] `docs/product/fe-context/schedule-calendar-merge.md` 개정 — "슬롯 3개 전부 채워 보내라"던 기존 규칙 2를 "건드린 슬롯만 보내라"로 교체
- [ ] 커밋에 `Breaking-Change-Reason:` 트레일러 — 슬롯 필드 필수→선택 전환, 삭제(CLEAR) 신호 조건 변경

### Nice to Have

- [ ] `PersonalScheduleItemResponse`에 "이 슬롯이 오버라이드인지 자동계산인지" 구분 필드 (지금은 Out — PATCH 응답도 최종 확정값만 내려줘도 충분)

### Out of Scope (이번 스펙에서 하지 않음)

- `personal_schedule` 테이블/엔티티/API 경로 리네임 (아래 "리스크·미결정" 참고 — 리네임 없이 진행)
- 슬롯별 오버라이드 별도 테이블 분리(대안 B, 채택 안 함)
- 추천(#13) 쪽 반영 — 추천은 `ScheduleCalendarResolver` 재사용 원칙(C1, 기존 스펙)만 유지되면 자동으로 새 알고리즘을 따름, 별도 작업 없음

## API / 인터페이스

| Method | Path | 변경 |
|--------|------|------|
| PATCH | `/api/v1/users/schedule/personal` | 슬롯 3필드 **REQUIRED → nullable**. 나머지(래퍼·`scheduleDate`·`uncertain`) 불변 |
| GET | `/api/v1/users/schedule/calendar` | **응답 모양·필드 불변.** 내부 계산 로직만 교체 |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | **응답 모양·필드 불변.** 내부 계산 로직만 교체 |

`PATCH /personal` 응답(`PersonalScheduleItemResponse`)의 슬롯 값도 DB에 저장된 원본(`null` 포함)이 아니라 **`GET /calendar`와 동일하게 정기+구글까지 반영한 최종 확정값**(`POSSIBLE`/`IMPOSSIBLE`)으로 내려준다 — nullable은 저장 계층에서만 의미가 있고 API 응답에는 노출하지 않는다(확정).

### 요청 예시 — 오후 슬롯만 커스터마이즈 (아침·저녁은 안 건드림)

```json
{
  "items": [
    { "scheduleDate": "2026-08-06", "morningStatus": null, "afternoonStatus": "IMPOSSIBLE", "eveningStatus": null, "uncertain": false }
  ]
}
```

→ 저장되는 `personal_schedule` row: `morning_status=NULL, afternoon_status=IMPOSSIBLE, evening_status=NULL, is_uncertain=false`.
→ 이후 `GET /calendar`에서 이 날짜는 "아침: 정기+구글 계산값 그대로, 오후: `IMPOSSIBLE`(오버라이드), 저녁: 정기+구글 계산값 그대로"로 내려간다.

### 부수 효과 — 슬롯 3개가 전부 `null`이면 그 날짜 row 자체가 삭제됨

```json
{ "scheduleDate": "2026-08-06", "morningStatus": null, "afternoonStatus": null, "eveningStatus": null, "uncertain": false }
```

**제품에 "기본값으로 되돌리기" 전용 버튼은 없다** — 이건 어디까지나 CLEAR 조건의 부수적 결과다. 슬롯 3개가 전부 `null`이고 `uncertain=false`면 이 row가 담고 있는 정보가 없으므로 서버가 **삭제**한다(CLEAR). `uncertain=true`만 남기고 싶으면 슬롯 3개는 `null`, `uncertain: true`로 보내면 row는 유지된다(오버라이드 없이 "이 날은 불확실"만 표시).

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` `personal_schedule` 절
- **스키마 변경 없음** — `SlotStatuses`(공용 `@Embeddable`)의 슬롯 3개 컬럼은 이미 nullable. `PersonalSchedule` 엔티티 코드도 변경 불필요(이미 `ScheduleStatus` nullable 필드).
- 변경 대상은 **애플리케이션 계층**뿐:
  - `ScheduleService.validatePersonalItem` — 슬롯 null 허용, null이 아닌 값만 `requireSlotStatus`(POSSIBLE/IMPOSSIBLE) 검증
  - `ScheduleService.isDeleteSignal` — "3슬롯 전부 `POSSIBLE`" → "3슬롯 전부 `null`"로 조건 변경(둘 다 `uncertain=false` 조건은 유지)
  - `UpdatePersonalScheduleRequest.PersonalScheduleItem` — 슬롯 3필드 `@NotNull` 제거, `@Schema(nullable = true, requiredMode = NOT_REQUIRED)`로 변경
  - `ScheduleCalendarResolver` — 병합 알고리즘 전면 교체(아래)
- ERD 문서 갱신: `personal_schedule.morning_status`/`afternoon_status`/`evening_status`의 Nullable 컬럼을 `N`→`Y`로, 설명을 "POSSIBLE/IMPOSSIBLE(오버라이드) — null이면 정기+구글 기본값을 그대로 씀"으로 갱신

## 병합 알고리즘 (O1)

날짜 `date`, 슬롯 `slot`(MORNING/AFTERNOON/EVENING)마다 아래 순서로 계산한다. **R2(정기 복수 겹침 시 슬롯별 IMPOSSIBLE 우선)는 기존 그대로 유지.**

```text
function resolveSlot(date, slot, regulars, personal, googleBusy):
  # 1. 정기 계산 (기존 R2 그대로)
  matched = regulars.filter(r => weekday(date) in r.daysOfWeek)
  regularValue = combineImpossibleWins(matched, slot)   # IMPOSSIBLE 우선, 매칭 없으면 null

  # 2. 정기 ⊕ 구글 (기존 Google 병합 그대로, OR)
  if regularValue == IMPOSSIBLE or googleBusy(date, slot) == true:
    base = IMPOSSIBLE
  elif regularValue == POSSIBLE or googleBusy(date, slot) == false:
    base = POSSIBLE
  else:
    base = null   # 정기 매칭도 없고 구글 신호도 없음

  # 3. 개별 오버라이드가 최종 승자
  override = personal?.slotValue(slot)   # personal 없거나 그 슬롯이 null이면 null
  final = override ?? base ?? POSSIBLE   # 끝까지 아무 신호도 없으면 "미입력≠불가능" 정책상 POSSIBLE

  return final

function resolveDay(date, regulars, personal, googleBusyMap):
  if personal == null and no regular matches date and no google data for date:
    return omit (sparse)   # 아무 정보도 없는 날짜
  slots = { MORNING: resolveSlot(...), AFTERNOON: resolveSlot(...), EVENING: resolveSlot(...) }
  uncertain = personal?.uncertain ?? false
  return { date, slots, uncertain }
```

핵심 차이(기존 S1 대비): **개별 오버라이드는 슬롯 단위로만 정기를 이긴다.** 그 날짜에 personal row가 있어도, 오버라이드 안 된 슬롯은 여전히 "정기 ⊕ 구글" 계산값을 쓴다.

## 유저 시나리오 (상세) — API·DB 상태 추적

공통 설정: user `U`(`userId = 550e8400-e29b-41d4-a716-446655440000`), 정기 일정 두 개를 미리 등록해둔 상태에서 시작한다.

| ID | title | daysOfWeek | start~end | 계산된 슬롯(요일 매칭 시) |
|----|-------|------------|-----------|---------------------------|
| R1 | 출근 | MON,TUE,WED,THU,FRI | 09:00~18:00 | MORNING=IMPOSSIBLE, AFTERNOON=IMPOSSIBLE, EVENING=POSSIBLE |
| R2 | 저녁 수업 | WED | 19:00~21:00 | MORNING=POSSIBLE, AFTERNOON=POSSIBLE, EVENING=IMPOSSIBLE |

2026년 8월 요일: 8/3(월)~8/7(금) 평일, 8/1·8/2·8/8·8/9는 주말. **8/5(수)는 R1·R2 둘 다 매칭.**

각 시나리오는 앞 시나리오의 DB 상태를 이어받는다(별도 언급 없으면 `personal_schedule`·`google_calendar_busy_day`는 비어 있음에서 시작).

---

### S-1. 정기만 있음 — 개별·구글 없음

**DB:** `personal_schedule` 없음. `google_calendar_busy_day` 없음.

**API:** `GET /users/schedule/calendar?startDate=2026-08-03&endDate=2026-08-09`

**응답:**

```json
{ "data": { "startDate": "2026-08-03", "endDate": "2026-08-09", "days": [
  { "date": "2026-08-03", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false },
  { "date": "2026-08-04", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false },
  { "date": "2026-08-05", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE", "uncertain": false },
  { "date": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false },
  { "date": "2026-08-07", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false }
] } }
```

8/5(수)만 R1·R2 겹침(R2=A, 슬롯별 IMPOSSIBLE 우선)이라 저녁까지 불가능. 8/1·8/2·8/8·8/9는 매칭 정기 없음+개별 없음+구글 없음이라 **응답에서 생략(sparse)**.

---

### S-2. 정기+개별 부분 오버라이드 — 이번 스펙의 핵심 시나리오

8/6(목)에 오후 반차를 써서 오후만 여행 가능하게 바꾸고 싶다. 아침·저녁은 안 건드린다.

**API:** `PATCH /users/schedule/personal`

```json
{ "items": [ { "scheduleDate": "2026-08-06", "morningStatus": null, "afternoonStatus": "POSSIBLE", "eveningStatus": null, "uncertain": false } ] }
```

**DB 반영 (`personal_schedule` INSERT):**

| user_id | schedule_date | morning_status | afternoon_status | evening_status | is_uncertain |
|---|---|---|---|---|---|
| U | 2026-08-06 | `NULL` | `POSSIBLE` | `NULL` | `false` |

**조회:** `GET /calendar?startDate=2026-08-06&endDate=2026-08-06`

```json
{ "data": { "days": [ { "date": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "POSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false } ] } }
```

**해설:** 아침은 오버라이드가 `NULL`이라 정기(R1) 값 `IMPOSSIBLE`이 그대로 유지된다 — S1이었다면 여기서 아침이 조용히 `POSSIBLE`로 뒤집혔을 자리다. 오후는 오버라이드값 `POSSIBLE`이 정기의 `IMPOSSIBLE`을 이긴다. 저녁은 오버라이드가 없어 정기값 `POSSIBLE` 그대로.

---

### S-3. 개별 전체 오버라이드 — 갑자기 하루 종일 일정이 생김

S-2 상태에서 이어서, 8/6에 급한 일이 생겨 하루 종일 불가능으로 바꾼다.

**API:** `PATCH /users/schedule/personal`

```json
{ "items": [ { "scheduleDate": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE", "uncertain": false } ] }
```

**DB (UPDATE, 기존 row 값만 교체):** `morning_status=IMPOSSIBLE, afternoon_status=IMPOSSIBLE, evening_status=IMPOSSIBLE`

**조회 결과:** 8/6 전체 `IMPOSSIBLE` — 저녁도 정기(POSSIBLE)와 무관하게 오버라이드가 이긴다.

---

### S-4. 슬롯 하나만 오버라이드 해제 — 부분 되돌리기

S-3 상태에서, 저녁 일정만 취소돼서 저녁은 다시 정기값(가능 여부는 정기가 결정)을 따르게 하고 싶다. 아침·오후 오버라이드는 그대로 둔다.

**API:** `PATCH /users/schedule/personal`

```json
{ "items": [ { "scheduleDate": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": null, "uncertain": false } ] }
```

**DB (UPDATE):** `morning_status=IMPOSSIBLE, afternoon_status=IMPOSSIBLE, evening_status=NULL` — 슬롯 3개가 전부 `null`은 아니므로 row는 **삭제되지 않고 갱신**된다.

**조회 결과:** 아침 `IMPOSSIBLE`(오버라이드 유지), 오후 `IMPOSSIBLE`(오버라이드 유지), 저녁 `POSSIBLE`(오버라이드 해제 → 정기 R1값 복귀).

---

### S-5. 오버라이드 완전 삭제(CLEAR) — API 레벨 부수 효과

S-4 상태에서, 남은 오버라이드도 전부 지운다(제품에 전용 "리셋 버튼"은 없지만, 슬롯 3개를 전부 `null`로 보내면 이 결과가 나온다).

**API:** `PATCH /users/schedule/personal`

```json
{ "items": [ { "scheduleDate": "2026-08-06", "morningStatus": null, "afternoonStatus": null, "eveningStatus": null, "uncertain": false } ] }
```

**DB:** 슬롯 3개 전부 `null` + `uncertain=false` → **row 자체가 DELETE**된다(CLEAR).

**조회 결과:** 8/6은 다시 S-1과 동일한 순수 정기값(`IMPOSSIBLE`/`IMPOSSIBLE`/`POSSIBLE`)으로 돌아간다.

---

### S-6. 슬롯 오버라이드 없이 "이 날 불확실"만 표시

8/12(수, R1·R2 둘 다 매칭)에 아직 일정이 정해지지 않았지만 "바뀔 수도 있다"는 것만 표시하고 싶다.

**API:** `PATCH /users/schedule/personal`

```json
{ "items": [ { "scheduleDate": "2026-08-12", "morningStatus": null, "afternoonStatus": null, "eveningStatus": null, "uncertain": true } ] }
```

**DB (INSERT):** `morning_status=NULL, afternoon_status=NULL, evening_status=NULL, is_uncertain=true` — `uncertain=true`라서 CLEAR 조건(슬롯 전부 null **그리고** uncertain=false)에 해당하지 않아 **row가 생성·유지**된다.

**조회 결과:** 슬롯은 오버라이드가 전혀 없으므로 정기 그대로(R1·R2 겹침이라 R2=A로 `IMPOSSIBLE`/`IMPOSSIBLE`/`IMPOSSIBLE`), `uncertain: true`만 얹혀서 내려간다.

---

### S-7. 정기 없는 날(주말) — 개별·구글도 없음 → sparse omit

**API:** `GET /calendar?startDate=2026-08-08&endDate=2026-08-09`

**응답:** `{ "data": { "startDate": "2026-08-08", "endDate": "2026-08-09", "days": [] } }` — 두 날짜 다 아무 신호가 없어 완전히 생략된다.

---

### S-8. 구글 캘린더 연동 — 정기 없는 날에 구글 이벤트만 있음

8/8(토)은 정기가 없지만, 유저가 구글 캘린더에 "가족 모임"(오후)을 등록해뒀고 이미 연동·동기화된 상태다.

**전제 DB:** `google_calendar_credential` 1행(연동 완료). `google_calendar_busy_day`:

| user_id | schedule_date | morning_busy | afternoon_busy | evening_busy |
|---|---|---|---|---|
| U | 2026-08-08 | false | **true** | false |

**API:** `GET /calendar?startDate=2026-08-08&endDate=2026-08-08`

```json
{ "data": { "days": [ { "date": "2026-08-08", "morningStatus": "POSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false } ] } }
```

정기 매칭 없음(주말)이라도 구글 신호만으로 `base`가 계산되고, 8/8이 더 이상 sparse가 아니게 된다.

---

### S-9. 정기+구글이 겹칠 때 개별 오버라이드로 최종 결정 — 우선순위 확인

8/6(목)에 정기(R1: 저녁 `POSSIBLE`)는 있지만, 그날 저녁 구글 캘린더에 "동창회" 일정이 잡혀 구글은 저녁을 busy로 본다. 유저는 "동창회는 취소해도 되니 저녁엔 여행 갈 수 있다"고 **명시적으로 오버라이드**한다.

**전제 DB:** `google_calendar_busy_day`(U, 2026-08-06): `morning_busy=false, afternoon_busy=false, evening_busy=true`. `personal_schedule` 8/6 없음(S-5에서 삭제된 이후 상태 재사용).

**참고(실제 별도 API 호출이 아니라 "오버라이드 등록 전" 상태 비교용):** 이 시점에 `GET /calendar`를 호출해도 — 정기·개별·구글을 항상 한 번에 다 합쳐서 응답한다는 원칙은 그대로다. 다만 아직 개별 오버라이드가 없으니 결과는 정기⊕구글까지만 반영된 값이 나온다:

```json
{ "date": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "IMPOSSIBLE", "uncertain": false }
```

저녁은 정기=`POSSIBLE`이지만 구글=`busy`라서 OR 병합으로 `IMPOSSIBLE`이 된다.

**API(오버라이드 등록):** `PATCH /users/schedule/personal`

```json
{ "items": [ { "scheduleDate": "2026-08-06", "morningStatus": null, "afternoonStatus": null, "eveningStatus": "POSSIBLE", "uncertain": false } ] }
```

**DB:** `personal_schedule`(U, 2026-08-06): `morning_status=NULL, afternoon_status=NULL, evening_status=POSSIBLE`

**조회 결과:**

```json
{ "date": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false }
```

**핵심:** 저녁은 정기(`POSSIBLE`)·구글(busy=`IMPOSSIBLE`) 둘 다 자동 계산값일 뿐이고, **개별 오버라이드는 이 자동 계산 결과 전체(정기⊕구글)를 이긴다.** 아침·오후는 오버라이드가 없어 여전히 정기값 그대로.

---

### S-10. 구글 캘린더 연동 해제 — 개별·정기는 그대로, 구글 신호만 사라짐

S-9 상태에서 유저가 구글 캘린더 연동을 끊는다.

**API:** `DELETE /api/v1/users/google-calendar`

**DB:** `google_calendar_credential`·`google_calendar_busy_day`(해당 user의 모든 행) **삭제**. `regular_schedule`·`personal_schedule`은 **전혀 건드리지 않음**.

**조회 결과(같은 8/6):**

```json
{ "date": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false }
```

저녁의 개별 오버라이드(`POSSIBLE`)는 그대로 남아 최종값도 그대로 `POSSIBLE`이다 — 다만 이제 그 결과에 구글의 영향은 없다(구글 데이터 자체가 없으므로). 아침·오후는 정기값(`IMPOSSIBLE`) 그대로.

---

### S-11. 여행방 멤버 달력에도 동일하게 반영

S-9 상태(8/6 저녁 오버라이드 `POSSIBLE`, 구글 저녁 busy)에서, U가 멤버로 있는 여행방의 희망 기간이 8/6을 포함한다.

**API:** `GET /trips/{tripId}/members/schedule-calendar`

**응답(U 항목만 발췌):**

```json
{ "userId": "550e8400-e29b-41d4-a716-446655440000", "days": [ { "date": "2026-08-06", "morningStatus": "IMPOSSIBLE", "afternoonStatus": "IMPOSSIBLE", "eveningStatus": "POSSIBLE", "uncertain": false } ] }
```

`TripMemberQueryService`가 `TripServiceSupport.resolveMergedSchedule`(= 본인 달력과 동일한 O1 알고리즘)을 그대로 재사용하므로, 본인 달력과 **완전히 같은 값**이 나온다 — 여기서만 다른 병합 로직이 있으면 안 된다(C1 원칙).

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-TRIP-002 | 슬롯 단위 가능/불가 — 개별은 이제 "슬롯 단위 오버라이드" | `ScheduleCalendarResolver` |
| BR-TRIP-003 | `uncertain`은 여전히 날짜 단위(슬롯별 아님), 오버라이드 유무와 무관하게 독립적으로 표시 | `PersonalSchedule.uncertain` |
| BR-TRIP-004 | 그룹/타인 조회 = 정기+개별+구글을 합친 최종 슬롯값만 (원본 레이어 노출 안 함, 기존 R4 유지) | `MemberScheduleCalendarResponse` |
| BR-USER-008 | 정기·개별 모두 User 전역, trip은 조회 컨텍스트만 (불변) | — |

## 검증 시나리오

### 정상

- [ ] 정기만 있고 개별 없음 → 정기 계산값 그대로(기존과 동일)
- [ ] 정기(아침 IMPOSSIBLE) + 개별 오버라이드(오후만 IMPOSSIBLE, 아침·저녁 null) → **아침 IMPOSSIBLE(정기 유지) / 오후 IMPOSSIBLE(오버라이드) / 저녁은 정기 계산값** — 이번 스펙의 핵심 시나리오
- [ ] 개별이 슬롯 3개 전부 명시(예: 전부 IMPOSSIBLE) → 정기·구글과 무관하게 전부 오버라이드값
- [ ] 개별 row가 슬롯 3개 전부 `null` + `uncertain=true` → 슬롯은 정기+구글 계산값 그대로, `uncertain`만 true로 표시
- [ ] 정기 없음(주말) + 개별 없음 + 구글 데이터 없음 → sparse omit(기존과 동일)
- [ ] 정기 없음 + 구글만 busy → 그 슬롯 IMPOSSIBLE(구글 단독, 기존 로직 그대로 유지)
- [ ] 이미 오버라이드된 슬롯 하나만 다시 `null`로 PATCH → 그 슬롯만 오버라이드 해제(정기+구글 값으로 복귀), 나머지 오버라이드는 유지
- [ ] 정기 복수 겹침(R2=A) + 개별 오버라이드 없음 → 기존 R2 결과 그대로

### 엣지 · 실패

- [ ] 슬롯 3개 전부 `null` + `uncertain=false` PATCH → 해당 날짜 row 삭제(CLEAR), 이미 없는 날짜면 idempotent(에러 아님)
- [ ] `items` 비어 있음 → 400 `INVALID_INPUT`(기존과 동일)
- [ ] 슬롯 값이 `null`이 아닌데 `POSSIBLE`/`IMPOSSIBLE` 외의 값 → 400 `INVALID_INPUT`(기존과 동일, null 허용만 추가됨)

### 수동 / 통합

- [ ] `PATCH /personal` (부분 오버라이드) → `GET /calendar` 라운드트립으로 나머지 슬롯이 정기값 유지되는지 확인
- [ ] 여행방 멤버 달력(`GET /trips/{tripId}/members/schedule-calendar`)도 동일한 부분 오버라이드가 반영되는지 확인(공용 resolver 재사용 검증)

## 완료 기준

- [ ] `./gradlew test` 통과 (`user.schedule.*`, `trip.service.TripMemberQueryService*`, `trip.service.TripScheduleSnapshotService*`)
- [ ] `./gradlew build` 성공
- [ ] 위 검증 시나리오 전부 테스트로 커버
- [ ] OpenAPI `@Schema`(`PersonalScheduleItem` 슬롯 3필드 nullable) 반영
- [ ] `docs/architecture/erd.md`·`schedule-unified.md`·`schedule-calendar-resolve.md`·`docs/product/fe-context/schedule-calendar-merge.md` 동기화
- [ ] 커밋 본문에 `Breaking-Change-Reason` 트레일러

## 리스크·미결정 (2026-07-29 전부 확정)

| 항목 | 확정 | 비고 |
|------|------|------|
| `personal_schedule` 테이블·엔티티·API 경로(`/schedule/personal`) 리네임 여부 | **유지** | 이름 그대로 간다. FE 경로·문서 리네임 비용 대비 실익 없음 — 나중에 실제로 혼동이 반복되면 재검토 |
| "기본값으로 되돌리기" 전용 UI/버튼 | **없음(Out of Scope)** | 제품에 그런 버튼 자체가 없다. 슬롯 3개 `null` + `uncertain=false`로 CLEAR되는 동작은 API 레벨의 **부수 효과**로만 존재하고, 별도 "리셋" 기능으로 노출하지 않는다 |
| 기존 FE(슬롯 3개를 항상 프리필해서 보내는 화면)는 그대로 동작하는가 | **호환됨** | 3개 슬롯을 항상 명시적으로 채워 보내는 기존 방식은 "3개 다 오버라이드"로 저장되어 결과가 기존과 동일 — 단, "3개 다 `POSSIBLE`로 보내면 삭제"라는 구 관례에 의존한 화면이 있다면 그 화면만 별도 점검 필요 |
| `PATCH` 응답(`PersonalScheduleItemResponse`)의 슬롯 값 | **최종 확정값**(옵션 A) | `GET /calendar`처럼 정기+구글까지 반영한 확정값으로 내려준다. nullable은 저장 계층 전용 — API 응답에는 노출 안 함 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-29 | Draft 초안 — S1(개별 전체 대체) → O1(슬롯 단위 오버라이드) 전환. 대안 비교(in-place nullable vs 별도 테이블) 포함 |
| 2026-07-29 | **Approved** — 리네임 안 함(이름 유지) · "기본값으로 되돌리기" 버튼 없음(Out of Scope, CLEAR는 부수 효과로만 존재) · PATCH 응답은 최종 확정값(옵션 A) |
| 2026-07-29 | "유저 시나리오(상세)" 절 추가(S-1~S-11) — API 호출·DB row·조회 응답을 단계별로 추적, 구글 캘린더 연동 케이스(S-8~S-10) 포함 |
