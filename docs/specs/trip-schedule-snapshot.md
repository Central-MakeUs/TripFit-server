# 완료·만료 여행방 일정 Snapshot

> wave: 2  
> 상태: **Approved** (2026-07-21)  
> MVP: In scope · **Wave 2 Must** ([#30](https://github.com/Central-MakeUs/TripFit-server/issues/30))  
> GitHub: **[#38](https://github.com/Central-MakeUs/TripFit-server/issues/38)**  
> 선행: [`trip-home-schedulers.md`](trip-home-schedulers.md) (#27), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) (#17), [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) (#37)  
> related: [`trip-room-api.md`](trip-room-api.md) (#12), [`trip-recommendation.md`](trip-recommendation.md) (#13)  
> implements: **BR-USER-008** (ONGOING만 전역 live)

## 목표

**CONFIRMED ∪ TERMINATED** 여행방 달력은 freeze 시점 effective를 **보존**하고 **읽기 전용**으로 열람한다.

## 제품 확정

| ID | 확정 |
|----|------|
| **S1** | 대상: **CONFIRMED ∪ TERMINATED** |
| **S2** | 기간: **`startRange` ~ `endRange`** |
| **S3** | 멤버 전원 **effective** (regular⊕personal, S1·R2=A) |
| **S4** | **읽기 전용** |
| **S5** | ONGOING = live (본 스펙 밖) |
| **S6** | CANCELED = 대상 아님 (#37 R1) |

### Freeze 시점 (R-freeze) — 공백 없음 (R-gap)

| 상태 | 트리거 | 트랜잭션 |
|------|--------|----------|
| **CONFIRMED** | 일정 **확정 API 성공**과 **동일 트랜잭션** | status 확정 + snapshot 원자적 |
| **TERMINATED** | **#27** job과 **동일 트랜잭션** | `ONGOING→TERMINATED` + Pin 해제 + snapshot |

- **R-gap:** freeze 전 공백 **불허**. lazy/live fallback/503 준비 중 **채택하지 않음**.

### 저장 모델 (R-model)

| 선택 | 내용 |
|------|------|
| **A (확정)** | `trip_member_schedule_snapshot`(가칭) — **멤버×날짜** effective 행 (슬롯3 + uncertain). 범위 = 희망 기간만 |
| 비채택 | B JSON blob · C 원본 복사+재resolve |

Flyway 금지 — 엔티티 + `ddl-auto`.

### 추천 (X8)

| 확정 | 내용 |
|------|------|
| **A** | CONFIRMED/TERMINATED **재추천 없음** (기존 #13·방 상태 정책과 정합). snapshot은 **달력 조회 전용** |

## 요구사항

### Must Have

- [ ] 엔티티·리포지토리 (R-model A) + ERD
- [ ] CONFIRMED 확정 경로에서 snapshot write (동일 TX)
- [ ] #27 `TripHomeMaintenanceService`에 TERMINATED snapshot (동일 TX)
- [ ] `GET .../members/schedule-calendar` — CONFIRMED/TERMINATED → snapshot · ONGOING → live · CANCELED 거부
- [ ] 전역 CRUD는 live만 · snapshot 불변
- [ ] BR-USER-008 · glossary 정합
- [ ] `./gradlew test`

### Nice to Have

- [ ] 응답 `frozenAt` / `readOnly: true`

### Out of Scope

- #37 C1 마이페이지 윈도우
- snapshot 수동 재생성
- CANCELED snapshot
- 추천 엔진이 snapshot 사용 (X8=A)

## API

| Method | Path | 동작 |
|--------|------|------|
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | ONGOING live · CONFIRMED/TERMINATED snapshot+readOnly · CANCELED 거부 |

## 비즈니스 규칙

| BR | 확정 |
|----|------|
| **BR-USER-008** | 전역 일정 변경 → **ONGOING** 방 달력만 동일. **CONFIRMED/TERMINATED**는 snapshot 고정 |

## 검증

- [ ] ONGOING live · 수정 반영
- [ ] CONFIRMED 직후 snapshot · 이후 regular 삭제해도 방 달력 불변
- [ ] TERMINATED(#27) 직후 동일
- [ ] freeze와 status 전환 원자적 (중간 GET이 live로 떨어지지 않음)
- [ ] 마이페이지 calendar는 전역 삭제 반영
- [ ] CANCELED schedule-calendar 거부

## 충돌·잔여 해소

| ID | 확정 |
|----|------|
| X6 · X9 · X10 · X12 | 2026-07-21 재확정 |
| **R-freeze** | CONFIRMED=확정 TX · TERMINATED=#27 동일 job |
| **R-gap** | 공백 불허 (원자적 freeze) |
| **R-model** | **A** 멤버×날짜 effective |
| **X8** | 재추천 없음 · calendar만 snapshot |

## 완료 기준

- [x] 제품·잔여 **Approved** + BR-USER-008 본문 amend
- [ ] ERD · 코드 · #27 확장 · `./gradlew test`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | Draft · CONFIRMED∪TERMINATED 재확정 |
| 2026-07-21 | **Approved** — R-freeze·R-gap·R-model=A·X8=A |
