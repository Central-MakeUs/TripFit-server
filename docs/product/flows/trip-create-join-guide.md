# 여행방 생성·참여 — 플로우·정책·시나리오

> ## ⚠️ 2026-08-18 amend (`#113`) — 전역 입장 게이트 폐지
>
> 이 문서 곳곳의 **`canEnterRoom` · `is_all_free` · `SCHEDULE_ENTRY_REQUIRED`** 는 모두 삭제됐다. 방 입장 판정은 이제 **그 방의 `trip_member.status = ACTIVE` 하나**이고, 미충족 시 `SCHEDULE_ACTIVATION_REQUIRED`(403)만 나온다. 아래에서 전역 조건을 언급하는 대목은 이력으로 읽어라.
>
> **동작은 그대로다** — 일정을 하나도 넣지 않은 사용자도 전과 똑같이 방에 들어갈 수 있고, 달력·추천에서 "전부 가능"으로 계산된다. 없어진 것은 그 사실을 별도 컬럼에 적어두고 다시 검사하던 층뿐이다.
>
> SSOT: [`../../specs/trip/trip-join-schedule-gate.md`](../../specs/trip/trip-join-schedule-gate.md) J-7

> ## ⚠️ 2026-08-19 amend — ① 방장·참여자 **2단계 통일**(`#114`) ② 사전 일정 입력 **최초/갱신** 2분기 ③ `activate` 입력 완료 게이트
>
> 1. **참여자도 방장과 같은 2단계다.** `POST /trips/join`이 일정 플로우 **맨 앞**으로 옮겨져 `SCHEDULE_PENDING` 멤버를 만들고, 플로우를 마친 뒤 `POST .../activate`로 `ACTIVE`가 된다. 구 "일정 먼저 → join = 즉시 ACTIVE"·"멤버에게 중간 상태 없음"은 **폐지**됐고, Redis 정원 hold(`#35`)도 DB 비관적 락으로 대체·삭제됐다. `last_activity_at` touch도 `join`이 아니라 `activate`에서만 일어난다(J-9).
> 2. **일정 플로우는 「정기 일정 유무」가 아니라 「최초 입력 / 갱신 입력」으로 갈린다.** 판정은 `hasCompletedPreSchedule`(= `users.vacation_apply_period`, 연차·휴일 정보의 **사전 신청일** 저장 여부) 하나이며, 두 갈래 모두 **연차·휴일 정보 화면을 지난다.**
> 3. **`activate`는 사전 일정 입력을 한 번도 끝내지 않은 사용자를 403 `PRE_SCHEDULE_REQUIRED`로 거부한다.** 정기·개별 일정이 0건인 것은 거부 사유가 아니다.
>
> SSOT: [`../../specs/user-schedule/pre-schedule-entry-flow.md`](../../specs/user-schedule/pre-schedule-entry-flow.md) · [`../../specs/trip/trip-join-schedule-gate.md`](../../specs/trip/trip-join-schedule-gate.md) J-1·J-4·J-9

> **상태: Approved/Implemented** (대안 A 채택, [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)). 생성·참여 플로우의 SSOT 가이드.
> 계약: [`trip-room-api.md`](../../specs/trip/trip-room-api.md) · [`schedule-participation-onboarding.md`](../../specs/trip/schedule-participation-onboarding.md)
> 설계 대안 검토(A~D) 이력은 #39 PR 참고 — 비교 문서는 채택 후 삭제됨
>
> **이 문서는 정책·엣지 케이스 시나리오용이다.** 프론트 구현이 그대로 따를 명령형 API 호출 순서·에러 매핑은 [`fe-context/trip/trip-room-create-join.md`](../fe-context/trip/trip-room-create-join.md)가 SSOT — 아래 단계 설명과 그 문서의 STEP 표가 같은 사실을 각자 갱신하지 않도록, 호출 순서가 바뀌면 그 문서를 먼저 고치고 여기는 필요한 만큼만 따라간다.

---

## 빠른 요약

**방장:**

