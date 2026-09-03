# 일정 참여·온보딩·submit 흐름 (재설계)

> implements: BR-USER-001(이름 게이트), BR-USER-006(부분), BR-USER-007(부분)
> deferred: BR-NOTI-001/002(MVP 출시)
> 상태: **Implemented** — 2026-07-21 #22 핵심 + **#39 amend**(방장 `SCHEDULE_PENDING`→`activate`→`ACTIVE`) + **`#113` amend(2026-08-18 — 전역 입장 게이트 `is_all_free`·`canEnterRoom` 폐지)** + **`#114` amend(2026-08-19 — 참여자도 join 시 `SCHEDULE_PENDING`, hold 폐지)**. submit 삭제. 완료 기준 체크리스트 전항 완료(2026-07-23 확인)
> GitHub: **#22** · amend **[#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)**
> 선행: [`user-onboarding.md`](../user/user-onboarding.md), [`schedule-unified.md`](../user-schedule/schedule-unified.md), [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md), [`trip-room-api.md`](trip-room-api.md)
> 결정 amend: [`007-user-profile-onboarding.md`](../../decisions/007-user-profile-onboarding.md) (D-REENTRY-2)

> ## ⚠️ 2026-08-19 amend — 최초/갱신 입력 판정 도입 (정기 일정 유무 2분기 폐기)
>
> **사전 일정 입력 플로우의 분기는 이제 「최초 입력 vs 갱신 입력」이며, 판정은 `사전 신청일`(`users.vacation_apply_period`) 저장 여부 하나다.** 아래 본문의 「정기 일정 보유 여부로 2분기」·`hasRegularSchedule` 서술은 **폐지된 계약**이며 이력으로만 남긴다.
>
> **왜:** 정기·개별 일정은 사용자에 따라 실제 데이터가 0건일 수 있어, 데이터 존재 여부로는 **"아직 입력을 안 한 사람"과 "입력을 끝냈지만 막힌 일정이 없는 사람"을 구분할 수 없다.** 연차·휴일 정보 4개 값 중 `사전 신청일`만 nullable이라(나머지 3개는 기본값이 늘 차 있음) 이 값이 유일한 입력 완료 마커가 된다.
>
> **함께 바뀐 것:** 회원가입 온보딩에서 사전 일정 단계 제거 · 모든 입력 플로우에서 건너뛰기 미노출 · `activate`에 입력 완료 게이트(403 `PRE_SCHEDULE_REQUIRED`) 추가 · `hasRegularSchedule`/`hasPreSchedule` 삭제 후 `hasCompletedPreSchedule` 신설
>
> SSOT: [`../user-schedule/pre-schedule-entry-flow.md`](../user-schedule/pre-schedule-entry-flow.md)

> ## ⚠️ 2026-08-18 amend (`#113`) — 전역 입장 게이트 폐지
>
> **방 입장 판정은 이제 방별 `trip_member.status = ACTIVE` 하나다.** 아래 본문의 "방 입장 3조건"·`canEnterRoom`·`user.is_all_free`·`SCHEDULE_ENTRY_REQUIRED` 서술은 **폐지된 계약**이며, 이력으로만 남긴다.
>
> **왜:** 전역 게이트는 `ACTIVE` 멤버에게 항상 참이라 실제로 아무것도 막지 못했다 — `activate`·`join`이 `markAllFreeIfNoSchedules`로 조건을 무조건 충족시키고, 정기 일정을 전부 지워도 `deleteRegular`가 다시 켰다. 그런데 `is_all_free`가 **서버에 의해 자동으로 켜지는** 값이라 두 번째 방 입장부터 프론트가 보는 값이 달라졌고, 프론트가 `hasPreSchedule || isAllFree`를 재구현하다 QA 이슈 1·2(P1)를 냈다. 같은 질문에 답하는 게이트를 둘로 두는 구조 자체를 없앤다.
>
> **동작 변화 없음:** 일정을 하나도 넣지 않은 사용자를 "전부 가능"으로 취급하는 계산은 그대로다(달력·추천에서 일정 row가 없으면 모든 슬롯 가능). 사라지는 것은 그 사실을 별도 컬럼에 기록하고 재검사하던 층뿐이다.
>
> SSOT: [`trip-join-schedule-gate.md`](trip-join-schedule-gate.md) J-7 · BR-USER-006·007 개정, BR-USER-011 삭제

## 목표

다음이 **한 세트**로 엮여 있어, 개별 확정(D1 등)만으로는 제품·API가 모순된다. MVP 출시에서 **하나의 설계**로 재확정한다.

