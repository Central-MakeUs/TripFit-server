# 회원 탈퇴

> 상태: Draft
> MVP: In scope
> 관련 BR: BR-USER-004
> wave: 2 (Nice)
> implements: BR-USER-004 `[미정]` 해소 — "진행 중 방" 처리 정책 확정
> deferred: (해당 없음)
> GitHub: 정책 근거 `#47`(hotfix, 확정) · 구현 이슈 TBD
> 선행: [`trip-member-leave.md`](trip-member-leave.md) · [`user-my-page.md`](user-my-page.md) · `trip-room-api.md`(여행방 삭제)

## 목표

사용자가 본인 계정을 탈퇴할 수 있게 한다. 참여 중인 방은 **차단 없이 자동으로 정리(cascade)** 한 뒤 탈퇴를 진행한다. 개인 전용 데이터(일정·구글 캘린더 연동·리프레시 토큰)는 즉시 제거하고, 다른 사용자의 여행방 이력을 보존하기 위해 `User` row 자체는 soft delete + 개인정보 스크럽 방식으로 처리한다.

## 배경

- Figma: [`figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) — 마이페이지(설정·탈퇴·캘린더 연동)
- `docs/product/business-rules/user.md` BR-USER-004: "확인 후 탈퇴 / `[미정]` 진행 중 방" — 본 스펙으로 확정
- `user-my-page.md`: 탈퇴 API를 명시적으로 Out of Scope로 남겨둠 — 본 스펙이 후속

### 설계 결정 배경

**1차 결정(2026-07-23, 폐기됨)**: `ONGOING`인 방이 있으면 탈퇴 자체를 차단하고, 사용자가 먼저 방을 삭제·나가기를 선행하도록 요구하는 정책이었음.

**정책 전면 수정(2026-07-24, `#47` hotfix, 기획자 확인 완료)**: 방 나가기·참여자 내보내기(`#20`)·방 삭제·회원 탈퇴 네 액션의 상태별 허용 조건을 정합성 있게 재정리하면서, 탈퇴 정책도 **차단 → 자동 cascade**로 뒤집힘.

1. **진행 중 방 처리(확정)**: 탈퇴를 차단하지 않는다.
   - **참여자(MEMBER)**: 참여 중인 모든 방(상태 무관, `ONGOING` 포함)에서 자동으로 나가기 처리([`trip-member-leave.md`](trip-member-leave.md) 로직 내부 재사용) 후 탈퇴 진행.
   - **방장(OWNER)**: 소유한 모든 방(상태 무관, `ONGOING` 포함)을 자동으로 삭제(`deleteTrip()` 로직 재사용, soft delete) 후 탈퇴 진행. 기획자 근거: "방장이 탈퇴를 누르는 건 여행이 무산됐거나 더 이상 방을 유지할 필요가 없는 상황으로 볼 수 있다 — 탈퇴 전에 방 삭제를 유도하면 번거로운 절차만 추가된다."
   - `CANCELED` 상태는 `src/new_decision.md`에서 **enum 자체가 삭제되기로 확정** — 더 이상 별도로 고려할 상태가 아님(enum 삭제 실행은 별도 이슈 TBD).
2. **데이터 처리(확정, 변경 없음)**: 최초 "Hard delete"로 검토했으나, `Trip.owner_id`/`TripMember.user_id`가 `nullable=false` FK이고 `deleteTrip()`이 soft delete만 하므로(row 존속), User row를 진짜 hard delete하면 그 사람이 방장이었던 Trip(다른 참여자 포함)까지 연쇄 삭제해야 하는 충돌이 발견됨. 사용자 결정: **User도 다른 엔티티와 동일하게 Soft Delete 사용** — Trip·TripMember는 FK 그대로 두어 다른 참여자의 이력을 보존하고, 개인 전용 테이블(일정·구글 캘린더·리프레시 토큰)만 실제로 hard delete.
3. **방장 소유 방 자동 삭제의 부수 효과(리스크로 인지, 수용 확정)**: `deleteTrip()`은 방 자체와 그 방의 모든 `TripMember`를 soft delete하므로, 방장이 탈퇴하면 그 방은 **방장뿐 아니라 다른 멤버 전원에게도** 더 이상 조회되지 않는다. "방장이 탈퇴하면 여행이 무산된 것"이라는 전제를 받아들인 결과이며, `src/new_decision.md`가 `CANCELED`(별도 "취소됨" 표시로 이력을 남기는 안) 자체를 없애기로 확정했으므로 이 부수효과도 그대로 수용하는 것으로 확정됨.

## 요구사항

### Must Have

- [ ] `DELETE /api/v1/users/me` — JWT 필수
- [ ] 차단 없이 항상 진행. 호출자가 활성(`deleted_at IS NULL`) `TripMember` row로 역할이 `MEMBER`인 것이 있으면(상태 무관) 전부 [`trip-member-leave.md`](trip-member-leave.md) 로직으로 자동 나가기 처리
- [ ] 호출자가 활성 `TripMember` row로 역할이 `OWNER`인 것이 있으면(상태 무관) 소유한 해당 Trip을 전부 `deleteTrip()` 로직으로 자동 삭제 처리
- [ ] 위 cascade 완료 후 탈퇴 진행:
  - [ ] 개인 전용 데이터 **hard delete**: `PersonalSchedule`, `RegularSchedule`, `GoogleCalendarCredential`, `GoogleCalendarBusyDay`, `RefreshToken` (전부 `userId` 기준)
  - [ ] `User` row **soft delete**(`deleted_at` set) + 개인정보 스크럽: `email`·`firstName`·`lastName`·`nickname`·`profileImageUrl` → `null`, `isGoogleCalendarConnected` → `false`
  - [ ] `socialId`·`provider`·`id`는 그대로 유지 — FK 무결성(다른 사용자의 Trip/TripMember 참조) 및 재로그인 차단 판별에 필요
- [ ] `AuthService` 로그인 흐름: `findByProviderAndSocialId`로 찾은 `User`가 이미 soft-deleted면 `AUTH_WITHDRAWN_ACCOUNT`(401, 신규 `AuthErrorCode`)로 차단 — soft-deleted 계정으로 조용히 로그인/JWT 발급되는 것을 방지
- [ ] 성공 시 `204 No Content`
- [ ] `./gradlew test` 통과, OpenAPI 반영

### Out of Scope (이번 스펙)

- 탈퇴 계정 재가입(부활) 정책 — `[미정]`, 필요 시 별도 스펙에서 결정
- 액세스 토큰(JWT) 즉시 무효화 — RTR/블랙리스트 인프라 없음(Wave 4 `#4`), 자연 만료까지는 유효할 수 있음. 리프레시 토큰은 hard delete로 즉시 무효화됨
- 소셜 제공자 측 연결 해제(카카오·구글 unlink API 호출) — Wave 4 `#6`(소셜 계정 연결·해제)과 별개, 본 스펙은 TripFit 내부 계정 데이터만 처리
- 탈퇴 확인 모달·UX — FE 책임 (다만 방장의 경우 소유한 모든 방이 삭제된다는 경고 문구가 필요할 수 있음 — FE 확인 필요)

## API

### `DELETE /api/v1/users/me`

| 항목 | 값 |
|------|-----|
| Auth | Bearer JWT **필수** |
| 용도 | 회원 탈퇴 |

성공:

```json
{
  "data": null
}
```

### 에러

| 상황 | HTTP | code |
|------|------|------|
| soft-deleted 계정으로 재로그인 시도 | 401 | `AUTH_WITHDRAWN_ACCOUNT` (신규, 로그인 API에서 발생) |

> 이전 초안에 있던 `USER_HAS_OWNED_TRIPS`/`USER_HAS_JOINED_TRIPS`(409 차단 에러)는 **폐기** — 차단 대신 자동 cascade로 정책이 바뀌어 더 이상 발생하지 않음.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` — 스키마 컬럼 변경 없음(soft delete 패턴 재사용)
- `AuthErrorCode`에 `AUTH_WITHDRAWN_ACCOUNT` 추가
- hard delete 대상 테이블: `personal_schedule`, `regular_schedule`, `google_calendar_credential`, `google_calendar_busy_day`, `refresh_token` (모두 `user_id` 단독 소유, 타 사용자 참조 없음)
- soft delete + 스크럽 대상: `users` (row 유지, PII 컬럼만 null)
- cascade 대상: 호출자가 MEMBER인 `trip_member` row(soft delete, [`trip-member-leave.md`](trip-member-leave.md) 재사용) · 호출자가 OWNER인 `trip` row(soft delete, `deleteTrip()` 재사용 — 해당 방의 다른 멤버 `trip_member` row도 함께 soft delete됨)

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-USER-004 | 확인 후 탈퇴, 차단 없이 자동 cascade(참여 방 자동 나가기 · 소유 방 자동 삭제) | `UserWithdrawalService`(신규) |

## 검증 시나리오

### 정상

- [ ] 활성 방 멤버십이 전혀 없는 사용자 → 탈퇴 성공(204), 개인 데이터 hard delete, `users.deleted_at` set, PII null
- [ ] `ONGOING` 방에 MEMBER로 참여 중인 사용자 → 탈퇴 시 해당 방 자동 나가기 처리 후 탈퇴 성공
- [ ] `ONGOING` 방에 OWNER인 사용자 → 탈퇴 시 해당 방 자동 삭제(soft delete) 후 탈퇴 성공. 그 방의 다른 멤버도 더 이상 해당 방을 조회할 수 없음
- [ ] `CONFIRMED`/`TERMINATED` 방에 OWNER·MEMBER로 남아 있어도 동일하게 cascade 처리 후 탈퇴 성공
- [ ] 탈퇴 후 같은 소셜 계정으로 재로그인 시도 → 401 `AUTH_WITHDRAWN_ACCOUNT`
- [ ] 탈퇴한 사용자가 과거 멤버였던(soft-deleted) 다른 방의 `TripMember` 이력은 그대로 남음(FK 위반 없음)

### 엣지 · 실패

- [ ] 방장으로 있는 방이 여러 개(상태 혼합) → 전부 자동 삭제 후 탈퇴 성공
- [ ] 멤버로 있는 방과 방장인 방이 동시에 있음 → 멤버인 방은 나가기, 방장인 방은 삭제, 둘 다 처리 후 탈퇴 성공

### 수동 / 통합 (해당 시)

- [ ] 탈퇴 → 로그인 재시도 → 401 흐름 수동 확인
- [ ] 방장 탈퇴 후 그 방의 다른 멤버 계정으로 로그인해 방 목록 조회 → 해당 방이 보이지 않음을 확인

## 완료 기준

- [ ] Must Have 전부
- [ ] `docs/product/business-rules/user.md` BR-USER-004 `[미정]` 해소 반영
- [ ] `user-my-page.md` Out of Scope에 본 스펙 deferred 링크 추가
- [ ] `docs/specs/README.md` wave 2 표·이슈 매핑 갱신
- [ ] Wave 2 Backlog(`#30`) Nice 섹션에 추가

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 탈퇴 계정 재가입(부활) 정책 | `[미정]` | 현재는 재로그인 자체를 막음(`AUTH_WITHDRAWN_ACCOUNT`). "재가입 허용" 요구 생기면 별도 스펙 필요 |
| 액세스 토큰 즉시 무효화 | 확정(Out of Scope) | Wave 4 `#4` RTR/Redis 인프라 선행 필요 |
| 방장 탈퇴 시 소유 방이 다른 멤버에게도 통째로 안 보이게 됨 | 확정(수용된 결과) | `deleteTrip()` soft delete가 방 전체를 대상으로 함(방장만이 아님). 별도 "취소됨" 표시로 이력을 남기는 기능은 두지 않기로 확정(`CANCELED` 삭제 결정과 일관) |
| `CANCELED` 상태 방 처리 | 확정 — 해당 없음 | enum 자체 삭제(`src/new_decision.md`). 실행은 별도 이슈(TBD) |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | `src/new_decision.md` 최종 확정 반영 — `CANCELED` 관련 항목을 "결과 대기"에서 "해당 없음(enum 삭제 확정)"으로 정리 |
| 2026-07-24 | 정책 전면 수정(`#47` hotfix, 기획자 확인) — "ONGOING 있으면 차단" 폐기, **차단 없이 자동 cascade**로 전환(참여자: 전 방 자동 나가기, 방장: 전 방 자동 삭제). `USER_HAS_OWNED_TRIPS`/`USER_HAS_JOINED_TRIPS` 에러 폐기 |
| 2026-07-23 | 탈퇴 차단 조건을 `ONGOING`으로 좁힘 — `#20`·`#47`과 게이트 대칭 (**2026-07-24 폐기**) |
| 2026-07-23 | 초안 — BR-USER-004 `[미정]` 해소, User soft delete 결정 반영 |