1. 홈에서 「여행방 신규 생성하기」→ 방 생성 폼(이름·기간·일수·인원·선택 여행지)
2. `POST /trips` → OWNER **`SCHEDULE_PENDING`**. DB에 invite_code 발급하나 **응답에 inviteCode 없음**
3. **사전 일정 입력 플로우** — 최초 입력이면 `정기 일정이 있나요?`, 갱신 입력이면 `일정 변경이 있나요?`부터. 두 갈래 모두 **정기 → 연차·휴일 정보 → 개별** 순서 · 이미 일정이 있어도 강제 · **건너뛰기 없음**
4. `POST /trips/{tripId}/activate` → **`ACTIVE`**
5. 방 상세(`inviteCode`) · **초대 공유** (방장·ACTIVE 이후만)

사전 조건: 로그인 + 프로필 이름(BR-USER-001). 예외: activate 전 이탈 → 재진입 시 일정 플로우, 상세 API는 `SCHEDULE_ACTIVATION_REQUIRED`.

**참여자(멤버):**

1. 초대 링크 → `POST /api/v1/trips/join` `{ inviteCode }` → INSERT **`SCHEDULE_PENDING`** (응답에 `inviteCode` 없음)
2. **사전 일정 입력 플로우** (최초/갱신 2분기 · 정기 → 연차·휴일 정보 → 개별 · 건너뛰기 없음)
3. `POST /api/v1/trips/{tripId}/activate` → **`ACTIVE`** — 방장과 **같은 2단계** (`#114`)
4. 정원 full → 409(락으로 동시 요청까지 보장) · 이미 멤버면 `join` 멱등(현재 `myMemberStatus` 반환) · 사전 일정 미완료로 `activate` 실패 → 403 `PRE_SCHEDULE_REQUIRED`

모집 현황(응답률): `memberFillRate = activeMemberCount / memberCount`(구 공식 `joinedMemberCount / memberCount`에서 전환, `joinedMemberCount`는 API 미노출 — [`trip-member-fill-rate-refactor.md`](../../specs/trip/trip-member-fill-rate-refactor.md)). 사전 조건: 소셜 로그인 필수(BR-USER-002) + 이름 완료. 상세·정책·시나리오는 아래 1~5절.

---

## 한 줄 요약

TripFit에서 “방에 들어간다”는 것은 **로그인 + 이름 완료** 후:

- **방장:** `POST /trips`(`SCHEDULE_PENDING`) → 일정 플로우 → `activate`(`ACTIVE`) **이후에야** 방 안·초대 공유
- **참여자:** `POST /trips/join`(`SCHEDULE_PENDING`) → 일정 플로우 → `activate`(`ACTIVE`) **이후에야** 방 안 — 방장과 동일 (`#114`, 2026-08-19)

**해당 trip에서 `ACTIVE`** 인지 하나만 본다 (구 전역 `canEnterRoom` 조건은 삭제).

> **프론트 주의:** create 응답에 `inviteCode` 없음. 홈에 SCHEDULE_PENDING 카드가 보여도 상세/공유로 바로 가지 말고 activate 플로우로.
> 용어·오해표: [`glossary.md`](../glossary.md) · 스펙 필독: [`trip-room-api.md`](../../specs/trip/trip-room-api.md)

---

## 핵심 개념

| 용어 | 의미 |
|------|------|
| **여행방 (`trip`)** | 조율 단위. 이름·희망 기간·일수·정원·초대코드 등 |
| **방장 (`OWNER`)** | 방을 만든 사람. **생성 시** 멤버 INSERT |
| **참여자 (`MEMBER`)** | `join` 시 `SCHEDULE_PENDING`으로 등록 → `activate` 후 `ACTIVE` (`#114`). 링크만으로는 미등록 |
| **초대 코드** | 6자 Crockford Base32. 링크 `https://tripfit.online/room/{inviteCode}` |
| **일정 데이터** | User **전역** (`regular` + `personal`). 방마다 복사하지 않음 (BR-USER-008) |
| ~~**`is_all_free`** · **`canEnterRoom`**~~ | 2026-08-18 `#113`으로 삭제 — 방 입장 판정은 방별 `ACTIVE` |

