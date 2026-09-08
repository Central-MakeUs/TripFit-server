# 추천 결과 계산 로직 (후보 윈도우 · 모드별 스코어링 · 동점)

> wave: 2
> implements: BR-TRIP-005, BR-TRIP-011, BR-TRIP-012
> deferred: 공휴일 API 연동 (주말만 우선, static table/외부 API 방식은 `[미정]`) · 연차 신청 가능 시점(`vacationApplyPeriod`) 반영 (`[미정]`, 아래 amend 절)
> 상태: **Approved**(2026-07-30 최초 확정본) — **2026-08-15 연차/반차 자동 반영 amend 구현 완료**(`fix/105-vacation-attendance` 브랜치, `./gradlew test` 486건 통과), PR·머지 전 최종 확인 대기
> GitHub: **#50**(최초 확정본, Closed) · amend는 **#105**
> 선행: [`trip-recommendation.md`](trip-recommendation.md) (#13, `Recommendation` 엔티티·Repository·Controller stub) · [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md) (#17, Closed)

## 블로커 해결 이력 (2026-08-15)

구현 중 "연차 예산을 어느 `RegularSchedule` 행 기준으로 볼지" 블로커를 발견해 중단했다가, 다음 확인을 거쳐 해결하고 구현을 재개했다.

1. **프론트엔드 직접 확인:** `TripFit-client`의 `useSaveRegularSchedule.ts`가 "기본 정보 관리" 화면에서 `annualLeaveCount`/`leaveNoticeDays`/`includeHalfDayHoliday`(연차 관련 4개 값)를 **화면 전체에 하나뿐인 공유 값**으로 관리하다가, 저장 시점에 사용자의 **모든** `RegularSchedule` 행에 동일한 값을 실어 PATCH/POST하는 것을 확인 — 실제 앱에서는 항상 모든 행이 같은 값으로 유지된다.
2. 이에 따라 **"첫 번째(가장 먼저 등록된, `createdAt` 오름차순) `RegularSchedule` 행 기준"**(후보안 1)으로 결정 — 실제 앱 동작과 정확히 일치하며, 여러 행이 존재해도 결과가 달라지지 않는다(프론트가 항상 동기화하므로).
3. **근본 원인(필드 위치가 `User`가 아니라 `RegularSchedule`)은 별도 이슈 `#52`로 분리** — API 계약이 바뀌는 breaking change라 프론트 담당자 부재중에는 진행하지 않기로 함(2026-08-15 사용자 결정). "첫 번째 행 기준" 로직은 `#52` 완료 시 제거 예정 — 2026-08-16 `#107` 구현에서 `RegularSchedule.policySource`로 옮겨, 연차·반차·공휴일 휴무가 같은 대표 행을 쓰도록 단일 SSOT가 됐다(그 메서드에 제거 TODO 표시).

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
- [x] **연차/반차 자동 전환 시뮬레이션 (신규, 2026-08-15 amend — 참여자 3분류보다 먼저 실행):** 참여자별로 후보 윈도우 내 "정기 근무만으로 IMPOSSIBLE"인 슬롯(개별 일정 override나 구글 캘린더 busy로 인한 IMPOSSIBLE은 대상 아님)을 **근무(정기 일정 한 행) 단위**로 묶어, `RegularSchedule.maxVacationDays`(여행당 사용 가능 최대 연차 일수) 예산 안에서 **"가능(POSSIBLE) 슬롯의 최장 연속 구간"이 가장 길어지는 전환 조합을 탐색**해 적용한다. 종일 연차 1일은 그 근무가 막는 슬롯(오전·오후·저녁 무관)을 통째로 열고, 반차 0.5일은 그 근무의 오전 또는 오후 반나절만 연다. `RecommendationEngine.applyVacationSimulation`/`collectVacationOptions`/`addShiftUnits`로 구현. 상세 규칙: 아래 "연차/반차 자동 반영 규칙" 절
- [x] **참여자 3분류(2026-08-15 amend — 판정 대상 슬롯 변경):** 위 시뮬레이션을 적용한 뒤의 슬롯으로 판정한다(판정 기준 자체는 변경 없음, 후보 윈도우 기준, 응답 참여자만 대상):
  - 전체 참석: 후보 윈도우의 모든 슬롯이 가능(POSSIBLE)
  - 부분 참석: 전체 슬롯 수(`durationDays × 3`)의 **⌈50%⌉ 이상**을 **하나의 연속된 구간**으로 참석 가능(늦참·조기귀가만 인정 — 중간 이탈 후 재합류는 불인정)
  - 불참: 위 두 조건을 만족하지 못하는 나머지
- [x] **불확실 인원:** 후보 윈도우 내 하루 이상 `personal_schedule.uncertain=true`인 응답 참여자 수(1인당 최대 1로 카운트, 여러 날짜 선택해도 1명) — 부분 참석과 독립 집계(중복 카운트 가능)
- [x] **연차 계산(2026-08-15 amend — 근무 단위 집계):** 완전 불참자 제외. 참석 구간 안에서 **근무마다** 연차를 환산해 합산한다 — 그 근무가 참석 구간에 저녁을 걸치고 있거나 오전·오후를 둘 다 걸치면 종일 연차 1일, 반나절 하나만 걸치면 반차 0.5일(`halfVacationAvailable=false`면 1일). 자동 전환분과 사용자가 미리 개별 일정으로 수동 override 해둔 분 모두 같은 방식으로 집계한다. `totalVacationDays`(총 연차 일수)·`vacationMemberCount`(연차 계산 대상 인원 수) 산출
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

`ScheduleCalendarResolver.resolve(...)` 결과 중, 해당 슬롯이 IMPOSSIBLE인 이유가 **오직 정기 근무와의 겹침뿐**인 슬롯만 전환 후보다 — 오전·오후뿐 아니라 **저녁 슬롯도 동일하게 후보**다(저녁을 막는 것이 근무라면 연차로 뺄 수 있다). 판별식: 그날 요일에 매칭되는 `RegularSchedule` 중 하나라도 그 슬롯을 IMPOSSIBLE로 막고 있으면서, 그 슬롯에 개별 일정(`PersonalSchedule`) override나 구글 캘린더 busy로 인한 별도 IMPOSSIBLE이 **없는** 경우(`RecommendationEngine.blockedByNonWorkReason`).

- 개별 일정이 그 슬롯을 명시적으로 IMPOSSIBLE로 override했거나(예: 결혼식 참석), 구글 캘린더가 해당 슬롯을 busy로 잡고 있으면 **전환 후보에서 제외** — 연차는 "근무"만 대체할 수 있고, 개인 약속·구글 캘린더 일정은 대체할 수 없다.
- 사용자가 이미 개별 일정으로 **수동 override 해둔** 슬롯(정기 근무 IMPOSSIBLE을 개별 일정 POSSIBLE로 덮어쓴 경우)은 이미 POSSIBLE 상태이므로 시뮬레이션 대상이 아니다 — 그대로 인정하고 연차 계산에도 그대로 집계(기존 동작 유지).

### 1-1. 전환 단위는 "근무 사이클" (2026-08-15 amend #4 — 슬롯 단위 계산 폐기)

**연차·반차의 정의(기획 확정, `scoring_draft.md` 5. 반차 계산):**

| 상품 | 값 | 의미 |
| --- | --- | --- |
| 오전 반차 | 0.5일 | 그 근무의 **오전 반나절**만 가능으로 전환 |
| 오후 반차 | 0.5일 | 그 근무의 **오후 반나절**만 가능으로 전환 |
| 종일 연차 | 1일 | **하나의 근무 사이클을 전부 가능으로 전환** — 그 근무가 막는 슬롯이 오전·오후·저녁 어디까지 걸쳐 있든 전부 |

**규칙:** 전환 단위는 **슬롯이 아니라 정기 일정 한 행(= 근무 하나) 단위**다. 그날 매칭되는 각 `RegularSchedule`마다 아래 단위를 만들고, 예산(`maxVacationDays`) 안에서 조합을 완전탐색한다. `RecommendationEngine.collectVacationOptions`/`addShiftUnits`로 구현.

- **종일 연차(1일):** 그 근무가 막는 슬롯 **전부**(오전·오후·저녁 무관)를 한 번에 연다. 13~23시 근무면 오후+저녁이 1일로 함께 열리고, 10~20시 근무면 오전+오후+저녁이 1일로 함께 열린다.
- **반차(0.5일, `halfVacationAvailable=true`만):** 그 근무의 오전 또는 오후 반나절 하나만 연다. **"저녁 반차"는 존재하지 않으므로**(위 표) 저녁이 걸린 근무를 저녁까지 열려면 항상 종일 연차를 쓴다.
- 근무가 저녁을 안 막는 경우(예: 09~18시), 오전 반차+오후 반차(0.5+0.5=1일)와 종일 연차(1일)는 여는 범위·값이 같다 — 구현은 조합 수를 줄이려 이때 종일 연차 단위를 따로 만들지 않는다(결과 동일).
- **근무가 여러 행이면 연차도 근무마다 따로 쓴다** (BR-TRIP-006상 사용자당 정기 일정 여러 행 허용 — 회사 출근 + 저녁 알바). 낮 근무를 연차로 빼도 저녁 알바는 **별개 근무**라 그대로 막히고, 둘 다 빼려면 1일+1일=2일이 든다. 한 슬롯을 두 근무가 동시에 막고 있으면 그 슬롯은 **두 근무를 모두 빼야** 열린다.
- **예산은 하나의 공유 풀**이고 "어느 연차를 어느 근무에 썼는지" 라벨링은 하지 않는다 — 완전탐색이 예산 안에서 참석 구간이 가장 길어지는 조합을 고르고, 같은 길이면 연차를 덜 쓰는 조합을 고른다.
- **`#52`(연차 필드를 `RegularSchedule`→`User`로 이동)와의 관계:** 이 절이 "연차는 근무 사이클 단위, 예산은 사람 단위"임을 분명히 하므로, `maxVacationDays`/`halfVacationAvailable`가 근무 행에 붙어 있는 현재 스키마는 개념상 어긋난다(근무가 2개면 예산 행도 2개가 되지만 실제로는 사람 하나의 예산). 현재는 "가장 먼저 등록된 행 기준"이라는 임시 규칙으로 읽고 있고(위 "블로커 해결 이력"), 근본 수정은 **#52**.

**최초 구현이 틀렸던 지점(2026-08-15 수정):** 전환 단위를 슬롯(오전/오후) 단위로 만들고 저녁은 "오전·오후가 둘 다 열리면 덤으로 열어주기"(구 `openFreeEvenings`) 또는 "저녁만 따로 1일에 사기"(구 저녁 단독 구매)로 처리했다. 이 모델에서는 13~23시 근무를 전부 없애는 데 오후 반차 0.5 + 저녁 1.0 = **1.5일**, 반차 불가 사용자는 **2.0일**이 드는 등 "근무 하나 = 연차 1일"이라는 기획 정의와 어긋난 값이 나왔다. 근무 단위로 바꾸면서 `openFreeEvenings`·`hasContinuousShiftIntoEvening`·저녁 단독 구매 분기는 **전부 삭제**했다 — 종일 연차가 근무의 저녁까지 함께 여는 것이 규칙 자체에 포함되므로 별도 보정이 필요 없다.

### 2. 전환 비용 (근무 1개 기준)

| 사용자 | 그 근무를 통째로 뺄 때 | 그 근무의 반나절(오전·오후 중 하나)만 뺄 때 |
| --- | --- | --- |
| `halfVacationAvailable=true` | 종일 연차 **1.0일** | 반차 **0.5일** |
| `halfVacationAvailable=false` | 종일 연차 **1.0일** | **1.0일**(반차 단위가 없어 종일 연차를 씀 — 2026-08-15 사용자 확인) |

- 저녁이 걸린 근무를 저녁까지 빼는 경우는 위 표의 **"통째로"** 열만 적용된다(저녁 반차 없음) — 반차 가능 여부와 무관하게 1.0일.
- 근무가 여러 개면 각 근무에 위 비용이 **따로** 든다(§1-1) — 낮 근무 1.0 + 저녁 알바 1.0 = 2.0일.
- **연차 소모량 집계(`vacationDaysForSpan`)도 같은 표를 쓴다.** 참석 구간 안에서 각 근무가 막고 있던 시간대를 보고 근무별로 환산해 합산한다(`vacationDaysForShift`) — 저녁이 참석 구간에 걸려 있으면 그 근무는 1.0, 오전·오후를 둘 다 걸치면 1.0, 반나절 하나면 위 표대로. 수동 override와 자동 전환이 같은 메서드를 공유하므로 두 경로의 값이 항상 일치한다.
- **구현 중 발견한 회귀(수정 완료):** 기존 집계가 `halfVacationAvailable` 값과 무관하게 "겹치는 슬롯 1개=0.5일"로 계산해, 반차 불가 사용자가 반나절 하나만 막혀 있어도 0.5일로 **과소** 집계됐다 — 위 표대로 1.0일이 되도록 수정.

### 3. 예산과 탐색

예산 = 참여자의 `RegularSchedule.maxVacationDays`(여행당 사용 가능 최대 연차 일수). **어느 행의 값을 쓸지는 가장 먼저 등록된(`createdAt` 오름차순) 행을 기준으로 한다** — `TripFit-client`가 저장 시점에 모든 행에 동일한 값을 다시 써서 항상 일치시키므로(위 "블로커 해결 이력" 참고) 어느 행을 골라도 결과는 같다. `RegularSchedule.policySource`로 구현(연차·반차·공휴일 휴무 공용 대표 행 — `#107`에서 `RecommendationEngine`에서 엔티티로 이동), `#52`(필드를 `User`로 이동) 완료 시 제거 예정.

전환 단위(§1-1, 날짜 × 근무마다 종일 연차·반차) 부분집합 중 예산을 넘지 않는 조합을 모두 검토해, 다음 순서로 **정확한 최적해**를 채택한다(그리디 근사 금지 — 단위 수가 `durationDays × 정기 일정 행 수`에 비례해 작다. 극단적으로 긴 구간에서 조합 수가 폭발하는 것만 `MAX_CONVERSION_UNITS`로 막는다):

1. 적용 후 "가능(POSSIBLE) 슬롯의 최장 연속 구간" 길이가 가장 긴 조합
2. 1이 동점이면 그중 **연차 소모량이 가장 적은** 조합(불필요하게 여분 연차를 쓰지 않음 — 연차 소모량은 페널티에 단조 반영되므로 항상 적게 쓰는 쪽이 같거나 더 낫다)
3. 2도 동점이면 전환 단위의 날짜가 빠른 쪽부터 우선 채택(deterministic 결과 보장용, 점수·판정에는 영향 없음)

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
| 5 | **반차** — 오전 반차 0.5일, 오후 반차 0.5일, **종일 연차 1일 = 하나의 근무 사이클을 전부 가능으로 전환**(기획 확정). "저녁 반차"라는 0.5일 상품은 없다 |
| 6 | **동점 처리** — 1) 불확실 일정 수 적은 순 2) 날짜(시작일) 빠른 순 |
| 7 | **연차 자동 전환 대상 (2026-08-15 amend)** — "정기 근무만으로 IMPOSSIBLE"인 슬롯만 전환 가능. 개별 일정 override·구글 캘린더 busy로 IMPOSSIBLE인 슬롯은 연차로 전환 불가(연차는 근무만 대체, 개인 일정·구글 일정은 대체 불가) — "연차/반차 자동 반영 규칙" 절 |
| 8 | **반차 불가 사용자의 반나절 전환 (2026-08-15 amend)** — `halfVacationAvailable=false`여도 종일 연차 1.0일로 반나절(오전 또는 오후) 하나를 없앨 수 있음(반차 단위 전환만 불가) |
| 9 | **전환 단위 = 근무 사이클 (2026-08-15 amend #4)** — 종일 연차 1일은 그 근무가 막는 슬롯을 오전·오후·저녁 가릴 것 없이 **전부** 연다. 저녁이 걸린 근무(예: 13~23시, 10~20시)도 1일이면 저녁까지 함께 열리고, 저녁만 하는 근무(19~23시)도 1일이면 열린다 — "연차/반차 자동 반영 규칙" §1-1 |
| 10 | **근무가 여러 개면 연차도 따로 (2026-08-15 amend #4)** — 낮 근무 + 저녁 알바처럼 정기 일정이 2행이면 각 근무에 연차가 따로 들어 둘 다 빼려면 1.0+1.0=2.0일. 한 슬롯을 두 근무가 동시에 막으면 **둘 다** 빼야 열림 — §1-1 |

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
- [x] **(2026-08-15 amend) 기존 수동 override 회귀 없음** — 기존 테스트(`classifyMembers_manualFullDayOverrideOnWorkday_countsOneVacationDay`)가 amend 후에도 동일하게 통과(assertion 수정 없이 재사용). 2026-08-15 amend #5에서 **이름·주석만** 정정 — 구 이름 `classifyMembers_halfDayOverrideOnWorkday_needsHalfDayVacation`은 "오전만 override해 반차 0.5일"이라고 적혀 있었지만 실제로는 하루 전체를 override하고 종일 연차 1.0일을 검증하고 있어, 이름이 코드와 어긋나 있었다
- [x] **(2026-08-15 amend) 정기 일정 N행 사용자의 연차 예산 산정** — 가장 먼저 등록된 행을 기준으로 예산이 산정됨을 확인 (`classifyMembers_multipleRegularSchedules_usesEarliestCreatedAsBudget`)
- [x] **(2026-08-15 amend) `new_problem/test_set.md` 5인 시나리오** — 동일 데이터를 fixture로 구성해 1순위가 10/24(토)~10/26(월)로 나오는지, 참석 인원·부분 참석·연차·불확실 값이 테스트셋의 "운영자용 정답"과 일치하는지 (`RecommendationEngineTestSetScenarioTest`). 이 재현 과정에서 §1-1 "저녁 자동 해제" 버그(B·D가 FULL_ATTEND 대신 PARTIAL_ATTEND로 오분류)를 발견·수정 — 실제 계정 5개로 수동 검증은 완료 기준 별도 항목.
  **재현 범위의 한계 2가지 (2026-08-15 amend #5에서 발견, 2026-08-16 사용자 확인 완료):** (1) **공휴일 미지원 — `#107`로 분리, 이번 범위 아님(2026-08-16 사용자 확정).** `holidayRest` 필드는 저장되지만 `ScheduleCalendarResolver`·`RecommendationEngine` 어디에서도 읽지 않는다(공휴일 캘린더 자체가 없음 — Out of Scope 절). 그래서 fixture는 test_set의 "A·B·C 공휴일 쉼 / D 공휴일 근무" 조건을 반영하지 못하고, 10/9(한글날)·10/3(개천절)이 낀 비교 후보 2·3은 문서의 근거 서술대로 재현되지 않는다. 1순위(10/24~26)에는 공휴일이 없어 결과 자체는 영향 없음. (2) **D(12:00~20:00)의 "아침 참여 가능"은 test_set 문서 쪽 오류 (2026-08-16 사용자 확인).** 오전 슬롯이 `[00:00, 13:00)`이라 12~13시가 겹쳐 오전도 IMPOSSIBLE이 되는 것이 `TimeSlot` 계약상 정상 동작이고, test_set의 "근무일에 아침 참여 가능"이 기획자 착오다 — **구현·슬롯 경계는 수정하지 않는다.** 1순위에서는 D의 근무일(10/24 토)에 연차 1일을 써서 세 슬롯이 함께 열리므로 결과·연차 값 모두 영향 없고, 어긋나는 것은 후보 1(10/23~25)의 "D는 오전까지만 참여 가능"이라는 **근거 서술 문구뿐**이다(부스 진행 시 이 문구만 정정 필요)
- [x] **(2026-08-15 amend #4) 저녁만 근무하는 사용자 — 종일 연차 1일로 전환** — 정기 근무가 저녁에만 있는(오전·오후는 원래 자유) 사용자가 반차 가능 여부와 무관하게 종일 연차 1.0일로 저녁을 없앨 수 있는지 확인 (`classifyMembers_eveningOnlyRegularSchedule_buysWithFullDayVacation`)
- [x] **(2026-08-15 amend #4) 오후+저녁 근무 = 종일 연차 1일로 통째 전환** — 13~23시(오전은 원래 자유) 근무가 1.0일로 오후+저녁 한 번에 열리는지 확인. 저녁 몫을 따로 사서 1.5일이 되던 구 모델의 회귀 방지 (`classifyMembers_afternoonEveningShift_fullDayVacationOpensWholeShift`)
- [x] **(2026-08-15 amend #4) 오후+저녁 근무, 반차 불가 사용자도 1일** — `halfVacationAvailable=false`여도 같은 근무라 1.0일. 구 모델에서 2.0일이 나오던 회귀 방지 (`classifyMembers_afternoonEveningShift_halfVacationUnavailable_stillCostsOneDay`)
- [x] **(2026-08-15 amend #4) 오전~저녁 종일 근무도 1일** — 10~20시 근무가 1.0일로 오전·오후·저녁 전부 열리는지 확인 (`classifyMembers_fullDayIntoEveningShift_fullDayVacationOpensWholeShift`)
- [x] **(2026-08-15 amend #4) 예산 부족 시 근무 하나만 전환** — 오후+저녁 근무가 이틀 연속이고 예산이 1일뿐이면 하루치 근무만 빠지고 나머지는 막힌 채 부분 참석이 되는지 확인 (`classifyMembers_afternoonEveningShiftTwoDays_budgetOneDay_clearsOnlyOneShift`)
- [x] **(2026-08-15 amend #4) 근무 2개(낮+저녁 알바)는 연차도 2개** — 예산 1일이면 더 긴 구간이 나오는 낮 근무만 빠지고(`..._doesNotAutoOpenUnrelatedEveningJob`, 1.0일), 예산 2일이면 둘 다 빠져 전체 참석·2.0일이 되는지 확인 (`classifyMembers_twoSeparateRegularSchedules_buysBothWithSufficientBudget`)
- [x] **(2026-08-15 amend #5) 기획 리포트 §3 예산 표 4행 전부** — `new_problem/p1.md` "겹치는 일수 / 사용 가능 연차 / 예상 처리" 표를 행별로 대조: 겹침 1일·연차 1일 → 전체 참석(`..._p1ExampleFridayOverlap_...`) · 겹침 2일·연차 2일 → 전체 참석(`..._vacationBudgetExactlyCoversOverlap_fullAttend`, amend #5에서 신규 추가) · 겹침 1일·연차 2일 → 전체 참석(`..._afternoonEveningShift_halfVacationUnavailable_stillCostsOneDay`, 예산 2일 중 1일만 사용) · 겹침 2일·연차 1일 → 부분 참석으로 강등(`..._vacationBudgetInsufficient_degradesToPartialAttend`)
- [x] **(2026-08-15 amend #5) 정수 예산에서 0.5일 단위로 쪼개 쓰기** — 예산은 정수 일수로 들어오지만 내부적으로 반나절 단위로 소비된다. 반차 가능 사용자가 연차 2일을 가지고 종일 연차 1.0일(월, 09~18시) + 오전 반차 0.5일(화, 09~12시) = **1.5일**만 쓰는 조합을 실제로 고르는지 확인 — 예산을 남김없이 쓰지 않고 필요한 만큼만 쓴다 (`classifyMembers_halfVacationAvailable_spendsFractionalDaysFromIntegerBudget`)
- [x] **(2026-08-15 amend #5) 반차 가능 사용자의 반나절 하나 = 0.5일** — 저녁을 안 막는 근무(09~12시)의 오전만 걸리고 `halfVacationAvailable=true`면 종일 연차가 아니라 반차 0.5일만 드는지 확인. 근무 단위 재설계가 반차 단위를 없애버리지 않았는지 보는 대칭 케이스 (`classifyMembers_halfVacationAvailable_singleHalfBlockCostsHalfDay`)
- [x] **(2026-08-15 amend #5) 정수 예산을 0.5일 단위로 쪼개 쓰는지** — 기획자 문의("연차 2일 + 반차 가능이면 0/0.5/1/1.5/2일 중에서 고를 수 있어야 하지 않나")에 대한 확인. 예산은 `maxVacationDays × 2`로 **반나절 단위로 환산해** 들고 있으므로 이미 그렇게 동작한다. 연차 2일 사용자가 월(종일 근무) 1.0 + 화(오전 근무) 0.5 = **1.5일**을 골라 전체 참석이 되는지 확인 (`classifyMembers_halfVacationAvailable_spendsFractionalDaysFromIntegerBudget`). **정수 제약은 "보유 예산의 입력 단위"에만 적용되고 "소모 단위"에는 적용되지 않는다** — 이 구분이 안내 문구에서 혼동을 일으켰던 사례
- [x] **(2026-08-15 amend #5) 오후+저녁 근무에서 반차 0.5일만 쓰게 되는 조건** — 저녁이 근무가 아닌 이유(구글 busy)로도 막혀 있으면 종일 연차를 사도 저녁이 안 열리므로 오후 반차 0.5일이 최적이 되는지 확인. **예산이 0.5일뿐인 상황은 `maxVacationDays`가 정수 일수라 발생하지 않는다**(최소 예산이 이미 1일) — 반차만 쓰게 되는 건 예산 부족이 아니라 "저녁을 연차로 못 여는 경우"뿐임을 명시 (`classifyMembers_afternoonEveningShift_eveningBlockedElsewhere_buysOnlyAfternoonHalf`)
- [x] **(2026-08-15 amend #4) 개인 일정으로 막힌 저녁은 예산이 있어도 못 엶** — 저녁 전용 근무(19~23시)에 개인 일정(예: 밤 결혼식)까지 겹치면 연차 예산이 충분해도 전환 후보에서 제외되는지 확인 (`classifyMembers_eveningBlockedByPersonalSchedule_cannotBeOpenedEvenWithBudget`)

## 완료 기준

- [x] `./gradlew test` 통과 (`RecommendationEngineTest`)
- [x] `#13`의 `TripRecommendationService`에서 플레이스홀더 대신 이 로직 호출로 교체
- [x] Wave 2 MVP 완료 기준: 방장이 4모드 중 하나로 실제 계산된 TOP 3를 확인 가능
- [x] (2026-08-15 amend) 위 "검증 시나리오" 신규 항목(test_set.md 실제 계정 수동 검증 제외) 전부 통과 · `./gradlew test` 전체 486건 통과
- [ ] (2026-08-15 amend) `new_problem/test_set.md`의 5개 계정 데이터로 실제 TripFit에 입력 후 추천 결과 수동 확인(부스 이벤트 전 최종 확인 필요 항목과 동일) — 미실시. 단, 공휴일이 낀 비교 후보(10/9·10/3)는 `#107` 전까지 문서 근거대로 나오지 않고, D의 "아침 참여 가능"은 test_set 쪽 오류이므로 이 둘은 불일치로 보지 않는다(위 검증 시나리오 한계 2가지)

## 리스크·미결정

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| `attendRate`(카드 참석률 %) 계산식 | `[제안]` | 화면 역산 추정 — 기획 확정 필요 |
| 공휴일 데이터 | **반영 완료 (#107)** | 공공데이터포털 특일정보 API + Redis 캐싱으로 확정([`decisions/011`](../../decisions/011-holiday-data-source.md)). `holidayRest`가 이제 추천 계산에도 반영된다 — 공휴일에 쉬는 사용자는 그날 근무가 없는 것으로 보고 연차를 청구하지 않음(`matchingRegulars`). 동점 처리에는 여전히 미사용(주말·공휴일 기준 폐기 유지). 상세: [`schedule-holiday-rest.md`](../user-schedule/schedule-holiday-rest.md) |
| `vacationApplyPeriod` 반영 여부 | `[미정]` | 2026-08-15 amend Out of Scope로 분리 — 후속 스펙 필요 시 별도 논의 |
| amend용 GitHub 이슈 | 확정 | **#105** — 2026-08-15 사용자 확인 후 신규 생성 |
| 연차 관련 필드 위치(`RegularSchedule`→`User`) | `[미정]` | **#52**로 분리(스키마 리팩토링, 프론트 담당자 복귀 후 진행) — "블로커 해결 이력" 참고 |
| `new_problem/test_set.md` 5인 시나리오 수동 검증 | 미실시 | 부스 이벤트 전 실제 계정 5개로 확인 필요 |

## 변경 이력

| 날짜 | 변경 |
| --- | --- |
| 2026-08-15 | **전환 단위를 "근무 사이클"로 재설계(amend #5 — 아래 amend #3·#4 모델 전면 폐기)** — "연차 = 오전 반차 + 오후 반차"로 슬롯 단위 계산을 하던 모델 자체가 기획 정의(`scoring_draft.md` 5. "종일 연차 1일 = 하나의 근무 사이클을 전부 가능으로 전환")와 어긋났음을 사용자 지적으로 확인. 13~23시 근무를 통째로 빼는 데 1.5일(반차 불가 사용자는 2.0일)이 나오던 게 대표 증상. 전환 단위를 **정기 일정 한 행(근무 하나)** 단위로 바꿔, 종일 연차 1일이 그 근무가 막는 오전·오후·저녁을 **가릴 것 없이 전부** 열도록 재구현(`collectVacationOptions`/`addShiftUnits`/`SlotRequirement`). 이로써 §1-2 저녁 단독 구매·`openFreeEvenings`·`hasContinuousShiftIntoEvening`는 **개념 자체가 불필요해져 전부 삭제**(아래 두 줄은 폐기된 중간 단계 이력). 연차 소모 집계(`vacationDaysForShift`)도 같은 근무 단위로 통일. 사용자가 지정한 11개 시나리오 전부 회귀 테스트로 커버(반차 0.5일 케이스 신규 추가 포함), `./gradlew test` 486건 전부 통과 |
| 2026-08-15 | (폐기) **저녁 자동 해제 대상 좁힘(amend #4)** — "오후+저녁이 이어지는 근무(예: 13~23시, 오전은 원래 자유)에서 오후 반차 하나만 사도 저녁까지 공짜로 열리는 게 이상하다"는 사용자 지적 확인 후, `hasContinuousShiftIntoEvening`이 "오전 **또는** 오후 하나라도 막혀 있으면" 인정하던 것을 "오전 **그리고** 오후 둘 다 막혀 있어야" 인정하도록 좁혔다. 저녁만 근무하는 사람은 종일 연차(1.0일) 전액을 내야 하는데, 오후+저녁이 이어지는 근무는 오후 반차(0.5일)만으로 더 긴 시간(오후+저녁)을 없앨 수 있었던 가격 역전을 바로잡음 — 이제 이 케이스는 §1-2 저녁 단독 구매(1.0일)를 별도로 사야 저녁이 열린다. 10~20시처럼 한 행이 오전·오후를 모두 막고 저녁까지 이어지는 "진짜 종일 근무"는 영향 없음(회귀 테스트로 확인). 회귀·엣지 케이스 테스트 5개 추가(§1-1 좁힌 케이스 2개, halfVacationAvailable 상호작용 1개, 종일 근무 회귀 방지 1개, 개인 일정으로 막힌 저녁은 예산 있어도 못 삼 1개), `./gradlew test` 482건 전부 통과 |
| 2026-08-15 | (폐기) **저녁 단독 구매 추가** — "저녁만 근무하는 사람은 연차로 저녁을 못 없앤다"는 지적 확인 중, 기획자 노션 문서("반차 계산: 오전 반차 0.5일·오후 반차 0.5일·종일 연차 1일")를 재확인해 "저녁 반차"라는 0.5일 상품은 없되 **종일 연차(1.0일)로는 저녁도 독립 구매 가능**해야 함을 확정. §1-1(같은 근무 연장 시 공짜 해제)에 해당하지 않는 저녁 슬롯(저녁만 하는 근무, 또는 낮 근무와 무관한 별개 저녁 근무)을 `collectConversionUnits`에 `cost=1.0일` 고정 후보로 추가(§1-2). "어느 근무에 썼는지" 라벨링 없이 공유 예산 안에서 완전탐색이 알아서 최적 조합(둘 다 살지, 하나만 살지)을 고름. `vacationDaysForSpan`에도 이 구매분만 1.0일로 집계 추가. 새 테스트 2개(저녁만 근무 시 종일 연차로 구매, 예산 충분 시 낮 근무+저녁 근무 둘 다 구매) 추가, `./gradlew test` 477건 전부 통과 |
| 2026-08-15 | **저녁 자동 해제 버그 수정** — `new_problem/test_set.md` 5인 시나리오를 `RecommendationEngineTestSetScenarioTest`로 재현하던 중, 퇴근 시간이 18시 이후인 사용자(B·D)가 하루 종일 연차를 써도 저녁 슬롯만 계속 막힌 채로 남아 FULL_ATTEND 대신 PARTIAL_ATTEND로 오분류되는 버그 발견 — 1순위가 기대값(10/24~26) 대신 10/4~6으로 잘못 나옴. `RecommendationEngine.openFreeEvenings` 추가(오전·오후가 둘 다 POSSIBLE이 된 날, 저녁 IMPOSSIBLE 사유가 정기 근무뿐이면 함께 연다 — 위 "연차/반차 자동 반영 규칙" §1-1). `blockedByNonWorkReason`을 저녁까지 지원하도록 확장. 진단용이던 시나리오 테스트를 실제 assertion 기반으로 전환, `./gradlew test` 475건 전부 통과 |
| 2026-08-15 | **구현 완료** — `fix/105-vacation-attendance` 브랜치. `SchedulePort.findPersonalSchedulesByUserIds` 신설(개별 일정 원본 조회), `RecommendationEngine`에 `applyVacationSimulation`/`collectConversionUnits`/`primaryVacationSchedule` 추가. `vacationDaysForSpan`의 `halfVacationAvailable` 미반영 회귀도 같은 김에 수정(위 "전환 비용" 절 참고). 새 테스트 6개 추가, 기존 7개 유지, `./gradlew test` 473건 전부 통과 |
| 2026-08-15 | **구현 착수 → 블로커로 중단 → 해결** — 연차 예산을 어느 `RegularSchedule` 행 기준으로 볼지 블로커 발견, `TripFit-client` 코드 직접 확인으로 "첫 번째 등록 행 기준"이 실제 앱 동작과 일치함을 확인해 해결. 필드 위치 자체의 근본 수정은 `#52`로 분리 |
| 2026-08-15 | **Draft amend** — 연차/반차 자동 전환 시뮬레이션 추가(`new_problem/p1.md` 기획자 리포트). `maxVacationDays`/`halfVacationAvailable` 필드를 처음으로 추천 계산에 사용. 참여자 3분류를 시뮬레이션 결과 기준으로 재정의. 사용자 승인 대기 — 미구현 |
| 2026-07-30 | **Approved** — `#13`과 함께 확정, 구현 착수 |
| 2026-07-30 | "추천 근거" 상세 화면 확인 반영 — 참여자 3분류 로직을 모드 무관 `classifyMembers(...)`로 분리해 `#13`의 `GET .../recommendations/{rank}` 상세 API(참여자별 `attendance`/`uncertainDays`/`vacationDaysNeeded`, 라이브 재계산)에서 재사용 가능하게 명시 |
| 2026-07-30 | 기획자 알고리즘 확정본([`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md)) 반영 — 패널티 구간표·모드별 가중치·최종점수 공식·동점 기준 전부 확정값으로 교체. `ALL_ATTEND` 하드 필터·`w1/w2/w3` 구식·"주말·공휴일" 동점 기준 폐기. 카드 UI 확인 기반 `attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays` 응답 지표 추가 |
| 2026-07-24 | `trip-recommendation.md`(#13)에서 계산 로직 분리 — 신규 이슈 `#50` 생성 |
