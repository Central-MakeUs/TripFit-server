# 일정 달력 컨텍스트 — 마이페이지 · 여행방 조회 윈도우

> wave: 2  
> 상태: **Approved** (2026-07-21)  
> MVP: In scope · **Wave 2 Must** ([#30](https://github.com/Central-MakeUs/TripFit-server/issues/30))  
> GitHub: **[#37](https://github.com/Central-MakeUs/TripFit-server/issues/37)**  
> 선행: [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md) (#17), [`trip-room-api.md`](trip-room-api.md) (#12)  
> related: [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md) (#38)  
> 용어: [`glossary.md`](../../product/glossary.md)

## 목표

화면 컨텍스트별로 **누구의 일정을 · 어느 날짜까지 · 수정 가능 여부**를 서버·클라 계약으로 고정한다.

## 제품 확정

### C1 — 마이페이지 달력 (“내 일정 입력하기”)

| 항목 | 확정 |
|------|------|
| 데이터 | **본인 전역** 일정만 |
| 조회·수정 가능 기간 | **`today` ~ `max(today+2년−1, 사용자 ONGOING 여행 endRange 최댓값)`** (R4) |
| today **이전** (R2) | 요청에 포함 시 **400 `INVALID_INPUT`**. FE가 clamp |
| 여행 칩 | 참여 ∧ **`ONGOING`** 방 이름 |
| 칩 데이터 (R3) | **`GET /trips?scope=ongoing` 재사용** (`tripId`, `name`, `startRange`) — 전용 API 없음 |
| 칩 클릭 | FE가 `startRange`의 **연·월**로 스크롤 (서버가 구간을 자르지 않음) |

### C2 — 여행방 달력 (ONGOING)

| 항목 | 확정 |
|------|------|
| 데이터 | 참여 **전원** 정기+개별 합친 달력 · **live** |
| 조회 기간 | **`startRange` ~ `endRange`** |
| 수정 | 본인 전역 regular/personal CRUD |

### C3 — 여행방 달력 (CONFIRMED / EXPIRED)

| 항목 | 확정 |
|------|------|
| 데이터 | **snapshot** — [#38](https://github.com/Central-MakeUs/TripFit-server/issues/38) |
| 조회 기간 | **`startRange` ~ `endRange`** |
| 수정 | **읽기 전용** |

### CANCELED (R1) — **#48 Implemented, 해당 없음**

`TripStatus.CANCELED`가 `#48`에서 enum 자체 삭제됨에 따라, 아래 R1 결정이 가리키던 상태가 더 이상 존재하지 않는다. `CANCELED`로 진입할 방법이 없으므로 `members/schedule-calendar` 거부 분기 자체도 코드에서 제거됨(당시 결정은 이력으로 보존).

| 항목 | 확정 (당시) |
|------|------|
| 달력 | **Out** — `members/schedule-calendar` **거부** (403/409, 기존 D4와 맞춤). snapshot 대상 아님. UI 비노출 |

> 구 W1(여행 시작~+2년) **폐기**. +2년은 **마이페이지(C1)만**.

### C1 상한 확장 (R4, 신규) — ONGOING 여행이 +2년 밖으로 걸칠 때

기본 상한(`today+2년−1`)보다 뒤에 끝나는 **참여 중 `ONGOING`** 여행방이 있으면, 그 여행의 **`endRange`(희망 기간 종료일)**까지 마이페이지 달력 조회·수정 상한을 늘린다. 여러 건이면 **`endRange` 최댓값**을 쓴다. `CONFIRMED`/`EXPIRED`는 상한 계산에 포함하지 않는다(=C1은 여전히 본인 전역 일정, ONGOING 여부는 상한 계산의 트리거일 뿐).

| 항목 | 확정 |
|------|------|
| 트리거 | 본인이 **활성 참여**(`TripMember.deletedAt IS NULL`) 중인 `Trip.status = ONGOING` |
| 확장 기준 필드 | `endRange` (`confirmedStartDate`/`confirmedEndDate`는 ONGOING 동안 보통 null) |
| 다중 ONGOING | `endRange` **최댓값** 하나로 전역 상한을 정함 (여행별 개별 허용 아님) |
| Google Calendar 동기화 윈도우 | **동일 적용** — `GoogleCalendarService.syncUserInternal`의 `windowEnd`도 같은 계산 재사용 |
| today 이전 (R2) | 영향 없음 — 하한은 그대로 `today` |

## 요구사항

### Must Have

- [x] C1: calendar 검증 = 요청 구간 ⊆ `[today, today+2y−1]` · today 이전 포함 시 400 (#17 A1 amend)
- [x] C1 상한 확장 (R4): 요청 구간 ⊆ `[today, max(today+2y−1, 사용자 ONGOING endRange 최댓값)]` — [#53](https://github.com/Central-MakeUs/TripFit-server/issues/53) (Closed)
- [x] C1 칩: `scope=ongoing` 문서·OpenAPI에 마이페이지 인덱싱 용도 명시 (신설 API 없음)
- [x] C2: live · 희망 기간 (현행 유지)
- [ ] C3: snapshot — **#38**
- [x] CANCELED: schedule-calendar 거부 — **#48**에서 enum 삭제, 해당 분기도 제거
- [x] `./gradlew test`

### Out of Scope

- 칩 클릭 시 서버 재조회/구간 재단
- 칩 전용 API · calendar 임베드 `ongoingTrips[]` (Nice 가능, Must 아님)
- CANCELED snapshot
- Google Calendar
- C1 과거 날짜 열람/수정

## API

| 컨텍스트 | Method | Path | 기간 |
|----------|--------|------|------|
| C1 | GET | `/api/v1/users/schedule/calendar` | ⊆ today~max(+2y−1, ONGOING endRange 최댓값) |
| C1 칩 | GET | `/api/v1/trips?scope=ongoing` | — |
| C2/C3 | GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | 희망 기간 |

## 충돌·잔여 해소

| ID | 확정 |
|----|------|
| X1~X5 | 2026-07-21 재확정 (C1=today+2년 · 방=희망 기간) |
| **R1** | CANCELED 달력 Out · 조회 거부 — **#48 Implemented**: enum 삭제로 해당 없음 |
| **R2** | today 이전 → 400 |
| **R3** | `scope=ongoing` 재사용 |
| **R4** | ONGOING 여행 `endRange`가 `today+2년−1` 밖이면 그 최댓값까지 C1 상한 확장 (2026-07-27 Approved) — [#53](https://github.com/Central-MakeUs/TripFit-server/issues/53) |

## 완료 기준

- [x] 제품·잔여 **Approved**
- [x] 코드·테스트 · #17 A1 amend (#37 범위 · C3 snapshot은 #38)
- [ ] #38 Implemented와 C3 연동
- [x] R4 구현 · `./gradlew test` — [#53](https://github.com/Central-MakeUs/TripFit-server/issues/53)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-27 | **R4 Approved** — C1 상한 `max(today+2y−1, 사용자 ONGOING endRange 최댓값)`로 확장. [#53](https://github.com/Central-MakeUs/TripFit-server/issues/53) |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제(R1 해당 없음), `TERMINATED` → `EXPIRED` 리네임 |
| 2026-07-21 | Draft · 재확정 C1/C2/C3 |
| 2026-07-21 | **Approved** — R1=A · R2=A · R3=A |
| 2026-07-21 | **#37 구현** — C1 윈도우 · CANCELED 거부 · OpenAPI · `./gradlew test` |