**`SCHEDULE_PENDING`/`ACTIVE` 정의는 [`glossary.md`](../glossary.md)가 SSOT** — 방장 전용 create 직후 상태(SCHEDULE_PENDING) vs 입장·공유 가능 상태(ACTIVE), 헷갈리기 쉬운 점 표 포함. 여기서 중복 정의하지 않는다.

---

## 1. 방 생성 플로우 (방장)

### 사전 조건

1. 소셜 로그인 (카카오 / 구글 / 애플)
2. 프로필 **이름 완료** (BR-USER-001) — 없으면 `403 PROFILE_NAME_REQUIRED`
3. (선택) 첫 가입 세션의 **Google 캘린더 연동**은 건너뛰기 가능 — 사전 일정 입력은 회원가입에 없다(2026-08-19)

### 단계 (제품 UX → API)

```text
홈 「방 생성」
  → [방 생성 폼] 이름·기간·일수·인원·(선택)여행지
  → POST /api/v1/trips
       → trip + OWNER + SCHEDULE_PENDING + inviteCode
  → [최초: "정기 일정이 있나요?" / 갱신: "일정 변경이 있나요?"]
  → [정기 일정] → [연차·휴일 정보] → [개별 일정]  (수정하면 patch · 건너뛰기 버튼 없음)
       ※ 이미 일정이 있어도 이 플로우를 보여 줌 (강제)
       ※ 최초 입력에서 "없어요"면 정기 화면을 건너뛰고 DELETE /users/schedule/regular 즉시 호출
  → POST /api/v1/trips/{tripId}/activate
       → SCHEDULE_PENDING → ACTIVE
  → 방 상세 (입장 완료)
```

| 단계 | 하는 일 |
|------|---------|
| 생성 폼 | 이름 ≤15자, 기간(생성 후 불변), `durationNights`+`durationDays`(또는 미정), 인원 **1~10**, destination 선택 |
| `POST /trips` | `trip`(`ONGOING`) + owner **`SCHEDULE_PENDING`** + DB에 6자 `invite_code` 발급. **응답에 inviteCode 미포함**(입장 전). **아직 방 안 입장·공유 아님** |
| 정기→연차→개별 | **이 방용 확인 플로우**. 전역 일정이 있어도 **매번** 노출. 최초/갱신은 `hasCompletedPreSchedule`로 갈린다 |
| 수정 | 정기 CRUD / 개별 bulk upsert — User 전역 |
| 변경 없이 통과 | 서버는 일정 **건수**를 보지 않음. 단 **사전 신청일이 한 번도 저장되지 않았으면** activate가 403 `PRE_SCHEDULE_REQUIRED` (2026-08-19) |
| `activate` | `ACTIVE` 전환 · 이후 상세·멤버·달력 API 허용 |

### 이탈·재진입 (방장)

- create 직후~activate 전: **member(`SCHEDULE_PENDING`)이지만 여행방 입장 불가**
- 앱 종료 후 같은 방 진입 시도 → 다시 **일정 플로우(2~3)** 로보냄
- 홈 목록에 방이 보여도 탭 시 상세가 아니라 확인 플로우

### 생성 시 서버가 만드는 것

- `trip.status = ONGOING`
- `invite_code` UNIQUE 6자
- 방장 `role=OWNER`, `status=SCHEDULE_PENDING` (정원 카운트에는 포함)

### 생성·확인 후

- **`ACTIVE` 이후에만** 방 상세·초대 공유·방 안 활동 (`GET /trips/{id}`의 `inviteCode` 사용). **SCHEDULE_PENDING(activate 전)에는 공유 불가** — 방 입장 자체가 막힘
- 인원 가득·종료 시 공유 UI 비노출 + join 409 (D8) — 현행과 동일

---

## 2. 참여 플로우 (참여자)

