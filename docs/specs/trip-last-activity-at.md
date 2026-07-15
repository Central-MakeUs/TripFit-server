# trip.last_activity_at — 갱신 정책·AOP

> wave: 2 (후속)  
> implements: (없음 — D5 `last_activity_at` 컬럼은 [#12](https://github.com/Central-MakeUs/TripFit-server/issues/12)에서 선행)  
> deferred from: [`trip-room-api.md`](trip-room-api.md) D5 · 2026-07-19  
> 상태: **Approved** (#26 · L1~L4 확정 — 2026-07-19)  
> GitHub: **[#26](https://github.com/Central-MakeUs/TripFit-server/issues/26)**

## 목표

홈 목록 정렬용 `trip.last_activity_at`의 **갱신 트리거 SSOT**를 확정하고, 분산된 `touchLastActivity()` 호출을 **일관된 메커니즘**(수동 hook vs AOP)으로 정리한다.

## 배경

- D5에서 `last_activity_at` 컬럼·정렬은 [#12](https://github.com/Central-MakeUs/TripFit-server/issues/12) Must.
- 기획상 “최 recent 활동” 이벤트(일정 제출/수정, join, 추천, 확정, PATCH 등)는 [`trip-room-api.md`](trip-room-api.md) §D5에 나열되어 있으나, **User 전역 일정 수정 → 참여 trip 반영(BR-USER-008)** 등 경계가 모호하다.
- 현재 구현: create/join/patch/submit에서 `Trip.touchLastActivity()` **수동 호출**(최소 C). #13 추천·확정 hook 미연동.

## 확정 (L1~L4)

### L1 — 갱신 이벤트 목록

홈 **노출·정렬** ([`trip-room-api.md`](trip-room-api.md) D5 · Figma 노출 규칙)과 동일. `last_activity_at`을 **갱신(touch)** 하는 이벤트:

| 이벤트 | touch | 구현·비고 |
|--------|-------|-----------|
| 여행방 생성 | ✓ | `created_at`과 동일 시각으로 초기화 ([#12](https://github.com/Central-MakeUs/TripFit-server/issues/12)) |
| 신규 참여 (join) | ✓ | Implemented |
| 여행방 정보 수정 (PATCH) | ✓ | Implemented |
| 일정 확인 완료 (구 trip `submit`) | ✓ | **#22** — submit 폐기 → **확인 완료 API** (`JOINED→RESPONDED`) touch |
| 추천 일정 생성 | ✓ | [#13](https://github.com/Central-MakeUs/TripFit-server/issues/13) — hook 미연동 |
| 일정 확정 | ✓ | [#13](https://github.com/Central-MakeUs/TripFit-server/issues/13) — hook 미연동 |
| 참여자 내보내기 | ✓ | [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) — MEMBER soft delete |
| Pin 토글 | ✗ | Pin → `pinned_at` 별도 정렬 (D5) |
| trip soft delete | ✗ | — |

**“일정 제출/수정” 해석:** trip `submit`은 touch. User **전역** regular/personal 일정 PATCH는 touch **하지 않음** (L2).

**정렬 (D5 SSOT):** 진행 중 캐러셀 — Pin → `pinned_at` → `last_activity_at` 내림차순. 전체 보기 — `last_activity_at`만.

### L2 — User 전역 일정 수정 → 참여 trip

**touch 안 함.**

- BR-USER-008: 일정 **데이터**는 User 전역 — trip FK 없음 · 참여 trip에 동일 데이터 반영.
- `last_activity_at`: **여행방 맥락** 활동(L1 표)만 갱신. 개인 달력만 수정했을 때 속한 모든 방이 홈 상단으로 올라가지 않음.

### L3 — 구현 방식

**B. `@TripActivity` + AOP**

- L1 touch 대상 **public** 유스케이스 메서드에 `@TripActivity` 선언.
- Spring AOP aspect: 메서드 **정상 종료 후** 해당 trip의 `lastActivityAt`을 `now()`로 갱신.
- create는 `@TripActivity` 대신 엔티티 생성 시 초기값 설정 유지 ([#12](https://github.com/Central-MakeUs/TripFit-server/issues/12)).
- 기존 #12 **수동** `touchLastActivity()` 호출은 AOP 도입 시 **제거**하고 어노테이션으로 통일.

### L4 — 적용 범위

**`TripCommandService`(및 trip command) + `TripRecommendationService`(#13) public 메서드**

- trip 도메인: join · PATCH · submit 등 L1 이벤트.
- 추천 도메인(#13): 추천 일정 생성 · 일정 확정 — 동일 `@TripActivity` + aspect.

## 구현 Must Have

- [x] L1~L4 확정·스펙 amend
- [x] `@TripActivity` · aspect 구현 · #12 수동 hook → AOP 이전
- [x] `TripRecommendationService` stub — L1 추천·확정 `@TripActivity` (#13 본 구현 대기)
- [x] `./gradlew test` — L1 갱신 이벤트별 1건 이상

## Out of Scope

- 홈 정렬·`scope` API — [`trip-room-api.md`](trip-room-api.md) D5 (Implemented)
- TERMINATED·Pin 스케줄러 — [`trip-home-schedulers.md`](trip-home-schedulers.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-19 | Draft — #12 후속 분리 |
| 2026-07-19 | **L1·L2 확정** — 갱신 이벤트 목록 · 전역 일정 PATCH touch 안 함 |
| 2026-07-19 | **L3·L4 확정 · Approved** — `@TripActivity` AOP · `TripRecommendationService` 포함 |
| 2026-07-21 | L1 — 참여자 내보내기 touch (#20) |
