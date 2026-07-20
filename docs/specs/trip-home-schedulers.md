# 홈 여행방 배치 — TERMINATED 전환 · Pin 자동 해제

> wave: 2 (후속)  
> implements: D5 Pin 자동 해제 · `TripStatus.TERMINATED` DB 정합  
> deferred from: [`trip-room-api.md`](trip-room-api.md) D5 · D8 · 2026-07-20  
> 상태: **Draft** — Approved 전 `@Scheduled` 구현 금지  
> GitHub: **[#27](https://github.com/Central-MakeUs/TripFit-server/issues/27)**

## 목표

1. **`end_range < today`** 인 `ONGOING` trip → DB `status=TERMINATED` (또는 팀 합의 정책) 일괄 반영  
2. **`end_range < today`** 인 `trip_member.is_pinned=true` → `is_pinned=false`, `pinned_at=null` 일괄 해제  

현재 [#12](https://github.com/Central-MakeUs/TripFit-server/issues/12)는 **조회 시 effectiveStatus·lazy Pin 해제**로 UX를 맞춘다. 본 스펙은 **스케줄러로 DB·정렬 정합**을 맞춘다.

## 배경

- effectiveStatus: `ONGOING` + `end_range` 경과 → API 응답 `TERMINATED` (lazy, DB는 `ONGOING` 유지 가능)
- Pin: 기획 “희망 여행 기간 종료 시 고정 자동 해제” — lazy clear는 read API 부수 write 유발
- wave 4 `BR-NOTI-005` 정기 리마인드와 **별 job** (본 스펙은 홈 D5 유지보수)

## 확정 전 `[미정]`

| ID | 항목 | 후보 |
|----|------|------|
| S1 | TERMINATED 전환 | DB `status` UPDATE vs effectiveStatus만 유지 |
| S2 | 실행 주기 | 매일 00:05 KST · hourly · `@Scheduled(cron=…)` |
| S3 | Pin job | TERMINATED job과 동일 트랜잭션 vs 분리 |
| S4 | `@EnableScheduling` | prod만 vs local/dev 포함 |

## Must Have (Approved 후)

- [ ] S1~S4 확정
- [ ] `TripScheduler`(가칭) — `end_range < :today` 대상 trip·trip_member batch UPDATE
- [ ] idempotent · soft-deleted trip 제외
- [ ] `./gradlew test` — 단위(날짜 mock) 또는 slice 1건

## Out of Scope

- 알림 발송 (wave 3 #21)
- `last_activity_at` 갱신 정책 — [`trip-last-activity-at.md`](trip-last-activity-at.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-20 | Draft — #12 후속 분리 |
