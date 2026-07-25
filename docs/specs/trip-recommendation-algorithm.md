# 추천 결과 계산 로직 (후보 윈도우 · 모드별 스코어링 · 동점)

> wave: 2
> implements: BR-TRIP-005, BR-TRIP-011, BR-TRIP-012
> deferred: 가중치(w1/w2/w3) 실제 수치 튜닝 — prod 전 별도 결정, MVP는 순위 재현성만
> 상태: Draft
> GitHub: **#50**
> 선행: [`trip-recommendation.md`](trip-recommendation.md) (#13, `Recommendation` 엔티티·Repository·Controller stub) · [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17, Closed)

## 목표

`#13`이 만든 API 계약(`POST /trips/{tripId}/recommendations`)이 실제로 반환할 **TOP 3 계산 로직**을 만든다. `#13`은 Controller·DTO·엔티티·상태 전이·hard DELETE 트리거를 담당하고, 이 스펙은 "무엇을 저장할지" — 즉 후보 생성부터 순위 산출까지의 계산만 담당한다.

## 배경

- `#13` 작업 중 사용자 요청으로 분리 확정(2026-07-24): API 껍데기와 계산 로직을 별도 이슈로 관리
- 원 스펙([`trip-recommendation.md`](trip-recommendation.md))의 "알고리즘(구현 가이드)" 절을 그대로 승계
- `#13`은 이 스펙이 끝나기 전까지 `POST /recommendations`를 플레이스홀더 값으로 응답해 API 계약만 검증한다
- Wave 2 MVP DoD("추천으로 최종 날짜 확정")가 실제로 동작하려면 `#13`과 이 이슈 **둘 다** Closed 필요 — Wave Backlog `#30` Must에 반영

## 요구사항

### Must Have

- [ ] 후보 윈도우: `[trip.startRange, trip.endRange]` 내 길이 = `durationDays`인 모든 연속 `[startDate, endDate]` 슬라이딩 생성. `durationDays` null이면 계산 자체 불가(호출 측에서 처리, 이 스펙은 non-null 전제)
- [ ] **입력 resolve 재사용:** `#17` `ScheduleCalendarResolveService`를 그대로 호출해 멤버×날짜 effective(가능/불가/미정) 집계 (C1 — 별도 병합 로직 신설 금지)
- [ ] TBD 판정: `personal_schedule.uncertain=true`인 날짜 (CERTAIN 모드 · U1 달력과 동일 정의)
- [ ] 정기 일정 연차 산출: `maxVacationDays`·`VacationApplyPeriod`·반차·공휴일 휴무 필드 참고 (BR-TRIP-006). workday IMPOSSIBLE → +1일 추정 `[제안]`(복수 행 집계 규칙은 `[미정]`)
- [ ] 모드별 점수화 4종 — 가중치 수치는 `[미정]`, **deterministic + 테스트로 순위 재현 가능**하면 충분:
  - `BASIC`: `w1*attendRate - w2*vacationDays - w3*tbdRate`
  - `ALL_ATTEND`: BASIC과 동일 정렬 + 아래 하드 필터
  - `SAVE_VACATION`: `-vacationDays` primary
  - `CERTAIN`: `-tbdCount` primary
- [ ] `ALL_ATTEND` 하드 필터: 가능 인원 < `memberCount`인 후보 제외 (BR-TRIP-011). 필터 후 후보 0건 → 호출 측에 `NO_RECOMMENDATION_CANDIDATES` 신호(예외/Result 타입 `[제안]`, `#13`이 400 매핑)
- [ ] BR-TRIP-012 동점 comparator: 1) 연차 적은 순 2) 기간 긴 순 3) 주말·공휴일 포함 순
- [ ] 계산 결과(TOP 3, 각 `rank`/`startDate`/`endDate`/`score`)를 `#13`의 `RecommendationService` 인터페이스로 반환 — 저장(hard DELETE + INSERT)은 `#13` 책임
- [ ] `./gradlew test` — 고정 fixture(멤버·`regular_schedule`/`personal_schedule`)로 모드별 rank 1 기대값, 동점 comparator 순서, `ALL_ATTEND` 필터, hard filter 단위 테스트

### Out of Scope

- API 요청/응답 DTO·Controller·상태 전이(`ONGOING`↔`CONFIRMED`)·hard DELETE 실행 — `#13`
- 가중치(w1/w2/w3) 실제 수치 튜닝 — prod 전 별도 결정
- 공휴일 API 연동 — 주말만 우선 `[제안]`, static table/외부 API 방식은 `[미정]`
- 알림 발송(BR-NOTI-004) — Wave 3 `#21`

## 인터페이스 (예시, `#13`과의 경계)

```java
// #13이 정의하는 계약 — 이 스펙은 이 메서드의 "본문"만 채운다
interface RecommendationEngine {
    List<RecommendationCandidate> generate(Trip trip, RecommendationMode mode);
}
```

`RecommendationCandidate { rank, startDate, endDate, score }` — 저장·직렬화 필드 매핑은 `#13`(엔티티/DTO) 소관.

## 비즈니스 규칙

| BR | 적용 내용 |
|----|-----------|
| BR-TRIP-005 | 4모드 TOP 3 계산 |
| BR-TRIP-011 | `ALL_ATTEND` 하드 필터 |
| BR-TRIP-012 | 동점 comparator |

## 검증 시나리오

- [ ] `BASIC` 모드 — 고정 fixture로 rank 1~3 기대값 일치
- [ ] `ALL_ATTEND` — target 6명, 5명만 가능한 후보 제외 확인
- [ ] `SAVE_VACATION`/`CERTAIN` — primary 정렬 키 확인
- [ ] 동점 fixture — comparator 순서(연차→기간→주말) 확인
- [ ] `ALL_ATTEND` 후보 0건 — `NO_RECOMMENDATION_CANDIDATES` 신호 반환
- [ ] resolve 결과가 `#17`과 동일한 effective 값을 사용하는지(별도 병합 로직 없음) 확인

## 완료 기준

- [ ] `./gradlew test` 통과 (RecommendationEngine 관련 단위 테스트)
- [ ] `#13`의 `RecommendationService`에서 플레이스홀더 대신 이 로직 호출로 교체
- [ ] Wave 2 MVP 완료 기준: 방장이 4모드 중 하나로 실제 계산된 TOP 3를 확인 가능

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| BR-TRIP-005 가중치 w1/w2/w3 | `[미정]` | MVP는 상대 순위만 맞으면 됨 — 튜닝은 prod 전 |
| 연차 산출 규칙 | `[제안]` | IMPOSSIBLE on workday → 1일; `halfVacationAvailable` 반영 `[미정]` |
| 공휴일 데이터 | `[미정]` | KR 공휴일 static table vs API |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | `trip-recommendation.md`(#13)에서 계산 로직 분리 — 신규 이슈 `#50` 생성 |
