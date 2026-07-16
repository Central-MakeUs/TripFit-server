# 일정 참여·온보딩·submit 흐름 (재설계)

> wave: **1**  
> implements: *(없음 — 설계 확정 전)*  
> deferred: BR-USER-006, BR-USER-007, BR-USER-008(참여 완료 의미), BR-NOTI-001, BR-NOTI-002(제출 트리거), D1(trip-room-api)  
> 상태: **`[미定]`** — 2026-07-17 에스컬레이션 (submit · sparse · onboarding skip 상호 충돌)  
> GitHub: **#22**  
> 선행: [`user-onboarding.md`](user-onboarding.md), [`schedule-unified.md`](schedule-unified.md), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md), [`trip-room-api.md`](trip-room-api.md)

## 목표

다음이 **한 세트**로 엮여 있어, 개별 확정(D1 등)만으로는 제품·API가 모순된다. wave 1에서 **하나의 설계**로 재확정한다.

1. **온보딩 skip** — `isScheduleRegistered=false`로 선택 온보딩을 끝낸 사용자
2. **정기 일정 게이트 (BR-USER-006)** — personal/calendar/submit 진입 조건
3. **`POST .../schedule/submit`** — trip별 “제출” vs User 전역 일정(BR-USER-008) 이중 모델
4. **sparse day** — regular·personal 모두 없는 날 = omit vs “매일 가능” vs “미입력” 구분

## 배경 — 왜 `[미定]`로 되돌렸는가

| 충돌 | 설명 |
|------|------|
| **전역 일정 vs trip submit** | 일정 데이터는 trip FK 없이 User 전역인데, submit은 `trip_member.RESPONDED`만 바꿈 → “trip별 제출” UX와 서버 모델 불일치 |
| **D1 (regular만으로 submit)** | trip 기간 personal 0행이어도 submit 가능 → sparse day가 “전부 가능”인지 “미작성”인지 API로 구분 불가 |
| **온보딩 skip + BR-USER-006** | skip 후 `isScheduleRegistered=false`인데, trip 참여 시 regular 강제 → skip의 의미와 trip 게이트가 충돌 |
| **RESPONDED 의미** | personal 수정 후에도 RESPONDED 유지 → “재제출”·응답률·알림(BR-NOTI-001/002) 정의 불명확 |

## OpenAPI 숨김 (2026-07-17)

설계 확정 전 클라이언트 연동을 막기 위해 아래 API는 **구현은 유지**하되 **`@Hidden`** (Swagger/OpenAPI 미노출).

| Method | Path | 숨김 사유 |
|--------|------|-----------|
| POST | `/api/v1/trips/{tripId}/schedule/submit` | BR-USER-007 · D1 `[미定]` |
| PATCH | `/api/v1/users/onboarding` | skip·`isScheduleRegistered` 정책 `[미定]` |
| GET | `/api/v1/users/schedule/personal` | BR-USER-006 게이트 `[미定]` |
| PATCH | `/api/v1/users/schedule/personal` | 동일 |
| GET | `/api/v1/users/schedule/calendar` | regular 게이트 + sparse 의미 `[미定]` |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | 그룹 effective · sparse 해석 `[미定]` |

**노출 유지 (wave 2 데이터 입력·여행방):**

| Method | Path | 비고 |
|--------|------|------|
| * | `/api/v1/users/schedule/regular` | 정기 CRUD — 데이터 모델 자체는 유지 |
| * | `/api/v1/trips/*` (submit 제외) | 생성·참여·Pin·members 목록 등 |
| GET | `.../members/personal-summary` | deprecated 표기 유지 |

---

## 수정 대상 인벤토리 (전체)

