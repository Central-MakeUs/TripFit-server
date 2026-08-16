# 011 — 공휴일 데이터 소스 (공공데이터포털 특일정보 API)

- **상태:** 확정
- **날짜:** 2026-08-16
- **관련:** [`docs/specs/user-schedule/schedule-calendar-resolve.md`](../specs/user-schedule/schedule-calendar-resolve.md) A4, [`docs/specs/trip/trip-recommendation-algorithm.md`](../specs/trip/trip-recommendation-algorithm.md), [`010-redis-infra.md`](010-redis-infra.md), Issue #107, Issue #2

## 맥락

`RegularSchedule.holidayRest`(공휴일 휴무 여부) 필드는 저장만 되고 `ScheduleCalendarResolver`의 실제 근무일 판정에는 반영되지 않는다. 대한민국 공휴일·대체공휴일 데이터 자체가 코드베이스에 없어, 이를 반영하려면 데이터 소스를 먼저 정해야 한다 (`#2` 미정 항목, 진행 이슈 `#107`).

후보는 두 가지였다: ① 연도별 static table(코드에 하드코드) ② 공공데이터포털 특일정보 API 연동.

## 결정

**공공데이터포털 특일정보 API**를 연동하고, `010`에서 이미 구축한 Redis에 결과를 캐싱한다.

- 스케줄러가 주기적으로 API를 조회해 Redis에 캐싱하는 구조로, `GoogleCalendarSyncScheduler`(외부 API 동기화)·`RedisTokenRevocationChecker`(Redis 캐시 조회)와 동일한 기존 패턴을 재사용한다 — 이 저장소에 새 인프라 개념을 들이는 게 아니다.
- 캐싱 TTL·재동기화 주기·API 실패 시 폴백 정책(마지막 캐시값 유지 등)의 세부값은 `#107` 스펙(`specify`)에서 확정한다 — 이 문서는 데이터 소스 자체의 선택만 다룬다.

## 고려한 대안

| 대안 | 장점 | 단점 | 채택 |
|------|------|------|------|
| A. 연도별 static table(코드 하드코드) | 외부 의존성·API 키 없음, 구현 최소 | 매년 + 정부가 연중 발표하는 임시공휴일마다 **사람이 기억해서 코드 수정 + 배포**해야 함 — 잊으면 추천 결과가 조용히 틀려짐 | ✗ |
| **B. 공공데이터포털 특일정보 API (택)** | 정부 공식 데이터로 자동 최신화, 임시공휴일도 재동기화로 반영, 기존 Redis(`010`)·스케줄러(`GoogleCalendarSyncScheduler`) 패턴 재사용 가능 | API 키 발급(활용신청 승인 대기) 필요, 응답 포맷·인코딩 이슈로 국내에서 흔한 초기 연동 디버깅 비용, 새 외부 실패 지점 추가 | **✓** |

## 트레이드오프 · 후속 리스크

- **API 키 발급**: 공공데이터포털 회원가입 + 활용신청이 실제 선행 작업으로 필요하다(승인까지 대기 시간 있을 수 있음) — `#107` 구현 착수 전에 확보해야 한다.
- **새 외부 실패 지점**: 앱 인증 경로가 아니라 추천 알고리즘 계산 경로가 이 API 가용성에 간접 의존하게 된다. `004`의 Redis fail-open 사례처럼, 조회 실패 시 마지막 캐시값을 계속 쓰는 정책이 유력하지만 확정은 `#107` 스펙에서 한다.
- **인코딩**: 공공데이터포털 API는 인증키의 URL 인코딩/디코딩 처리로 국내에서 흔히 겪는 이슈이므로 초기 연동 시 별도 검증이 필요하다.

## 후속 작업

- [x] `#2` 미정 항목 확정 처리
- [ ] `#107` — `specify` 스킬로 스펙 작성: API 필드 매핑, Redis 캐싱 TTL·재동기화 주기, 실패 폴백 정책, `ScheduleCalendarResolver` 반영 범위
- [ ] 공공데이터포털 활용신청 + 인증키 발급 (`#107` 착수 전 선행)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-16 | 초안 — 공공데이터포털 특일정보 API 확정 (static table 대비 Redis 기존 인프라 재사용 근거) |
