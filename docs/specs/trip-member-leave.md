# 방 나가기 (멤버 자진 탈퇴)

> 상태: Draft
> MVP: In scope
> 관련 BR: BR-USER-004 (회원 탈퇴 cascade 시 자동 호출 + 사용자 자진 나가기)
> wave: 2 (Nice)
> implements: (없음 — Figma ROOM_02, 마이페이지 탈퇴 플로우 전제)
> deferred: (해당 없음)
> GitHub: 정책 근거 `#47`(hotfix, 확정) · 구현 이슈 TBD
> 선행: [`trip-room-api.md`](trip-room-api.md) · [`trip-member-remove.md`](trip-member-remove.md) · [`trip-last-activity-at.md`](trip-last-activity-at.md)

## 목표

일반 참여자(MEMBER)가 스스로 여행방에서 나갈 수 있게 한다. `trip-member-remove.md`(#20)가 "멤버 자진 탈퇴"를 명시적으로 Out of Scope로 남겨둔 이후 지금까지 이 기능이 없었고, [`user-account-withdrawal.md`](user-account-withdrawal.md)(회원 탈퇴)가 참여 중인 방을 자동으로 정리하는 cascade 단계로 이 로직을 그대로 재사용한다.

## 배경

- `trip-member-remove.md`(#20): 방장이 멤버를 내보내는 기능만 있고, 멤버 자진 탈퇴는 Out of Scope
- 회원 탈퇴([`user-account-withdrawal.md`](user-account-withdrawal.md))를 구현하려면 참여자가 참여 중인 방을 정리할 방법이 있어야 함
- **정책 전면 수정(2026-07-24, `#47` hotfix, 기획자 확인 완료)**: 방 나가기·참여자 내보내기(`#20`)·방 삭제·회원 탈퇴 네 액션의 상태별 허용 조건이 서로 다르게 설계돼 있던 걸 정합성 있게 재정리. 방 나가기는 `#20`(내보내기)과 달리 **상태 무관**으로 확정(자진 나가기 vs 강제 내보내기의 차이) — 이전(2026-07-23)의 "`ONGOING`만 허용" 결정은 **폐기**

## 요구사항

### Must Have

- [ ] `DELETE /api/v1/trips/{tripId}/members/me` — JWT 필수. `@TripMemberOnly`/`@TripOwnerOnly` 인터셉터 미사용(방 입장 조건 RESPONDED·canEnterRoom과 무관하게 나갈 수 있어야 하므로 서비스 레벨에서 직접 멤버십 검증)
- [ ] 호출자의 해당 방 활성(`deleted_at IS NULL`) `TripMember` row가 없으면 `TRIP_ACCESS_DENIED`
- [ ] 호출자 역할이 `OWNER`면 `TRIP_OWNER_CANNOT_LEAVE` — 방장은 나갈 수 없고 "여행방 삭제"를 사용해야 함(방장 위임 기능 없음)
- [ ] 호출자 역할이 `MEMBER`면 해당 `TripMember.deleted_at` soft delete
- [ ] **모든 상태(ONGOING/CONFIRMED/TERMINATED)에서 허용** — 상태 게이트 없음(`TRIP_NOT_ONGOING` 미적용, `#20`과 달리 나가기는 상태 무관). `TripStatus.CANCELED`는 `src/new_decision.md`에서 **삭제 확정**돼 더 이상 고려 대상 아님(enum 자체 삭제는 별도 이슈에서 진행)
- [ ] 성공 시 `204 No Content`
- [ ] `last_activity_at` touch (`@TripActivity`) — L1 갱신 이벤트 추가
- [ ] 재가입: soft delete 후 같은 초대로 재 join 허용 (기존 join 경로 그대로, #20과 동일)
- [ ] `recommendation` 테이블/서비스 **미터치** (#20과 동일 정책, #13 보류)
- [ ] `./gradlew test` 통과, OpenAPI 반영

### Out of Scope (이번 스펙)

- 방장 위임(ownership transfer) — 명시적으로 없는 기능
- 나간 후 알림 (`#21`)
- 추천 캐시 무효화·재계산 (`#13`, #20과 동일 보류)
- `CANCELED` 상태 처리 — enum 자체가 삭제되므로 해당 없음
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

- [ ] MEMBER가 ONGOING 방에서 나가기 → 204, `trip_member.deleted_at` 설정, `last_activity_at` touch
- [ ] MEMBER가 CONFIRMED 방에서 나가기 → 204 (상태 게이트 없음)
- [ ] MEMBER가 TERMINATED 방에서 나가기 → 204 (상태 게이트 없음)
- [ ] 나간 후 같은 초대 코드로 재 join → 신규 `TripMember` row INSERT 허용

### 엣지 · 실패

- [ ] OWNER가 호출 → 400 `TRIP_OWNER_CANNOT_LEAVE`
- [ ] 멤버가 아닌 사용자가 호출 → 403 `TRIP_ACCESS_DENIED`
- [ ] 이미 나간(soft-deleted) 멤버가 재호출 → 403 `TRIP_ACCESS_DENIED`
- [ ] 존재하지 않는 tripId → 404 `TRIP_NOT_FOUND`

## 완료 기준

- [ ] Must Have 전부
- [ ] `#26`(`trip-last-activity-at.md`) L1 표에 "방 나가기" touch 행 추가
- [ ] `docs/specs/README.md` wave 2 표·이슈 매핑 갱신
- [ ] Wave 2 Backlog(`#30`) Nice 섹션에 추가

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 나가기 허용 상태(ONGOING/CONFIRMED/TERMINATED) | 확정 (2026-07-24, `#47`) | 방장 위임 없음, 나가기는 상태 무관 — 내보내기(`#20`, ONGOING만)와는 의도적 비대칭 |
| `CANCELED` 상태 | 확정 — 해당 없음 | enum 자체 삭제(`src/new_decision.md`). 실행은 별도 이슈(TBD) |
| "확정 취소" 시점의 멤버 제외 처리 | 확정 — 별도 메커니즘 불필요 | 즉시 soft delete + 기존 `#38` 스냅샷 freeze/폐기로 충분 (위 Out of Scope 참고) |
| `TERMINATED` → `EXPIRED` 리네임 | 확정(방향), 미실행 | 코드·`#27`/`#37`/`#38` 스펙과 동시에 별도 이슈(TBD)에서 반영 예정 — 본 스펙은 아직 `TERMINATED` 표기 유지 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | `src/new_decision.md` 최종 확정 반영 — `CANCELED` 관련 항목을 "결과 대기"에서 "해당 없음(enum 삭제 확정)"으로 정리, "확정 취소" 지연 삭제 로직은 별도 메커니즘 불필요로 결론(기존 `#38` 스냅샷으로 충분) |
| 2026-07-24 | 정책 전면 수정(`#47` hotfix, 기획자 확인) — 나가기 허용 상태를 `ONGOING`만 → **상태 무관**(ONGOING/CONFIRMED/TERMINATED)으로 변경. `TRIP_NOT_ONGOING` 게이트 제거 |
| 2026-07-23 | `ONGOING`만 허용으로 수정 — 회원 탈퇴 차단 조건도 `ONGOING`으로 좁혀짐에 따라 `#20`과 대칭 유지 (**2026-07-24 폐기**) |
| 2026-07-23 | 초안 — 회원 탈퇴 선행 조건으로 작성 |
