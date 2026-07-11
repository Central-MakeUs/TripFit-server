# TripFit 용어집

> NotebookLM 기획 자료 정리본.

## 용어

| 용어 | 정의 | 비고 |
| :--- | :--- | :--- |
| **TripFit** | 서비스의 정식 명칭 (전 명칭: When We Meet) | |
| **방장** | 여행 방을 생성한 사용자. 일정 확정 및 방 정보 수정 권한을 가짐 | 총대와 동일 |
| **참여자** | `RESPONDED` 멤버. 링크만으로는 미가입 | 비회원 없음. 방장 create 직후는 `JOINED`(입장 전) |
| **JOINED** | 멤버 row 있음 · 이 방 일정 확인 미완료 | 방 입장 불가 · `schedule/confirm` 필요 (#39) |
| **여행 방** | 여행 일정을 조율하기 위해 생성된 가상의 협업 공간 | |
| **후보 일정** | 추천 알고리즘이 계산하여 제시한 상위 3개의 일정 | |
| **확정 일정** | 방장이 후보 일정 중 최종적으로 선택한 일정 | |
| **근무 정보** | (레거시 용어) 정기 일정 중 출근·연차 성격의 행 | → **정기 일정** |
| **정기 일정** | 반복되는 개인 일정 (출근·수업·회의 등). `regular_schedule` N행 | `erd.md` |
| **개인 일정 (PersonalSchedule)** | 특정 날짜·시간대 가능/불가/미정. `personal_schedule` | BR-TRIP-002~004 |
| **전부 free** | `user.is_all_free=true`. 일정 row 0 + 선언됨. 가입 default `false`(미입력) | login/me `isAllFree`. 신규 trip 플로우 생략 근거 **아님** |
| **일정 관리** | 개인의 일정을 등록, 수정, 삭제하는 기능 | 오전/오후/저녁 + 미정(TBD) 상태 |
| **희망 여행 시기** / **희망 기간** | `trip.startRange`~`endRange`. 여행을 떠나고 싶은 **탐색·조율 범위** | 추천 후보 윈도우와 혼동 금지 |
| **조회 윈도우** | 여행방 일정(`members/schedule-calendar` 등)을 **조회할 수 있는** 서버 구간 (#37, 시작 앵커~+2년) | 희망 기간과 **별 축**. 앵커(X1)는 Approved 전 `[미정]` |
| **A1 (730일)** | 본인 `GET /users/schedule/calendar`의 **요청 `startDate`~`endDate` 길이** 상한 (#17) | 조회 윈도우·희망 기간과 **의미 다름** |
| **여행 일수** | 여행을 몇 박 며칠로 진행할지 설정하는 정보 | `duration_days` |
| **Wave Must** | 해당 Wave DoD에 필수인 이슈 | Backlog Must 섹션 SSOT — 이슈 `## Must Have`와 **다름** |
| **Nice** | wave 소속이지만 DoD 불필요 | Backlog Nice · 비고 `분류: Wave N Nice` |
| **Out** | 이 Wave에서 안 함 | Backlog Out — Nice와 혼용 금지 |
| **미정(불확실) 일정** | 참석 가능 여부 미확정 | `personal_schedule` · `TBD` |
| **schedule (폐기)** | 구 A안 단일 테이블 (`row_type`) | → `regular_schedule` + `personal_schedule` |
| **오전/오후/저녁** | 하루를 세 단위로 나눈 일정 입력 최소 단위 | 오전(00:00–13:00), 오후(13:00–18:00), 저녁(18:00–24:00) — 정책서 4-3 |
| **연차 조건** | 1회 여행 시 사용 가능한 최대 연차 및 신청 기간 조건 | |
| **추천 모드** | 방장이 후보 산출 시 선택하는 전략 | 기본 / 모두 참석 / 휴가 아끼기 / 확실하게 가기 (wave 2) |

## 약어

| 약어 | 풀네임 | 설명 |
| :--- | :--- | :--- |
| **SSOT** | Single Source of Truth | 단일 진실 공급원 |
| **IA** | Information Architecture | 서비스의 정보 구조도 |
| **PRD** | Product Requirements Document | 제품 요구사항 정의서 |
| **MVP** | Minimum Viable Product | 핵심 가치를 검증하기 위한 최소 기능 제품 |
| **KPI** | Key Performance Indicator | 핵심 성과 지표 |
| **BR** | Business Rule | `business-rules/` 규칙 ID 접두 |
| **wave** | 개발 물결 | 계획·우선순위 유일 축 — [`waves.md`](waves.md) |
