# 추천 결과 계산 로직 (후보 윈도우 · 모드별 스코어링 · 동점)

> wave: 2
> implements: BR-TRIP-005, BR-TRIP-011, BR-TRIP-012
> deferred: 공휴일 API 연동 (주말만 우선, static table/외부 API 방식은 `[미정]`)
> 상태: **Approved** (2026-07-30 — `scoring_draft.md` 확정본 반영 후 확정)
> GitHub: **#50**
> 선행: [`trip-recommendation.md`](trip-recommendation.md) (#13, `Recommendation` 엔티티·Repository·Controller stub) · [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17, Closed)

## 목표

`#13`이 만든 API 계약(`POST /trips/{tripId}/recommendations`)이 실제로 반환할 **TOP 3 계산 로직**을 만든다. `#13`은 Controller·DTO·엔티티·상태 전이·hard DELETE 트리거를 담당하고, 이 스펙은 "무엇을 저장할지" — 즉 후보 생성부터 순위 산출까지의 계산만 담당한다.

## 배경

- `#13` 작업 중 사용자 요청으로 분리 확정(2026-07-24): API 껍데기와 계산 로직을 별도 이슈로 관리
- **2026-07-30 확정:** 기획자 알고리즘 확정본([`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md))을 그대로 반영 — 패널티 구간표·모드별 가중치·최종점수 공식·동점 기준까지 전부 확정값. **이전 초안**(이 문서 2026-07-24판)의 `w1*attendRate - w2*vacationDays - w3*tbdRate` 식·`ALL_ATTEND` **하드 필터**·`NO_RECOMMENDATION_CANDIDATES` 에러·동점 기준 "주말·공휴일"은 **전부 폐기**하고 이 버전으로 대체한다
- `#13`은 이 스펙이 끝나기 전까지 `POST /recommendations`를 플레이스홀더 값으로 응답해 API 계약만 검증한다
- Wave 2 MVP DoD("추천으로 최종 날짜 확정")가 실제로 동작하려면 `#13`과 이 이슈 **둘 다** Closed 필요 — Wave Backlog `#30` Must에 반영
- **2026-07-30 화면 확인(추천 결과 카드, 방장 뷰):** 카드에 `참석률(%)`·`불확실 일정 인원`·`부분 참여 인원`·`연차 일수`가 노출됨 — 응답 DTO·`Recommendation` 엔티티에 이 4개 원시 지표를 그대로 담아야 한다(자연어 `reason`/`riskNote` 자동생성 Nice to have는 화면에 없어 **폐기**). 상세: `trip-recommendation.md` 데이터 모델 절

## 요구사항

### Must Have

- [ ] **후보 윈도우 생성:** `[trip.startRange, trip.endRange]` 내에서 길이 = `trip.durationDays`인 모든 연속 `[startDate, endDate]`를 하루씩 슬라이딩하며 생성. `durationDays`가 null이면 계산 자체 불가(호출 측 `#13`이 사전 검증, 이 스펙은 non-null 전제)
- [ ] **입력 resolve 재사용:** `ScheduleCalendarResolver.resolve(...)`(`#17`, static utility)를 그대로 호출해 멤버×날짜×슬롯(오전/오후/저녁) effective(가능/불가) + 날짜 단위 `uncertain`을 산출 — 별도 병합 로직 신설 금지(C1)
- [ ] **응답 참여자 판정:** 후보 윈도우 전체 날짜에 대해 `resolve` 결과가 완전히 비어 있는(정기·개별 일정 신호가 전혀 없는) ACTIVE 멤버는 **미응답**으로 분류해 아래 모든 계산에서 제외. 방장은 일정 확인(`schedule/confirm`) 완료가 ACTIVE 전제이므로 항상 응답자로 카운트됨 — 응답 참여자 0명은 발생하지 않음
- [ ] **참여자 3분류** (후보 윈도우 기준, 응답 참여자만 대상):
  - 전체 참석: 후보 윈도우의 모든 슬롯이 가능(POSSIBLE)
  - 부분 참석: 전체 슬롯 수(`durationDays × 3`)의 **⌈50%⌉ 이상**을 **하나의 연속된 구간**으로 참석 가능(늦참·조기귀가만 인정 — 중간 이탈 후 재합류는 불인정)
  - 불참: 위 두 조건을 만족하지 못하는 나머지
- [ ] **불확실 인원:** 후보 윈도우 내 하루 이상 `personal_schedule.uncertain=true`인 응답 참여자 수(1인당 최대 1로 카운트, 여러 날짜 선택해도 1명) — 부분 참석과 독립 집계(중복 카운트 가능)
- [ ] **연차 계산:** 완전 불참자 제외. 반차(오전/오후)=0.5일, 종일=1일로 환산해 `totalVacationDays`(총 연차 일수)·`vacationMemberCount`(연차 계산 대상 인원 수) 산출
- [ ] **평가 항목 4종 패널티** (아래 "패널티 구간표" 절 수치 그대로):
  1. 불참률 = 불참 인원 / 응답 참여자 수
  2. 부분 참석률 = 부분 참석 인원 / 응답 참여자 수
  3. 불확실 인원 비율 = 불확실 인원 / 응답 참여자 수
  4. 1인당 평균 연차 일수 = `totalVacationDays` / `vacationMemberCount` (`vacationMemberCount=0`이면 0일로 취급)
- [ ] **모드별 가중치 적용** (아래 "모드별 가중치" 절 수치 그대로) → **최종점수 = 100 - Σ(패널티×가중치)**
- [ ] **`ALL_ATTEND`는 하드 필터가 아니다** — 목표 인원 미달 후보를 제외하지 않는다. 불참률·부분 참석 인원 비율 가중치를 크게(5.0/3.0) 둬 점수로만 반영한다(BR-TRIP-011 개정)
- [ ] **동점 comparator** (BR-TRIP-012 개정): 1) 불확실 일정 수 적은 순 2) 시작일 빠른 순 (구 기준 "연차→기간→주말·공휴일"은 폐기)
- [ ] **Best 3 정렬:** 최종점수 내림차순 → 동점 comparator → 상위 3개
- [ ] 계산 결과(TOP 3, 각 `rank`/`startDate`/`endDate`/`attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays`/`score`)를 `#13`의 `RecommendationEngine` 인터페이스로 반환 — 저장(hard DELETE + INSERT)은 `#13` 책임
- [ ] **참여자 분류를 참여자별로도 재사용 가능하게 노출** (2026-07-30 "추천 근거" 상세 화면 확인) — `#13`의 `GET .../recommendations/{rank}` 상세 API는 이 스펙의 참여자 3분류 로직을 특정 `[startDate, endDate]` 구간 하나에 대해 **다시 호출**해 참여자별 `attendance`(`FULL_ATTEND`/`PARTIAL_ATTEND`/`NON_ATTEND`)·`uncertainDays`(해당 구간 내 불확실 날짜 수)·`vacationDaysNeeded`(해당 참여자의 필요 연차일수)를 받는다. 이 결과는 저장하지 않고 상세 조회 시 라이브 재계산(카드 목록 응답을 무겁게 만들지 않기 위함) — 계산 자체는 모드에 의존하지 않으므로(모드는 가중치에만 영향) 참여자 3분류·불확실·연차 로직을 모드 파라미터 없이 별도로 호출 가능한 형태로 분리해 둘 것
- [ ] `./gradlew test` — 고정 fixture(멤버·`regular_schedule`/`personal_schedule`)로 모드별 rank 1 기대값, 부분 참석 경계값(⌈50%⌉ 올림), 패널티 구간 경계값, 동점 comparator 순서 단위 테스트

### Out of Scope

- API 요청/응답 DTO·Controller·상태 전이(`ONGOING`↔`CONFIRMED`)·hard DELETE 실행 — `#13`
- 공휴일 API 연동 — 주말만 우선 `[제안]`, static table/외부 API 방식은 `[미정]`
- 알림 발송(BR-NOTI-004) — Wave 3 `#21`
- `attendRate`(카드 표시용 참석률 %) 계산식의 **최종 확정** — 아래 "카드 표시 지표" 절 참고, 화면 역산 기반 추론값이며 기획 확정 대기

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
| 1 | **미응답 참여자** — 응답 참여자 수·불참률·부분 참석 비율·불확실 인원 비율·연차 계산 전부에서 제외 |
| 2 | **완전 불참자** — 연차 계산(`totalVacationDays`·`vacationMemberCount`)에서만 제외(응답 참여자 수에는 포함) |
| 3 | **부분 참석과 불확실은 독립** — 동일 참여자가 둘 다 만족하면 양쪽 항목에 모두 포함(중복 계산 가능) |
| 4 | **불확실 일정 처리** — 날짜 단위 판정. 후보 윈도우 내 하루 이상 불확실 선택 시 그 참여자를 불확실 인원 1명으로 카운트(여러 날짜 선택해도 1명). 불확실 **일정 수**(날짜 개수 합)는 동점 처리에만 사용 |
| 5 | **반차** — 오전 반차 0.5일, 오후 반차 0.5일, 종일 연차 1일 |
| 6 | **동점 처리** — 1) 불확실 일정 수 적은 순 2) 날짜(시작일) 빠른 순 |

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

