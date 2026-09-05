# TripFit 용어집

> NotebookLM 기획 자료 정리본.

## 용어

| 용어 | 정의 | 비고 |
| :--- | :--- | :--- |
| **TripFit** | 서비스의 정식 명칭 (전 명칭: When We Meet) | |
| **방장** | 여행 방을 생성한 사용자. 일정 확정 및 방 정보 수정 권한을 가짐 | 총대와 동일 |
| **참여자** | `ACTIVE` 멤버. 링크만으로는 미가입 | 비회원 없음. 방장 create 직후·참여자 join 직후는 `SCHEDULE_PENDING`(입장·공유 전) |
| **SCHEDULE_PENDING** | 방장 `POST /trips` 직후 · 참여자 `POST /trips/join` 직후 — `activate` 전 | 방 입장·초대 공유 불가. **방장·참여자 모두 이 상태를 거친다** (2026-08-18 `#114`) |
| **ACTIVE** | 방장·참여자 **모두** `activate` 후 | 방 입장 가능 · 방장 초대 공유 가능 |
| **여행 방** | 여행 일정을 조율하기 위해 생성된 가상의 협업 공간 | |
| **후보 일정** | 추천 알고리즘이 계산하여 제시한 상위 3개의 일정 | |
| **확정 일정** | 방장이 후보 일정 중 최종적으로 선택한 일정 | |
| **근무 정보** | (레거시 용어) 정기 일정 중 출근·연차 성격의 행 | → **정기 일정** |
| **정기 일정** | 요일/시간 기준으로 반복되는 일정 (출근·수업·회의 등). `regular_schedule` N행 | `erd.md` |
| **개별 일정** | 특정 날짜에만 있는 일정. 시간대별 가능/불가/미정. `personal_schedule` | BR-TRIP-002~004 · 코드·API는 `PersonalSchedule`·`/users/schedule/personal` |
| **개인 일정** | (구 용어) 위 **개별 일정**의 옛 표기 | 2026-08-19 표기 통일 — 옛 문서를 읽을 때만 참고 |
| **연차·휴일 정보** | 여행 가능 여부 계산에 필요한 연차/반차·**사전 신청일**·공휴일 휴무 설정. `users` 4개 컬럼 | `GET`/`PATCH /users/schedule/vacation-policy` · 4개 값 모두 필수 |
| **사전 신청일** | 연차를 며칠 전까지 신청해야 하는지 (`vacation_apply_period` — 상관없음/1주 전/2주 전/한달 전). **최초/갱신 입력 판정 마커** | `ANY`(상관없음)와 `null`(미저장)은 다른 값 |
| **최초 입력** | 사전 일정 정보를 한 번도 입력 완료하지 않은 상태 = **사전 신청일 미저장** | 첫 화면 `정기 일정이 있나요?` · `hasCompletedPreSchedule = false` |
| **갱신 입력** | 사전 일정 정보를 한 번 이상 입력 완료한 상태 = **사전 신청일 저장됨**. 입력 플로우를 완료했으나 일정이 없는 경우도 포함 | 첫 화면 `일정 변경이 있나요?` · `hasCompletedPreSchedule = true` |
| **일정 관리** | 개인의 일정을 등록, 수정, 삭제하는 기능 | 오전/오후/저녁 + 미정(TBD) 상태 |
| **희망 여행 시기** / **희망 기간** | `trip.startRange`~`endRange`. 여행을 떠나고 싶은 **탐색·조율 범위**. **여행방 달력 조회 기간과 동일** (#37 C2/C3) | 추천 후보 윈도우와 혼동 금지 |
| **마이페이지 조회 윈도우** | 본인 `GET /users/schedule/calendar` 허용 구간: **`today` ~ `max(today+2년−1일, 참여 중 ONGOING 여행 endRange 최댓값)`** (#37 C1 · #53 R4) | 여행방 희망 기간과 **별 축**. ONGOING 여행 희망 기간 종료일이 +2년보다 뒤면 그 날짜까지 확장 |
| **A1** | (구) 요청 구간 길이 ≤730일. **#37 Approved:** 구간 ⊆ **`today`~`today+2년−1`** · today 이전 400 | #17 Implemented · #37 amend |
| **여행 일수** | 여행을 몇 박 며칠로 진행할지. DB는 `duration_nights`(n박)+`duration_days`(m일) 둘 다 저장(파생 아님). 유효 범위 `nights+1 ≤ days ≤ min(nights+2, 희망기간일수)`. **0박(당일치기)도 동일 규칙**(days=1 또는 2) | API `durationNights`+`durationDays` |
| **Must** | 기능 구현 자체·버그 수정 | 이슈의 `priority: must` 라벨 — 이슈 `## 완료 조건`(구 `## Must Have`)와 **다름** |
| **Could** | 성능 개선·폴더/패키지 구조 정리·리팩터·최적화 (MoSCoW Could have, 구 명칭 "Nice") | 이슈의 `priority: could` 라벨 |
| **Out** | MVP 범위 밖 (런칭 후 또는 안 함) | `mvp.md` Out — Could(작업 성격)와 별개 축, 혼동 금지 |
| **미정(불확실) 일정** | 참석 가능 여부 미확정 | `personal_schedule` · `TBD` |
| **schedule (폐기)** | 구 A안 단일 테이블 (`row_type`) | → `regular_schedule` + `personal_schedule` |
| **오전/오후/저녁** | 하루를 세 단위로 나눈 일정 입력 최소 단위 | 오전(00:00–13:00), 오후(13:00–18:00), 저녁(18:00–24:00) — 정책서 4-3 |
| **클라이언트 환경 A** | 순수 모바일 앱 (iOS/Android 스토어) | Google = 네이티브 SDK. [`platform.md`](platform.md) |
| **클라이언트 환경 B** | 카카오톡 인앱 브라우저 · 모바일 웹 | Google UI는 시스템 브라우저 탈출. API는 A와 동일 |
| **연차 조건** | (구 용어) → **연차·휴일 정보** | 2026-08-19 표기 통일 |
| **추천 모드** | 방장이 후보 산출 시 선택하는 전략 | 기본 / 모두 참석 / 휴가 아끼기 / 확실하게 가기 (MVP 출시) |

### 헷갈리기 쉬운 점 (프론트·신규 서버)

| 오해 | 실제 |
|------|------|
| SCHEDULE_PENDING = "일정 아직 안 넣은 일반 멤버" | **정확히는** "이 방의 일정 확인을 아직 안 끝낸 멤버". 방장은 `POST /trips` 직후, 참여자는 `POST /trips/join` 직후 이 상태이며, `activate`로 ACTIVE가 된다 |
| create 응답의 inviteCode로 바로 카톡 공유 | **불가.** create에 `inviteCode` **없음**. activate→ACTIVE→상세의 `inviteCode` |
| 홈에 방이 보이면 상세·공유 가능 | SCHEDULE_PENDING면 홈에만 보일 수 있음 → **activate 플로우**. 상세/공유는 ACTIVE 후 |
| 멤버는 SCHEDULE_PENDING을 거치지 않는다 | **거친다.** 2026-08-18 `#114`로 참여자도 `join` 직후 SCHEDULE_PENDING이 되고 `activate`로 ACTIVE가 된다 |

## 약어

| 약어 | 풀네임 | 설명 |
| :--- | :--- | :--- |
| **SSOT** | Single Source of Truth | 단일 진실 공급원 |
| **IA** | Information Architecture | 서비스의 정보 구조도 |
| **PRD** | Product Requirements Document | 제품 요구사항 정의서 |
| **MVP** | Minimum Viable Product | 핵심 가치를 검증하기 위한 최소 기능 제품 |
| **KPI** | Key Performance Indicator | 핵심 성과 지표 |
| **BR** | Business Rule | `business-rules/` 규칙 ID 접두 |
