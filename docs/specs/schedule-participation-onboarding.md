# 일정 참여·온보딩·submit 흐름 (재설계)

> wave: **1**
> implements: BR-USER-001(이름 게이트), BR-USER-006(부분), BR-USER-007(부분)
> deferred: BR-NOTI-001/002(wave 3), **정원 hold → [#35](https://github.com/Central-MakeUs/TripFit-server/issues/35)** [`trip-join-capacity-hold.md`](trip-join-capacity-hold.md)
> 상태: **Draft** — 2026-07-21 #22 핵심 + **#39 amend** (방장 `JOINED`→`schedule/confirm`→`RESPONDED`). submit 삭제 · `is_all_free` · canEnterRoom · hold→#35
> 다음: #39 정합 잔여 amend · 인벤토리 stale 정리 · PR
> GitHub: **#22** · amend **[#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)**
> 선행: [`user-onboarding.md`](user-onboarding.md), [`schedule-unified.md`](schedule-unified.md), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md), [`trip-room-api.md`](trip-room-api.md), [`trip-create-join-flow-redesign.md`](trip-create-join-flow-redesign.md)
> 결정 amend: [`007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md) (D-REENTRY-2)

## 목표

다음이 **한 세트**로 엮여 있어, 개별 확정(D1 등)만으로는 제품·API가 모순된다. wave 1에서 **하나의 설계**로 재확정한다.

1. **방 입장 3조건 (D-JOIN-ENTRY)** — 정기 OR 개별 OR **`is_all_free`**
2. **신규 trip 확인 플로우** — 방장: create=`JOINED` → 일정 → **`POST .../schedule/confirm`**=`RESPONDED`. 멤버: 일정 → **`POST /trips/join`**=`RESPONDED` (#39)
3. **구 `schedule/submit` 삭제** · 멤버십 완료 API는 join + confirm (방장)
4. **omit ≠ is_all_free** (별개 유지) · Hidden **단계적** 공개

## 배경 — 왜 `[미정]`로 되돌렸는가

| 충돌 | 설명 |
|------|------|
| **전역 일정 vs trip submit** | 일정 데이터는 trip FK 없이 User 전역인데, submit은 `trip_member.RESPONDED`만 바꿈 → “trip별 제출” UX와 서버 모델 불일치 |
| **D1 (regular만으로 submit)** | trip 기간 personal 0행이어도 submit 가능 → sparse day가 “전부 가능”인지 “미작성”인지 API로 구분 불가 |
| **온보딩 skip + BR-USER-006** | skip 후 `isScheduleRegistered=false`인데, trip 참여 시 regular 강제 → skip의 의미와 trip 게이트가 충돌 |
| **RESPONDED 의미** | personal 수정 후에도 RESPONDED 유지 → “재제출”·응답률·알림(BR-NOTI-001/002) 정의 불명확 |
| **구 D-JOIN-3 vs 신규 trip 확인** | “사전 일정 있으면 직행”은 **D-JOIN-TRIP-FLOW**(항상 정기→개별 확인)와 모순 → **구 D-JOIN-3 폐기** |
| **row 0 = 전부 free?** | → **`user.is_all_free`** 로 구분 (default false=미입력). 스코프=User 전역 확정 |
| **전역 전부 free vs 신규 trip** | A방에서 전부 free여도 B방 join 시 **플로우 생략 금지** — UX=수정 기회 + Skip (D-JOIN-TRIP-FLOW) |

## OpenAPI 숨김 · 단계적 공개 (D-HIDDEN-7)

설계 확정 전 클라이언트 연동을 막기 위해 일부 API는 **`@Hidden`**. **단계적으로 해제** (한 번에 전부 공개하지 않음).

| Method | Path | 비고 |
|--------|------|------|
| ~~POST~~ | ~~`/api/v1/trips/{tripId}/schedule/submit`~~ | **삭제** — 재사용 금지. 방장 확인은 **`schedule/confirm`** (#39) |
| POST | `/api/v1/trips/{tripId}/schedule/confirm` | **#39** JOINED→RESPONDED |
| PATCH | `/api/v1/users/onboarding` | **삭제** (2026-07-20 #22) |
| GET | `/api/v1/users/schedule/personal` | **1단계:** #22 PR에서 `@Hidden` 해제 |
| PATCH | `/api/v1/users/schedule/personal` | 동일 |
| GET | `/api/v1/users/schedule/calendar` | 동일 |
| GET | `/api/v1/trips/{tripId}/members/schedule-calendar` | **2단계 해제 완료** — OpenAPI 공개 |

**이미 노출 / 유지:**

| Method | Path | 비고 |
|--------|------|------|
| * | `/api/v1/users/schedule/regular` | 정기 CRUD |
| * | `/api/v1/trips/*` (submit **제외·삭제**) | 생성·`POST /join`·Pin·members · **`members/schedule-calendar` 공개** |

---

## 확정 정책 (#22 — 2026-07-20)

아래는 GitHub **#22** 논의에서 **확정**된 항목이다. 구현 전 Approved 승인·나머지 `[미정]` 항목 확정이 필요하다.

### D-NAME-1: 소셜 로그인 + 프로필 이름 필수 (Kakao / Google / Apple 동일)

| 항목 | 확정 |
|------|------|
| HTTP 결합 | login과 이름 입력을 **한 HTTP 트랜잭션으로 묶지 않음** |
| 흐름 | `POST /auth/login` → JWT 발급 → `firstName` **또는** `lastName` null이면 **이름 화면** (건너뛰기 없음) |
| Provider | **Kakao = Google = Apple** — 이름 필수·게이트 동일 ([`007`](../decisions/007-user-profile-onboarding.md) 정렬) |
| 클라이언트 | Routing Guard (`replace` / stack reset), 뒤로가기·건너뛰기 **없음**, `BackHandler` 차단 |
| 서버 게이트 | `requireProfileNameComplete()` — 핵심 API(trip 생성·join 등)에서 **403** `PROFILE_NAME_REQUIRED` |
| 서버 **차단 금지** | login, refresh, `GET /auth/me`, `PATCH /users/profile` |
| 클라이언트 403 | 전역 403 핸들러 → `/onboarding/name` 강제 이동 |

### D-REENTRY-2: 이름만 완료 시 재진입 → 메인 (007 amend)

| 항목 | 확정 |
|------|------|
| **구 007** | `isOptionalOnboardingCompleted=false` → 재진입 시 캘린더·사전 일정 온보딩 **재노출** |
| **신 확정 (D-ONBOARD-4)** | 이름 직후 **첫 세션**에만 캘린더·사전 일정 화면 노출. 건너뛰기 가능 화면에서 **이탈 후 재접속** → **메인 직행** (D-REENTRY-2와 동일) |
| 선택 온보딩 | 건너뛰기 **전부** 완료 시 → 메인. 중간 이탈해도 재접속 시 **트랩하지 않음** |
| SSOT | **재접속·재로그인 라우팅:** `first_name` + `last_name` 완료 → 메인. `isOptionalOnboardingCompleted`는 **재진입 SSOT 아님** |

상세 amend: [`007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md) · [`user-onboarding.md`](user-onboarding.md)

### D-JOIN-ENTRY: 방 입장 가능 조건 (확정 — 2026-07-20 amend)

여행방 **입장 가능** = 아래 **셋 중 하나 이상** 만족. 일정·전부 free는 **User 전역** — 참여 중 **모든 여행방에 동일** 적용 (BR-USER-008).

| # | 조건 | 판별 (제품) | 구현 메모 |
|---|------|-------------|-----------|
| **1** | 정기 일정 등록함 | `regular_schedule` ≥ 1행 | User 전역 row |
| **2** | 개별 일정 등록함 | `personal_schedule` ≥ 1행 | User 전역 row |
| **3** | **전부 free** | 넣을 일정이 없어 **전부 가능** | **`user.is_all_free`** (boolean) |

```text
canEnterRoom(user) =
  EXISTS(regular_schedule)
  OR EXISTS(personal_schedule)
  OR user.is_all_free == true
```

**`user.is_all_free` (확정):**

| 항목 | 확정 |
|------|------|
| DB | `user.is_all_free` boolean **NOT NULL**, default **`false`** (가입 직후 = 미입력) |
| API | `UserSummaryResponse.isAllFree` — **login · `GET /auth/me`** (및 profile 등 동일 요약)에 포함 |
| 의미 | `false` + row 0 = **미입력**(입장 불가). `true` = 전부 free **선언됨**(입장 가능) |

- **셋 모두 불만족** → **방 입장 불가** (서버가 trip 멤버 API에서 `canEnterRoom` 검증).
- `hasPreSchedule` (= regular OR personal ≥1)는 **데이터 존재 파생값**일 뿐, **단독 입장 게이트가 아님**.
- **전역 전부 free ≠ 신규 trip 프리패스** (D-JOIN-TRIP-FLOW).

> **구 D-JOIN-3/4 폐기.** row 0만으로는 미입력과 전부 free 구분 불가 → `is_all_free` 필수.

### D-JOIN-CLEAR · 전이: `is_all_free` ↔ 일정 row (확정)

| 상황 | 서버 동작 |
|------|-----------|
| 정기·개별을 지워 **둘 다 0행** (CLEAR) | `is_all_free = true` **자동** (방 안·마이페이지·전역 일정 어디서든) |
| `is_all_free=true`인데 정기/개별 **추가** | `is_all_free = false` **자동** |
| Skip인데 **이미 ≥1행** | **변경 없음** (`is_all_free`·row 유지). patch 호출 불필요 |
| Skip인데 **이미 0행** (미입력) | **`is_all_free = true`** — 방장: `POST /trips` 시 · 참여자: `POST /trips/join` 시 (서버가 설정) |
| 일정이 있는데 “전부 free 할래” **선언** | **그런 버튼/API 없음**. row를 지워 0행이 되어야만 CLEAR로 `true` |
| null/empty body patch | **all-free 신호 아님** — 무시하거나 400. Skip ≠ null body |

→ 클라이언트만으로 row≥1인 채 `is_all_free=true`를 켤 수 없음. 서버는 row≥1이면 `is_all_free=true` 요청을 **거부**.

**일정 수정 API 형태 (확정):**

| 종류 | 동작 |
|------|------|
| **정기** | CRUD — `POST` 생성 · `PATCH /{id}` 단건 수정 · `DELETE /{id}` |
| **개별** | **bulk upsert + 삭제** — `PATCH /personal` · `items` insert/update · **`deletedDates`** 로 날짜 row 삭제 |

**개인 CLEAR:** `deletedDates`로 해당 날짜 삭제. regular도 0이면 `is_all_free=true` (D-JOIN-CLEAR). `items`와 `deletedDates`에 **같은 날짜** → 400 `INVALID_INPUT`. `items`·`deletedDates` **둘 다 비어 있으면** 400.

### D-JOIN-TRIP-FLOW: 신규 trip · 일정 확인 플로우 (확정 — #39 amend)

**목적 (UX):** 수정되었으면 고치고, 아니면 **Skip**.  
전역 전부 free·기존 일정이 있어도 **신규 trip마다** 플로우 노출 (프리패스 금지).

**대상:**

| 경로 | 동작 |
|------|------|
| **방장** | 「방 생성」→ **방 생성 폼** → `POST /trips`(`JOINED`) → **정기→개별** → `POST .../schedule/confirm`(`RESPONDED`) |
| **참여자** | 초대 링크 → **정기→개별** → **(수정 시 patch)** → `POST /trips/join` (`RESPONDED`) |

```text
방장:     [방 생성 폼] → POST /trips (JOINED) → [정기] → [개별] → POST .../schedule/confirm → [여행방]
참여자:   [정기] → [개별] → (수정 시 patch) → POST /trips/join → [여행방]
```

**Skip 의미 (확정):**

| 현재 전역 상태 | Skip / 마지막 버튼 시 |
|----------------|----------------------|
| 정기 또는 개별 ≥1 | **이전 상태 유지** (patch 불필요) |
| regular 0 **AND** personal 0 | **`is_all_free = true`** — 방장 **confirm** · 참여자 **join** 시 서버 설정 (create에서는 안 함) |

**백엔드 가드 (프론트 “선언 버튼”과 분리):**

1. **입장 게이트:** “방 안” 리소스는 `RESPONDED` ∧ `canEnterRoom` 불만족 시 **403**. UI Skip만으로 우회 불가.
2. **방장 confirm / 참여자 join:** row ≥1 → 유지 · row 0 → 서버가 `is_all_free=true` 후 `RESPONDED`. create는 `JOINED`만 (markAllFree 안 함).
3. **금지:** row ≥1인 채 `is_all_free=true` PATCH — **거부**. “전부 free 선언 버튼” API 없음.
4. **카피/버튼 문구**는 **프론트 책임**.

| 항목 | 확정 |
|------|------|
| 플로우 순서 | **정기 → 개별** |
| **방장** | 생성 폼 → `POST /trips`=`JOINED` → 일정 플로우 → `POST .../schedule/confirm`=`RESPONDED` (#39) |
| **prefill** | **프론트 UX** — 백엔드 계약·#22 미정 **아님** |
| 재입장 | `RESPONDED` → 방 상세 (BR-USER-010). `JOINED` → 일정 플로우. 미가입 참여자 → 플로우 |

### D-JOIN-MEMBER · API (확정 — 2026-07-21 · #39 amend)

| 역할 | 흐름 | `trip_member` |
|------|------|---------------|
| **방장** | 방 생성 폼 → **`POST /trips`** → 일정 플로우 → **`POST .../schedule/confirm`** | create 시 **`JOINED`**. confirm 시 **`RESPONDED`** (+ row0이면 `is_all_free=true`). create에서는 `markAllFree` 안 함 |
| **참여자** | 링크 → 일정 플로우 → **`POST /api/v1/trips/join`** | INSERT **`RESPONDED`** (+ row0이면 `is_all_free=true`). 미완료면 **row 없음**. 중간 `JOINED` 없음 |

**멤버십 API:**
- 참여자 가입: `POST /api/v1/trips/join` (`{ inviteCode }`)
- 방장(및 JOINED) 일정 확인: `POST /api/v1/trips/{tripId}/schedule/confirm`
- **구 `POST .../schedule/submit` — 삭제·재사용 금지.**

**`JOINED`:** 방장 create 직후 **사용** (#39). 멤버 신규 INSERT는 **`RESPONDED`만**.  
**초대 공유:** 방장 ∧ **`RESPONDED`(방 입장 후)** 만 — JOINED는 입장 불가 → 공유 불가. create 응답에 `inviteCode` 없음 ([`kakao-invite-share.md`](kakao-invite-share.md) S-1·S-2).

**정원 경쟁 (MVP 감수):** join INSERT 시 409. hold → [#35](https://github.com/Central-MakeUs/TripFit-server/issues/35).

### D-MEMBER-FILL: 모집 현황 (확정)

| 필드 | 의미 |
|------|------|
| `memberCount` | 정원 |
| `joinedMemberCount` | `trip_member` 수 |
| `memberFillRate` | `joinedMemberCount / memberCount` |
| `respondedCount` | `RESPONDED` 수 (= 전원 RESPONDED면 joined와 동일) |

### D-SPARSE vs `is_all_free` (확정 — A안)

| | `is_all_free` | omit=POSSIBLE |
|--|---------------|---------------|
| 층 | **입장 게이트** | **입장 후** 달력/추천 |
| 관계 | **별개** — 동일 개념으로 합치지 않음 |

### D-HIDDEN-7: OpenAPI 공개 (확정 — 단계적 C안)

| 단계 | 공개 |
|------|------|
| **1** | `#22` 구현 PR: `is_all_free` · join 재정의 · **submit 삭제** · personal/calendar `@Hidden` **해제** |
| **2** | 그룹 `members/schedule-calendar` — **`@Hidden` 해제 완료** |
| — | submit은 **공개하지 않음** (삭제) |

**라우팅:**

```text
방장: [일정 플로우] → (수정 시 patch) → [방 생성 폼] → POST /trips (owner RESPONDED) → [여행방]

참여자: 링크 → [일정 플로우] → (수정 시 patch) → POST /trips/join (RESPONDED) → [여행방]
        이미 멤버 → [여행방]
```

### D-ONBOARD-4: 첫 가입 세션 — 선택 온보딩 UX

| 항목 | 확정 |
|------|------|
| 대상 | **방금 소셜 회원가입을 끝낸 첫 세션** (이름 PATCH 직후) |
| 노출 | **캘린더 연동** → **사전 일정 입력** 순 (건너뛰기 가능) |
| 전부 건너뛰기 | 마지막 단계까지 skip → **메인** |
| 건너뛰기 가능 화면에서 **이탈** | 앱 종료·백그라운드 포함 — **재접속 시 메인 직행** (온보딩 재강제 없음, D-REENTRY-2) |
| 재가입 유저 | 이름 이미 있음 → **처음부터 메인** (선택 온보딩 생략) |

**`User` 온보딩 필드 (2026-07-20 구현):**

| 필드 | 상태 |
|------|------|
| `isGoogleCalendarConnected` | **유지** — OAuth 연동 SSOT (Google Calendar API wave 4) |
| `isScheduleRegistered` | **제거** — `hasPreSchedule` 파생 (D-BR006-C) |
| `isOptionalOnboardingCompleted` | **제거** — 재접속 SSOT = 이름 완료 (D-REENTRY-2) |
| `PATCH /users/onboarding` | **삭제** |

**`hasPreSchedule` (D-BR006-C 확정):** login/me 응답 필드. `UserSummaryService`가 `regular_schedule` OR `personal_schedule` row 존재 여부를 **조회 시 파생**.

### D-BR006-C: `hasPreSchedule` 파생 (확정)

| 항목 | 확정 |
|------|------|
| SSOT | `regular_schedule` / `personal_schedule` **테이블 row** |
| API | `UserSummaryResponse.hasPreSchedule` — login · `GET /auth/me` · profile PATCH |
| DB 컬럼 | `is_schedule_registered` **제거** (Hibernate ddl-auto) |
| 구 `@Hidden` onboarding PATCH | **삭제** |

### D-SPARSE-3: 달력 omit(빈 날) — 방 입장 후 해석

| 항목 | 확정 |
|------|------|
| 전제 | **이미 여행방에 입장한 이후** (join·D-JOIN-ENTRY/TRIP-FLOW 통과 후) |
| omit day | regular·personal 모두 없어 calendar에서 **날짜가 생략된 날** = **하루 종일 가능 (`POSSIBLE`)** |
| 범위 | 추천(#13)·그룹 달력·trip 기간 effective 계산에 동일 적용 (방 **밖** join 게이트와 별개) |

> **전부 free vs omit=POSSIBLE:** **별개 유지 (A안)**. `is_all_free` = 입장 게이트 · omit = 입장 후 달력/추천. 합치지 않음 — D-SPARSE vs `is_all_free` 절.

### D-BR006-5: 정기·개인 일정 독립 (BR-USER-006 게이트 삭제)

| 항목 | 확정 |
|------|------|
| **BR-USER-006** | **삭제** — “정기 일정 먼저 등록해야 personal/calendar 수정 가능” **403 `REGULAR_SCHEDULE_REQUIRED` 폐기** |
| 관계 | `regular_schedule`과 `personal_schedule`은 **서로 영향 없는 별도 일정** |
| personal-only | personal만 있어도 입장 조건 **2** 충족 (D-JOIN-ENTRY) |
| 구현 | `ScheduleService.requireRegularScheduleRegistered` 호출 제거 · personal GET/PATCH/calendar **regular 게이트 없음** |

**`isScheduleRegistered` — D-BR006-C 확정 (2026-07-20):** DB 컬럼 **제거**. `hasPreSchedule` = 조회 시 `EXISTS(regular) OR EXISTS(personal)` 파생.

### D-PERSONAL-6: 개인 일정 수정 — 나비효과 없음

| 항목 | 확정 |
|------|------|
| 범위 | 여행방 **참여 중** 마이페이지·외부 달력 등에서 `personal_schedule` 수정 |
| 동작 | **아무 변화 없음** — `RESPONDED` **유지**(되돌림 없음), 알림 **없음**, 방 UI **별도 갱신 유도 없음** |
| 데이터 | BR-USER-008 — User 전역 일정, 참여 중 모든 trip에 **동일 데이터** 반영 (조회 시 최신 effective) |

### D-SUBMIT-2: submit 폐기 (확정) — 상단 D-TRIP-CONFIRM · D-SUBMIT-2 절 참조

| 항목 | 확정 |
|------|------|
| submit | **폐기** |
| RESPONDED | Skip/확인 완료 = 응답 완료 |
| 참여율 | RESPONDED / 멤버 수 |

### D-HIDDEN-7: OpenAPI 단계적 공개 (확정)

상단 D-HIDDEN-7 표 참조. submit **삭제**. personal/calendar → #22 PR에서 해제. schedule-calendar → 후속.

### D-AUTH-8: 여행방 권한 — #22 범위 밖 (#24 완료)

**초대 링크(join)와 무관.** [`decisions/008`](../decisions/008-trip-authorization-guard.md) · Issue **#24** — **이미 구현 완료**.

| 구분 | 내용 |
|------|------|
| **join (초대)** | 링크/코드 → 로그인 → `POST .../trips/join` — **비멤버도 호출 가능** (여기서 멤버 등록) |
| **trip API (입장 후)** | `GET/PATCH /trips/{tripId}` · Pin · members · (구) submit 등 — **해당 trip `trip_member`인지** 검증 |
| **방장 전용** | trip 수정·삭제·Pin 등 — **`trip.owner == userId`** |
| **구현** | `@TripMemberOnly` / `@TripOwnerOnly` + `TripAuthorizationInterceptor` (#24). 비멤버가 `{tripId}` API 호출 → **403** `TRIP_ACCESS_DENIED` |

→ “카톡 링크로 초대받으면 join은 된다” ≠ “아무 JWT나 `{tripId}` API를 다 볼 수 있다”. **#22에서 재결정할 항목 아님.**

---

## 수정 대상 인벤토리 (전체)

구현·문서 동기화 시 아래를 **한 이슈(#TBD)에서** 처리한다.

### A. 스펙 (`docs/specs/`)

| 파일 | 현재 | 필요 조치 |
|------|------|-----------|
| **본 파일** `schedule-participation-onboarding.md` | Draft | 설계 확정 후 Approved |
| [`trip-room-api.md`](trip-room-api.md) | D1·submit Approved | D1 → `[미정]` · submit Must Have → deferred · #12 체크리스트에서 제외 |
| [`user-onboarding.md`](user-onboarding.md) | 사전 일정 skip Approved | ② 사전 일정 단계·`isScheduleRegistered` → `[미정]` |
| [`schedule-unified.md`](schedule-unified.md) | BR-USER-006 personal 게이트 | 게이트 정책 → `[미정]` · API 표에 Hidden 표기 |
| [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) | sparse omit 확정 | sparse = 가능 vs 미입력 → `[미정]` (A10 등) |
| [`trip-recommendation.md`](trip-recommendation.md) | RESPONDED·uncertain 참조 | 입력 전제·TBD 해석 → 본 스펙 확정 후 amend |
| [`auth-social-login.md`](auth-social-login.md) | login 응답에 onboarding boolean | boolean 의미 주석 → `[미정]` 링크 |
| [`docs/specs/README.md`](README.md) | wave 2 인덱스 | wave 1 항목 + 이슈 매핑 추가 |

### B. 결정 (`docs/decisions/`)

| 파일 | 조치 |
|------|------|
| [`007-user-profile-onboarding.md`](../decisions/007-user-profile-onboarding.md) | `isScheduleRegistered`·skip → `[미정]` amend · 본 스펙 링크 |
| [`008-trip-authorization-guard.md`](../decisions/008-trip-authorization-guard.md) | `@TripMemberOnly`/`@TripOwnerOnly` + Interceptor 권한 설계안 (제안) — submit·members 권한과 함께 확정 |

### C. 제품 (`docs/product/`)

| 파일 | 조치 |
|------|------|
| [`waves.md`](../product/waves.md) | wave 1에 본 재설계 항목 추가 |
| [`mvp.md`](../product/mvp.md) | “일정 응답·참여 완료” UX → `[미정]` 또는 wave 1 선행 |
| [`prd.md`](../product/prd.md) | BR-USER-006/007 행 → `[미정]` |
| [`glossary.md`](../product/glossary.md) | “참여자” 정의(RESPONDED) → `[미정]` |
| [`business-rules/user.md`](../product/business-rules/user.md) | BR-USER-006·007 → `[미정]` |
| [`business-rules/notification.md`](../product/business-rules/notification.md) | BR-NOTI-001/002 트리거 → `[미정]` |
| [`flows/trip-join.md`](../product/flows/trip-join.md) | 4~7단계 submit → `[미정]` |
| [`flows/schedule-edit.md`](../product/flows/schedule-edit.md) | submit 분기 → `[미정]` |
| [`flows/trip-confirm.md`](../product/flows/trip-confirm.md) | “1명 이상 제출” 전제 → `[미정]` |
| [`design/figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) | RESPONDED·isScheduleRegistered → `[미정]` |

### D. 아키텍처

| 파일 | 조치 |
|------|------|
| [`architecture/erd.md`](../architecture/erd.md) | `trip_member.status` **JOINED\|RESPONDED** (#39) · BR-USER-007 |
| [`docs/README.md`](../README.md) | specs 인덱스에 본 스펙 추가 |

### E. Java — Controller

| 파일 | 조치 |
|------|------|
| [`TripController.java`](../../src/main/java/com/tripfit/tripfit/trip/controller/TripController.java) | **`submitSchedule` 삭제** · **`schedule/confirm` 추가** (#39) |
| [`UserScheduleController.java`](../../src/main/java/com/tripfit/tripfit/user/schedule/controller/UserScheduleController.java) | personal/calendar — **1단계** Hidden 해제 |
| [`TripMemberController.java`](../../src/main/java/com/tripfit/tripfit/trip/controller/TripMemberController.java) | schedule-calendar — **2단계** |

### F. Java — Service / Domain

| 파일 | 검토 항목 |
|------|-----------|
| [`TripCommandService`](../../src/main/java/com/tripfit/tripfit/trip/service/TripCommandService.java) / [`TripJoinService`](../../src/main/java/com/tripfit/tripfit/trip/service/TripJoinService.java) | create=`JOINED` · confirm/join=`RESPONDED` (#39) · `is_all_free`는 confirm/join |
| [`TripMemberStatus.java`](../../src/main/java/com/tripfit/tripfit/trip/domain/TripMemberStatus.java) | `JOINED` · `RESPONDED` (#39). 멤버 INSERT는 RESPONDED만 |
| [`User.java`](../../src/main/java/com/tripfit/tripfit/user/domain/User.java) | `is_all_free` 컬럼 |
| [`ScheduleService.java`](../../src/main/java/com/tripfit/tripfit/user/schedule/service/ScheduleService.java) | CLEAR/추가 ↔ `is_all_free` 전이 |

### G. 테스트

| 파일 | 조치 |
|------|------|
| `TripControllerTest` / `TripServiceTest` | submit 제거 · create=`JOINED` · confirm (#39) |
| `User*` / `Schedule*` | `is_all_free` · canEnterRoom |

### H. GitHub

| 대상 | 조치 |
|------|------|
| **#22** | schedule-participation-onboarding | Open · wave 1 |
| **#12** | submit·D1·schedule-calendar Must Have 제거 → #22 deferred |
| **#13** | RESPONDED·sparse 입력 전제 → #22 선행 |
| **#21** | NOTI-001/002 → 본 스펙 선행 |

---

## 설계 시 결정해야 할 질문 (체크리스트)

### 확정 (#22 — 2026-07-20)

- [x] **이름 필수·소셜 동일** — D-NAME-1
- [x] **재접속 라우팅** — D-REENTRY-2 · D-ONBOARD-4: 이름 완료 → 메인; 첫 세션만 선택 온보딩
- [x] **방 입장 3조건** — D-JOIN-ENTRY · **`user.is_all_free`** (default false, login/me)
- [x] **CLEAR · 전이** — 0행→true · 추가→false · “선언 버튼” 없음
- [x] **신규 trip / 방장 생성** — D-JOIN-TRIP-FLOW 수정/Skip · 프리패스 금지
- [x] **Skip** — 일정 있으면 유지 · 없으면 `is_all_free=true` · 서버 가드
- [x] **같은 trip 재입장 UX** — `RESPONDED` → 직행 (BR-USER-010)
- [x] **D-TRIP-CONFIRM** — `trip_member.status=RESPONDED`
- [x] **submit 폐기** — Skip/확인 = RESPONDED 한 이벤트
- [x] **omit = POSSIBLE (입장 후)** — D-SPARSE-3
- [x] **BR-USER-006 정기 선행 게이트 삭제** — D-BR006-5
- [x] **personal 수정 나비효과 없음** — D-PERSONAL-6
- [x] **Hidden API** — D-HIDDEN-7
- [x] **trip 권한 008** — D-AUTH-8

### 미정

- [x] **`POST /trips/join`(멤버) + `schedule/confirm`(방장)** · submit **삭제** · create=`JOINED` (#39)
- [x] **prefill** — 프론트 영역 (백엔드 미정 제외)
- [x] **omit ≠ is_all_free** (A)
- [x] **Hidden 단계적 공개** (C)
- [x] **memberFillRate** · late-join · hold→#35
- [x] **D-BR006-C** · is_all_free · CLEAR · Skip · 방장 · 재입장 UX

---

## 완료 기준 (본 이슈)

- [x] D-JOIN-ENTRY · CLEAR · TRIP-FLOW · RESPONDED · submit 폐기 · **#39 confirm** 문서화
- [x] **멤버십 path** — `POST /trips` · `POST /trips/join` · `POST .../schedule/confirm` · submit **삭제**
- [x] 코드: submit 제거 · create=`JOINED` · confirm/join=`RESPONDED` (#39)
- [x] 코드: `is_all_free` · canEnterRoom · Hidden 1단계 해제
- [x] A~H 인벤토리 반영 · trip-room/#22 정합 · personal `deletedDates`
- [x] `./gradlew test` 통과

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | **#39 amend** — 방장 JOINED→confirm · D-JOIN-MEMBER/TRIP-FLOW · 인벤토리 stale 정리 |
| 2026-07-21 | **Amend** — personal `deletedDates` CLEAR 경로 · trip-room stale 정합 |
| 2026-07-21 | **Amend** — late-join · 방장 A · 단일 가입 API · `memberFillRate` · 정원 hold #35 |
| 2026-07-20 | **Amend** — Skip=`RESPONDED` 한 이벤트 · **submit 폐기** · D-TRIP-CONFIRM=`RESPONDED` |
| 2026-07-20 | **Amend** — `is_all_free` 컬럼/API · 전이 · Skip 가드 · 방장 생성 플로우 · 재입장 UX · D-TRIP-CONFIRM `[미정]` |
| 2026-07-20 | **Amend** — 전부 free=**User 전역** · CLEAR=어디서든 0행 · TRIP-FLOW=수정/Skip(전역이어도 프리패스 금지) |
| 2026-07-20 | **Amend** — D-JOIN-ENTRY(입장 3조건) · D-JOIN-CLEAR · D-JOIN-TRIP-FLOW. **구 D-JOIN-3/4 폐기** |
| 2026-07-20 | D-BR006-C 구현 — `hasPreSchedule` 파생 · onboarding 필드/API 제거 · BR-USER-006 게이트 삭제 |
| 2026-07-20 | D-ONBOARD-4 · D-SPARSE-3 · D-BR006-5 · D-PERSONAL-6 · D-HIDDEN-7 · D-AUTH-8 · D-SUBMIT-2 `[미정]` |
| 2026-07-20 | **Draft** — D-NAME-1 · D-REENTRY-2 · (구) D-JOIN-3/4 · 007 amend |
| 2026-07-17 | `[미정]` 에스컬레이션 · OpenAPI Hidden · 수정 인벤토리 초안 |
| 2026-07-16 | 여행방 권한 가드 설계안([decisions/008](../decisions/008-trip-authorization-guard.md)) 추가 · #22 범위 포함 |
