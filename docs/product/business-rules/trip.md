# Trip (여행방·일정) 비즈니스 규칙

> NotebookLM 기획 자료 정리본. UI 상세는 `design/figma-wireframe-v1.md` 참고.

| 규칙 ID | 규칙명 | 조건 | 동작 | 위반 시 (에러/제약) |
| :--- | :--- | :--- | :--- | :--- |
| **BR-TRIP-001** | 여행방 생성 필수 정보 | 방장이 여행방을 생성할 때 | 여행방 이름(최대 **15자**), 여행 희망 기간(탐색 범위), 참여 인원(**1~10**) 필수. **희망 여행 일정**(n박 m일)은 선택 — 「아직 못정했어요」면 `duration_days` null. 정했을 때 API는 `durationNights`+`durationDays` 수신·`nights == days - 1`·`days ≥ 1`·**`nights ≥ 0`(당일치기 0박 1일 허용)** 검증 후 **m일만** 저장. **여행지**는 `trip.destination` (선택·null=미정) | 필수 값 누락·이름 16자 이상·인원 범위 밖·박/일 불일치 시 400 |
| **BR-TRIP-002** | 일정 응답 단위 | 개인 일정 입력 시 | `personal_schedule` — 날짜당 오전/오후/저녁 **가능·불가** (`TimeSlot`/`SlotStatuses`) | 슬롯 상태 누락 시 400 |
| **BR-TRIP-003** | 일정 상태 정의 | 개인 일정 입력 시 | 슬롯: **가능/불가**. 날짜 단위 **불확실(`uncertain`)** (슬롯별 TBD 아님) | 상태 누락 시 400 |
| **BR-TRIP-004** | 개인 일정 프라이버시 | 타 참여자 조회 시 | 개별 일정 **상태만** 노출 (`note` 컬럼 없음) | 상세 노출 차단 |
| **BR-TRIP-005** | 추천 모드 및 알고리즘 | 방장이 후보 일정을 요청할 때 | **wave 2 — 4모드 전부.** **(1) 기본** — 참석↑·연차↓·미정↓ 균형, **(2) 모두 참석** — 불가 인원 최소화(하드 필터는 BR-TRIP-011), **(3) 휴가 아끼기** — 연차 최소화, **(4) 확실하게 가기** — 미정(TBD) 최소화. 각 모드 TOP 3 + 추천 근거 | 잘못된 모드·데이터 부족 시 400 |
| **BR-TRIP-006** | 정기 일정 설정 | 사용자가 정기 일정을 등록할 때 | `regular_schedule` — 출근·수업·회의 등 **user당 N행**. 연차 일수 **0~10**(default 2), 연차 신청 시점 enum·nullable | 범위 초과 시 저장 거부 |
| **BR-TRIP-007** | 일정 확정 권한 | 최종 일정 결정 시 | **방장만** 후보 선택 또는 직접 날짜 입력으로 확정 | 참여자 확정 API 403 |
| **BR-TRIP-008** | 희망 여행 일수 제약 | 여행방 생성·수정 시 | `duration_days`가 있을 때 `duration_days` ≤ 희망 기간(일수). 미정(null)이면 본 제약 스킵 | 검증 에러 |
| **BR-TRIP-009** | 여행방 수정 권한 | 여행방 정보 수정 시 | **방장만** 이름·일수(또는 미정)·인원·**여행지** 수정. **희망 기간(`start_range`/`end_range`)은 생성 후 수정 불가** | 참여자 403 · 기간 변경 시도 400 |
| **BR-TRIP-010** | 추천 결과 초기화 | 추천에 영향 주는 옵션 변경 시 | **일수(`duration_days`)** 변경 시 **`recommendation` hard DELETE** 후 재추천 (기간은 불변이므로 PATCH 트리거 없음) | stale 추천 노출 금지 |
| **BR-TRIP-011** | 모두 참석 하드 필터 | **모두 참석** 모드 | 방장 설정 인원 미달 후보는 **즉시 제외** | 후보 없음 UI |
| **BR-TRIP-012** | 동점자 처리 | 추천 점수 동일 시 | 1) 연차 소모 적은 순, 2) 여행 기간 긴 순, 3) 주말·공휴일 포함 순 `[제안]` | `[미정]` 세부 가중치 |
| **BR-TRIP-013** | 여행방 삭제 | 삭제 요청 시 | **방장만** 삭제(soft delete). 참여자 접근 상실 | 참여자 403 |

생성 권한(로그인): **BR-USER-001**.

## 기획 메모 (NotebookLM)

### `[미정]`

- BR-TRIP-001: 희망 기간 최대 탐색 범위(예: 6개월)
- BR-TRIP-006: `max_vacation_days` 0~10 (default 2); 정기 일정 N행 상한은 `[미정]`
- BR-TRIP-010: 옵션 변경 시 참여자 알림 — BR-NOTI-003과 연동 (`[미정]` 상세)
- BR-TRIP-012: 동점 처리 수치

### 확정 (2026-07-08)

- BR-TRIP-005: 추천 4모드 wave 2 전부
- BR-TRIP-001: `trip.destination` MVP In
- BR-TRIP-010: 기간·일수·모드 변경 시 `recommendation` **hard DELETE**

### 확정 (2026-07-17)

- BR-TRIP-001: 여행방 이름 최대 **15자**

### 확정 (2026-07-20)

- BR-TRIP-001: 참여 인원 **1~10**

### 확정 (2026-07-21)

- BR-TRIP-001: 희망 일정·여행지 **미정(null) 허용**. API n박+m일 → DB는 `duration_days`만
- BR-TRIP-001: **당일치기(0박 1일) 허용** (`nights=0`, `days=1`) — [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)
- BR-TRIP-009: 희망 기간 **생성 후 수정 불가**

### 확정 (2026-07-13)

- BR-TRIP-006: `regular_schedule` N행 (A안 CONDITION 1행 폐기)
- 일정 SSOT: `regular_schedule` + `personal_schedule` (`erd.md`)