1. ~~**방 입장 3조건 (D-JOIN-ENTRY)** — 정기 OR 개별 OR **`is_all_free`**~~ → **`#113`(2026-08-18)으로 폐지.** 방 입장 판정 = 그 방의 `trip_member.status == ACTIVE`
2. **신규 trip 확인 플로우** — 방장·멤버 동일: 방 진입(방장 create · 멤버 **`POST /trips/join`**)=`SCHEDULE_PENDING` → 일정 → **`POST .../activate`**=`ACTIVE` (#39 · **#114**)
3. **구 `schedule/submit` 삭제** · 멤버십 완료 API는 join + activate (방장)
4. ~~**omit ≠ is_all_free** (별개 유지)~~ (`is_all_free` 폐지로 무의미 — omit=POSSIBLE 해석은 유지) · Hidden **단계적** 공개

## 배경 — 왜 `[미정]`로 되돌렸는가

| 충돌 | 설명 |
|------|------|
| **전역 일정 vs trip submit** | 일정 데이터는 trip FK 없이 User 전역인데, submit은 `trip_member.ACTIVE`만 바꿈 → “trip별 제출” UX와 서버 모델 불일치 |
| **D1 (regular만으로 submit)** | trip 기간 personal 0행이어도 submit 가능 → sparse day가 “전부 가능”인지 “미작성”인지 API로 구분 불가 |
| **온보딩 skip + BR-USER-006** | skip 후 `isScheduleRegistered=false`인데, trip 참여 시 regular 강제 → skip의 의미와 trip 게이트가 충돌 |
| **ACTIVE 의미** | personal 수정 후에도 ACTIVE 유지 → “재제출”·응답률·알림(BR-NOTI-001/002) 정의 불명확 |
| **구 D-JOIN-3 vs 신규 trip 확인** | “사전 일정 있으면 직행”은 **D-JOIN-TRIP-FLOW**(항상 정기→개별 확인)와 모순 → **구 D-JOIN-3 폐기** |
| **row 0 = 전부 free?** | ~~`user.is_all_free`로 구분~~ → **`#113`으로 폐지.** row 0은 그냥 "일정 없음"이고, 달력·추천에서 모든 슬롯 가능으로 계산된다 |
| **전역 전부 free vs 신규 trip** | A방에서 전부 free여도 B방 join 시 **플로우 생략 금지** — UX=수정 기회 + Skip (D-JOIN-TRIP-FLOW) |

## OpenAPI 숨김 · 단계적 공개 (D-HIDDEN-7)

설계 확정 전 클라이언트 연동을 막기 위해 일부 API는 **`@Hidden`**. **단계적으로 해제** (한 번에 전부 공개하지 않음).

| Method | Path | 비고 |
|--------|------|------|
| ~~POST~~ | ~~`/api/v1/trips/{tripId}/schedule/submit`~~ | **삭제** — 재사용 금지. 방장 확인은 **`activate`** (#39) |
| POST | `/api/v1/trips/{tripId}/activate` | **#39** SCHEDULE_PENDING→ACTIVE |
| PATCH | `/api/v1/users/onboarding` | **삭제** (2026-07-20 #22) |
| ~~GET~~ | ~~`/api/v1/users/schedule/personal`~~ | **1단계:** #22 PR에서 `@Hidden` 해제 → **이후 조회 API 자체가 삭제됨**(전용 조회 없음, `PATCH` 응답 또는 `GET /calendar`로 대체 — `schedule-unified.md` 폐기 목록) |
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
| Provider | **Kakao = Google = Apple** — 이름 필수·게이트 동일 ([`007`](../../decisions/007-user-profile-onboarding.md) 정렬) |
| 클라이언트 | Routing Guard (`replace` / stack reset), 뒤로가기·건너뛰기 **없음**, `BackHandler` 차단 |
| 서버 게이트 | `requireProfileNameComplete()` — 핵심 API(trip 생성·join 등)에서 **403** `PROFILE_NAME_REQUIRED` |
| 서버 **차단 금지** | login, refresh, `GET /auth/me`, `PATCH /users/onboarding/name` |
| 클라이언트 403 | 전역 403 핸들러 → `/onboarding/name` 강제 이동 |

### D-REENTRY-2: 이름만 완료 시 재진입 → 메인 (007 amend)

| 항목 | 확정 |
|------|------|
| **구 007** | `isOptionalOnboardingCompleted=false` → 재진입 시 캘린더·사전 일정 온보딩 **재노출** |
| **신 확정 (D-ONBOARD-4)** | 이름 직후 **첫 세션**에만 캘린더·사전 일정 화면 노출. 건너뛰기 가능 화면에서 **이탈 후 재접속** → **메인 직행** (D-REENTRY-2와 동일) |
| 선택 온보딩 | 건너뛰기 **전부** 완료 시 → 메인. 중간 이탈해도 재접속 시 **트랩하지 않음** |
| SSOT | **재접속·재로그인 라우팅:** `first_name` + `last_name` 완료 → 메인. `isOptionalOnboardingCompleted`는 **재진입 SSOT 아님** |

상세 amend: [`007-user-profile-onboarding.md`](../../decisions/007-user-profile-onboarding.md) · [`user-onboarding.md`](../user/user-onboarding.md)

### D-JOIN-ENTRY: 방 입장 가능 조건 (확정 — 2026-07-20 amend)

여행방 **입장 가능** = 아래 **셋 중 하나 이상** 만족. 일정·전부 free는 **User 전역** — 참여 중 **모든 여행방에 동일** 적용 (BR-USER-008).

| # | 조건 | 판별 (제품) | 구현 메모 |
|---|------|-------------|-----------|
| **1** | 정기 일정 등록함 | `regular_schedule` ≥ 1행 | User 전역 row |
| **2** | 개별 일정 등록함 | `personal_schedule` ≥ 1행 | User 전역 row |
| **3** | **전부 free** | 넣을 일정이 없어 **전부 가능** | **`user.is_all_free`** (boolean) |

```text
방 입장 판정 (2026-08-18 `#113` 이후 — 현행)
  trip_member.status == ACTIVE          // 그 방의 일정 확인을 마쳤는가
  ↳ 미충족: 403 SCHEDULE_ACTIVATION_REQUIRED

구 전역 게이트 (폐지)
  canEnterRoom(user) = EXISTS(regular) OR EXISTS(personal) OR user.is_all_free
  ↳ 403 SCHEDULE_ENTRY_REQUIRED — 컬럼·메서드·ErrorCode 모두 삭제됨
```

**`user.is_all_free` (확정):**

| 항목 | 확정 |
|------|------|
| DB | `user.is_all_free` boolean **NOT NULL**, default **`false`** (가입 직후 = 미입력) |
| API | `UserSummaryResponse.isAllFree` — **login · `GET /auth/me`** (및 profile 등 동일 요약)에 포함 |
| 의미 | `false` + row 0 = **미입력**(입장 불가). `true` = 전부 free **선언됨**(입장 가능) |

- ~~셋 모두 불만족 → 방 입장 불가~~ → **`#113`으로 폐지.** 방 안 API 차단은 `@TripMemberOnly`의 `ACTIVE` 검사 하나가 담당한다.
- `hasCompletedPreSchedule`은 **입장 게이트가 아니라 화면 분기값**이다. 방 입장 가능 여부는 그 방의 `myMemberStatus = ACTIVE`가 답한다.
- **전역 전부 free ≠ 신규 trip 프리패스** (D-JOIN-TRIP-FLOW).

> **구 D-JOIN-3/4 폐기.** row 0만으로는 미입력과 전부 free 구분 불가 → `is_all_free` 필수.

### D-JOIN-CLEAR · 전이: `is_all_free` ↔ 일정 row (~~확정~~ → **2026-08-18 `#113`으로 전체 폐지**)

> 아래 표는 이력이다. `is_all_free` 컬럼과 자동 전이 로직(`markAllFreeIfNoSchedules`·`clearAllFreeOnScheduleAdded`)이 모두 삭제돼, 일정 CRUD는 더 이상 어떤 플래그도 건드리지 않는다.

| 상황 | 서버 동작 |
|------|-----------|
| 정기를 지우고, 개별도 **한 번도 등록한 적 없어** 둘 다 0행 (CLEAR) | `is_all_free = true` **자동** (방 안·마이페이지·전역 일정 어디서든). **개별은 O1.4 이후 삭제 불가**라 한 번이라도 등록했다면 이 경로 자체가 적용 안 됨(아래 128행 노트 참고) |
| `is_all_free=true`인데 정기/개별 **추가** | `is_all_free = false` **자동** |
| 일정 화면을 변경 없이 통과했는데 **이미 ≥1행** | **변경 없음** (`is_all_free`·row 유지). patch 호출 불필요 |
| 일정 화면을 변경 없이 통과했는데 **이미 0행** (미입력) | **`is_all_free = true`** — 방장: **`POST .../activate`** 시 · 참여자: `POST /trips/join` 시 (서버가 설정). **`POST /trips`(create)에서는 설정하지 않는다** (`TripCommandService.activateMembership`, 아래 D-JOIN-TRIP-FLOW 백엔드 가드 2와 동일) |
| 일정이 있는데 “전부 free 할래” **선언** | **그런 버튼/API 없음**. row를 지워 0행이 되어야만 CLEAR로 `true` |
| null/empty body patch | **all-free 신호 아님** — 무시하거나 400. 화면 통과 ≠ null body |

→ 클라이언트만으로 row≥1인 채 `is_all_free=true`를 켤 수 없음. 서버는 row≥1이면 `is_all_free=true` 요청을 **거부**.

> **⚠️ [미정-후속 논의] (2026-07-30, #67 O1.4 검토 중 발견):** 121행("일정이 있는데 전부 free 선언 → 그런 버튼/API 없음, row를 지워 0행이 되어야만 CLEAR")은 O1.4(`schedule-slot-override.md`)가 개별 일정 row 삭제 경로를 전면 제거하면서 한때 "기능 충돌"로 보였다. 그러나 재검토 결과 **실제 충돌은 아님** — `canEnterRoom`이 `EXISTS(regular) OR EXISTS(personal) OR is_all_free` OR 조건이라, 개별 일정 row가 이미 있는 유저는 조건 2로 이미 입장 가능하므로 애초에 `is_all_free` 전환이 **불필요**하다.
>
> 다만 "예전엔 학원 다녔지만 이제 안 다녀서 뭐든 상관없어졌다"처럼, 유저가 기존 데이터를 낱개로 고치는 대신 **한 번에 "이제 전부 상관없어요"를 명시적으로 선언**하고 싶어할 UX 여지는 남아있다(지금은 각 날짜를 개별적으로 "가능"으로 고쳐야 함). **지금 당장 게이트 로직엔 영향 없어 구현 불필요**하지만, 필요성이 실제로 제기되면 별도 스펙(전용 "전부 free 선언" API — row 삭제와 무관하게 별도 플래그만 세팅)으로 검토. 트래커: [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)

**일정 수정 API 형태 (확정):**

| 종류 | 동작 |
|------|------|
| **정기** | CRUD — `POST` 생성 · `PATCH /{id}` 단건 수정 · `DELETE /{id}` |
| **개별** | **bulk upsert(삭제 없음, O1.4)** — `PATCH /personal` · `items` insert/update, `slots`/`uncertain` 각각 선택 갱신. 어떤 값 조합을 보내도 row는 삭제되지 않음(`schedule-slot-override.md`) |

**개인 CLEAR는 O1.4 이후 없음:** 슬롯 3개를 `POSSIBLE`로, `uncertain`을 `false`로 보내는 것은 이제 "그 날짜를 명시적으로 하루 종일 가능으로 오버라이드"하는 평범한 upsert일 뿐, 삭제 신호가 아니다 — 정기 계산값과 값이 같아 보여도 오버라이드 row는 그대로 남는다. 개별 일정으로 `is_all_free`를 켜는 경로는 없음(126~128행 `[미정]` 노트 참고). `items`가 **비어 있으면** 400.

### D-JOIN-TRIP-FLOW: 신규 trip · 일정 확인 플로우 (확정 — #39 amend)

**목적 (UX):** 방에 들어가기 전 **일정을 반드시 한 번 확인**시킨다. 수정할 게 있으면 고치고, 없으면 그대로 통과.
전역 전부 free·기존 일정이 있어도 **신규 trip마다** 플로우 노출 (프리패스 금지).

**⚠️ 회원가입 온보딩과 다른 점 — 건너뛰기 없음 (2026-08-16 확정, Figma 대조):** 회원가입 2단계는 「건너뛰기」 버튼으로 화면을 넘길 수 있지만(`user-onboarding.md`), **방 입장 플로우에는 건너뛰기 버튼이 없다.** 정기·개별 화면을 모두 거쳐야 방에 들어갈 수 있다. 아래 표에서 "입력 없이 통과"는 **화면을 확인하고 바꿀 게 없어 그대로 진행한 것**이지 건너뛴 것이 아니다 — 구 문서의 "Skip" 표현은 이 뜻으로 읽는다.

**대상:**

| 경로 | 동작 |
|------|------|
| **방장** | 「방 생성」→ **방 생성 폼** → `POST /trips`(`SCHEDULE_PENDING`) → **일정 확인 플로우** → `POST .../activate`(`ACTIVE`) |
| **참여자** | 초대 링크 → `POST /trips/join`(`SCHEDULE_PENDING`) → **일정 확인 플로우** → **(수정 시 patch)** → `POST .../activate`(`ACTIVE`) — `#114`로 `join`이 플로우 맨 앞으로 이동, 구 hold는 폐지 |

**일정 확인 플로우 — 최초/갱신 입력으로 2분기 (2026-08-19 확정):**

```text
[hasCompletedPreSchedule] ← 판정은 이 값 하나로만 한다 (= 사전 신청일 저장 여부)
   │
   ├─ false (최초 입력) → "정기 일정이 있나요?"
   │       ├─ 예     → [정기 일정] → [연차·휴일 정보] → [개별 일정] → 입장
   │       └─ 없어요 → DELETE /users/schedule/regular(즉시) → [연차·휴일 정보] → [개별 일정] → 입장
   │
   └─ true (갱신 입력) → "일정 변경이 있나요?" (있어요/없어요 — 안내용, 분기 없음)
           → [정기 일정 수정(기존 값 프리필)] → [연차·휴일 정보] → [개별 일정] → 입장
```

- **`정기 일정이 있나요?`는 최초 입력에서만 노출된다.** 갱신 입력에는 대신 `일정 변경이 있나요?`가 뜨는데, 이 화면은 **분기 목적이 아니라** "처음부터 다시 입력하는 게 아니라 기존 정보를 확인·수정하는 과정"임을 알리는 안내 화면이다 — 어느 쪽을 골라도 같은 경로로 간다.
- **연차·휴일 정보는 두 갈래 모두 지난다.** `없어요` 경로에서도 묻는다(2026-08-19 변경 — 구 확정은 "정기와 한 덩어리라 없어요면 미노출"이었다). 이 단계를 지나야 `사전 신청일`이 저장되므로, 여기서 빠지면 판정 마커가 영원히 세워지지 않는다.
- **`없어요` 선택 시 기존 정기 일정을 즉시 전부 삭제한다** (`DELETE /api/v1/users/schedule/regular`). 최초 판정은 사전 신청일만 보므로, 예전에 넣어둔 정기가 남아 있으면 사용자가 "없다"고 답한 것과 추천 계산이 어긋난다.
- **건너뛰기는 없다.** 여행방 입장 플로우에서는 필요한 정보를 넣기 전까지 `다음` 버튼이 비활성화되고, 갱신 입력은 기존 값이 있으므로 기본 활성화다.
- **서버 게이트:** `activate`는 `사전 신청일`이 없으면 **403 `PRE_SCHEDULE_REQUIRED`**로 거부한다. 정기·개별 일정이 0건인 것은 거부 사유가 **아니다**.

> **개별 일정 화면은 "손댄 날짜만" 저장해야 한다.** 이 화면의 프리필은 `GET /users/schedule/calendar`(정기+개별+구글 합친 값)인데, 그 값을 화면에 보인 전 기간 그대로 `PATCH /personal`로 되돌려보내면 **정기 유래 계산값이 전부 개별 오버라이드 row로 굳는다**(O1.4 이후 삭제 경로 없음 — 이후 정기를 수정해도 그 날짜들은 옛 값으로 고정, 되돌릴 수 없음). 방 입장 플로우는 **방마다 매번** 이 화면을 거치므로 위험이 누적된다. 상세: [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md) "마이페이지 개별 일정 편집 UX" 엣지 케이스.

**백엔드 가드 (프론트 “선언 버튼”과 분리):**

1. **입장 게이트:** “방 안” 리소스는 `ACTIVE`가 아니면 **403 `SCHEDULE_ACTIVATION_REQUIRED`**. UI 통과만으로 우회 불가. (~~∧ `canEnterRoom`~~ — 2026-08-18 `#113`으로 삭제)
2. **activate:** 일정 row 수와 무관하게 `ACTIVE`로 전환 — 방장·참여자 모두 이 경로를 쓴다. 방 진입(create·join)은 `SCHEDULE_PENDING`까지만 (~~row 0 → `is_all_free=true`~~ — `#113` 삭제).
3. ~~**금지:** row ≥1인 채 `is_all_free=true` PATCH — 거부~~ → 컬럼 자체가 없어져 해당 없음. “전부 free 선언 버튼” API 없음은 유지.
4. **카피/버튼 문구**는 **프론트 책임**.

| 항목 | 확정 |
|------|------|
| 플로우 순서 | **정기 → 연차·휴일 정보 → 개별** (2026-08-19 — 구 "연차는 정기와 한 덩어리, 없어요면 미노출" 폐기. `없어요` 경로도 연차·휴일 정보를 거친다) |
| 건너뛰기 | **없음** — 두 화면 모두 거쳐야 입장 (회원가입 온보딩과 반대) |
| **방장·참여자 공통** | 방 진입(방장 `POST /trips` · 참여자 `POST /trips/join`)=`SCHEDULE_PENDING` → 일정 플로우 → `POST .../activate`=`ACTIVE` (#39 · #114) |
| **prefill** | **프론트 UX** — 백엔드 계약·#22 미정 **아님** |
| 재입장 | `ACTIVE` → 방 상세 (BR-USER-010). `SCHEDULE_PENDING` → 일정 플로우. 미가입 참여자 → 플로우 |

### D-JOIN-MEMBER · API (확정 — 2026-07-21 · #39 amend · **2026-08-19 `#114` amend**)

| 역할 | 흐름 | `trip_member` |
|------|------|---------------|
| **방장** | 방 생성 폼 → **`POST /trips`** → 일정 플로우 → **`POST .../activate`** | create 시 **`SCHEDULE_PENDING`** → activate 시 **`ACTIVE`** |
| **참여자** | 링크 → **`POST /api/v1/trips/join`** → 일정 플로우 → **`POST .../activate`** | join 시 **`SCHEDULE_PENDING`** → activate 시 **`ACTIVE`** |

**멤버십 API:**
- 참여자 가입: `POST /api/v1/trips/join` (`{ inviteCode }`) — 이미 멤버면 새 row 없이 현재 `myMemberStatus`를 반환(멱등)
- 일정 확인 완료(방장·참여자 공통): `POST /api/v1/trips/{tripId}/activate`
- **구 `POST .../schedule/submit` — 삭제·재사용 금지.**

**`SCHEDULE_PENDING`:** 방 진입 직후 상태 — **방장 create·참여자 join 모두** 이 값으로 시작한다(2026-08-19 `#114`. 이전에는 방장 전용이었고 멤버 신규 INSERT는 `ACTIVE`만이었다).
**초대 공유:** 방장 ∧ **`ACTIVE`(방 입장 후)** 만 — SCHEDULE_PENDING은 입장 불가 → 공유 불가. create·join 응답에 `inviteCode` 없음 ([`kakao-invite-share.md`](kakao-invite-share.md) S-1·S-2).

**정원 보장:** `POST /trips/join`이 `trip` 행을 잠근 채 카운트+INSERT를 한 트랜잭션에서 처리해 동시 요청에도 정원을 넘기지 않는다. 자리는 `SCHEDULE_PENDING`부터 차지하며, 일정 확인을 끝내지 않은 사람의 자리는 자동 회수하지 않고 **방장 내보내기로만 해제**된다(2026-08-19 `#122` — 나가기도 입장(`ACTIVE`) 후에만 가능). 초과 시 409 `TRIP_MEMBER_FULL`. ~~hold → #35~~ (2026-08-19 `#114`로 폐지).

### D-MEMBER-FILL: 모집 현황 (확정 — 2026-07-28 amend)

| 필드 | 의미 |
|------|------|
| `memberCount` | 정원 |
| `activeMemberCount` | `ACTIVE` 수 |
| `memberFillRate`(응답률) | `activeMemberCount / memberCount` (구 공식 `joinedMemberCount / memberCount`에서 전환) |

`joinedMemberCount`(참여 인원, `trip_member` 수)는 API 미노출로 전환 — 필요 시 `membersPreview.size() + membersPreviewOverflow`(또는 멤버 목록 `members` 배열 크기)로 유도. 상세: [`trip-member-fill-rate-refactor.md`](trip-member-fill-rate-refactor.md)

### D-SPARSE vs `is_all_free` (~~확정 — A안~~ → `is_all_free` 폐지, **omit=POSSIBLE 해석만 유효**)

| | `is_all_free` | omit=POSSIBLE |
|--|---------------|---------------|
| 층 | **입장 게이트** | **입장 후** 달력/추천 |
| 관계 | **별개** — 동일 개념으로 합치지 않음 |

### D-HIDDEN-7: OpenAPI 공개 (확정 — 단계적 C안)

상단 [OpenAPI 숨김 · 단계적 공개 (D-HIDDEN-7)](#openapi-숨김--단계적-공개-d-hidden-7) 표 참조 — 여기서 중복 서술하지 않는다. 방장·참여자 라우팅은 위 D-JOIN-TRIP-FLOW가 SSOT.

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
| `isGoogleCalendarConnected` | **유지** — OAuth 연동 SSOT (Google Calendar API MVP 출시) |
| `isScheduleRegistered` | **제거** — 현행 대체값은 `hasCompletedPreSchedule` 파생 (D-BR006-C. 당시 대체값이던 `hasPreSchedule`은 2026-08-19 삭제) |
| `isOptionalOnboardingCompleted` | **제거** — 재접속 SSOT = 이름 완료 (D-REENTRY-2) |
| `PATCH /users/onboarding` | **삭제** |

**`hasCompletedPreSchedule` (D-BR006-C 확정, 2026-08-19 개정):** login/me 응답 필드. `UserSummaryService`가 `users.vacation_apply_period`의 존재 여부를 **조회 시 파생**한다.

### D-BR006-C: `hasCompletedPreSchedule` 파생 (확정 — 2026-08-19 전면 개정)

| 항목 | 확정 |
|------|------|
| SSOT | `users.vacation_apply_period` **한 컬럼** |
| API | `UserSummaryResponse.hasCompletedPreSchedule` — login · `GET /auth/me` · profile PATCH · google-calendar 연결/해제 |
| 파생식 | `vacation_apply_period IS NOT NULL` — 정기·개별 일정 row 수는 **읽지 않는다** |
| 되돌아가는 경로 | **탈퇴 후 재가입뿐**. 일정 삭제·연차 재저장으로는 `false`가 되지 않는다 |
| DB 컬럼 | 파생 필드라 **컬럼 없음**. 마커 자체는 기존 연차 컬럼을 그대로 쓴다(신규 컬럼 없음) |

**구 `hasRegularSchedule`·`hasPreSchedule` (2026-08-19 삭제):** 각각 "정기 EXISTS", "정기 OR 개별 EXISTS"를 노출하던 파생 필드였다. `hasRegularSchedule`은 정기 유무 2분기 판정 전용이었는데 그 분기가 사라져 존재 이유를 잃었고, `hasPreSchedule`은 원래 용도(전역 입장 게이트 `canEnterRoom`)가 `#113`에서 삭제된 뒤 **서버 어디에서도 소비되지 않는 값**으로 남아 있었다. 비슷한 boolean을 여럿 노출하면 프론트가 조합식을 만드는 표면이 생긴다(QA 이슈 1·2의 원인) — 하나만 남긴다.

### D-SPARSE-3: 달력 omit(빈 날) — 방 입장 후 해석

| 항목 | 확정 |
|------|------|
| 전제 | **이미 여행방에 입장한 이후** (join·D-JOIN-ENTRY/TRIP-FLOW 통과 후) |
| omit day | regular·personal 모두 없어 calendar에서 **날짜가 생략된 날** = **하루 종일 가능 (`POSSIBLE`)** |
| 범위 | 추천(#13)·그룹 달력·trip 기간 정기+개별 합산 계산에 동일 적용 (방 **밖** join 게이트와 별개) |

> **전부 free vs omit=POSSIBLE:** **별개 유지 (A안)**. `is_all_free` = 입장 게이트 · omit = 입장 후 달력/추천. 합치지 않음 — D-SPARSE vs `is_all_free` 절.

### D-BR006-5: 정기·개별 일정 독립 (BR-USER-006 게이트 삭제)

| 항목 | 확정 |
|------|------|
| **BR-USER-006** | **삭제** — “정기 일정 먼저 등록해야 personal/calendar 수정 가능” **403 `REGULAR_SCHEDULE_REQUIRED` 폐기** |
| 관계 | `regular_schedule`과 `personal_schedule`은 **서로 영향 없는 별도 일정** |
| personal-only | personal만 있어도 입장 조건 **2** 충족 (D-JOIN-ENTRY) |
| 구현 | `ScheduleService.requireRegularScheduleRegistered` 호출 제거 · personal GET/PATCH/calendar **regular 게이트 없음** |

**`isScheduleRegistered` — D-BR006-C 확정 (2026-07-20):** DB 컬럼 **제거**. 당시 대체값은 `hasPreSchedule`(조회 시 `EXISTS(regular) OR EXISTS(personal)` 파생)이었으나, **2026-08-19 그 필드도 삭제되고 `hasCompletedPreSchedule`로 대체됐다** — 현행 계약은 위 D-BR006-C 표.

### D-PERSONAL-6: 개별 일정 수정 — 나비효과 없음

| 항목 | 확정 |
|------|------|
| 범위 | 여행방 **참여 중** 마이페이지·외부 달력 등에서 `personal_schedule` 수정 |
| 동작 | **아무 변화 없음** — `ACTIVE` **유지**(되돌림 없음), 알림 **없음**, 방 UI **별도 갱신 유도 없음** |
| 데이터 | BR-USER-008 — User 전역 일정, 참여 중 모든 trip에 **동일 데이터** 반영 (조회 시 최신 합친 값) |

### D-SUBMIT-2: submit 폐기 (확정) — 상단 D-TRIP-CONFIRM · D-SUBMIT-2 절 참조

| 항목 | 확정 |
|------|------|
| submit | **폐기** |
| ACTIVE | Skip/확인 완료 = 응답 완료 |
| 참여율 | ACTIVE / 멤버 수 |

### D-HIDDEN-7: OpenAPI 단계적 공개 (확정)

상단 D-HIDDEN-7 표 참조. submit **삭제**. personal/calendar → #22 PR에서 해제. schedule-calendar → 후속.

### D-AUTH-8: 여행방 권한 — #22 범위 밖 (#24 완료)

**초대 링크(join)와 무관.** [`decisions/008`](../../decisions/008-trip-authorization-guard.md) · Issue **#24** — **이미 구현 완료**.

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
| [`user-onboarding.md`](../user/user-onboarding.md) | 사전 일정 skip Approved | ② 사전 일정 단계·`isScheduleRegistered` → `[미정]` |
| [`schedule-unified.md`](../user-schedule/schedule-unified.md) | BR-USER-006 personal 게이트 | 게이트 정책 → `[미정]` · API 표에 Hidden 표기 |
| [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md) | sparse omit 확정 | sparse = 가능 vs 미입력 → `[미정]` (A10 등) |
| [`trip-recommendation.md`](trip-recommendation.md) | ACTIVE·uncertain 참조 | 입력 전제·TBD 해석 → 본 스펙 확정 후 amend |
| [`auth-social-login.md`](../auth/auth-social-login.md) | login 응답에 onboarding boolean | boolean 의미 주석 → `[미정]` 링크 |
| [`docs/specs/README.md`](../README.md) | MVP 출시 인덱스 | MVP 출시 항목 + 이슈 매핑 추가 |

### B. 결정 (`docs/decisions/`)

| 파일 | 조치 |
|------|------|
| [`007-user-profile-onboarding.md`](../../decisions/007-user-profile-onboarding.md) | `isScheduleRegistered`·skip → `[미정]` amend · 본 스펙 링크 |
| [`008-trip-authorization-guard.md`](../../decisions/008-trip-authorization-guard.md) | `@TripMemberOnly`/`@TripOwnerOnly` + Interceptor 권한 설계안 (제안) — submit·members 권한과 함께 확정 |

### C. 제품 (`docs/product/`)

| 파일 | 조치 |
|------|------|
| [`release-milestones.md`](../../product/release-milestones.md) | MVP 출시에 본 재설계 항목 추가 |
| [`mvp.md`](../../product/mvp.md) | “일정 응답·참여 완료” UX → `[미정]` 또는 MVP 출시 선행 |
| [`prd.md`](../../product/prd.md) | BR-USER-006/007 행 → `[미정]` |
| [`glossary.md`](../../product/glossary.md) | “참여자” 정의(ACTIVE) → `[미정]` |
| [`business-rules/user.md`](../../product/business-rules/user.md) | BR-USER-006·007 → `[미정]` |
| [`business-rules/notification.md`](../../product/business-rules/notification.md) | BR-NOTI-001/002 트리거 → `[미정]` |
| [`flows/trip-create-join-guide.md`](../../product/flows/trip-create-join-guide.md) | 참여 플로우 4~7단계 submit → `[미정]` |
| [`flows/schedule-edit.md`](../../product/flows/schedule-edit.md) | submit 분기 → `[미정]` |
| [`flows/trip-confirm.md`](../../product/flows/trip-confirm.md) | “1명 이상 제출” 전제 → `[미정]` |
| [`design/figma-wireframe-v1.md`](../../product/design/figma-wireframe-v1.md) | ACTIVE·isScheduleRegistered → `[미정]` |

### D. 아키텍처

| 파일 | 조치 |
|------|------|
| [`architecture/erd.md`](../../architecture/erd.md) | `trip_member.status` **SCHEDULE_PENDING\|ACTIVE** (#39) · BR-USER-007 |
| [`docs/README.md`](../README.md) | specs 인덱스에 본 스펙 추가 |

### E. Java — Controller

| 파일 | 조치 |
|------|------|
| [`TripController.java`](../../../src/main/java/com/tripfit/tripfit/trip/controller/TripController.java) | **`submitSchedule` 삭제** · **`activate` 추가** (#39) |
| [`UserScheduleController.java`](../../../src/main/java/com/tripfit/tripfit/user/schedule/controller/UserScheduleController.java) | personal/calendar — **1단계** Hidden 해제 |
| [`TripMemberController.java`](../../../src/main/java/com/tripfit/tripfit/trip/membership/controller/TripMemberController.java) | schedule-calendar — **2단계** |

### F. Java — Service / Domain

| 파일 | 검토 항목 |
|------|-----------|
| [`TripCommandService`](../../../src/main/java/com/tripfit/tripfit/trip/service/TripCommandService.java) / [`TripJoinService`](../../../src/main/java/com/tripfit/tripfit/trip/membership/service/TripJoinService.java) | create=`SCHEDULE_PENDING` · activate/join=`ACTIVE` (#39) · `is_all_free`는 activate/join |
| [`TripMemberStatus.java`](../../../src/main/java/com/tripfit/tripfit/trip/membership/domain/TripMemberStatus.java) | `SCHEDULE_PENDING` · `ACTIVE` (#39). 멤버 INSERT는 ACTIVE만 |
| [`User.java`](../../../src/main/java/com/tripfit/tripfit/user/domain/User.java) | ~~`is_all_free` 컬럼~~ (`#113` 삭제) |
| [`ScheduleService.java`](../../../src/main/java/com/tripfit/tripfit/user/schedule/service/ScheduleService.java) | CLEAR/추가 ↔ `is_all_free` 전이 |

### G. 테스트

| 파일 | 조치 |
|------|------|
| `TripControllerTest` / `TripServiceTest` | submit 제거 · create=`SCHEDULE_PENDING` · activate (#39) |
| `User*` / `Schedule*` | ~~`is_all_free` · canEnterRoom~~ (`#113` 삭제) |

### H. GitHub

| 대상 | 조치 |
|------|------|
| **#22** | schedule-participation-onboarding | Open · MVP 출시 |
| **#12** | submit·D1·schedule-calendar Must Have 제거 → #22 deferred |
| **#13** | ACTIVE·sparse 입력 전제 → #22 선행 |
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
- [x] **같은 trip 재입장 UX** — `ACTIVE` → 직행 (BR-USER-010)
- [x] **D-TRIP-CONFIRM** — `trip_member.status=ACTIVE`
- [x] **submit 폐기** — Skip/확인 = ACTIVE 한 이벤트
- [x] **omit = POSSIBLE (입장 후)** — D-SPARSE-3
- [x] **BR-USER-006 정기 선행 게이트 삭제** — D-BR006-5
- [x] **personal 수정 나비효과 없음** — D-PERSONAL-6
- [x] **Hidden API** — D-HIDDEN-7
- [x] **trip 권한 008** — D-AUTH-8

### 미정

- [x] **`POST /trips/join`(멤버) + `activate`(방장)** · submit **삭제** · create=`SCHEDULE_PENDING` (#39)
- [x] **prefill** — 프론트 영역 (백엔드 미정 제외)
- [x] **omit ≠ is_all_free** (A)
- [x] **Hidden 단계적 공개** (C)
- [x] **memberFillRate** · late-join · hold→#35
- [x] **D-BR006-C** · is_all_free · CLEAR · Skip · 방장 · 재입장 UX

---

## 완료 기준 (본 이슈)

- [x] D-JOIN-ENTRY · CLEAR · TRIP-FLOW · ACTIVE · submit 폐기 · **#39 activate** 문서화
- [x] **멤버십 path** — `POST /trips` · `POST /trips/join` · `POST .../activate` · submit **삭제**
- [x] 코드: submit 제거 · create=`SCHEDULE_PENDING` · activate/join=`ACTIVE` (#39)
- [x] 코드: `is_all_free` · canEnterRoom · Hidden 1단계 해제
- [x] A~H 인벤토리 반영 · trip-room/#22 정합 · personal `deletedDates`
- [x] `./gradlew test` 통과

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-19 | **최초/갱신 입력 판정 도입 — 정기 일정 유무 2분기 폐기.** ① 분기 판정을 `사전 신청일`(`users.vacation_apply_period`) 저장 여부 하나로 단일화 ② `없어요` 경로에서도 연차·휴일 정보를 묻고, 남아 있던 정기 일정은 `DELETE /users/schedule/regular`로 즉시 전체 삭제 ③ 갱신 입력은 `일정 변경이 있나요?`(안내용, 분기 없음)로 시작 ④ `activate`에 403 `PRE_SCHEDULE_REQUIRED` 게이트 추가 ⑤ `hasRegularSchedule`·`hasPreSchedule` 삭제 → `hasCompletedPreSchedule` 신설 ⑥ 회원가입 온보딩에서 사전 일정 단계 제거·건너뛰기 미노출. SSOT: [`../user-schedule/pre-schedule-entry-flow.md`](../user-schedule/pre-schedule-entry-flow.md) |
| 2026-08-18 | **`#113` amend — 전역 입장 게이트 폐지.** D-JOIN-ENTRY 3조건·`canEnterRoom`·`user.is_all_free`·`SCHEDULE_ENTRY_REQUIRED`·D-JOIN-CLEAR 전이 규칙을 모두 폐지하고, 방 입장 판정을 **방별 `trip_member.status = ACTIVE`** 하나로 단일화. BR-USER-006·007 개정 · BR-USER-011 삭제 동반. 동작 변화 없음(전역 게이트는 `ACTIVE` 멤버에게 항상 참이라 이미 아무것도 막지 못했음). SSOT: [`trip-join-schedule-gate.md`](trip-join-schedule-gate.md) J-7 |
| 2026-08-17 | **D-BR006-C amend — `hasRegularSchedule` 필드 추가** — 요약 응답(login · `GET /auth/me` · profile PATCH)에 정기 일정 EXISTS만 반영하는 파생 boolean을 신설. 계기: 요약 응답에 정기 유무를 알려주는 값이 없어 프론트가 `hasPreSchedule`(정기 OR 개별)을 일정 확인 플로우 분기에 전용해, 개별 일정만 등록한 사용자가 빈 정기 화면에 갇히는 QA 이슈가 발생. D-JOIN-TRIP-FLOW 분기 판정 근거에 이 필드를 추가(기존 `GET /users/schedule/regular` 길이 판정도 계속 유효) |
| 2026-08-16 | **D-JOIN-CLEAR stale 정정** — "Skip인데 이미 0행 → 방장 `POST /trips` 시 `is_all_free=true`"는 #39 amend(create는 markAllFree 안 함) 이후 갱신되지 않은 문구였다. 실제 구현은 `TripCommandService.activateMembership`이 설정하므로 **방장은 `activate` 시점**으로 정정 (같은 문서 D-JOIN-TRIP-FLOW 백엔드 가드 2·BR-USER-007과 이미 일치) |
| 2026-08-16 | **D-JOIN-TRIP-FLOW amend (Figma Wireframe v1 대조)** — ① 방 입장 플로우는 **건너뛰기 버튼 없음**(회원가입 온보딩만 건너뛰기 가능), 구 "Skip" 표현은 "확인 후 변경 없이 통과"로 재정의 ② 정기 일정 **보유 여부 2분기**("사전 일정 입력이 필요해요" vs "입력하신 일정을 확인해주세요") 명문화 — "정기 일정이 있나요?"는 정기 0건인 사용자에게만 노출 ③ **분기 판정에 `hasPreSchedule` 사용 금지**(정기 OR 개별 파생값이라 개별만 있는 사용자를 오분기) → `GET /users/schedule/regular` 길이로 판정 ④ 연차는 정기 일정과 한 덩어리(정기 미입력 시 미노출) ⑤ 개별 일정 화면은 손댄 날짜만 PATCH(전 기간 재전송 시 정기 유래 값이 개별 오버라이드로 굳어 복구 불가) |
| 2026-07-30 | **O1.4 정합 반영** — `schedule-slot-override.md` O1.4가 개별 일정 삭제 경로를 전면 제거함에 따라 D-JOIN-CLEAR·"일정 수정 API 형태" 절 갱신: 개별 일정으로 `is_all_free`를 켜는 경로는 이제 없음(정기 삭제 + 개별 미등록 조합만 유효), "개인 CLEAR" 삭제 시맨틱 문구 제거. `canEnterRoom`이 OR 조건이라 개별 일정이 있으면 애초에 `is_all_free` 전환이 불필요하다는 점을 121행 아래 `[미정]` 노트로 남김(기존 데이터를 지우지 않고 명시적으로 "전부 free" 선언하는 UX는 추후 검토 대상, [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)) |
| 2026-08-05 | **Amend** — personal `deletedDates` 필드 제거. `items`에서 슬롯 3개 모두 POSSIBLE·uncertain=false인 항목을 삭제(CLEAR) 신호로 통합 (상세: [`schedule-unified.md`](../user-schedule/schedule-unified.md) 변경 이력) |
| 2026-07-28 | **Amend** — 방장 멤버십 전환 API `POST .../schedule/confirm` → `POST .../activate`로 rename(`TripStatus.CONFIRMED` 등 "일정 확정" 개념과 이름 혼동 해소), `SCHEDULE_CONFIRM_REQUIRED` → `SCHEDULE_ACTIVATION_REQUIRED`. activate·join 자신에게는 도달 불가능했던 `SCHEDULE_ENTRY_REQUIRED` 문서·검증 제거(상세: [`trip-room-api.md`](trip-room-api.md) 변경 이력) |
| 2026-07-28 | 온보딩 이름 API 경로 리네이밍 반영 — `PATCH /users/profile` → `PATCH /users/onboarding/name` (`user-onboarding.md` 변경 이력 참고) |
| 2026-07-28 | **Amend (#60)** — D-MEMBER-FILL 공식 전환(`memberFillRate = activeMemberCount / memberCount`), `joinedMemberCount` API 미노출. 상세: [`trip-member-fill-rate-refactor.md`](trip-member-fill-rate-refactor.md) |
| 2026-07-21 | **#39 amend** — 방장 SCHEDULE_PENDING→confirm · D-JOIN-MEMBER/TRIP-FLOW · 인벤토리 stale 정리 |
| 2026-07-21 | **Amend** — personal `deletedDates` CLEAR 경로 · trip-room stale 정합 |
| 2026-07-21 | **Amend** — late-join · 방장 A · 단일 가입 API · `memberFillRate` · 정원 hold #35 |
| 2026-07-20 | **Amend** — Skip=`ACTIVE` 한 이벤트 · **submit 폐기** · D-TRIP-CONFIRM=`ACTIVE` |
| 2026-07-20 | **Amend** — `is_all_free` 컬럼/API · 전이 · Skip 가드 · 방장 생성 플로우 · 재입장 UX · D-TRIP-CONFIRM `[미정]` |
| 2026-07-20 | **Amend** — 전부 free=**User 전역** · CLEAR=어디서든 0행 · TRIP-FLOW=수정/Skip(전역이어도 프리패스 금지) |
| 2026-07-20 | **Amend** — D-JOIN-ENTRY(입장 3조건) · D-JOIN-CLEAR · D-JOIN-TRIP-FLOW. **구 D-JOIN-3/4 폐기** |
| 2026-07-20 | D-BR006-C 구현 — `hasPreSchedule` 파생 · onboarding 필드/API 제거 · BR-USER-006 게이트 삭제 |
| 2026-07-20 | D-ONBOARD-4 · D-SPARSE-3 · D-BR006-5 · D-PERSONAL-6 · D-HIDDEN-7 · D-AUTH-8 · D-SUBMIT-2 `[미정]` |
| 2026-07-20 | **Draft** — D-NAME-1 · D-REENTRY-2 · (구) D-JOIN-3/4 · 007 amend |
| 2026-07-17 | `[미정]` 에스컬레이션 · OpenAPI Hidden · 수정 인벤토리 초안 |
| 2026-07-16 | 여행방 권한 가드 설계안([decisions/008](../../decisions/008-trip-authorization-guard.md)) 추가 · #22 범위 포함 |