### 사전 조건

- 소셜 로그인 필수 — **비회원 참여 없음** (BR-USER-002)
- 이름 완료 필수

### 단계

```text
초대 링크 (…/room/{inviteCode})
  → (미로그인) 로그인·이름
  → POST /api/v1/trips/join { "inviteCode": "A2B3C4" }
       → MEMBER + SCHEDULE_PENDING (정원 1자리 차지 · 응답에 inviteCode 없음)
  → [최초: "정기 일정이 있나요?" / 갱신: "일정 변경이 있나요?"]
  → [정기] → [연차·휴일 정보] → [개별]  (건너뛰기 없음)
  → POST /api/v1/trips/{tripId}/activate
       → SCHEDULE_PENDING → ACTIVE
  → 방 상세
```

| 상황 | 결과 |
|------|------|
| 일정 미완료·이탈 | 멤버 row는 **`SCHEDULE_PENDING`으로 남는다**(정원 1자리 유지, 자동 회수 없음 — 방 나가기로만 해제). 재진입 시 일정 플로우부터 |
| 처음 join 성공 | INSERT `SCHEDULE_PENDING` · `last_activity_at` **touch 안 함**(touch는 `activate` 한 곳 — J-9) |
| 이미 멤버인 상태로 join 재호출 | 멱등 — 새 row·이벤트 없이 현재 `myMemberStatus` 반환, 그 값으로 라우팅 |
| 이미 `ACTIVE` 멤버 | 방 상세 직행 (BR-USER-010) |
| 변경 없이 통과 + 일정 row 0 | activate 그대로 통과 — 단 **사전 신청일이 저장돼 있어야** 한다(없으면 403 `PRE_SCHEDULE_REQUIRED`) |

~~멤버에게는 중간 `SCHEDULE_PENDING`를 두지 않는다. 정원 hold는 #35 후속.~~ → **2026-08-19 `#114`로 폐기.** 멤버도 `join` 직후 `SCHEDULE_PENDING`이 되고 `activate`로 `ACTIVE`가 된다. 정원 hold(#35)는 DB 비관적 락으로 대체·삭제됐다.

### 모집 현황 숫자

| 필드 | 의미 |
|------|------|
| `memberCount` | 방장이 정한 정원 |
| `activeMemberCount` | **`ACTIVE`만** 집계 (확인 완료 인원) |
| `memberFillRate`(응답률) | `activeMemberCount / memberCount` |

`joinedMemberCount`(SCHEDULE_PENDING 방장 포함 전체 참여 수)는 **API 미노출** — 필요하면 `membersPreview.size() + membersPreviewOverflow`(또는 멤버 목록 `members` 배열 크기)로 유도.

→ 방장만 SCHEDULE_PENDING인 직후: 참여 인원=1, `active=0` 가능(이 구간엔 `memberFillRate`도 0).

---

## 3. 정책 체크리스트

### A. 이중 게이트

```text
방 안 API 허용 =
  trip_member 존재
  AND status == ACTIVE          // trip별 일정 확인 완료
```

| 상태 | 방 입장 |
|------|---------|
| 비멤버 | ❌ `TRIP_ACCESS_DENIED` (초대 링크로 `join` 전) |
| `SCHEDULE_PENDING` (**방장·참여자 공통**) | ❌ `SCHEDULE_ACTIVATION_REQUIRED` — 일정 플로우 강제 |
| `SCHEDULE_PENDING` + 사전 일정 미입력 | `activate` 자체가 ❌ **403 `PRE_SCHEDULE_REQUIRED`** (2026-08-19) |
| `ACTIVE` | ✅ |

**강제 플로우:** 방장·참여자 모두 신규 방에서는 이미 일정이 있어도 `SCHEDULE_PENDING`이면 일정 UI를 보여 준다.
서버는 status로 상세를 막아 우회를 차단하고, 사전 일정 입력을 한 번도 끝내지 않은 사용자는 `activate` 자체를 거부한다.

