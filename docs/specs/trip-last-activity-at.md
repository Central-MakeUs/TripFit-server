# trip.last_activity_at — 갱신 정책·AOP

> wave: 2 (후속)  
> implements: (없음 — D5 `last_activity_at` 컬럼은 [#12](https://github.com/Central-MakeUs/TripFit-server/issues/12)에서 선행)  
> deferred from: [`trip-room-api.md`](trip-room-api.md) D5 · 2026-07-20  
> 상태: **Draft** — Approved 전 구현 확장 금지  
> GitHub: **[#26](https://github.com/Central-MakeUs/TripFit-server/issues/26)**

## 목표

홈 목록 정렬용 `trip.last_activity_at`의 **갱신 트리거 SSOT**를 확정하고, 분산된 `touchLastActivity()` 호출을 **일관된 메커니즘**(수동 hook vs AOP)으로 정리한다.

## 배경

- D5에서 `last_activity_at` 컬럼·정렬은 [#12](https://github.com/Central-MakeUs/TripFit-server/issues/12) Must.
- 기획상 “최 recent 활동” 이벤트(일정 제출/수정, join, 추천, 확정, PATCH 등)는 [`trip-room-api.md`](trip-room-api.md) §D5에 나열되어 있으나, **User 전역 일정 수정 → 참여 trip 반영(BR-USER-008)** 등 경계가 모호하다.
- 현재 구현: create/join/patch/submit에서 `Trip.touchLastActivity()` **수동 호출**(최소 C). #13 추천·확정 hook 미연동.

## 확정 전 `[미정]`

| ID | 항목 | 후보 |
|----|------|------|
| L1 | 갱신 이벤트 목록 | 스펙 §D5 전체 vs join/patch/submit만 vs #13·#22 확정 후 |
| L2 | User schedule PATCH | 참여 trip 전부 touch vs touch 안 함 |
| L3 | 구현 방식 | **A** 수동 `touchLastActivity()` · **B** `@TripActivity` + AOP · **C** Domain event + listener |
| L4 | AOP 범위 | TripService public만 vs #13 TripRecommendationService 포함 |

## Must Have (Approved 후)

- [ ] L1~L4 확정·스펙 amend
- [ ] #13·#22와 hook 계약 문서화
- [ ] `./gradlew test` — 갱신 이벤트별 1건 이상

## Out of Scope

- 홈 정렬·`scope` API — [`trip-room-api.md`](trip-room-api.md) D5 (Implemented)
- TERMINATED·Pin 스케줄러 — [`trip-home-schedulers.md`](trip-home-schedulers.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-20 | Draft — #12 후속 분리 |
