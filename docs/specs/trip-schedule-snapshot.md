# 완료·만료 여행방 일정 Snapshot

> wave: 2  
> 상태: **Approved** (2026-07-21) · **구현 중** `feat/38-trip-schedule-snapshot`  
> MVP: In scope · **Wave 2 Must** ([#30](https://github.com/Central-MakeUs/TripFit-server/issues/30))  
> GitHub: **[#38](https://github.com/Central-MakeUs/TripFit-server/issues/38)**  
> 선행: [`trip-home-schedulers.md`](trip-home-schedulers.md) (#27), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17), [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) (#37)  
> related: [`trip-room-api.md`](trip-room-api.md) (#12), [`trip-recommendation.md`](trip-recommendation.md) (#13)  
> implements: **BR-USER-008** (ONGOING만 전역 live)

## 목표

**CONFIRMED ∪ EXPIRED** 여행방 달력은 freeze 시점 effective를 **보존**하고 **읽기 전용**으로 열람한다.

## 제품 확정

대상(CONFIRMED∪EXPIRED)·조회 기간(`startRange`~`endRange`)·읽기 전용은 [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) C3가 SSOT — 여기서 중복 정의하지 않는다. 구 `CANCELED` 제외 규칙은 `#48`에서 enum 자체 삭제로 해당 없음. 본 스펙 고유 내용:

| ID | 확정 |
|----|------|
| **S3** | 멤버 전원 **effective** (regular⊕personal, R2=A — [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md)) — freeze 시점 값을 snapshot 테이블에 저장 |
| **S5** | ONGOING = live (본 스펙 밖, `trip-schedule-calendar-window.md` C2) |

### Freeze 시점 (R-freeze) — 공백 없음 (R-gap)

| 상태 | 트리거 | 트랜잭션 |
|------|--------|----------|
| **CONFIRMED** | `TripRecommendationService.confirmSchedule` — status=CONFIRMED 직후 `freezeTrip` (#13이 confirmed_* 보강) | 동일 TX |
| **EXPIRED** | `#27` `TripHomeMaintenanceService.runForDate` — freeze 후 status=EXPIRED | 동일 TX |

### 저장 모델 (R-model)

| 선택 | 내용 |
|------|------|
| **A (확정)** | `trip_member_schedule_snapshot` — 멤버×날짜 effective. 희망 기간·sparse |

### 추천 (X8)

재추천 없음. snapshot은 달력 조회 전용.

## 요구사항

### Must Have

- [x] 엔티티·리포지토리 (R-model A) + ERD
- [x] CONFIRMED 확정 경로에서 snapshot write (`TripRecommendationService.confirmSchedule` · #13 dates TODO)
- [x] #27 `TripHomeMaintenanceService`에 EXPIRED snapshot (동일 TX)
- [x] `GET .../members/schedule-calendar` — CONFIRMED/EXPIRED → snapshot · ONGOING → live
- [x] 전역 CRUD는 live만 · snapshot 불변
- [x] BR-USER-008 · glossary 정합 (기반영)
- [x] `./gradlew test`

### Nice to Have

- [x] 응답 `readOnly: true`

## 완료 기준

- [x] 제품·잔여 **Approved** + BR-USER-008 본문 amend
- [x] ERD · 코드 · #27 확장 · `./gradlew test`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제(CANCELED 제외 규칙 해당 없음), `TERMINATED` → `EXPIRED` 리네임 |
| 2026-07-21 | Draft · CONFIRMED∪TERMINATED 재확정 |
| 2026-07-21 | **Approved** — R-freeze·R-gap·R-model=A·X8=A |
| 2026-07-21 | **구현** — 엔티티·freeze·#27·GET 분기 · confirm 훅 |
