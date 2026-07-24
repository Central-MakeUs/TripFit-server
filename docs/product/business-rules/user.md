# User (사용자·권한) 비즈니스 규칙

> NotebookLM 기획 자료 정리본.

| 규칙 ID | 규칙명 | 조건 | 동작 | 위반 시 (에러/제약) |
| :--- | :--- | :--- | :--- | :--- |
| **BR-USER-001** | 방장(총대) 인증 | 여행방 생성 시 | 소셜 로그인 + 이름 완료. **생성 폼 → `POST /trips`(JOINED) → 일정 플로우 → confirm** | 미인증·이름 미입력 401/403 |
| **BR-USER-002** | 참여자 진입 및 인증 | 초대 링크·코드 | 소셜 로그인 필수. 비회원 없음 | 미로그인 401 |
| **BR-USER-003** | 소셜 계정 연동 | 설정 | 카카오·구글 등 | wave 4 |
| **BR-USER-004** | 회원 탈퇴 | 탈퇴 요청 | 확인 후 탈퇴 — 차단 없이 자동 cascade. 참여 중인 모든 방에서 자동 나가기(MEMBER) 또는 소유한 모든 방 자동 삭제(OWNER) 후 탈퇴 처리. 전 상태(`ONGOING`/`CONFIRMED`/`EXPIRED`) 적용 | [`user-account-withdrawal.md`](../../specs/user-account-withdrawal.md) · [`trip-member-leave.md`](../../specs/trip-member-leave.md) · 정책 근거 `#47` |
| **BR-USER-005** | 알림 허용 | 마이페이지 | `users.notification_enabled` on/off (default true), `PATCH /users/my-page`(partial update)로 설정 | Off 시 BR-NOTI-001~005·009 **전체** 미발송(예외 없음) |
| **BR-USER-006** | 방 입장 가능 조건 | D-JOIN-ENTRY | 정기≥1 OR 개별≥1 OR **`is_all_free`** | 불만족 시 차단 |
| **BR-USER-007** | trip 일정 확인·가입 | **#39** | **방장:** `POST /trips`=`JOINED` → 일정 플로우 → `POST .../schedule/confirm`=`RESPONDED`. **참여자:** 플로우 후 **`POST /trips/join`**=`RESPONDED`. 방 안=`RESPONDED`∧canEnterRoom | 정원 409 · `SCHEDULE_CONFIRM_REQUIRED` · `SCHEDULE_ENTRY_REQUIRED` |
| **BR-USER-008** | 전역 일정 | 일정·`is_all_free` 변경 | **ONGOING** 방 달력에만 동일(live). **CONFIRMED/EXPIRED**는 snapshot 고정·읽기 전용 — [`trip-schedule-snapshot.md`](../../specs/trip-schedule-snapshot.md) (#38 **Approved**) | — |
| **BR-USER-009** | 동일 이름 표시 | 목록 | `홍길동(2)` | — |
| **BR-USER-010** | 재접속 | 이미 `trip_member` | 방 상세 직행 | 미가입 참여자 → 플로우 |
| **BR-USER-011** | 일정↔전부 free | 0행 / 추가 | 0행→`is_all_free=true`. 추가→`false`. 선언 버튼 없음 | — |

### `[미정]`

- BR-USER-002 UI · 정원 hold [#35](https://github.com/Central-MakeUs/TripFit-server/issues/35)
- 탈퇴 계정 재가입(부활) 정책 — [`user-account-withdrawal.md`](../../specs/user-account-withdrawal.md) 리스크·미결정 참고

### 확정 (2026-07-21 · #22)

- Skip+0행 → **confirm/join** 시 `is_all_free=true` · omit≠`is_all_free` · Hidden 단계적 · prefill=FE · `memberFillRate`
- 정기=CRUD · 개별=bulk upsert · 구 `schedule/submit` 삭제

### 확정 (2026-07-21 · #39 amend)

- 방장=`POST /trips` JOINED → 일정 플로우 → `schedule/confirm` RESPONDED
- 멤버=일정 후 join RESPONDED · 방 안 API는 RESPONDED ∧ canEnterRoom
- Skip+0행 → **confirm/join** 시 `is_all_free=true` (create에서는 설정 안 함)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-23 | BR-USER-005 `[미정]`(필수 알림) 해소 — `notification_enabled`(default true), 예외 없이 전체 BR-NOTI 이벤트에 적용 (`docs/specs/notification.md` D2·D10), `PATCH /users/my-page` partial update로 설정 (`user-my-page.md` amend) |
| 2026-07-24 | `src/new_decision.md` 최종 확정 — `TripStatus.CANCELED` enum 자체 삭제 확정(Soft Delete로 통일), "확정 취소"(CONFIRMED→ONGOING, `unconfirm`) 신규 액션 확정(새 Status 불필요) |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임 |
| 2026-07-24 | BR-USER-004 정책 전면 수정 — "ONGOING 방 있으면 차단" 폐기, **차단 없이 자동 cascade**로 전환(기획자 확인, `#47`) |
| 2026-07-23 | BR-USER-004 `[미정]`(진행 중 방) 해소 — 활성 OWNER/MEMBER 방 있으면 탈퇴 차단 (`#47`·`#48`, **2026-07-24 폐기**) |
| 2026-07-21 | **#39** — BR-USER-001/007 방장 JOINED→confirm |
| 2026-07-21 | BR-USER-008 — ONGOING만 live · CONFIRMED/TERMINATED snapshot (#38 **Approved**) |
| 2026-07-21 | Skip+0행 `is_all_free` 방장=create 확정 · 전이 표 보강 |
| 2026-07-21 | 방장 생성 전 플로우 · JOINED 제거 · join 단일 · submit 삭제 |
| 2026-07-21 | late-join · memberFillRate · #35 |
| 2026-07-20 | is_all_free · Skip · submit 폐기 방향 |
