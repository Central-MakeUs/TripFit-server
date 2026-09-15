# 방 나가기 (멤버 자진 탈퇴)

> 상태: Implemented · **2026-08-19 amend (`#122`) — 나가기도 입장(`ACTIVE`) 후에만 가능**
> MVP: In scope
> 관련 BR: BR-USER-004 (회원 탈퇴 cascade 시 자동 호출 + 사용자 자진 나가기)
> wave: 2 (Nice)
> implements: (없음 — Figma ROOM_02, 마이페이지 탈퇴 플로우 전제)
> deferred: (해당 없음)
> GitHub: 정책 근거 `#47`(hotfix, 확정) · 구현도 `#47` 브랜치(`docs/47-trip-status-policy-alignment`)에서 완료(별도 구현 이슈 없이 진행)
> 선행: [`trip-room-api.md`](trip-room-api.md) · [`trip-member-remove.md`](trip-member-remove.md) · [`trip-last-activity-at.md`](trip-last-activity-at.md)

## 목표

일반 참여자(MEMBER)가 스스로 여행방에서 나갈 수 있게 한다. `trip-member-remove.md`(#20)가 "멤버 자진 탈퇴"를 명시적으로 Out of Scope로 남겨둔 이후 지금까지 이 기능이 없었고, [`user-account-withdrawal.md`](../user/user-account-withdrawal.md)(회원 탈퇴)가 참여 중인 방을 자동으로 정리하는 cascade 단계로 이 로직을 그대로 재사용한다.

## 배경

- `trip-member-remove.md`(#20): 방장이 멤버를 내보내는 기능만 있고, 멤버 자진 탈퇴는 Out of Scope
- 회원 탈퇴([`user-account-withdrawal.md`](../user/user-account-withdrawal.md))를 구현하려면 참여자가 참여 중인 방을 정리할 방법이 있어야 함
- **정책 전면 수정(2026-07-24, `#47` hotfix, 기획자 확인 완료)**: 방 나가기·참여자 내보내기(`#20`)·방 삭제·회원 탈퇴 네 액션의 상태별 허용 조건이 서로 다르게 설계돼 있던 걸 정합성 있게 재정리. 방 나가기는 `#20`(내보내기)과 달리 **방 상태 무관**으로 확정(자진 나가기 vs 강제 내보내기의 차이) — 이전(2026-07-23)의 "`ONGOING`만 허용" 결정은 **폐기**
- **멤버 상태 게이트 추가(2026-08-19, `#122`, 사용자 결정)**: 위 결정의 두 축 중 **「멤버십 상태(`ACTIVE`) 무관」 부분만 번복**한다. 기획 확정 — **방 안의 모든 기능은 입장 후에만 쓸 수 있고 나가기도 여기 포함**된다. 일정 확인 전(`SCHEDULE_PENDING`)에는 스스로 나갈 수 없고, 그 자리는 **방장 내보내기**로만 회수된다. **방(trip) 상태 무관은 그대로 유지**한다 — `ACTIVE` 멤버는 ONGOING·CONFIRMED·EXPIRED 어디서든 나갈 수 있다.
  계기: `#112`가 `activate`에 사전 일정 입력 완료 게이트(403 `PRE_SCHEDULE_REQUIRED`)를 추가하면서 「입장도 못 하고 나가지도 못하는」 구간의 성격이 분명해졌고, 초대 링크로만 진입하는 구조라 실제 이탈 경로는 ①입력 완료 후 입장→나가기 ②방장 내보내기로 수렴한다고 판단

## 요구사항

### Must Have

- [x] `DELETE /api/v1/trips/{tripId}/members/me` — JWT 필수. ~~인터셉터 미사용(ACTIVE 여부 무관)~~ → **2026-08-19 `#122`: `@TripMemberOnly` 부착** — 이 방에 입장(`ACTIVE`)한 멤버만 호출 가능, `SCHEDULE_PENDING`이면 403 `SCHEDULE_ACTIVATION_REQUIRED`
- [x] **회원 탈퇴 cascade는 상태 무관 유지** — `TripService.leaveAllActiveTripsAsMember`는 인터셉터를 타지 않고 서비스를 직접 호출하므로 `SCHEDULE_PENDING` 멤버십도 계속 정리된다(게이트를 서비스에 두지 않는 이유)
- [x] **미입장자 자리 회수 경로 = 방장 내보내기** — `DELETE /trips/{tripId}/members/{userId}`는 대상 멤버 상태를 보지 않아 `SCHEDULE_PENDING`도 내보낼 수 있다(조율 중인 방 한정). 자동 회수(TTL·배치)는 `#114`에서 금지
- [x] 호출자의 해당 방 활성(`deleted_at IS NULL`) `TripMember` row가 없으면 `TRIP_ACCESS_DENIED`
- [x] 호출자 역할이 `OWNER`면 `TRIP_OWNER_CANNOT_LEAVE` — 방장은 나갈 수 없고 "여행방 삭제"를 사용해야 함(방장 위임 기능 없음)
- [x] 호출자 역할이 `MEMBER`면 해당 `TripMember.deleted_at` soft delete
- [x] **모든 방 상태(ONGOING/CONFIRMED/EXPIRED)에서 허용** — 방 상태 게이트 없음(`TRIP_NOT_ONGOING` 미적용, `#20`과 달리 나가기는 방 상태 무관). 멤버 상태 게이트는 위 `#122` 항목 참고. `TripStatus.CANCELED`는 `#48`에서 **enum 자체 삭제 완료**돼 더 이상 고려 대상 아님
- [x] 성공 시 `204 No Content`
- [x] `last_activity_at` touch (`@TripActivity`) — L1 갱신 이벤트 추가
- [x] 재가입: soft delete 후 같은 초대로 재 join 허용 (기존 join 경로 그대로, #20과 동일)
- [x] `recommendation` 테이블/서비스 **미터치** (#20과 동일 정책, #13 보류)
- [x] `./gradlew test` 통과, OpenAPI 반영

### Out of Scope (이번 스펙)

- 방장 위임(ownership transfer) — 명시적으로 없는 기능
- 나간 후 알림 (`#21`)
- 추천 캐시 무효화·재계산 (`#13`, #20과 동일 보류)
- `CANCELED` 상태 처리 — `#48`에서 enum 자체 삭제 완료, 해당 없음
- "확정 취소" 시점의 멤버 제외 처리 — **별도 로직 불필요로 확정.** 나간 멤버는 상태 무관 항상 **즉시** `trip_member` soft delete되고, CONFIRMED 방에서 다른 멤버가 계속 보게 되는 건 이 스펙이 아니라 `trip-schedule-snapshot.md`(#38)가 이미 confirm 시점에 얼려둔(freeze) 스냅샷이 그대로 유지되기 때문. "확정 취소"(`trip-recommendation.md`의 `unconfirm`)가 그 스냅샷을 폐기하는 순간에야 다른 멤버가 라이브 데이터(나간 사람 제외)를 다시 보게 됨 — 자세한 내용은 `trip-recommendation.md` `unconfirm` Must Have 참고

## API

| Method | Path | Auth | 성공 |
|--------|------|------|------|
| DELETE | `/api/v1/trips/{tripId}/members/me` | JWT | `204 No Content` |

### 에러

| 상황 | HTTP | code |
|------|------|------|
| 존재하지 않는 방 | 404 | `TRIP_NOT_FOUND` |
| 호출자가 이 방 멤버가 아님(또는 이미 나감) | 403 | `TRIP_ACCESS_DENIED` |
| 호출자가 `OWNER` | 400 | `TRIP_OWNER_CANNOT_LEAVE` (신규) |

## 데이터 모델

- 신규 컬럼 없음. `trip_member.deleted_at`만 설정(`SoftDeleteEntity`, #20과 동일 패턴)
- `TripErrorCode`에 `TRIP_OWNER_CANNOT_LEAVE` 상수 추가 필요

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-USER-004 | 회원 탈퇴 cascade 시 참여 중인 모든 방에 대해 자동 호출(내부 재사용) + 사용자가 직접 호출하는 자진 나가기 | `TripCommandService.leaveTrip` |

## 검증 시나리오

### 정상

- [x] `ACTIVE` MEMBER가 ONGOING 방에서 나가기 → 204, `trip_member.deleted_at` 설정, `last_activity_at` touch (`TripLeaveGateIntegrationTest#leave_whenActive_returns204`)
- [x] MEMBER가 CONFIRMED 방에서 나가기 → 204 (상태 게이트 없음)
- [x] MEMBER가 EXPIRED 방에서 나가기 → 204 (상태 게이트 없음)
- [ ] 나간 후 같은 초대 코드로 재 join → 신규 `TripMember` row INSERT 허용 (기존 join 경로 재사용 — 별도 신규 테스트 없이 회귀 없음 확인)

### 엣지 · 실패

- [x] OWNER가 호출 → 400 `TRIP_OWNER_CANNOT_LEAVE`
- [x] 멤버가 아닌 사용자가 호출 → 403 `TRIP_ACCESS_DENIED`
- [x] 이미 나간(soft-deleted) 멤버가 재호출 → 403 `TRIP_ACCESS_DENIED` (동일 로직 — soft-deleted는 `findByTripIdAndUserIdAndDeletedAtIsNull` 미조회)
- [x] 존재하지 않는 tripId → 404 `TRIP_NOT_FOUND`
- [x] **`SCHEDULE_PENDING` 멤버가 호출 → 403 `SCHEDULE_ACTIVATION_REQUIRED`, 멤버 row·정원 자리 그대로 유지** (`TripLeaveGateIntegrationTest#leave_whenSchedulePending_returns403AndKeepsSeat`) — 게이트를 떼면 이 테스트가 실패하는 것까지 확인
- [x] **방장이 `SCHEDULE_PENDING` 멤버를 내보내면 자리가 회수돼 새 참여자가 들어올 수 있다** (`TripLeaveGateIntegrationTest#ownerRemove_whenTargetSchedulePending_reclaimsSeat` — 정원 초과 409 → 내보내기 → join 성공)
- [x] **탈퇴 cascade는 `SCHEDULE_PENDING` 멤버십도 정리한다** (`TripServiceTest#leaveAllActiveTripsAsMember_leavesSchedulePendingMembershipToo`)

## 완료 기준

- [x] Must Have 전부
- [x] `#26`(`trip-last-activity-at.md`) L1 표에 "방 나가기" touch 행 추가
- [x] `docs/specs/README.md` wave 2 표·이슈 매핑 갱신
- [x] Wave 2 Backlog(`#30`) Nice 섹션에 추가

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 나가기 허용 **방 상태**(ONGOING/CONFIRMED/EXPIRED) | 확정 (2026-07-24, `#47`) | 방장 위임 없음, 나가기는 방 상태 무관 — 내보내기(`#20`, ONGOING만)와는 의도적 비대칭 |
| 나가기 허용 **멤버 상태** | **확정 — `ACTIVE`만** (2026-08-19, `#122` 사용자 결정) | 구 결정(상태 무관) 번복. 미입장자 자리는 방장 내보내기로만 회수 — 방치되면 응답률이 100%에 도달하지 못하고 "전원 제출" 알림도 발송되지 않는다(감수) |
| `CANCELED` 상태 | **#48 Implemented** — 해당 없음 | enum 자체 삭제 완료 |
| "확정 취소" 시점의 멤버 제외 처리 | 확정 — 별도 메커니즘 불필요 | 즉시 soft delete + 기존 `#38` 스냅샷 freeze/폐기로 충분 (위 Out of Scope 참고) |
| `TERMINATED` → `EXPIRED` 리네임 | **#48 Implemented** | 코드·`#27`/`#37`/`#38` 스펙과 함께 반영 완료 — 본 스펙도 `EXPIRED` 표기로 동기화 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-19 | **`#122` — 나가기에 멤버 상태 게이트 추가.** `DELETE /members/me`에 `@TripMemberOnly` 부착 → `SCHEDULE_PENDING`은 403 `SCHEDULE_ACTIVATION_REQUIRED`. **2026-07-24 `#47` 결정 중 「멤버십 상태 무관」 부분을 사용자 결정으로 번복**(방 상태 무관은 유지). 근거: 방 안 기능은 입장 후에만 — 나가기도 포함. 미입장자 자리 회수는 방장 내보내기로 단일화, 탈퇴 cascade는 서비스 직접 호출이라 상태 무관 유지 |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임. 본 스펙 코드 참조 동기화 |
| 2026-07-24 | 구현 완료(`#47` 브랜치) — `TripCommandService.leaveTrip`·`TripMemberController DELETE /members/me`·`TRIP_OWNER_CANNOT_LEAVE`, `./gradlew test` 통과 |
| 2026-07-24 | `src/new_decision.md` 최종 확정 반영 — `CANCELED` 관련 항목을 "결과 대기"에서 "해당 없음(enum 삭제 확정)"으로 정리, "확정 취소" 지연 삭제 로직은 별도 메커니즘 불필요로 결론(기존 `#38` 스냅샷으로 충분) |
| 2026-07-24 | 정책 전면 수정(`#47` hotfix, 기획자 확인) — 나가기 허용 상태를 `ONGOING`만 → **상태 무관**(ONGOING/CONFIRMED/TERMINATED)으로 변경. `TRIP_NOT_ONGOING` 게이트 제거 |
| 2026-07-23 | `ONGOING`만 허용으로 수정 — 회원 탈퇴 차단 조건도 `ONGOING`으로 좁혀짐에 따라 `#20`과 대칭 유지 (**2026-07-24 폐기**) |
| 2026-07-23 | 초안 — 회원 탈퇴 선행 조건으로 작성 |
