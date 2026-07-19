# 홈 여행방 배치 — TERMINATED 전환 · Pin 자동 해제

> wave: 2 (후속)  
> implements: D5 Pin 자동 해제 · `TripStatus.TERMINATED` DB 정합  
> deferred from: [`trip-room-api.md`](trip-room-api.md) D5 · D8 · 2026-07-19  
> 상태: **Approved** (#27 · S1~S4 확정 — 2026-07-19)  
> GitHub: **[#27](https://github.com/Central-MakeUs/TripFit-server/issues/27)**

## 목표

1. **`end_range < today`** 인 `ONGOING` trip → DB `status=TERMINATED` 일괄 반영  
2. **`end_range < today`** 인 `trip_member.is_pinned=true` → `is_pinned=false`, `pinned_at=null` 일괄 해제  

현재 [#12](https://github.com/Central-MakeUs/TripFit-server/issues/12)는 **조회 시 effectiveStatus**로 TERMINATED UX를 맞춘다. 본 스펙은 **스케줄러로 DB·정렬 정합**을 맞춘다. 배치 반영 후 `effectiveStatus` lazy 분기는 **동일 동작**을 유지(idempotent).

## 배경

- effectiveStatus: `ONGOING` + `end_range` 경과 → API 응답 `TERMINATED` (lazy, DB는 `ONGOING` 유지 가능 — **본 스펙으로 DB UPDATE**)
- Pin: 기획 “희망 여행 기간 종료 시 고정 자동 해제” — lazy clear는 read API 부수 write 유발
- wave 4 `BR-NOTI-005` 정기 리마인드와 **별 job** (본 스펙은 홈 D5 유지보수)

## 확정 (S1~S4)

### S1 — TERMINATED 전환

**DB `status` UPDATE**

- 대상: `deleted_at IS NULL` · `status = ONGOING` · `end_range < :today` (KST 기준 `LocalDate`)
- `trip.status` → `TERMINATED` batch UPDATE
- DB와 API·필터 정합. effectiveStatus lazy는 배치 후에도 `TERMINATED` trip에 동일 결과.

### S2 — 실행 주기

**매일 00:05 KST, 1회**

- `@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")` (Spring 6-field cron)
- 서버 부하 최소 · Pin·종료 반영은 **일 1회**로 충분

### S3 — Pin job

**TERMINATED 전환과 동일 job · 동일 트랜잭션**

- 한 `@Scheduled` 메서드에서 순서:
  1. `ONGOING` + `end_range < today` → `status = TERMINATED`
  2. 해당 trip(및 동일 `end_range` 조건)의 `trip_member.is_pinned = true` → `false`, `pinned_at = null`
- 하나 실패 시 **전체 롤백** (@Transactional)
- idempotent — 재실행해도 이미 `TERMINATED`/unpinned면 no-op

### S4 — `@EnableScheduling`

**local · dev · prod 포함**

- `@EnableScheduling`은 **공통** 또는 local/dev/prod 프로필 모두에서 활성
- `bootRun`(local)에서도 cron 동작 · 테스트·수동 검증 가능
- 단위 테스트는 `@Scheduled` 직접 호출 또는 서비스 메서드 extract로 날짜 mock

## 구현 Must Have

- [x] S1~S4 확정·스펙 amend
- [x] `TripHomeScheduler` · `TripHomeMaintenanceService` — S2 cron · S3 단일 `@Transactional` job
- [x] soft-deleted trip 제외 · idempotent
- [x] `./gradlew test` — 날짜 mock 1건 이상

## Out of Scope

- 알림 발송 (wave 3 #21)
- `last_activity_at` 갱신 정책 — [`trip-last-activity-at.md`](trip-last-activity-at.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-19 | Draft — #12 후속 분리 |
| 2026-07-19 | **S1~S4 확정 · Approved** — DB TERMINATED · 00:05 KST · 통합 job · local/dev 포함 |
| 2026-07-19 | **Implemented** — `TripHomeScheduler` · batch UPDATE (#27) |
