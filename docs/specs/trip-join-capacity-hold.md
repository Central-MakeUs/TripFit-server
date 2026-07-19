# 여행방 join 정원 선점·예약 (Capacity hold)

> wave: **4**
> 상태: **Draft**
> MVP: Out — #22 새 join 모델에서 **동시 플로우 경쟁은 MVP 감수**
> 관련: [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) · [`trip-room-api.md`](trip-room-api.md) D8
> GitHub: **#35**
> 선행: #22 late-join · [`trip-room-api.md`](trip-room-api.md) D8

## 목표

참여자가 **확인 플로우 마지막에야** `trip_member`를 생성하는 모델에서, 정원(`memberCount`) 자리를 **플로우 진입 시점에 예약**해 늦게 완료한 사용자가 억울하게 409로 튕기는 UX를 줄인다.

## 배경

#22 확정(참여자): 정기→개별 플로우 후 **가입 API 한 번**으로 멤버 INSERT.  
정원 검사는 INSERT 시점 → 플로우 중 여러 명이 동시에 진행하면 **먼저 완료한 1명만 성공**, 나머지는 `TRIP_MEMBER_FULL`(409).

MVP는 이를 **감수**. 선점/예약은 wave 4.

## Must Have (wave 4)

- [ ] 플로우 진입(또는 초대 수락 의도) 시 **정원 hold** (TTL·만료)
- [ ] 가입 완료 시 hold → `trip_member` 확정
- [ ] hold 만료·이탈 시 자리 반환
- [ ] 동시성 테스트 (정원 1 남음 + N명 플로우)

## Out of Scope (MVP / #22)

- hold 없이 INSERT 시점 409만 사용
- 방장 생성 시 owner 즉시 멤버 (A안) — hold 대상 아님

## 대안 (미선택)

| 안 | 요약 |
|----|------|
| Soft hold 테이블 | `(trip_id, user_id, expires_at)` |
| Redis 카운터 | 빠른 TTL, 인프라 wave 4 |
| JOINED 조기 INSERT | #22에서 **폐기**한 선점 모델로 회귀 |

## 완료 기준

- [ ] 스펙 Approved
- [ ] hold·만료·409 시나리오 테스트
- [ ] `./gradlew test`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | Draft — #22 late-join MVP 감수 후속 |
