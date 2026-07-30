# 여행방 생성·참여 — 플로우·정책·시나리오

> **상태: Approved/Implemented** (대안 A 채택, [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)). 생성·참여 플로우의 SSOT 가이드.
> 계약: [`trip-room-api.md`](../../specs/trip-room-api.md) · [`schedule-participation-onboarding.md`](../../specs/schedule-participation-onboarding.md)
> 설계 대안 검토(A~D) 이력은 #39 PR 참고 — 비교 문서는 채택 후 삭제됨

---

## 빠른 요약

**방장:**

1. 홈에서 「여행방 신규 생성하기」→ 방 생성 폼(이름·기간·일수·인원·선택 여행지)
2. `POST /trips` → OWNER **`SCHEDULE_PENDING`**. DB에 invite_code 발급하나 **응답에 inviteCode 없음**
3. **정기→개별** 일정 확인(수정/Skip) — `canEnterRoom`이어도 강제
4. `POST /trips/{tripId}/activate` → **`ACTIVE`**
5. 방 상세(`inviteCode`) · **초대 공유** (방장·ACTIVE 이후만)

사전 조건: 로그인 + 프로필 이름(BR-USER-001). 예외: activate 전 이탈 → 재진입 시 일정 플로우, 상세 API는 `SCHEDULE_ACTIVATION_REQUIRED`.

**참여자(멤버):**

1. 초대 링크 → (미멤버) **정기→개별** (수정/Skip)
2. `POST /api/v1/trips/join` `{ inviteCode }` → INSERT **`ACTIVE` 즉시** (+ row0이면 `is_all_free`)
3. **중간 `SCHEDULE_PENDING` 없음** — "일정 넣고 join = ACTIVE 한 방"
4. 정원 full → 409 · 이미 ACTIVE → idempotent. 방장(SCHEDULE_PENDING)이 join으로 우회 → `SCHEDULE_ACTIVATION_REQUIRED` → `activate` 사용

모집 현황(응답률): `memberFillRate = activeMemberCount / memberCount`(구 공식 `joinedMemberCount / memberCount`에서 전환, `joinedMemberCount`는 API 미노출 — [`trip-member-fill-rate-refactor.md`](../../specs/trip-member-fill-rate-refactor.md)). 사전 조건: 소셜 로그인 필수(BR-USER-002) + 이름 완료. 상세·정책·시나리오는 아래 1~5절.

---

## 한 줄 요약

TripFit에서 “방에 들어간다”는 것은 **로그인 + 이름 완료** 후:

- **방장:** `POST /trips`(`SCHEDULE_PENDING`) → 일정 플로우 → `activate`(`ACTIVE`) **이후에야** 방 안·초대 공유
- **참여자:** 일정 플로우 → `POST /trips/join` → **곧바로 `ACTIVE`** (중간 SCHEDULE_PENDING 없음)

전역 `canEnterRoom`만으로는 부족하고, **해당 trip에서 `ACTIVE`** 가 추가로 필요하다.

> **프론트 주의:** create 응답에 `inviteCode` 없음. 홈에 SCHEDULE_PENDING 카드가 보여도 상세/공유로 바로 가지 말고 activate 플로우로.
> 용어·오해표: [`glossary.md`](../glossary.md) · 스펙 필독: [`trip-room-api.md`](../../specs/trip-room-api.md)

---

## 핵심 개념

| 용어 | 의미 |
|------|------|
| **여행방 (`trip`)** | 조율 단위. 이름·희망 기간·일수·정원·초대코드 등 |
| **방장 (`OWNER`)** | 방을 만든 사람. **생성 시** 멤버 INSERT |
| **참여자 (`MEMBER`)** | join 완료 후 `ACTIVE`. 링크·일정만으로는 미등록 |
| **초대 코드** | 6자 Crockford Base32. 링크 `https://tripfit.online/room/{inviteCode}` |
| **일정 데이터** | User **전역** (`regular` + `personal`). 방마다 복사하지 않음 (BR-USER-008) |
| **`is_all_free`** | “넣을 일정이 없어 전부 가능” 선언 |
| **`canEnterRoom`** | 정기≥1 **또는** 개별≥1 **또는** `is_all_free` (전역) |

**`SCHEDULE_PENDING`/`ACTIVE` 정의는 [`glossary.md`](../glossary.md)가 SSOT** — 방장 전용 create 직후 상태(SCHEDULE_PENDING) vs 입장·공유 가능 상태(ACTIVE), 헷갈리기 쉬운 점 표 포함. 여기서 중복 정의하지 않는다.

---

## 1. 방 생성 플로우 (방장)

### 사전 조건

1. 소셜 로그인 (카카오 / 구글 / 애플)
2. 프로필 **이름 완료** (BR-USER-001) — 없으면 `403 PROFILE_NAME_REQUIRED`
3. (선택) 첫 가입 세션 온보딩 skip 가능

### 단계 (제품 UX → API)

