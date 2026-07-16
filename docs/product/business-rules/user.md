# User (사용자·권한) 비즈니스 규칙

> NotebookLM 기획 자료 정리본.

| 규칙 ID | 규칙명 | 조건 | 동작 | 위반 시 (에러/제약) |
| :--- | :--- | :--- | :--- | :--- |
| **BR-USER-001** | 방장(총대) 인증 | 여행방 생성 시 | 소셜 로그인(Google, Kakao, Apple) 및 필수 프로필(성·이름) 완료 | 미인증·이름 미입력 시 401/403 |
| **BR-USER-002** | 참여자 진입 및 인증 | 초대 링크·참여 코드 진입 시 | 앱 설치자 → 앱, 미설치 → **웹 랜딩**. **소셜 로그인 필수.** 비회원·게스트 없음 | 미로그인 401; 로그인 유도 |
| **BR-USER-003** | 소셜 계정 연동 | 설정에서 계정 관리 | 카카오·구글 등 연동 정보 관리·해제 | wave 4 — [`auth-social-login.md`](../../specs/auth-social-login.md) Deferred |
| **BR-USER-004** | 회원 탈퇴 | 탈퇴 요청 시 | 확인 후 탈퇴; 본인이 생성한 여행방 데이터 삭제 | `[미정]` 진행 중 여행방 차단 여부 |
| **BR-USER-005** | 알림 허용 | 마이페이지 알림 설정 | on/off 저장, off 시 푸시 미발송 (BR-NOTI-*) | `[미정]` 필수 알림 예외 |
| **BR-USER-006** | 정기 일정 등록 게이트 | **`[미定]` #22** | ~~온보딩 skip → regular≥1 후 personal~~ | — |
| **BR-USER-007** | 여행방 참여 완료 | **`[미定]` #22** | ~~submit → RESPONDED~~ | — |
| **BR-USER-008** | 전역 일정 연동 | 개별·정기 일정 변경 시 | **참여 중 모든 여행방**에 동일 데이터 반영 (trip FK 없음) | — |

## 보조 규칙 (Figma·PRD)

| 규칙 ID | 규칙명 | 조건 | 동작 | 위반 시 |
| :--- | :--- | :--- | :--- | :--- |
| **BR-USER-009** | 동일 이름 표시 | 참여자 목록 표시 | 동명이인 시 `홍길동(2)` 형식 | — |
| **BR-USER-010** | 재접속 | 이미 참여한 사용자가 링크 재접속 | 해당 여행방 상세로 즉시 이동 | — |

### `[미정]` (#22)

- **BR-USER-006·007** — [`schedule-participation-onboarding.md`](../../specs/schedule-participation-onboarding.md)
- BR-USER-004: 진행 중 여행방 존재 시 탈퇴 차단
- BR-USER-002: 참여 코드만 입력(링크 없이) UI 상세

### 확정 (2026-07-08)

- BR-USER-002: 소셜 로그인 필수, 비회원 없음 → `trip_member.user_id` NOT NULL
- BR-USER-006: `regular_schedule` ≥1 → `isScheduleRegistered=true`
- BR-USER-008: 정기·개별 User 전역 — trip 삭제와 무관

### 확정 (2026-07-13)

- A안 단일 `schedule` 폐기 → `regular_schedule` + `personal_schedule`