**일정이 이미 있어도 신규 trip 프리패스는 없다** — 방장 create 후, 멤버 `join` 후 모두 확인 플로우를 거친다(`#114` 이후 두 경로가 같은 모양이다).

### B. 정원 (D8)

- create/patch: `memberCount` **1~10**
- 신규 join: 삭제되지 않은 전체 멤버 수 `>= memberCount` → `409 TRIP_MEMBER_FULL`
  (`SCHEDULE_PENDING`도 자리를 차지한다 — 방장은 create 직후부터, 참여자는 `join` 직후부터 1자리 사용)
- **정원 보장:** `join`이 `trip` 행을 `SELECT ... FOR UPDATE`로 잠근 채 카운트+INSERT를 한 트랜잭션에서 처리 (`#114` — 구 Redis hold(`#35`)는 삭제)

### C. 방 상태별 join / 수정

| `TripStatus` | 신규 join | 기존 `ACTIVE` 멤버 | 방장 PATCH |
|--------------|-----------|----------------------|------------|
| `ONGOING` | ✅ (정원·기간 OK) | 조회·활동 | ✅ (SCHEDULE_PENDING에게 메타 PATCH 허용 여부는 Open Q) |
| `CONFIRMED` | ❌ | 재접속 OK | ❌ |
| `EXPIRED` | ❌ | 조회 등 | ❌ |

방장 `SCHEDULE_PENDING`의 “재접속” = 일정 확인 플로우 재개 (상세 아님).

### D. 권한