```text
홈 「방 생성」
  → [방 생성 폼] 이름·기간·일수·인원·(선택)여행지
  → POST /api/v1/trips
       → trip + OWNER + SCHEDULE_PENDING + inviteCode
  → [정기 일정] → [개별 일정]  (수정하면 patch / Skip 가능)
       ※ canEnterRoom이어도 이 플로우를 보여 줌 (강제)
  → POST /api/v1/trips/{tripId}/activate
       → SCHEDULE_PENDING → ACTIVE (+ row0이면 is_all_free)
  → 방 상세 (입장 완료)
```

| 단계 | 하는 일 |
|------|---------|
| 생성 폼 | 이름 ≤15자, 기간(생성 후 불변), `durationNights`+`durationDays`(또는 미정), 인원 **1~10**, destination 선택 |
| `POST /trips` | `trip`(`ONGOING`) + owner **`SCHEDULE_PENDING`** + DB에 6자 `invite_code` 발급. **응답에 inviteCode 미포함**(입장 전). **아직 방 안 입장·공유 아님** |
| 정기→개별 | **이 방용 확인 플로우**. 전역 일정이 있어도 **매번** 노출 |
| 수정 | 정기 CRUD / 개별 bulk upsert — User 전역 |
| Skip | row≥1 유지 · 둘 다 0행이면 activate 시 서버가 `is_all_free=true` |
| `activate` | `ACTIVE` 전환 · 이후 상세·멤버·달력 API 허용 |

### 이탈·재진입 (방장)

- create 직후~activate 전: **member(`SCHEDULE_PENDING`)이지만 여행방 입장 불가**
- 앱 종료 후 같은 방 진입 시도 → 다시 **일정 플로우(2~3)** 로보냄
- 홈 목록에 방이 보여도 탭 시 상세가 아니라 확인 플로우

### 생성 시 서버가 만드는 것

- `trip.status = ONGOING`
- `invite_code` UNIQUE 6자
- 방장 `role=OWNER`, `status=SCHEDULE_PENDING` (정원 카운트에는 포함)
- `is_all_free`는 **activate 시점**에 row0이면 설정 (create 시점이 아님)

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
  → [정기] → [개별]  (수정/Skip)
  → POST /api/v1/trips/join { "inviteCode": "A2B3C4" }
  → MEMBER + ACTIVE
  → 방 상세
```

| 상황 | 결과 |
|------|------|
| 일정 미완료·이탈 | **멤버 row 없음** — 방에 등록되지 않음. 재진입 시 일정 플로우부터 |
| 처음 join 성공 | INSERT `ACTIVE` · `last_activity_at` 갱신 |
| 이미 `ACTIVE` 멤버 | idempotent — 방 상세 직행 (BR-USER-010) |
| Skip + row 0 | join 시 서버가 `is_all_free=true` 후 INSERT |

멤버에게는 중간 `SCHEDULE_PENDING`를 두지 않는다. 정원 hold는 #35 후속.

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
  AND canEnterRoom(user)           // 전역: 정기|개별|is_all_free
```

| 상태 | 방 입장 |
|------|---------|
| 비멤버 | ❌ (멤버는 join 전) |
| 방장 `SCHEDULE_PENDING` | ❌ `SCHEDULE_ACTIVATION_REQUIRED` — 일정 플로우 강제 |
| `ACTIVE` + `canEnterRoom` false | ❌ `SCHEDULE_ENTRY_REQUIRED` |
| `ACTIVE` + `canEnterRoom` true | ✅ |

**강제 플로우:** 방장 신규 방에서는 `canEnterRoom == true`여도 `SCHEDULE_PENDING`이면 일정 UI를 보여 준다.
서버는 status로 상세를 막아 우회를 차단한다.

**전역 free ≠ 신규 trip 프리패스** — 방장 create 후 확인 플로우, 멤버 join 전 확인 플로우 모두 유지.

### B. 정원 (D8)

- create/patch: `memberCount` **1~10**
- 신규 join: `joinedMemberCount >= memberCount` → `409 TRIP_MEMBER_FULL`
  (SCHEDULE_PENDING 방장을 포함하면 create 직후부터 1자리 사용)
- MVP: INSERT 시점 검사 · hold는 #35

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
| 일정 activate | 해당 trip `SCHEDULE_PENDING` 멤버(방장) |
| 메타 수정·삭제 | 방장 (SCHEDULE_PENDING 단계 허용 범위는 Open Q) |
| Pin·상세·그룹 달력 | **`ACTIVE` + canEnterRoom** |
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
| 403 | `SCHEDULE_ACTIVATION_REQUIRED` | `SCHEDULE_PENDING` — 방 안 API (**신규**) |
| 403 | `SCHEDULE_ENTRY_REQUIRED` | `canEnterRoom` 불만족 |
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
| 멤버십 생성 시점 | **생성 직후** (`SCHEDULE_PENDING`) | **일정 완료 후** join (`ACTIVE`) |
| 일정 확인 | 생성 **후** · 입장 **전** 강제 | join **전** |
| 완료 API | `POST .../activate` | `POST /trips/join` |
| 미완료 이탈 | member이나 **입장 불가** | **member 아님** |
| 최종 status | `ACTIVE` | `ACTIVE` |

