# User (사용자·권한) 비즈니스 규칙

> NotebookLM 기획 자료 정리본.

| 규칙 ID | 규칙명 | 조건 | 동작 | 위반 시 (에러/제약) |
| :--- | :--- | :--- | :--- | :--- |
| **BR-USER-001** | 방장(총대) 인증 | 여행방 생성 시 | 소셜 로그인 + 이름 완료. **일정 플로우 → 생성 폼 → `POST /trips`** | 미인증·이름 미입력 401/403 |
| **BR-USER-002** | 참여자 진입 및 인증 | 초대 링크·코드 | 소셜 로그인 필수. 비회원 없음 | 미로그인 401 |
| **BR-USER-003** | 소셜 계정 연동 | 설정 | 카카오·구글 등 | wave 4 |
| **BR-USER-004** | 회원 탈퇴 | 탈퇴 요청 | 확인 후 탈퇴 | `[미정]` 진행 중 방 |
| **BR-USER-005** | 알림 허용 | 마이페이지 | on/off | `[미정]` 필수 알림 |
| **BR-USER-006** | 방 입장 가능 조건 | D-JOIN-ENTRY | 정기≥1 OR 개별≥1 OR **`is_all_free`** | 불만족 시 차단 |
| **BR-USER-007** | trip 일정 확인·가입 | **#22** | **방장:** 생성 **전** 일정 플로우 → `POST /trips` 시 `RESPONDED`. **참여자:** 플로우 후 **`POST /trips/join`** 만. submit **삭제**. `JOINED` **미사용** | 정원 409 · canEnterRoom |
| **BR-USER-008** | 전역 일정 | 일정·`is_all_free` 변경 | 모든 참여 방에 동일 | — |
| **BR-USER-009** | 동일 이름 표시 | 목록 | `홍길동(2)` | — |
| **BR-USER-010** | 재접속 | 이미 `trip_member` | 방 상세 직행 | 미가입 참여자 → 플로우 |
| **BR-USER-011** | 일정↔전부 free | 0행 / 추가 | 0행→`is_all_free=true`. 추가→`false`. 선언 버튼 없음 | — |

### `[미정]`

- BR-USER-004/002 UI · 정원 hold [#35](https://github.com/Central-MakeUs/TripFit-server/issues/35)

### 확정 (2026-07-21)

- 방장=생성 전 플로우 · `RESPONDED`만 · `POST /trips/join` 단일 · submit 삭제
- Skip+0행 → create/join 시 `is_all_free=true` · omit≠`is_all_free` · Hidden 단계적 · prefill=FE · `memberFillRate`
- 정기=CRUD · 개별=bulk upsert

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | Skip+0행 `is_all_free` 방장=create 확정 · 전이 표 보강 |
| 2026-07-21 | 방장 생성 전 플로우 · JOINED 제거 · join 단일 · submit 삭제 |
| 2026-07-21 | late-join · memberFillRate · #35 |
| 2026-07-20 | is_all_free · Skip · submit 폐기 방향 |