| 행동 | 누가 |
|------|------|
| 방 생성 | 이름 완료한 로그인 유저 |
| 일정 activate | 해당 trip `SCHEDULE_PENDING` 멤버 (**방장·참여자 공통**) — 사전 일정 입력 완료 필요 |
| 메타 수정·삭제 | 방장 (SCHEDULE_PENDING 단계 허용 범위는 Open Q) |
| Pin·상세·그룹 달력 | **`ACTIVE`** |
| 참여자 내보내기 | Nice (#20) |

### E. 초대 코드

- create 시 발급 (SCHEDULE_PENDING 단계에서도 코드는 존재)
- 잘못된 코드 → `404 INVITE_CODE_NOT_FOUND`
- SCHEDULE_PENDING 단계에서 공유 UI 노출 여부 — Open Question

### F. 주요 에러 코드 (요약)

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_INPUT` | 이름·인원·duration 등 |
| 403 | `PROFILE_NAME_REQUIRED` | 이름 미완료 |
| 403 | `SCHEDULE_ACTIVATION_REQUIRED` | `SCHEDULE_PENDING` — 방 안 API |
| 403 | `PRE_SCHEDULE_REQUIRED` | 사전 일정 입력 미완료(사전 신청일 미저장) — `activate` (2026-08-19 **신규**) |
| 403 | `TRIP_FORBIDDEN` / `TRIP_ACCESS_DENIED` | 권한·비참여자 |
| 404 | `TRIP_NOT_FOUND` / `INVITE_CODE_NOT_FOUND` | |
| 409 | `TRIP_MEMBER_FULL` | 정원 가득 (신규 join) |
| 409 | `TRIP_*` | CONFIRMED/EXPIRED 신규 join |
| 409 | `TRIP_NOT_ONGOING` | 비 ONGOING PATCH |

---

## 4. 방장 vs 참여자 비교

| | 방장 | 참여자 |
|--|------|--------|
| 진입 | 홈 「방 생성」 | 초대 링크 |
| 멤버십 생성 시점 | **생성 직후** (`SCHEDULE_PENDING`) | **`join` 직후** (`SCHEDULE_PENDING`) |
| 일정 확인 | 생성 **후** · 입장 **전** 강제 | `join` **후** · 입장 **전** 강제 |
| 완료 API | `POST .../activate` | `POST .../activate` (**동일**) |
| 미완료 이탈 | member이나 **입장 불가** (정원 1자리 유지) | member이나 **입장 불가** (정원 1자리 유지) |
| 최종 status | `ACTIVE` | `ACTIVE` |

---

## 5. 유저 시나리오

### 시나리오 1 — 민수가 제주 방 만들기 (정상)

1. 민수 로그인·이름 완료
2. 「방 생성」→ 폼만 작성 (`제주 3박4일`, 8/1~8/10, 4일, 인원 6)
3. `POST /trips` → OWNER **SCHEDULE_PENDING**, invite `K7M2N9` · 아직 상세 입장 아님
4. 사전 일정 입력 플로우(최초/갱신 · 정기→연차→개별, 이미 일정이 있어도 화면 강제) → 필요 시 patch
5. `activate` → **ACTIVE** → 방 상세
6. 링크 공유 · 홈에 방 노출 · 모집 `joined=1`, `responded=1`

### 시나리오 2 — 민수가 생성 직후 앱 종료 (핵심 케이스)

1. `POST /trips`까지 완료 (`SCHEDULE_PENDING`)
2. 일정 플로우 중 이탈
3. 재실행 → 홈에서 방 탭 또는 딥링크
4. **상세 불가** · 일정 플로우로 복귀 → activate 전까지 반복
5. activate 후에야 입장

### 시나리오 3 — 이미 일정을 등록해 둔 방장의 새 방

1. A방 참여로 전역 일정이 이미 등록돼 있음
2. B방 `POST /trips` → `SCHEDULE_PENDING`
3. **그래도** 플로우 표시 (프리패스 없음) — 이 사용자는 사전 신청일이 이미 있으므로 **갱신 입력**(`일정 변경이 있나요?`부터)
4. 아무것도 안 바꿔도 activate → `ACTIVE` (기존 데이터 유지)

### 시나리오 4 — 지아가 링크로 처음 참여 (정상 · 멤버)

1. 링크 → 로그인·이름
2. `POST /join` → MEMBER **`SCHEDULE_PENDING`** (정원 1자리 차지)
3. 사전 일정 입력 플로우(최초/갱신) → `POST .../activate` → **`ACTIVE`**
4. 플로우 중 이탈 → **`SCHEDULE_PENDING`으로 남음**(자리 유지) · 재진입 시 일정 플로우부터(`join`은 멱등). 자리를 비우려면 **방 나가기**
5. 이후 같은 링크 → `myMemberStatus`로 분기 — 이미 `ACTIVE` 멤버면 상세 직행, `SCHEDULE_PENDING`이면 다시 플로우

### 시나리오 5 — 정원 마감 레이스 (멤버)

1. 정원 6, 멤버 row가 방장 포함 5개
2. A·B가 같은 순간 `POST /trips/join` → `trip` 행 락 아래 카운트+INSERT라 **먼저 잡은 쪽만** 성공 (`trip` 행 비관적 락으로 카운트+INSERT 원자화)
3. 나머지 `409 TRIP_MEMBER_FULL`
4. 마지막 1자리는 `SCHEDULE_PENDING`이 되는 순간부터 점유된다 — 일정 플로우 중 이탈해도 자동 회수하지 않는다. 구 Redis hold(#35)는 `#114`로 폐지

### 시나리오 6 — 확정·종료 방

1. `CONFIRMED`/`EXPIRED` 신규 join 409
2. 기존 `ACTIVE` 멤버 재접속 OK
3. 방장만 `SCHEDULE_PENDING`인 채 CONFIRMED가 되는 경로가 있는지는 제품상 막아야 함 (activate 전 확정 금지 등 — Open Q)

### 시나리오 7 — 이름 없이 생성

1. `POST /trips` → `403 PROFILE_NAME_REQUIRED` (현행과 동일)

### 시나리오 9 — 사전 일정 입력을 건너뛰고 `activate`만 호출 (2026-08-19 신규)

1. 신규 가입자가 방을 만들거나 초대 링크로 `join` → `SCHEDULE_PENDING`
2. 프론트가 일정 화면을 건너뛰고 `POST .../activate` 호출
3. → **403 `PRE_SCHEDULE_REQUIRED`** (사전 신청일 미저장). `SCHEDULE_PENDING` 유지
4. 연차·휴일 정보 4개 값을 `PATCH /users/schedule/vacation-policy`로 저장한 뒤 다시 `activate` → `ACTIVE`
5. 정기·개별 일정이 0건인 것은 거부 사유가 **아니다** — 입력을 끝냈지만 막힌 일정이 없는 사용자는 그대로 통과한다

### 시나리오 8 — ~~`ACTIVE`인데 전역 일정 삭제로 `canEnterRoom` false~~ (2026-08-18 `#113`으로 **성립하지 않음**)

> 전역 게이트가 사라져, 일정을 전부 지워도 이미 `ACTIVE`인 방에서 튕기지 않는다. 아래는 이력이다.

1. **정기 일정을 전부 삭제**한다 — 개별 일정은 없거나(한 번도 등록 안 함) O1.4 이후 삭제 불가이므로 이 시나리오에 관여하지 않는다. 정기 0행 + 개별 0행 + `is_all_free=false` → `canEnterRoom` false
2. ~~방 안 API → `403 SCHEDULE_ENTRY_REQUIRED`~~ → 현재는 정상 통과
3. status는 `ACTIVE` 유지 가능 — 전역 게이트와 trip 확인은 별층
4. **개별 일정을 한 번이라도 등록한 적 있는 유저에게는 이 시나리오가 재현되지 않는다** — 그 row가 삭제되지 않고 남아있어 `canEnterRoom`이 계속 true다(`schedule-slot-override.md` O1.4)

---

## 6. 남은 미정 항목

| 항목 | 비고 |
|------|------|
| SCHEDULE_PENDING 단계 초대·PATCH | 미정 |
| 내보내기 · 푸시 · 카카오·링크 공유 | #20 **Nice** · #21·#19 Wave3 (~~hold #35~~ — `#114`로 폐지) |

---

## 관련 문서

| 문서 | 역할 |
|------|------|
| [`../../specs/trip/trip-room-api.md`](../../specs/trip/trip-room-api.md) | 현행 API |
| [`../../specs/trip/schedule-participation-onboarding.md`](../../specs/trip/schedule-participation-onboarding.md) | #22/#39 참여 게이트 스펙 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-19 | **Amend** — ① 사전 일정 입력 플로우를 「최초 입력 / 갱신 입력」 2분기로 개정(판정 = 사전 신청일 저장 여부), 두 갈래 모두 연차·휴일 정보 경유 ② `activate`에 사전 일정 입력 완료 게이트(403 `PRE_SCHEDULE_REQUIRED`) ③ `#114`의 참여자 2단계 전환(`join`=`SCHEDULE_PENDING` → `activate`=`ACTIVE`)·hold 폐지를 본문 전체에 반영 — 구 "일정 먼저 → join = 즉시 ACTIVE" 서술 제거. SSOT: [`../../specs/user-schedule/pre-schedule-entry-flow.md`](../../specs/user-schedule/pre-schedule-entry-flow.md) |
| 2026-07-28 | **Amend** — `POST .../schedule/confirm` → `POST .../activate`로 rename(`TripStatus.CONFIRMED` 등 "일정 확정" 개념과 이름 혼동 해소), `SCHEDULE_CONFIRM_REQUIRED` → `SCHEDULE_ACTIVATION_REQUIRED`. 상세: [`trip-room-api.md`](../../specs/trip/trip-room-api.md) 변경 이력 |
| 2026-07-23 | 문서 정리 — `trip-create.md`/`trip-join.md`를 "빠른 요약" 절로 흡수(파일 삭제), RFC 어투("제안"·"SSOT로 승격 예정") 제거해 Approved/Implemented 사실로 명시, 자기참조 깨진 링크 제거, `trip-create-join-flow-redesign.md`(대안 비교) 삭제에 따라 참조 제거 |
| 2026-07-21 | **#39** — 대안 A SSOT (create SCHEDULE_PENDING → confirm ACTIVE) |