구현·문서 동기화 시 아래를 **한 이슈(#TBD)에서** 처리한다.

### A. 스펙 (`docs/specs/`)

| 파일 | 현재 | 필요 조치 |
|------|------|-----------|
| **본 파일** `schedule-participation-onboarding.md` | Draft | 설계 확정 후 Approved |
| [`trip-room-api.md`](trip-room-api.md) | D1·submit Approved | D1 → `[미定]` · submit Must Have → deferred · #12 체크리스트에서 제외 |
| [`user-onboarding.md`](user-onboarding.md) | 사전 일정 skip Approved | ② 사전 일정 단계·`isScheduleRegistered` → `[미定]` |
| [`schedule-unified.md`](schedule-unified.md) | BR-USER-006 personal 게이트 | 게이트 정책 → `[미定]` · API 표에 Hidden 표기 |
| [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) | sparse omit 확정 | sparse = 가능 vs 미입력 → `[미定]` (A10 등) |
| [`trip-recommendation.md`](trip-recommendation.md) | RESPONDED·uncertain 참조 | 입력 전제·TBD 해석 → 본 스펙 확정 후 amend |
| [`auth-social-login.md`](auth-social-login.md) | login 응답에 onboarding boolean | boolean 의미 주석 → `[미定]` 링크 |
| [`docs/specs/README.md`](README.md) | wave 2 인덱스 | wave 1 항목 + 이슈 매핑 추가 |

### B. 결정 (`docs/decisions/`)

| 파일 | 조치 |
|------|------|
| [`007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md) | `isScheduleRegistered`·skip → `[미定]` amend · 본 스펙 링크 |

### C. 제품 (`docs/product/`)

| 파일 | 조치 |
|------|------|
| [`waves.md`](../product/waves.md) | wave 1에 본 재설계 항목 추가 |
| [`mvp.md`](../product/mvp.md) | “일정 응답·참여 완료” UX → `[미定]` 또는 wave 1 선행 |
| [`prd.md`](../product/prd.md) | BR-USER-006/007 행 → `[미定]` |
| [`glossary.md`](../product/glossary.md) | “참여자” 정의(RESPONDED) → `[미定]` |
| [`business-rules/user.md`](../product/business-rules/user.md) | BR-USER-006·007 → `[미定]` |
| [`business-rules/notification.md`](../product/business-rules/notification.md) | BR-NOTI-001/002 트리거 → `[미定]` |
| [`flows/trip-join.md`](../product/flows/trip-join.md) | 4~7단계 submit → `[미定]` |
| [`flows/schedule-edit.md`](../product/flows/schedule-edit.md) | submit 분기 → `[미定]` |
| [`flows/trip-confirm.md`](../product/flows/trip-confirm.md) | “1명 이상 제출” 전제 → `[미定]` |
| [`design/figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) | RESPONDED·isScheduleRegistered → `[미定]` |

### D. 아키텍처

| 파일 | 조치 |
|------|------|
| [`architecture/erd.md`](../architecture/erd.md) | `trip_member.status` RESPONDED 의미 · BR-USER-007 → `[미定]` |
| [`docs/README.md`](../README.md) | specs 인덱스에 본 스펙 추가 |

### E. Java — Controller (`@Hidden`)

| 파일 | 메서드 |
|------|--------|
| [`TripController.java`](../../src/main/java/com/tripfit/tripfit/trip/controller/TripController.java) | `submitSchedule` |
| [`UserController.java`](../../src/main/java/com/tripfit/tripfit/user/controller/UserController.java) | `updateOnboarding` |
| [`UserScheduleController.java`](../../src/main/java/com/tripfit/tripfit/user/schedule/controller/UserScheduleController.java) | `getPersonal`, `upsertPersonal`, `getCalendar` |
| [`TripMemberController.java`](../../src/main/java/com/tripfit/tripfit/trip/controller/TripMemberController.java) | `getScheduleCalendar` |

### F. Java — Service / Domain (설계 확정 후)

| 파일 | 검토 항목 |
|------|-----------|
| [`TripService.java`](../../src/main/java/com/tripfit/tripfit/trip/service/TripService.java) | `submitSchedule` 유지/삭제/410 |
| [`ScheduleService.java`](../../src/main/java/com/tripfit/tripfit/user/schedule/service/ScheduleService.java) | `requireRegularScheduleRegistered` 호출 위치 |
| [`UserProfileService.java`](../../src/main/java/com/tripfit/tripfit/user/service/UserProfileService.java) | onboarding PATCH `isScheduleRegistered` 수동 설정 |
| [`TripMemberStatus.java`](../../src/main/java/com/tripfit/tripfit/trip/domain/TripMemberStatus.java) | RESPONDED enum 유지 여부 |
| [`User.java`](../../src/main/java/com/tripfit/tripfit/user/domain/User.java) | `isScheduleRegistered` 컬럼 의미 |

### G. 테스트

| 파일 | 조치 |
|------|------|
| `TripControllerTest` | submit 테스트 — Hidden 후 유지 또는 `@Disabled` + 이슈 링크 |
| `TripServiceTest` | `submitSchedule_*` 동일 |
| `UserControllerTest` | onboarding PATCH |
| `UserScheduleControllerTest` | personal/calendar |
| `TripMemberControllerTest` | schedule-calendar |
| `ScheduleServiceTest` | BR-USER-006 gate |

### H. GitHub

| 대상 | 조치 |
|------|------|
| **#22** | schedule-participation-onboarding | Open · wave 1 |
| **#12** | submit·D1·schedule-calendar Must Have 제거 → #22 deferred |
| **#13** | RESPONDED·sparse 입력 전제 → #22 선행 |
| **#21** | NOTI-001/002 → 본 스펙 선행 |

---

## 설계 시 결정해야 할 질문 (체크리스트)

- [ ] **참여 완료**를 trip별 상태(`RESPONDED`)로 둘지, join만으로 충분한지, 스냅샷/버전이 필요한지
- [ ] **온보딩 skip** 후 trip 참여 시 regular/personal **강제 시점** (join 직후 vs submit 직전 vs never)
- [ ] **sparse day** 의미: POSSIBLE / UNKNOWN / omit — 추천(#13)·그룹 달력 공통 enum
- [ ] **“매일 가능”** 사용자 표현: explicit personal fill vs 프로필 플래그 vs regular 0행 허용
- [ ] **personal 수정** 시 RESPONDED **되돌림** 여부
- [ ] **submit API** 유지·삭제·join 시 자동 RESPONDED 중 선택
- [ ] `isScheduleRegistered` — DB boolean vs `regular_schedule` exists 파생만

---

## 완료 기준 (본 이슈)

- [ ] 위 질문 Approved 답변 · 본 스펙 또는 decisions amend
- [ ] A~H 인벤토리 반영 · `@Hidden` 해제 또는 API 제거 결정 반영
- [ ] #12·#13·wave 문서 동기화
- [ ] `./gradlew test` 통과

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-17 | `[미定]` 에스컬레이션 · OpenAPI Hidden · 수정 인벤토리 초안 |
