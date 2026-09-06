# 추천 결과 계산 로직 (후보 윈도우 · 모드별 스코어링 · 동점)

> wave: 2
> implements: BR-TRIP-005, BR-TRIP-011, BR-TRIP-012
> deferred: 공휴일 API 연동 (주말만 우선, static table/외부 API 방식은 `[미정]`) · 연차 신청 가능 시점(`vacationApplyPeriod`) 반영 (`[미정]`, 아래 amend 절)
> 상태: **Approved**(2026-07-30 최초 확정본) — **2026-08-15 연차/반차 자동 반영 amend 구현 완료**(`fix/105-vacation-attendance` 브랜치, `./gradlew test` 475건 통과), PR·머지 전 최종 확인 대기
> GitHub: **#50**(최초 확정본, Closed) · amend는 **#105**
> 선행: [`trip-recommendation.md`](trip-recommendation.md) (#13, `Recommendation` 엔티티·Repository·Controller stub) · [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md) (#17, Closed)

## 블로커 해결 이력 (2026-08-15)

구현 중 "연차 예산을 어느 `RegularSchedule` 행 기준으로 볼지" 블로커를 발견해 중단했다가, 다음 확인을 거쳐 해결하고 구현을 재개했다.

1. **프론트엔드 직접 확인:** `TripFit-client`의 `useSaveRegularSchedule.ts`가 "기본 정보 관리" 화면에서 `annualLeaveCount`/`leaveNoticeDays`/`includeHalfDayHoliday`(연차 관련 4개 값)를 **화면 전체에 하나뿐인 공유 값**으로 관리하다가, 저장 시점에 사용자의 **모든** `RegularSchedule` 행에 동일한 값을 실어 PATCH/POST하는 것을 확인 — 실제 앱에서는 항상 모든 행이 같은 값으로 유지된다.
2. 이에 따라 **"첫 번째(가장 먼저 등록된, `createdAt` 오름차순) `RegularSchedule` 행 기준"**(후보안 1)으로 결정 — 실제 앱 동작과 정확히 일치하며, 여러 행이 존재해도 결과가 달라지지 않는다(프론트가 항상 동기화하므로).
3. **근본 원인(필드 위치가 `User`가 아니라 `RegularSchedule`)은 별도 이슈 `#52`로 분리** — API 계약이 바뀌는 breaking change라 프론트 담당자 부재중에는 진행하지 않기로 함(2026-08-15 사용자 결정). `RecommendationEngine`의 "첫 번째 행 기준" 로직은 `#52` 완료 시 제거 예정(`primaryVacationSchedule` 메서드에 표시).

## 목표

`#13`이 만든 API 계약(`POST /trips/{tripId}/recommendations`)이 실제로 반환할 **TOP 3 계산 로직**을 만든다. `#13`은 Controller·DTO·엔티티·상태 전이·hard DELETE 트리거를 담당하고, 이 스펙은 "무엇을 저장할지" — 즉 후보 생성부터 순위 산출까지의 계산만 담당한다.

## 배경

- `#13` 작업 중 사용자 요청으로 분리 확정(2026-07-24): API 껍데기와 계산 로직을 별도 이슈로 관리
- **2026-07-30 확정:** 기획자 알고리즘 확정본([`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md))을 그대로 반영 — 패널티 구간표·모드별 가중치·최종점수 공식·동점 기준까지 전부 확정값. **이전 초안**(이 문서 2026-07-24판)의 `w1*attendRate - w2*vacationDays - w3*tbdRate` 식·`ALL_ATTEND` **하드 필터**·`NO_RECOMMENDATION_CANDIDATES` 에러·동점 기준 "주말·공휴일"은 **전부 폐기**하고 이 버전으로 대체한다
- `#13`은 이 스펙이 끝나기 전까지 `POST /recommendations`를 플레이스홀더 값으로 응답해 API 계약만 검증한다
- Wave 2 MVP DoD("추천으로 최종 날짜 확정")가 실제로 동작하려면 `#13`과 이 이슈 **둘 다** Closed 필요 — Wave Backlog `#30` Must에 반영
- **2026-07-30 화면 확인(추천 결과 카드, 방장 뷰):** 카드에 `참석률(%)`·`불확실 일정 인원`·`부분 참여 인원`·`연차 일수`가 노출됨 — 응답 DTO·`Recommendation` 엔티티에 이 4개 원시 지표를 그대로 담아야 한다(자연어 `reason`/`riskNote` 자동생성 Nice to have는 화면에 없어 **폐기**). 상세: `trip-recommendation.md` 데이터 모델 절
- **2026-08-15 amend 배경:** 기획자가 `new_problem/p1.md`로 전달한 리포트 — "근무일과 여행이 겹쳐도 연차가 남아 있으면 참석 가능으로 판단해야 하는데, 현재는 연차 사용 가능 여부가 참석/불참 판단에 반영되지 않는다"는 이슈. 코드 조사 결과, `RegularSchedule.maxVacationDays`(여행당 사용 가능 최대 연차 일수)·`halfVacationAvailable`(반차 가능 여부) 필드는 이미 저장되고 있지만 `RecommendationEngine` 어디에서도 읽히지 않고 있었고, "연차 계산"은 사용자가 개별 일정(`PersonalSchedule`)에 **미리 수동으로 override 해둔 날짜**에 대해서만 사후적으로 몇 일 썼는지 집계(점수 페널티용)할 뿐, 근무일과 겹치는 후보 구간을 "연차를 쓰면 참석 가능"으로 **자동 판단하는 로직 자체가 없었다.** 이 amend는 이 갭을 메운다. `new_problem/test_set.md`(부스 이벤트용 5인 테스트셋)의 기대 1순위(10/24~10/26)도 이 자동 판단이 있어야 나오는 결과다.

## 변경 범위 (2026-08-15 amend — 연차/반차 자동 반영)

### ADDED

- 참여자 3분류 **이전에** 실행되는 "연차/반차 자동 전환 시뮬레이션" 단계 — 아래 "연차/반차 자동 반영 규칙" 절
- `halfVacationAvailable=false` 사용자도 반나절만 근무와 겹치면 **종일 연차 1.0일**로 그 슬롯을 없앨 수 있다는 명시적 규칙(기존엔 반차 불가 사용자의 반나절 겹침 처리 자체가 미정의)

### MODIFIED

- **연차 계산** (Must Have) — (변경 전) "개별 일정으로 덮어써 참석 가능하게 만든 날만 집계"(사용자가 해당 날짜를 미리 수동 override 해둔 경우만 인식) → (변경 후) "자동 시뮬레이션이 선택한 전환 슬롯 + 기존처럼 사용자가 미리 수동 override 해둔 슬롯을 함께 집계"(수동 override 기능은 그대로 유지, 자동 탐색이 추가되는 것)
- **참여자 3분류** (Must Have) — (변경 전) `ScheduleCalendarResolver.resolve(...)` 결과를 그대로 판정 → (변경 후) 그 결과에 연차 시뮬레이션을 적용한 뒤의 슬롯으로 판정. 판정 기준(전체 슬롯 100% 또는 ⌈50%⌉ 이상 연속)은 변경 없음

### REMOVED

- 해당 없음 (기존 필드·API·DTO 삭제 없음 — `maxVacationDays`/`halfVacationAvailable`를 처음으로 "사용"하게 되는 것이지, 기존 계약을 없애는 게 아님)

## 요구사항

### Must Have

- [x] **후보 윈도우 생성:** `[trip.startRange, trip.endRange]` 내에서 길이 = `trip.durationDays`인 모든 연속 `[startDate, endDate]`를 하루씩 슬라이딩하며 생성. `durationDays`가 null이면 계산 자체 불가(호출 측 `#13`이 사전 검증, 이 스펙은 non-null 전제)
- [x] **입력 resolve 재사용:** `ScheduleCalendarResolver.resolve(...)`(`#17`, static utility)를 그대로 호출해 멤버×날짜×슬롯(오전/오후/저녁) effective(가능/불가) + 날짜 단위 `uncertain`을 산출 — 별도 병합 로직 신설 금지(C1)
- [x] **응답 참여자 판정(2026-07-30 구현 확정 — 최초 초안에서 정정):** **이 방의 ACTIVE 멤버 전원**이 응답 참여자다. 별도의 "resolve 결과가 비어 있으면 미응답 제외" 필터는 두지 않는다 — activate/join 시점에 일정을 하나도 입력하지 않은 멤버는 `User.isAllFree=true`로 명시적으로 "전부 가능"이 확정된 상태(`markAllFreeIfNoSchedules`)라, resolve 결과가 비어 있는 것과 "미응답"은 다른 의미이기 때문. 이 방식이면 응답 참여자 0명은 자연히 발생하지 않는다(방장은 항상 ACTIVE 상태로만 추천을 생성할 수 있음)
- [x] **연차/반차 자동 전환 시뮬레이션 (신규, 2026-08-15 amend — 참여자 3분류보다 먼저 실행):** 참여자별로 후보 윈도우 내 "정기 근무(오전/오후)만으로 IMPOSSIBLE"인 슬롯(개별 일정 override나 구글 캘린더 busy로 인한 IMPOSSIBLE은 대상 아님)을 후보로 모아, `RegularSchedule.maxVacationDays`(여행당 사용 가능 최대 연차 일수) 예산 안에서 **"가능(POSSIBLE) 슬롯의 최장 연속 구간"이 가장 길어지는 전환 조합을 탐색**해 적용한다. 오전·오후가 둘 다 풀린 날은 저녁도(정기 근무로만 막혀있던 경우) 추가 비용 없이 함께 연다. `RecommendationEngine.applyVacationSimulation`/`openFreeEvenings`로 구현. 상세 규칙: 아래 "연차/반차 자동 반영 규칙" 절
- [x] **참여자 3분류(2026-08-15 amend — 판정 대상 슬롯 변경):** 위 시뮬레이션을 적용한 뒤의 슬롯으로 판정한다(판정 기준 자체는 변경 없음, 후보 윈도우 기준, 응답 참여자만 대상):
  - 전체 참석: 후보 윈도우의 모든 슬롯이 가능(POSSIBLE)
  - 부분 참석: 전체 슬롯 수(`durationDays × 3`)의 **⌈50%⌉ 이상**을 **하나의 연속된 구간**으로 참석 가능(늦참·조기귀가만 인정 — 중간 이탈 후 재합류는 불인정)
  - 불참: 위 두 조건을 만족하지 못하는 나머지
- [x] **불확실 인원:** 후보 윈도우 내 하루 이상 `personal_schedule.uncertain=true`인 응답 참여자 수(1인당 최대 1로 카운트, 여러 날짜 선택해도 1명) — 부분 참석과 독립 집계(중복 카운트 가능)
- [x] **연차 계산(2026-08-15 amend — 집계 대상 확장):** 완전 불참자 제외. 위 시뮬레이션에서 **실제로 채택된 전환 슬롯**(+ 기존처럼 사용자가 미리 개별 일정으로 수동 override 해둔 슬롯도 계속 인정)을 오전·오후 중 하나만 해당하면 반차 0.5일, 둘 다 해당하면 종일 1일로 환산해 합산. **저녁 슬롯은 연차 개념에서 제외**(2026-07-30 구현 확정 — `scoring_draft.md`의 "오전 반차·오후 반차·종일 연차" 정의에 저녁이 없어, 저녁 override는 연차 계산에 포함하지 않음). `totalVacationDays`(총 연차 일수)·`vacationMemberCount`(연차 계산 대상 인원 수) 산출
- [x] **평가 항목 4종 패널티** (아래 "패널티 구간표" 절 수치 그대로):
  1. 불참률 = 불참 인원 / 응답 참여자 수
  2. 부분 참석률 = 부분 참석 인원 / 응답 참여자 수
  3. 불확실 인원 비율 = 불확실 인원 / 응답 참여자 수
  4. 1인당 평균 연차 일수 = `totalVacationDays` / `vacationMemberCount` (`vacationMemberCount=0`이면 0일로 취급)
- [x] **모드별 가중치 적용** (아래 "모드별 가중치" 절 수치 그대로) → **최종점수 = 100 - Σ(패널티×가중치)**
- [x] **`ALL_ATTEND`는 하드 필터가 아니다** — 목표 인원 미달 후보를 제외하지 않는다. 불참률·부분 참석 인원 비율 가중치를 크게(5.0/3.0) 둬 점수로만 반영한다(BR-TRIP-011 개정)
- [x] **동점 comparator** (BR-TRIP-012 개정): 1) 불확실 일정 수 적은 순 2) 시작일 빠른 순 (구 기준 "연차→기간→주말·공휴일"은 폐기)
- [x] **Best 3 정렬:** 최종점수 내림차순 → 동점 comparator → 상위 3개
- [x] 계산 결과(TOP 3, 각 `rank`/`startDate`/`endDate`/`attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays`/`score`)를 `#13`의 `RecommendationEngine` 인터페이스로 반환 — 저장(hard DELETE + INSERT)은 `#13` 책임
- [x] **참여자 분류를 참여자별로도 재사용 가능하게 노출** (2026-07-30 "추천 근거" 상세 화면 확인) — `#13`의 `GET .../recommendations/{rank}` 상세 API는 이 스펙의 참여자 3분류 로직을 특정 `[startDate, endDate]` 구간 하나에 대해 **다시 호출**해 참여자별 `attendance`(`FULL_ATTEND`/`PARTIAL_ATTEND`/`NON_ATTEND`)·`uncertainDays`(해당 구간 내 불확실 날짜 수)·`vacationDaysNeeded`(해당 참여자의 필요 연차일수)를 받는다. 이 결과는 저장하지 않고 상세 조회 시 라이브 재계산(카드 목록 응답을 무겁게 만들지 않기 위함) — 계산 자체는 모드에 의존하지 않으므로(모드는 가중치에만 영향) 참여자 3분류·불확실·연차 로직을 모드 파라미터 없이 별도로 호출 가능한 형태로 분리해 둘 것
- [x] `./gradlew test` — `RecommendationEngineTest`: `scoring_draft.md` 부분참석/불참 경계 예시(윤지·은서) 고정 fixture, 전체참석·불확실·연차(반차) 계산, `ALL_ATTEND` 무필터, 동점 comparator 순서. (모드 4개 각각의 rank 1 기대값·패널티 4종 전 구간 경계값 개별 테스트는 후속 보강 여지 있음)

### Out of Scope

- API 요청/응답 DTO·Controller·상태 전이(`ONGOING`↔`CONFIRMED`)·hard DELETE 실행 — `#13`
- 공휴일 API 연동 — 주말만 우선 `[제안]`, static table/외부 API 방식은 `[미정]`
- 알림 발송(BR-NOTI-004) — Wave 3 `#21`
- `attendRate`(카드 표시용 참석률 %) 계산식의 **최종 확정** — 아래 "카드 표시 지표" 절 참고, 화면 역산 기반 추론값이며 기획 확정 대기
- **연차 신청 가능 시점(`vacationApplyPeriod`) 반영 (2026-08-15 amend, `[미정]`)** — `RegularSchedule.vacationApplyPeriod`(1주 전/2주 전/한달 전 등)를 기준으로 "오늘부터 후보 날짜까지 리드타임이 부족하면 그 사용자는 연차 전환 자체를 적용하지 않는다"는 로직은 이번 amend에 포함하지 않는다. `new_problem/p1.md`에 이 필드에 대한 언급이 없고, 범위를 넓히면 "오늘 날짜" 기준 시점 의존성이 추천 계산에 새로 생겨 별도 검토가 필요 — 사용자 확인 후 후속 스펙으로 분리

## 참여자 3분류 — 부분 참석 판정 (확정, [`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md))

1. 후보 윈도우의 전체 일정 수 = `durationDays × 3` (하루 = 오전/오후/저녁 3개)
2. 부분 참석 인정 최소 연속 참석 수 = `⌈전체 일정 수 × 0.5⌉` (소수점 올림)

| 여행 기간 | 전체 일정 수 | 부분 참석 인정 기준 |
| --- | --- | --- |
| 1박 2일 | 6 | 3개 이상 연속 참석 |
| 2박 3일 | 9 | 5개 이상 연속 참석 |
| 3박 4일 | 12 | 6개 이상 연속 참석 |
| 4박 5일 | 15 | 8개 이상 연속 참석 |

연속 참석 구간은 **늦참(앞부분 결손) 또는 조기귀가(뒷부분 결손)** 형태만 인정 — 중간에 이탈했다가 재합류하는 형태(가운데 결손)는 불인정(불참으로 분류).

## 연차/반차 자동 반영 규칙 (2026-08-15 amend, Draft — `new_problem/p1.md` 기반)

참여자 3분류(위 절)를 계산하기 **전에**, 참여자별로 아래 시뮬레이션을 먼저 수행한다.

### 1. 전환 후보 슬롯

`ScheduleCalendarResolver.resolve(...)` 결과 중, 해당 슬롯이 IMPOSSIBLE인 이유가 **오직 정기 근무(오전/오후)와의 겹침뿐**인 슬롯만 전환 후보다. 판별식: `combineImpossibleWins(그날 요일에 매칭되는 RegularSchedule 목록)`이 IMPOSSIBLE이면서, 그 슬롯에 개별 일정(`PersonalSchedule`) override나 구글 캘린더 busy로 인한 별도 IMPOSSIBLE이 **없는** 경우.

- 개별 일정이 그 슬롯을 명시적으로 IMPOSSIBLE로 override했거나(예: 결혼식 참석), 구글 캘린더가 해당 슬롯을 busy로 잡고 있으면 **전환 후보에서 제외** — 연차는 "근무"만 대체할 수 있고, 개인 약속·구글 캘린더 일정은 대체할 수 없다.
- 저녁 슬롯 자체는 전환 후보(예산을 써서 직접 선택하는 대상)에 포함하지 않는다 — 저녁만 따로 골라 연차를 쓸 수는 없다. 다만 오전·오후가 모두 풀리면 아래 "1-1. 저녁 자동 해제" 절에 따라 **추가 비용 없이** 같이 풀릴 수 있다.
- 사용자가 이미 개별 일정으로 **수동 override 해둔** 슬롯(정기 근무 IMPOSSIBLE을 개별 일정 POSSIBLE로 덮어쓴 경우)은 이미 POSSIBLE 상태이므로 시뮬레이션 대상이 아니다 — 그대로 인정하고 연차 계산에도 그대로 집계(기존 동작 유지).

### 1-1. 저녁 자동 해제 (2026-08-15 amend #2 — `test_set.md` 재현 중 발견)

퇴근 시간이 18시 이후인 사용자(예: 10~19시, 12~20시 근무)는 정기 근무 시간이 저녁 슬롯[18:00, 24:00)까지 겹쳐서, 연차와 무관하게 **원래도** 저녁이 IMPOSSIBLE이다(`SlotStatuses.fromTimeRange`). 최초 구현은 연차 시뮬레이션이 오전·오후만 전환하고 저녁은 그대로 둬서, 이런 사용자가 실제로는 그날 연차를 하루 다 썼는데도(오전·오후가 전부 풀렸는데도) 저녁만 계속 막힌 채로 남아 전체 참석 대신 부분 참석/불참으로 잘못 판정되는 문제가 있었다 — `new_problem/test_set.md` B(최은서, 10~19시 근무)·D(김소은, 12~20시 근무) 재현 중 발견(아래 "검증 시나리오" 참고).

**규칙:** 그날 오전·오후가 (수동 override·자동 전환 어느 경로로든) **둘 다** POSSIBLE이 됐고, 저녁의 IMPOSSIBLE 사유가 오직 정기 근무뿐(개별 일정 override·구글 busy 아님)이라면, 저녁도 함께 POSSIBLE로 바꾼다. `RecommendationEngine.openFreeEvenings`로 구현.

- 오전·오후 중 **하나만** POSSIBLE이 된 날(반차만 쓴 경우)은 저녁을 건드리지 않는다 — 나머지 반나절은 여전히 근무 중이라 저녁까지 자동으로 비어있다고 볼 수 없다.
- **정기 일정이 2개 이상이고, 서로 다른 행이 각각 오전/오후·저녁을 나눠 막는 경우(예: 낮 근무 09~18시 + 저녁 알바 19~23시)는 대상이 아니다.** 판별은 `RegularSchedule` **한 행 단독**(`getSlotStatuses()`, 등록 시점에 시작~종료 시각으로 미리 계산돼 저장됨)이 오전/오후 중 하나와 저녁을 **동시에** IMPOSSIBLE로 막고 있는지로 한다(`hasContinuousShiftIntoEvening`) — 즉 "한 근무의 퇴근이 늦어 저녁까지 자연히 이어지는" 경우만 해당하고, "낮 근무와 저녁 알바가 별개 근무"인 경우 낮 근무를 연차로 전환해도 저녁 알바는 별도 근무이므로 그대로 막힌 채 둔다(BR-TRIP-006상 사용자당 정기 일정 여러 행 허용 — 회사 출근 + 저녁 알바처럼 실제로 발생 가능).
- 저녁 자동 해제는 **연차 소모량 계산(`vacationDaysForSpan`, 위 §2)에 영향을 주지 않는다** — 그 계산은 원래부터 오전·오후만 보고 있어서, 저녁이 열리든 안 열리든 같은 값이 나온다.
- **`#52`(연차 필드를 `RegularSchedule`→`User`로 이동)와는 무관.** `#52`는 `maxVacationDays`/`halfVacationAvailable` 두 필드의 저장 위치만 바꾸는 것이고, 사용자당 정기 일정 여러 행 자체는 `#52` 이후에도 그대로 허용된다(BR-TRIP-006). 이 §1-1의 판별은 그 두 필드가 아니라 각 행의 **근무 시간대**(`slotStatuses`)만 보므로, `#52` 완료 여부와 상관없이 그대로 유효하다.

### 2. 전환 비용

- `halfVacationAvailable=true`인 사용자: 오전 전환 0.5일, 오후 전환 0.5일 — 오전·오후를 독립적으로 선택 가능
- `halfVacationAvailable=false`인 사용자: 반차 단위 전환 자체가 불가능하다. 다만 하루 중 오전 또는 오후 **하나만** 근무와 겹쳐도, **종일 연차 1.0일**을 사용해 그 슬롯(반나절)을 없앨 수 있다 — 반차만 못 쓸 뿐, 종일 연차 사용 자체는 항상 가능하다는 전제(2026-08-15 사용자 확인). 하루 중 오전·오후가 모두 근무와 겹치면 동일하게 종일 연차 1.0일로 둘 다 전환.
- **구현 중 발견한 회귀(수정 완료):** 기존 `vacationDaysForSpan`(연차 계산 집계)이 `halfVacationAvailable` 값과 무관하게 "겹치는 슬롯 1개=0.5일"로 계산하고 있었다 — `halfVacationAvailable=false` 사용자가 반나절 하나만 막혀 있어도 실제로는 종일 연차(1.0일)를 써야 하는데 0.5일로 과소 집계됐다. 이번 amend에서 `halfVacationAvailable`을 반영해 workSlotsCovered==1일 때 `halfVacationAvailable ? 0.5 : 1.0`으로 수정 — **자동 전환뿐 아니라 기존 수동 override 경로에도 동일하게 적용**(같은 메서드를 공유하므로 회귀 없이 일관됨)

### 3. 예산과 탐색

예산 = 참여자의 `RegularSchedule.maxVacationDays`(여행당 사용 가능 최대 연차 일수). **어느 행의 값을 쓸지는 가장 먼저 등록된(`createdAt` 오름차순) 행을 기준으로 한다** — `TripFit-client`가 저장 시점에 모든 행에 동일한 값을 다시 써서 항상 일치시키므로(위 "블로커 해결 이력" 참고) 어느 행을 골라도 결과는 같다. `RecommendationEngine.primaryVacationSchedule`로 구현, `#52`(필드를 `User`로 이동) 완료 시 제거 예정.

후보 슬롯들의 부분집합 중 예산을 넘지 않는 조합을 모두 검토해, 다음 순서로 **정확한 최적해**를 채택한다(그리디 근사 금지 — 후보 슬롯 수가 최대 `durationDays × 2`로 작아 완전탐색 성능 문제 없음):

1. 적용 후 "가능(POSSIBLE) 슬롯의 최장 연속 구간" 길이가 가장 긴 조합
2. 1이 동점이면 그중 **연차 소모량이 가장 적은** 조합(불필요하게 여분 연차를 쓰지 않음 — 연차 소모량은 페널티에 단조 반영되므로 항상 적게 쓰는 쪽이 같거나 더 낫다)
3. 2도 동점이면 전환 슬롯의 날짜가 빠른 쪽부터 우선 채택(deterministic 결과 보장용, 점수·판정에는 영향 없음)

이 결과로 얻은 "가능 슬롯" 배열을 그대로 참여자 3분류에 사용한다. 예산이 근무 겹침을 다 덮지 못하는 경우(예: 겹치는 일수 2일, 사용 가능 연차 1일) 별도의 "처리 기준" 분기가 필요한 게 아니라, 최장 연속 구간 계산 결과가 자연히 전체 참석에 못 미치게 되어 부분 참석/불참으로 판정된다.

### 4. 연차 신청 가능 시점(`vacationApplyPeriod`)은 이번 시뮬레이션에 관여하지 않는다

Out of Scope 절 참고 — 오늘 날짜 기준 리드타임 체크는 이번 amend 범위 밖.

## 패널티 구간표 (확정, [`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md))

### 1. 불참률 (= 불참 인원 / 응답 참여자 수)

| 불참률 | 페널티 |
| --- | --- |
| 0% | 0 |
| 15% 이하 | 20 |
| 15% 초과 30% 미만 | 50 |
| 30% 이상 | 100 |

### 2. 부분 참석률 (= 부분 참석 인원 / 응답 참여자 수)

| 부분 참석 비율 | 페널티 |
| --- | --- |
| 0% | 0 |
| 15% 이하 | 5 |
| 15% 초과 30% 미만 | 10 |
| 30% 이상 | 20 |

### 3. 불확실 인원 비율 (= 불확실 인원 / 응답 참여자 수)

| 불확실 인원 비율 | 페널티 |
| --- | --- |
| 0% | 0 |
| 15% 이하 | 10 |
| 15% 초과 30% 미만 | 20 |
| 30% 이상 | 40 |

### 4. 1인당 평균 연차 일수 (= `totalVacationDays` / `vacationMemberCount`)

| 1인당 평균 연차 일수 | 페널티 |
| --- | --- |
| 0일 | 0 |
| 0.5일 이하 | 1 |
| 0.5일 초과 1일 이하 | 3 |
| 1일 초과 2일 이하 | 5 |
| 2일 초과 | 1인당 평균 연차일수 × 5 |

**경계값 표기:** "이하/초과/미만"을 표 그대로 따른다(예: 정확히 15%는 "15% 이하" 구간). 위 표들 모두 상한이 명시 안 된 마지막 행은 이상(≥) 구간.

## 모드별 가중치 (확정, [`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md))

`최종점수 = 100 - Σ(패널티 × 가중치)`

| 추천 모드 | 불참률 | 부분 참석 비율 | 불확실 인원 비율 | 1인당 평균 연차 일수 |
| --- | --- | --- | --- | --- |
| `BASIC` (기본 선택) | 1.0 | 1.0 | 1.0 | 1.0 |
| `ALL_ATTEND` (모두 참석) | 5.0 | 3.0 | 1.0 | 0.5 |
| `SAVE_VACATION` (휴가 아끼기) | 1.0 | 0.5 | 1.0 | 5.0 |
| `CERTAIN` (확실하게 가기) | 1.0 | 1.0 | 5.0 | 1.0 |

## 특수 규칙 (확정, [`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md))

| # | 규칙 |
| --- | --- |
| 1 | **미응답 참여자** — `scoring_draft.md` 원칙상 계산에서 제외 대상이나, 이 구현에서는 ACTIVE 멤버 전원이 곧 응답 참여자라 실제로 발생하지 않는 케이스(위 "응답 참여자 판정" 참고) |
| 2 | **완전 불참자** — 연차 계산(`totalVacationDays`·`vacationMemberCount`)에서만 제외(응답 참여자 수에는 포함) |
| 3 | **부분 참석과 불확실은 독립** — 동일 참여자가 둘 다 만족하면 양쪽 항목에 모두 포함(중복 계산 가능) |
| 4 | **불확실 일정 처리** — 날짜 단위 판정. 후보 윈도우 내 하루 이상 불확실 선택 시 그 참여자를 불확실 인원 1명으로 카운트(여러 날짜 선택해도 1명). 불확실 **일정 수**(날짜 개수 합)는 동점 처리에만 사용 |
| 5 | **반차** — 오전 반차 0.5일, 오후 반차 0.5일, 종일 연차(오전+오후) 1일. **저녁은 연차 계산 대상 아님**(2026-07-30 구현 확정) |
| 6 | **동점 처리** — 1) 불확실 일정 수 적은 순 2) 날짜(시작일) 빠른 순 |
| 7 | **연차 자동 전환 대상 (2026-08-15 amend)** — "정기 근무만으로 IMPOSSIBLE"인 슬롯만 전환 가능. 개별 일정 override·구글 캘린더 busy로 IMPOSSIBLE인 슬롯은 연차로 전환 불가(연차는 근무만 대체, 개인 일정·구글 일정은 대체 불가) — "연차/반차 자동 반영 규칙" 절 |
| 8 | **반차 불가 사용자의 반나절 전환 (2026-08-15 amend)** — `halfVacationAvailable=false`여도 종일 연차 1.0일로 반나절(오전 또는 오후) 슬롯 하나를 없앨 수 있음(반차 단위 전환만 불가) |
| 9 | **저녁 자동 해제 (2026-08-15 amend #2)** — 오전·오후가 둘 다 POSSIBLE이 되고 저녁 IMPOSSIBLE 사유가 정기 근무뿐이면, 저녁도 추가 비용 없이 함께 POSSIBLE로 전환(퇴근 시간이 18시 이후인 근무자 대상) — "연차/반차 자동 반영 규칙" §1-1 |

## 카드 표시 지표 (`#13` 응답 DTO에 실릴 값)

2026-07-30 확인한 추천 결과 화면(카드 UI)에는 아래 4개 지표가 노출된다. 이 스펙의 계산 결과에서 아래처럼 매핑한다.

| 화면 표시 | 값 |
| --- | --- |
| 참석률 (%) | `attendRate` — **추론값**(화면 역산, 기획 확정 대기): `round((전체참석 인원 + 부분참석 인원) / 응답 참여자 수 × 100)` |
| 불확실 일정 (N명) | `uncertainCount` |
| 부분 참여 (N명) | `partialAttendCount` |
| 연차 일수 (N일) | `totalVacationDays` |

`score`(100 - Σ패널티×가중치)는 화면에 노출되지 않는다 — 서버 내부 정렬용으로만 쓰고 응답 DTO에 넣을지는 `#13`에서 결정(제안: 불필요한 필드 노출을 피하기 위해 응답에서 제외).

**⚠️ `attendRate` 계산식은 미확정이다.** [`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md)에는 "참석률" 자체가 정의돼 있지 않고 화면 예시(참석률 80%, 부분 참여 1명, 불확실 1명)를 역산한 추정치다. 기획자 확인 필요 — 확인 전까지 이 값으로 구현하고, 확정되면 이 절만 amend.

## 인터페이스 (예시, `#13`과의 경계)

```java
// #13이 정의하는 계약 — 이 스펙은 이 메서드의 "본문"만 채운다
interface RecommendationEngine {
    List<RecommendationCandidate> generate(Trip trip, RecommendationMode mode, List<TripMember> activeMembers);

    // 모드 무관 — 특정 구간 하나에 대한 참여자별 분류 (GET .../recommendations/{rank} 상세용, 라이브 재계산)
    List<MemberAttendanceDetail> classifyMembers(LocalDate startDate, LocalDate endDate, List<TripMember> activeMembers);
}
```

`RecommendationCandidate { rank, startDate, endDate, attendRate, partialAttendCount, uncertainCount, totalVacationDays, score }` — 저장·직렬화 필드 매핑은 `#13`(엔티티/DTO) 소관.

`MemberAttendanceDetail { displayName, attendance(FULL_ATTEND|PARTIAL_ATTEND|NON_ATTEND), uncertainDays, vacationDaysNeeded }` — `#13`의 `GET .../recommendations/{rank}` 응답 `members[]`로 직렬화(저장하지 않음).

**호출부 2곳 (`#13`):** ① `GET .../recommendations/{rank}` 상세 조회 시 라이브 재계산 ② `POST .../confirm` 성공 시 `confirmedStartDate`~`confirmedEndDate`로 1회 호출해 `Trip.confirmedAttendCount`/`confirmedVacationMemberCount`/`confirmedUncertainCount` 집계·저장(둘 다 이 스펙의 동일 로직 재사용, 새 계산 로직 추가 없음).

## 비즈니스 규칙

| BR | 적용 내용 |
| --- | --- |
| BR-TRIP-005 | 4모드 TOP 3 계산 |
| BR-TRIP-011 (개정) | `ALL_ATTEND` 가중치(하드 필터 아님) |
| BR-TRIP-012 (개정) | 동점 comparator — 불확실 일정 수 → 시작일 |

## 검증 시나리오

- [ ] `BASIC` 모드 — 고정 fixture로 rank 1~3 기대값 일치 (`[제안]` 후속 — 지금은 tie-break·ALL_ATTEND 시나리오만 커버)
- [x] `ALL_ATTEND` — 목표 인원 미달 후보도 **제외되지 않고** 낮은 점수로 포함되는지 확인(하드 필터 없음)
- [ ] `SAVE_VACATION`/`CERTAIN` — 가중치 반영한 정렬 확인 (`[제안]` 후속)
- [x] 부분 참석 경계값 — `scoring_draft.md` 윤지(6/9=부분참석)·은서(4/9=불참) 예시 그대로 재현
- [ ] 패널티 구간 경계값 — 불참률 정확히 15%/30% 등 각 표의 이하/초과/미만 경계 전부 (`[제안]` 후속 — 지금은 코드 리뷰로만 확인)
- [x] 동점 fixture — comparator 순서(불확실 일정 수 → 시작일) 확인
- [x] 완전 불참자가 연차 계산에서 제외되는지 확인
- [x] 미응답 참여자 처리 — ACTIVE 멤버 전원이 응답 참여자로 카운트되어 별도 제외 케이스가 없음을 확인(구조상 자명 · 위 "응답 참여자 판정" 참고)
- [ ] `classifyMembers(...)`가 모드와 무관하게 동일한 결과를 반환하는지 별도 assertion (`[제안]` 후속 — 현재는 시그니처에 mode 파라미터가 아예 없어 구조적으로 보장됨)
- [x] resolve 결과가 `#17`과 동일한 합친 값을 사용하는지 확인 (`ScheduleCalendarResolver.resolve(...)` 직접 재사용, 별도 병합 로직 없음)
- [x] **(2026-08-15 amend) p1.md AS-IS/TO-BE 예시 재현** — 월~금 09:00~18:00 근무·연차 1일·금~일 2박3일 여행에서 전체 참석 + 필요 연차 1일로 판정됨 (`classifyMembers_p1ExampleFridayOverlap_fullAttendWithOneVacationDay`)
- [x] **(2026-08-15 amend) 연차 예산 부족 시 강등** — 근무 겹침 2일·사용 가능 연차 1일 케이스에서 전체 참석이 아니라 부분 참석으로 자동 강등됨 (`classifyMembers_vacationBudgetInsufficient_degradesToPartialAttend`)
- [x] **(2026-08-15 amend) 반차 불가 사용자의 종일 연차 대체** — `halfVacationAvailable=false` 사용자가 반나절만 근무와 겹칠 때 종일 연차 1.0일로 전환됨(0.5일 아님) (`classifyMembers_halfVacationUnavailable_singleHalfBlockCostsFullDay`)
- [x] **(2026-08-15 amend) 개인 일정·구글 busy는 연차로 전환 안 됨** — 개별 일정 override(`classifyMembers_personalScheduleBlocksVacationConversion`)·구글 캘린더 busy(`classifyMembers_googleBusyBlocksVacationConversion`) 둘 다 연차 예산이 충분해도 전환되지 않음을 확인
- [x] **(2026-08-15 amend) 기존 수동 override 회귀 없음** — 기존 `classifyMembers_halfDayOverrideOnWorkday_needsHalfDayVacation` 테스트가 amend 후에도 동일하게 통과(수정 없이 재사용)
- [x] **(2026-08-15 amend) 정기 일정 N행 사용자의 연차 예산 산정** — 가장 먼저 등록된 행을 기준으로 예산이 산정됨을 확인 (`classifyMembers_multipleRegularSchedules_usesEarliestCreatedAsBudget`)
- [x] **(2026-08-15 amend) `new_problem/test_set.md` 5인 시나리오** — 동일 데이터를 fixture로 구성해 1순위가 10/24(토)~10/26(월)로 나오는지, 참석 인원·부분 참석·연차·불확실 값이 테스트셋의 "운영자용 정답"과 일치하는지 (`RecommendationEngineTestSetScenarioTest`). 이 재현 과정에서 §1-1 "저녁 자동 해제" 버그(B·D가 FULL_ATTEND 대신 PARTIAL_ATTEND로 오분류)를 발견·수정 — 실제 계정 5개로 수동 검증은 완료 기준 별도 항목

## 완료 기준

- [x] `./gradlew test` 통과 (`RecommendationEngineTest`)
- [x] `#13`의 `TripRecommendationService`에서 플레이스홀더 대신 이 로직 호출로 교체
- [x] Wave 2 MVP 완료 기준: 방장이 4모드 중 하나로 실제 계산된 TOP 3를 확인 가능
- [x] (2026-08-15 amend) 위 "검증 시나리오" 신규 항목(test_set.md 실제 계정 수동 검증 제외) 전부 통과 · `./gradlew test` 전체 475건 통과
- [ ] (2026-08-15 amend) `new_problem/test_set.md`의 5개 계정 데이터로 실제 TripFit에 입력 후 추천 결과 수동 확인(부스 이벤트 전 최종 확인 필요 항목과 동일) — 미실시

## 리스크·미결정

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| `attendRate`(카드 참석률 %) 계산식 | `[제안]` | 화면 역산 추정 — 기획 확정 필요 |
| 공휴일 데이터 | `[미정]` | KR 공휴일 static table vs API — 동점 처리엔 더 이상 불필요(주말·공휴일 기준 폐기), 카드 UI에 별도 필요해지면 재논의 |
| `vacationApplyPeriod` 반영 여부 | `[미정]` | 2026-08-15 amend Out of Scope로 분리 — 후속 스펙 필요 시 별도 논의 |
| amend용 GitHub 이슈 | 확정 | **#105** — 2026-08-15 사용자 확인 후 신규 생성 |
| 연차 관련 필드 위치(`RegularSchedule`→`User`) | `[미정]` | **#52**로 분리(스키마 리팩토링, 프론트 담당자 복귀 후 진행) — "블로커 해결 이력" 참고 |
| `new_problem/test_set.md` 5인 시나리오 수동 검증 | 미실시 | 부스 이벤트 전 실제 계정 5개로 확인 필요 |

## 변경 이력

| 날짜 | 변경 |
| --- | --- |
| 2026-08-15 | **저녁 자동 해제 버그 수정** — `new_problem/test_set.md` 5인 시나리오를 `RecommendationEngineTestSetScenarioTest`로 재현하던 중, 퇴근 시간이 18시 이후인 사용자(B·D)가 하루 종일 연차를 써도 저녁 슬롯만 계속 막힌 채로 남아 FULL_ATTEND 대신 PARTIAL_ATTEND로 오분류되는 버그 발견 — 1순위가 기대값(10/24~26) 대신 10/4~6으로 잘못 나옴. `RecommendationEngine.openFreeEvenings` 추가(오전·오후가 둘 다 POSSIBLE이 된 날, 저녁 IMPOSSIBLE 사유가 정기 근무뿐이면 함께 연다 — 위 "연차/반차 자동 반영 규칙" §1-1). `blockedByNonWorkReason`을 저녁까지 지원하도록 확장. 진단용이던 시나리오 테스트를 실제 assertion 기반으로 전환, `./gradlew test` 475건 전부 통과 |
| 2026-08-15 | **구현 완료** — `fix/105-vacation-attendance` 브랜치. `SchedulePort.findPersonalSchedulesByUserIds` 신설(개별 일정 원본 조회), `RecommendationEngine`에 `applyVacationSimulation`/`collectConversionUnits`/`primaryVacationSchedule` 추가. `vacationDaysForSpan`의 `halfVacationAvailable` 미반영 회귀도 같은 김에 수정(위 "전환 비용" 절 참고). 새 테스트 6개 추가, 기존 7개 유지, `./gradlew test` 473건 전부 통과 |
| 2026-08-15 | **구현 착수 → 블로커로 중단 → 해결** — 연차 예산을 어느 `RegularSchedule` 행 기준으로 볼지 블로커 발견, `TripFit-client` 코드 직접 확인으로 "첫 번째 등록 행 기준"이 실제 앱 동작과 일치함을 확인해 해결. 필드 위치 자체의 근본 수정은 `#52`로 분리 |
| 2026-08-15 | **Draft amend** — 연차/반차 자동 전환 시뮬레이션 추가(`new_problem/p1.md` 기획자 리포트). `maxVacationDays`/`halfVacationAvailable` 필드를 처음으로 추천 계산에 사용. 참여자 3분류를 시뮬레이션 결과 기준으로 재정의. 사용자 승인 대기 — 미구현 |
| 2026-07-30 | **Approved** — `#13`과 함께 확정, 구현 착수 |
| 2026-07-30 | "추천 근거" 상세 화면 확인 반영 — 참여자 3분류 로직을 모드 무관 `classifyMembers(...)`로 분리해 `#13`의 `GET .../recommendations/{rank}` 상세 API(참여자별 `attendance`/`uncertainDays`/`vacationDaysNeeded`, 라이브 재계산)에서 재사용 가능하게 명시 |
| 2026-07-30 | 기획자 알고리즘 확정본([`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md)) 반영 — 패널티 구간표·모드별 가중치·최종점수 공식·동점 기준 전부 확정값으로 교체. `ALL_ATTEND` 하드 필터·`w1/w2/w3` 구식·"주말·공휴일" 동점 기준 폐기. 카드 UI 확인 기반 `attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays` 응답 지표 추가 |
| 2026-07-24 | `trip-recommendation.md`(#13)에서 계산 로직 분리 — 신규 이슈 `#50` 생성 |