---

## 5. 유저 시나리오

### 시나리오 1 — 민수가 제주 방 만들기 (정상)

1. 민수 로그인·이름 완료
2. 「방 생성」→ 폼만 작성 (`제주 3박4일`, 8/1~8/10, 4일, 인원 6)
3. `POST /trips` → OWNER **SCHEDULE_PENDING**, invite `K7M2N9` · 아직 상세 입장 아님
4. 정기·개별 확인(이미 일정이 있어도 화면 강제) → 필요 시 patch
5. `activate` → **ACTIVE** → 방 상세
6. 링크 공유 · 홈에 방 노출 · 모집 `joined=1`, `responded=1`

### 시나리오 2 — 민수가 생성 직후 앱 종료 (핵심 케이스)

1. `POST /trips`까지 완료 (`SCHEDULE_PENDING`)
2. 일정 플로우 중 이탈
3. 재실행 → 홈에서 방 탭 또는 딥링크
4. **상세 불가** · 일정 플로우로 복귀 → activate 전까지 반복
5. activate 후에야 입장

### 시나리오 3 — 이미 `canEnterRoom`인 방장의 새 방

1. A방 참여로 전역 일정·`is_all_free` 충족
2. B방 `POST /trips` → `SCHEDULE_PENDING`
3. **그래도** 정기→개별 플로우 표시 (프리패스 없음)
4. Skip만 해도 activate → `ACTIVE` (row≥1이면 데이터 유지)

### 시나리오 4 — 지아가 링크로 처음 참여 (정상 · 멤버)

1. 링크 → 로그인·이름
2. 정기·개별 → `POST /join` → MEMBER ACTIVE
3. 플로우 중 이탈 → **미등록** · 재진입 시 일정부터
4. 이후 같은 링크 → 이미 멤버면 상세 직행

### 시나리오 5 — 정원 마감 레이스 (멤버 · MVP)

1. 정원 6, responded/joined에 방장 포함해 5명
2. A·B 동시 일정 플로우 → 먼저 join 성공한 쪽만 OK
3. 나머지 `409 TRIP_MEMBER_FULL`
4. hold는 #35

### 시나리오 6 — 확정·종료 방

1. `CONFIRMED`/`EXPIRED` 신규 join 409
2. 기존 `ACTIVE` 멤버 재접속 OK
3. 방장만 `SCHEDULE_PENDING`인 채 CONFIRMED가 되는 경로가 있는지는 제품상 막아야 함 (activate 전 확정 금지 등 — Open Q)

### 시나리오 7 — 이름 없이 생성

1. `POST /trips` → `403 PROFILE_NAME_REQUIRED` (현행과 동일)

### 시나리오 8 — `ACTIVE`인데 전역 일정 삭제로 `canEnterRoom` false

1. **정기 일정을 전부 삭제**한다 — 개별 일정은 없거나(한 번도 등록 안 함) O1.4 이후 삭제 불가이므로 이 시나리오에 관여하지 않는다. 정기 0행 + 개별 0행 + `is_all_free=false` → `canEnterRoom` false
2. 방 안 API → `403 SCHEDULE_ENTRY_REQUIRED`
3. status는 `ACTIVE` 유지 가능 — 전역 게이트와 trip 확인은 별층
4. **개별 일정을 한 번이라도 등록한 적 있는 유저에게는 이 시나리오가 재현되지 않는다** — 그 row가 삭제되지 않고 남아있어 `canEnterRoom`이 계속 true다(`schedule-slot-override.md` O1.4)

---

## 6. 남은 미정 항목

| 항목 | 비고 |
|------|------|
| SCHEDULE_PENDING 단계 초대·PATCH | 미정 |
| 내보내기 · 푸시 · 카카오·링크 공유 · hold | #20 **Nice** · #21·#19 Wave3 · #35 Wave4 |

---

## 관련 문서

| 문서 | 역할 |
|------|------|
| [`../../specs/trip-room-api.md`](../../specs/trip-room-api.md) | 현행 API |
| [`../../specs/schedule-participation-onboarding.md`](../../specs/schedule-participation-onboarding.md) | #22/#39 참여 게이트 스펙 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-28 | **Amend** — `POST .../schedule/confirm` → `POST .../activate`로 rename(`TripStatus.CONFIRMED` 등 "일정 확정" 개념과 이름 혼동 해소), `SCHEDULE_CONFIRM_REQUIRED` → `SCHEDULE_ACTIVATION_REQUIRED`. 상세: [`trip-room-api.md`](../../specs/trip-room-api.md) 변경 이력 |
| 2026-07-23 | 문서 정리 — `trip-create.md`/`trip-join.md`를 "빠른 요약" 절로 흡수(파일 삭제), RFC 어투("제안"·"SSOT로 승격 예정") 제거해 Approved/Implemented 사실로 명시, 자기참조 깨진 링크 제거, `trip-create-join-flow-redesign.md`(대안 비교) 삭제에 따라 참조 제거 |
| 2026-07-21 | **#39** — 대안 A SSOT (create SCHEDULE_PENDING → confirm ACTIVE) |
