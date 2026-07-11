# Figma Wireframe v1 (개발 소스용)

> Figma·NotebookLM 04 병합 (2026-07-08). 상세 기획은 `prd.md`·`business-rules/`·`erd.md`와 교차 검증.

## Source

| 항목 | 값 |
|------|-----|
| 파일 | [TripFit 개발 소스용](https://www.figma.com/design/xEqgeZYrv8h4kQLr2Bkzh0/TripFit-%EA%B0%9C%EB%B0%9C-%EC%86%8C%EC%8A%A4%EC%9A%A9) |
| 노드 | `Wireframe - ver1` (`2:13589`); ver2 Prototype 일부 반영 |
| 버전 | ver1 (+ ver2 ROOM_02 등) |

UI 픽셀 스펙이 아니라 **백엔드가 알아야 할 도메인·상태·필드** 위주.

---

## 제품 한 줄

여러 사람의 근무 패턴과 연차 조건을 반영하여 모두가 납득할 수 있는 최적의 여행 일정을 추천하는 의사결정 지원 서비스.

---

## 화면 맵 (와이어프레임 섹션)

| 섹션 | 주요 화면 | 백엔드 관련성 |
|------|-----------|---------------|
| **홈** | 진행 중 캐러셀 + 전체 목록·필터·Pin·방 생성 | `GET /trips?scope=` · `last_activity_at` · `is_pinned`/`pinned_at` (wave 2 · D5) |
| **온보딩** | 소셜 로그인, 가이드, 캘린더 `[제안]` | JWT, `User` boolean, wave 1 |
| **여행방 생성/참여** | 이름·기간·일수·인원·여행지·초대 코드 | `trip` POST, `invite_code` 검증 (wave 2) |
| **내 일정 관리** | 근무·연차, 날짜별 슬롯 | `schedule` CONDITION + AVAILABILITY (wave 2) |
| **여행방 상세** | 그룹 달력, 응답률, 추천 CTA | `trip_member.status`, User `schedule` 필터 (달력 UI wave 3) |
| **추천 결과** | 4모드, TOP 3, 근거 | `recommendation`, `last_recommendation_mode` (wave 2) |
| **확정·공유** | 확정, 카카오 공유, 취소 | `CONFIRMED` / `CANCELED`; 취소 사유 **wave 4** |
| **알림센터** | 알림 목록 | BR-NOTI-* (wave 3) |
| **마이페이지** | 설정, 탈퇴, 캘린더 | BR-USER-004/005, wave 4 연동 |

---

## 핵심 도메인

### 1. 여행방 (Trip)

| 항목 | 내용 |
|------|------|
| **필드** | `name`, `destination`, `start_range`, `end_range`, `duration_days`, `member_count`, `invite_code`, `last_recommendation_mode`, `last_activity_at`, `confirmed_*` |
| **DB status** | `ONGOING`, `CONFIRMED`, `CANCELED`, **`TERMINATED`** (희망 기간 `end_range` 경과 — 정책서 “종료”) |
| **UI 뱃지** | **조율 중** / **일정 확정** — `TripStatus` 매핑 확정 (2026-07-17). `ONGOING`→조율 중, `CONFIRMED`→일정 확정, `TERMINATED`→종료. 구 “응답대기중”은 UI 서브 상태(백엔드 `ONGOING` 유지) |
| **권한** | 방장만 메타 수정 **BR-TRIP-009**, 추천·확정 **BR-TRIP-005·007**, 삭제 **BR-TRIP-013** |
| **홈 2뷰 (D5)** | **진행 중:** `end_range >= today` · Pin → `pinned_at` → `last_activity_at`. **전체:** `last_activity_at`만 · Pin 미적용 · 필터(전체/조율 중/확정 + 내가 생성한 방) |
| **홈 Pin** | `trip_member.is_pinned` + `pinned_at` — **진행 중 캐러셀만** 정렬 적용; `end_range` 경과 시 자동 해제 |

여행지: Figma **정했어요/못 정했어요** 분기 → `destination` nullable (ERD).
여행 일정: Figma **정했어요/못 정했어요** → `duration_days` nullable. 정했을 때 n박 m일 입력 → API `durationNights`+`durationDays` · DB는 m일만.
희망 기간: 생성 시 설정 · **편집 화면에서 수정 불가** (BR-TRIP-009).

### 2. 일정 (User 전역 — `regular_schedule` + `personal_schedule`)

| 항목 | 내용 |
|------|------|
| **정기 일정** | 반복 패턴 (출근·수업 등). user당 N행 |
| **개인 일정** | 날짜×슬롯 가능/불가 + `uncertain` |
| **effective** | #17 resolve — regular ⊕ personal (S1·R2=A) |
| **time_slot** | MORNING(00–13), AFTERNOON(13–18), EVENING(18–24) |
| **status** | POSSIBLE, IMPOSSIBLE (슬롯). **uncertain** = 날짜 단위 미정 |
| **프라이버시** | 타인에게 상태만 — **BR-TRIP-004** |
| **연동** | 변경 시 모든 참여 trip에 반영 — **BR-USER-008** |

| **방 입장** | 정기≥1 OR 개별≥1 OR **`is_all_free`** — **BR-USER-006** |
| **신규 trip / 방 생성** | **정기→개별**(수정/Skip) — **BR-USER-007** · 프리패스 금지 |
| **전이** | 0행→`is_all_free=true` · 일정 추가→`false` · 선언 버튼 없음 — **BR-USER-011** |

참여 완료 = **`trip_member.status=RESPONDED`** (확인 플로우 Skip/완료). 구 submit **폐기**. 일정 수정 후에도 `RESPONDED` 유지.

### 3. 추천·확정

- **4모드** (wave 2 전부): 기본 / 모두 참석 / 휴가 아끼기 / 확실하게 가기 — **BR-TRIP-005**, 하드 필터 **BR-TRIP-011**
- **TOP 3** — `recommendation_rank`; 모드·기간 변경 시 **hard DELETE** — **BR-TRIP-010**
- **동점** — **BR-TRIP-012** `[제안]`

### 4. 사용자·온보딩

- 소셜 로그인 필수 — **BR-USER-001/002**
- `hasPreSchedule` — regular OR personal ≥1 **파생** (입장 단독 게이트 아님 — D-JOIN-ENTRY)
- 프로필 이미지: wave 1 provider CDN — [`006`](../decisions/006-profile-image-url-storage.md)

### 5. 알림

| ID | wave | 비고 |
|----|------|------|
| BR-NOTI-001~004 | 3 | 참여·전원응답·변경·확정/취소 |
| BR-NOTI-005 | **4** | 월 1·15 스케줄러 Out (MVP) |
| BR-NOTI-006~008 | 3 | 카카오 공유 카피 |

### 6. wave 4 (문서만 — MVP Out)

- **`trip.cancel_reason`** — 취소·삭제 VOC 사유 수집 (Figma 플로우 있음, 구현 wave 4)

---

## 화면별 API 힌트 (기획 수준)

| 화면 | 데이터·액션 | BR | wave |
|------|-------------|-----|------|
| 홈 · 진행 중 | `GET /trips?scope=ongoing` — Pin → `pinned_at` → `last_activity_at` | D5 | 2 |
| 홈 · 전체 | `GET /trips?scope=all` (+ `status`·`ownerOnly`) — `last_activity_at`만 | D5 | 2 |
| 온보딩 | profile / onboarding PATCH | BR-USER-001, 006 | 1 |
| 방 생성 | trip POST + invite | BR-TRIP-001 | 2 |
| 방 상세 | 기간 내 AVAILABILITY 집계 | BR-TRIP-002~004 | 2 (UI 달력 3) |
| 일정 시트 | schedule AVAILABILITY PATCH | BR-TRIP-003 | 2 |
| 추천 모드 | 재계산 + recommendation replace | BR-TRIP-005, 010 | 2 |
| 확정 | status → CONFIRMED | BR-TRIP-007 | 2 |
| Pin 토글 | `trip_member.is_pinned` + `pinned_at` | D5 | 2 |
| 알림센터 | 알림 이력 | BR-NOTI-* | 3 |
| 취소 사유 | cancel_reason | `[wave 4]` | 4 |

---

## Figma vs PRD 메모

| 항목 | 정리 |
|------|------|
| **destination** | ver2·Figma ROOM_02 — ERD **MVP In** (PRD 구버전보다 최신) |
| **Pin·내보내기** | Pin MVP In (D5 홈 2뷰). 내보내기 API [#20](https://github.com/Central-MakeUs/TripFit-server/issues/20) (**Wave 2 Nice**) |
| **불가 사유 UI** | 팝업 텍스트 노출 여부 `[미정]` — API `note` 없음 |
| **TBD** | 상태값으로 확정 (시간대 아님) |

---

## 관련 문서

- ERD: [`docs/architecture/erd.md`](../architecture/erd.md)
- BR: [`docs/product/business-rules/`](../product/business-rules/)
- 플로우: [`docs/product/flows/`](../product/flows/)
- Cursor: [`.cursor/rules/figma-product.mdc`](../../.cursor/rules/figma-product.mdc)