- [ ] `BASIC` 모드 — 고정 fixture로 rank 1~3 기대값 일치
- [ ] `ALL_ATTEND` — 목표 인원 미달 후보도 **제외되지 않고** 낮은 점수로 포함되는지 확인(하드 필터 없음)
- [ ] `SAVE_VACATION`/`CERTAIN` — 가중치 반영한 정렬 확인
- [ ] 부분 참석 경계값 — ⌈50%⌉ 올림 계산(예: 2박 3일 9개 중 5개 연속 = 부분 참석, 4개 = 불참)
- [ ] 패널티 구간 경계값 — 불참률 정확히 15%/30% 등 각 표의 이하/초과/미만 경계
- [ ] 동점 fixture — comparator 순서(불확실 일정 수 → 시작일) 확인
- [ ] 완전 불참자가 연차 계산에서 제외되는지 확인
- [ ] 미응답 참여자가 모든 계산에서 제외되고, 방장은 항상 응답자로 카운트되는지 확인
- [ ] `classifyMembers(...)`가 모드와 무관하게 동일한 참여자별 `attendance`/`uncertainDays`/`vacationDaysNeeded`를 반환하는지(모드는 점수 가중치에만 영향)
- [ ] resolve 결과가 `#17`과 동일한 합친 값을 사용하는지(별도 병합 로직 없음) 확인

