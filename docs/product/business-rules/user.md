# User (사용자·권한) 비즈니스 규칙

> NotebookLM 기획 자료 정리본.

| 규칙 ID | 규칙명 | 조건 | 동작 | 위반 시 (에러/제약) |
| :--- | :--- | :--- | :--- | :--- |
| **BR-USER-001** | 방장(총대) 인증 | 여행방 생성 시 | 소셜 로그인 + 이름 완료. **생성 폼 → `POST /trips`(SCHEDULE_PENDING) → 일정 플로우 → activate** | 미인증·이름 미입력 401/403 |
| **BR-USER-002** | 참여자 진입 및 인증 | 초대 링크·코드 | 소셜 로그인 필수. 비회원 없음 | 미로그인 401 |
| **BR-USER-003** | 소셜 계정 연동 | 설정 | 카카오·구글 등 | wave 4 |
| **BR-USER-004** | 회원 탈퇴 | 탈퇴 요청 | 확인 후 탈퇴 — 차단 없이 자동 cascade. 참여 중인 모든 방에서 자동 나가기(MEMBER) 또는 소유한 모든 방 자동 삭제(OWNER) 후 탈퇴 처리. 전 상태(`ONGOING`/`CONFIRMED`/`EXPIRED`) 적용 | [`user-account-withdrawal.md`](../../specs/user/user-account-withdrawal.md) · [`trip-member-leave.md`](../../specs/trip/trip-member-leave.md) · 정책 근거 `#47` |
| **BR-USER-005** | 알림 허용 | 마이페이지 | `users.notification_enabled` on/off (default true), `PATCH /users/profile`(partial update)로 설정 | Off 시 BR-NOTI-001~005·009 **전체** 미발송(예외 없음) |
| **BR-USER-006** | 방 입장 가능 조건 | D-JOIN-ENTRY | **그 방의 일정 확인 완료**(`trip_member.status = ACTIVE`) — 사용자 전역 조건 없음 | 미완료 시 `SCHEDULE_ACTIVATION_REQUIRED` |
| **BR-USER-007** | trip 일정 확인·가입 | **#39 · #114** | **방장·참여자 동일:** 방 진입(방장 `POST /trips` · 참여자 `POST /trips/join`)=`SCHEDULE_PENDING` → 일정 플로우 → `POST .../activate`=`ACTIVE`. 방 안=`ACTIVE` | 정원 409 · `SCHEDULE_ACTIVATION_REQUIRED` |
| **BR-USER-008** | 전역 일정 | 일정 변경 | **ONGOING** 방 달력에만 동일(live). **CONFIRMED/EXPIRED**는 snapshot 고정·읽기 전용 — [`trip-schedule-snapshot.md`](../../specs/trip/trip-schedule-snapshot.md) (#38 **Approved**) | — |
| **BR-USER-009** | 동일 이름 표시 | 목록 | `홍길동(2)` | — |
| **BR-USER-010** | 재접속 | 이미 `trip_member` | 방 상세 직행 | 미가입 참여자 → 플로우 |

### `[미정]`

- BR-USER-002 UI

### 확정 (2026-07-21 · #22)

- ~~Skip+0행 → **activate/join** 시 `is_all_free=true`~~ (**2026-08-18 폐기** — 전역 게이트 삭제, `#113`) · omit≠전부 가능 · Hidden 단계적 · prefill=FE · `memberFillRate`
- 정기=CRUD · 개별=bulk upsert · 구 `schedule/submit` 삭제

### 확정 (2026-07-21 · #39 amend)

- 방장=`POST /trips` SCHEDULE_PENDING → 일정 플로우 → `activate` ACTIVE
- 멤버=join으로 `SCHEDULE_PENDING` 생성 → 일정 확인 → activate ACTIVE (**2026-08-18 `#114`** — 이전에는 일정 확인 후 join 한 번으로 바로 ACTIVE) · 방 안 API는 ACTIVE (전역 `canEnterRoom` 조건은 **2026-08-18 삭제**, `#113`)
- ~~Skip+0행 → **activate/join** 시 `is_all_free=true`~~ (**2026-08-18 폐기** — `#113`)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-18 | **BR-USER-006·007 개정 · BR-USER-011 삭제** (`#113`) — 방 입장 판정을 사용자 전역 조건(`정기≥1 OR 개별≥1 OR is_all_free`)에서 **방별 `trip_member.status = ACTIVE` 하나**로 단일화. `users.is_all_free` 컬럼·`canEnterRoom`·`SCHEDULE_ENTRY_REQUIRED` 삭제. **동작 변화 없음** — 전역 게이트는 `ACTIVE` 멤버에게 항상 참이라 이미 아무것도 막지 못했고(`activate`/`join`이 `markAllFreeIfNoSchedules`로 조건을 무조건 충족시킴), 일정 0건 사용자를 "전부 가능"으로 보는 계산은 그대로다. 프론트가 `hasPreSchedule \|\| isAllFree`를 재구현하다 QA 이슈 1·2를 낸 표면을 없애는 것이 목적 |
| 2026-07-28 | BR-USER-001/007 표기 갱신 — `POST .../schedule/confirm` → `POST .../activate`, `SCHEDULE_CONFIRM_REQUIRED` → `SCHEDULE_ACTIVATION_REQUIRED` (rename 상세: `trip-room-api.md` 변경 이력) |
| 2026-07-28 | BR-USER-005 표기 갱신 — API 경로 리네이밍(`PATCH /users/my-page` → `PATCH /users/profile`) 반영. 상세: `user-my-page.md` 변경 이력 |
| 2026-07-27 | BR-USER-004 관련 `[미정]`(탈퇴 계정 재가입 정책) 해소 — **무조건 재가입 가능**으로 확정(사용자 결정). [`user-account-withdrawal.md`](../../specs/user/user-account-withdrawal.md) amend |
| 2026-07-23 | BR-USER-005 `[미정]`(필수 알림) 해소 — `notification_enabled`(default true), 예외 없이 전체 BR-NOTI 이벤트에 적용 (`docs/specs/notification/notification.md` D2·D10), `PATCH /users/my-page` partial update로 설정 (`user-my-page.md` amend) |
| 2026-07-24 | `src/new_decision.md` 최종 확정 — `TripStatus.CANCELED` enum 자체 삭제 확정(Soft Delete로 통일), "확정 취소"(CONFIRMED→ONGOING, `unconfirm`) 신규 액션 확정(새 Status 불필요) |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임 |
| 2026-07-24 | BR-USER-004 정책 전면 수정 — "ONGOING 방 있으면 차단" 폐기, **차단 없이 자동 cascade**로 전환(기획자 확인, `#47`) |
| 2026-07-23 | BR-USER-004 `[미정]`(진행 중 방) 해소 — 활성 OWNER/MEMBER 방 있으면 탈퇴 차단 (`#47`·`#48`, **2026-07-24 폐기**) |
| 2026-07-21 | **#39** — BR-USER-001/007 방장 SCHEDULE_PENDING→confirm |
| 2026-07-21 | BR-USER-008 — ONGOING만 live · CONFIRMED/TERMINATED snapshot (#38 **Approved**) |
| 2026-07-21 | Skip+0행 `is_all_free` 방장=create 확정 · 전이 표 보강 |
| 2026-07-21 | 방장 생성 전 플로우 · SCHEDULE_PENDING 제거 · join 단일 · submit 삭제 |
| 2026-07-21 | late-join · memberFillRate · #35 |
| 2026-07-20 | is_all_free · Skip · submit 폐기 방향 |
