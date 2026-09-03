# 방 입장 전 달력 윈도우 — 미가입 참여자가 여행 기간을 조회하지 못하는 문제

> 상태: **Implemented** (2026-08-18) — 본래 증상(미가입 참여자에게 멤버 row가 없어 C1 상한이 확장되지 않던 문제)은 `#114`가 `join`을 일정 플로우 맨 앞으로 옮기면서 **자연히 해소**됐다(`SCHEDULE_PENDING` row도 `findMaxOngoingEndRangeByUserId` 계산에 포함된다). 남아 있던 **`GET /calendar` ↔ `PATCH /personal` 윈도우 검증 비대칭**은 **해결안 D(저장에도 같은 윈도우 적용)**로 확정·구현했다. 구 해결안 A/B는 불필요해졌다(B는 hold 폐지로 성립 자체가 불가)
> MVP: 해당 없음 (기존 계약의 빈틈 보완)
> GitHub: **[#110](https://github.com/Central-MakeUs/TripFit-server/issues/110)**
> 선행: [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) (#37 · R4 #53) — 본 스펙은 그 C1 윈도우의 예외 케이스
> related: [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) D-JOIN-TRIP-FLOW · [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md)

## 목표

여행방 입장 플로우(정기→개별)에서 **초대받은 참여자가 아직 `join` 전이라** 그 방의 희망 기간을 마이페이지 달력 윈도우(C1)로 조회하지 못하는 구간을 없앤다.

## 배경 — 무엇이 문제인가

C1 윈도우 상한은 `max(today+2년−1, 사용자가 **참여 중**인 ONGOING 여행 endRange 최댓값)`이다 (R4, [#53](https://github.com/Central-MakeUs/TripFit-server/issues/53)). "참여 중"의 판정은 `TripMember` row 존재 여부다:

```java
// TripMemberRepository.findMaxOngoingEndRangeByUserId — 발췌
SELECT MAX(tm.trip.endRange) FROM TripMember tm
WHERE tm.user.id = :userId
  AND tm.deletedAt IS NULL
  AND tm.trip.deletedAt IS NULL
  AND tm.trip.status = ONGOING
```

**방장은 해당 없음** — `POST /trips` 시점에 `SCHEDULE_PENDING` 멤버 row가 즉시 INSERT되므로(`TripCommandService`), 일정 플로우에 들어갈 때 이미 그 방이 상한 계산에 포함된다.

**참여자는 `POST /trips/join` **전에** 일정 플로우를 진행하는데**(D-JOIN-TRIP-FLOW), 이때는 멤버 row가 없어 그 방의 `endRange`가 상한에 반영되지 않는다. 따라서 방의 `endRange`가 `today+2년−1`보다 뒤면 그 구간을 조회할 수 없다.

`Trip` 생성 시 `endRange`에 **상한 검증이 없다**(`startRange <= endRange`와 기간 길이만 검증) — 즉 2년 밖 여행방을 만드는 것 자체는 막히지 않는다.

### 조회는 막히는데 저장은 되는 비대칭 (부수 발견)

| API | 윈도우 검증 |
|-----|------------|
| `GET /api/v1/users/schedule/calendar` | **있음** — `validateCalendarDateRange`, 범위 밖이면 400 `INVALID_INPUT` |
| `PATCH /api/v1/users/schedule/personal` | **없음** — `validatePersonalItem`은 슬롯 값·중복 날짜만 검사, 날짜 상한 미검사 |

→ 2년 밖 날짜의 개별 일정을 **저장할 수는 있는데 조회는 400**이 된다. 미가입 참여자 시나리오와 무관하게도 성립하는 계약 비대칭이다. 어느 쪽에 맞출지(저장도 막을지 / 조회도 열지)는 아래 A안·B안 선택과 함께 결정한다.

## 영향 범위

| 항목 | 내용 |
|------|------|
| 증상 | 초대받은 참여자의 개별 일정 화면에서 프리필 조회가 400 → 기존 일정을 보여주지 못함 |
| 발생 조건 | 초대받은 방의 `endRange` > `today+2년−1` **AND** 사용자가 참여 중인 다른 ONGOING 방 중 그보다 뒤에 끝나는 방이 없음 |
| 빈도 | **낮음** — 2년 이상 뒤의 여행방을 만드는 경우 자체가 드물다 |
| 데이터 손상 | 없음 (조회 실패만) |
| 우회 | 없음 — `join`을 먼저 하면 해결되지만 플로우 순서상 불가 |

## 해결안 (택1 — 승인 시 확정)

| 안 | 내용 | 장점 | 단점 |
|----|------|------|------|
| **A. 초대 코드로 윈도우 확장** | 일정 플로우 진입 시 `inviteCode`(또는 `tripId`)를 `GET /calendar`에 선택 파라미터로 받아, 그 방이 ONGOING이면 `endRange`까지 상한 확장 | 원인 그대로 해결 · 방장/참여자 대칭 | API 계약 변경(파라미터 추가) · 비멤버가 방 기간을 유추할 수 있음(초대 코드 보유자로 한정되므로 경미) |
| **B. `join/hold` 보유자를 상한 계산에 포함** | 참여자는 일정 플로우 전에 `POST /trips/join/hold`로 정원 hold를 잡는다([`trip-join-capacity-hold.md`](trip-join-capacity-hold.md) #35) — 그 hold를 R4 상한 계산의 트리거로 추가 | 클라 계약 무변경 · hold는 이미 "그 방에 들어가려는 의사" 표시 | #35 hold 구현·TTL(10분)에 의존 · hold 만료 시 상한이 도로 줄어드는 경계 |
| **C. `Trip.endRange` 상한 검증 추가** | 방 생성 시 `endRange <= today+2년−1` 강제 | 서버 로직 단순 · 문제 자체가 성립 불가 | 제품 정책 변경(먼 미래 여행방 금지) — **기획 확인 필수** |

> **권장:** A 또는 B. C는 제품 제약을 새로 거는 것이라 기획 확정 없이 채택하지 않는다.

### 최종 결론 (2026-08-18 사용자 확정)

**A·B·C 모두 채택하지 않는다.** `#114`가 `join`을 일정 플로우 맨 앞으로 옮기면서 참여자도 일정 화면 진입 시점에 이미 `SCHEDULE_PENDING` 멤버 row를 갖게 돼, R4 상한이 그 방 `endRange`까지 자동으로 확장된다 — A가 풀려던 문제가 사라졌고, B는 hold 자체가 폐지돼 성립하지 않는다.

**D. 저장에도 조회와 같은 윈도우를 적용한다 (채택).** `PATCH /users/schedule/personal`이 저장 전에 `validateCalendarDateRange`를 호출해, 요청 날짜가 `[today, max(today+2년−1, 참여 중 ONGOING endRange 최댓값)]` 밖이면 400 `INVALID_INPUT`으로 거부한다.

- **상한 계산은 조회와 같은 함수(`ScheduleService.resolveCalendarWindowEnd`)를 재사용한다** — R4의 "참여 중 ONGOING 여행 종료일까지 확장" 규칙이 저장에도 그대로 적용되고, 두 API가 갈라질 수 없다.
- **지난 날짜도 같은 이유로 막는다.** 조회 구간이 `today`부터라 과거 날짜에 저장된 개별 일정 역시 다시 조회할 수 없다 — 상한만 막고 하한을 두면 같은 결함이 반대쪽에 남는다.
- **한 항목이라도 구간 밖이면 요청 전체를 거부한다**(부분 저장 없음) — 기존 검증(중복 날짜·슬롯 누락)과 같은 방식.

**알려진 한계 (이번 범위 밖):** 윈도우는 고정값이 아니라 조회 시점에 계산된다. 저장 시점엔 구간 안이었어도 그 방이 `EXPIRED`로 바뀌거나 사용자가 방을 나가면 상한이 다시 `today+2년−1`로 줄어, 이미 저장된 날짜가 구간 밖으로 밀려 조회되지 않을 수 있다. 저장 검증만으로는 없앨 수 없고(상한이 움직이는 값인 이상 구조적), 해소하려면 "이미 저장된 일정은 상한과 무관하게 조회 허용" 같은 별도 정책이 필요하다. 빈도가 낮아 이번엔 명시만 하고 다루지 않는다.

## 요구사항

### Must Have

- [x] 해결안 확정 (승인 게이트) — **D 채택** (2026-08-18)
- [x] 미가입 참여자가 초대받은 방의 희망 기간을 개별 일정 화면에서 조회 가능 — `#114`로 해소
- [x] 방장 경로 회귀 없음 (`SCHEDULE_PENDING` 멤버 row로 이미 확장되는 동작 유지)
- [x] `GET /calendar`와 `PATCH /personal`의 윈도우 검증 비대칭 해소 — `upsertPersonal`이 `validateCalendarDateRange` 호출
- [x] 계약 변경 `Breaking-Change-Reason` 트레일러 + `trip-schedule-calendar-window.md` R4 amend
- [x] `./gradlew test`

### Out of Scope

- C1 과거 날짜 열람·수정 (기존 Out 유지)
- 여행방 달력(C2/C3) 윈도우 — 멤버 전용이라 본 문제와 무관
- Google Calendar 동기화 윈도우 — 같은 계산을 재사용하나, 미가입 방은 동기화 대상이 아님
- `Trip.endRange` 상한 정책 자체의 제품 결정 (C안 채택 시 별도 기획 확인)

## 완료 기준

- [x] 해결안 확정 후 Approved → Implemented (2026-08-18)
- [x] 구현 + 회귀 테스트 — 상한 밖 400 · 과거 날짜 400 · ONGOING 여행으로 확장된 구간은 저장 허용
- [x] `trip-schedule-calendar-window.md` R4 절 amend
- [x] `docs/specs/README.md` 상태 갱신

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-18 | **해결안 D 확정·구현 (`#110`)** — 본래 증상은 `#114`로 해소돼 A/B/C를 모두 폐기하고, 남은 `GET`/`PATCH` 비대칭을 "저장에도 같은 윈도우 적용"으로 해소. 상한 계산은 조회와 같은 함수를 재사용해 R4 확장 규칙이 저장에도 동일하게 걸린다. 과거 날짜도 같은 이유로 거부. 윈도우가 나중에 줄어들면 기존 저장분이 조회 밖으로 밀리는 한계는 알려진 사항으로 명시 |

| 날짜 | 변경 |
|------|------|
| 2026-08-16 | **Draft** — Figma 방 입장 플로우 대조 중 발견. 미가입 참여자의 C1 윈도우 공백 + `GET`/`PATCH` 윈도우 검증 비대칭 기록, 해결안 A/B/C 제시 |