## 완료 기준

- [ ] `./gradlew test` 통과 (RecommendationEngine 관련 단위 테스트)
- [ ] `#13`의 `RecommendationService`에서 플레이스홀더 대신 이 로직 호출로 교체
- [ ] Wave 2 MVP 완료 기준: 방장이 4모드 중 하나로 실제 계산된 TOP 3를 확인 가능

## 리스크·미결정

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| `attendRate`(카드 참석률 %) 계산식 | `[제안]` | 화면 역산 추정 — 기획 확정 필요 |
| 공휴일 데이터 | `[미정]` | KR 공휴일 static table vs API — 동점 처리엔 더 이상 불필요(주말·공휴일 기준 폐기), 카드 UI에 별도 필요해지면 재논의 |

## 변경 이력

| 날짜 | 변경 |
| --- | --- |
| 2026-07-30 | **Approved** — `#13`과 함께 확정, 구현 착수 |
| 2026-07-30 | "추천 근거" 상세 화면 확인 반영 — 참여자 3분류 로직을 모드 무관 `classifyMembers(...)`로 분리해 `#13`의 `GET .../recommendations/{rank}` 상세 API(참여자별 `attendance`/`uncertainDays`/`vacationDaysNeeded`, 라이브 재계산)에서 재사용 가능하게 명시 |
| 2026-07-30 | 기획자 알고리즘 확정본([`trip-recommendation-scoring-source.md`](trip-recommendation-scoring-source.md)) 반영 — 패널티 구간표·모드별 가중치·최종점수 공식·동점 기준 전부 확정값으로 교체. `ALL_ATTEND` 하드 필터·`w1/w2/w3` 구식·"주말·공휴일" 동점 기준 폐기. 카드 UI 확인 기반 `attendRate`/`partialAttendCount`/`uncertainCount`/`totalVacationDays` 응답 지표 추가 |
| 2026-07-24 | `trip-recommendation.md`(#13)에서 계산 로직 분리 — 신규 이슈 `#50` 생성 |
