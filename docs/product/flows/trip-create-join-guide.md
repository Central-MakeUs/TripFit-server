# 여행방 생성·참여 — 플로우·정책·시나리오

> SSOT 가이드 (대안 A · [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)).  
> 계약: [`trip-room-api.md`](../../specs/trip-room-api.md) · [`trip-create-join-flow-redesign.md`](../../specs/trip-create-join-flow-redesign.md) · [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)  
> 짧은 요약: [`trip-create.md`](trip-create.md) · [`trip-join.md`](trip-join.md)

---

## 한 줄 요약

TripFit에서 “방에 들어간다”는 것은 **로그인 + 이름 완료** 후:

- **방장:** 먼저 `POST /trips`로 방·멤버(`JOINED`)를 만들고 → **일정 확인 플로우** → `confirm`으로 `RESPONDED`가 된 뒤에야 방 안 이용  
- **참여자:** 일정 확인 플로우 → `POST /trips/join`으로 **그때** 멤버(`RESPONDED`) 등록  

전역 `canEnterRoom`만으로는 부족하고, **해당 trip에서 일정 확인 완료(`RESPONDED`)** 가 추가로 필요하다.  
방장은 `canEnterRoom`이 true여도 **신규 방 최초 확인 플로우는 강제**된다.

---

## 핵심 개념

| 용어 | 의미 |
|------|------|
| **여행방 (`trip`)** | 조율 단위. 이름·희망 기간·일수·정원·초대코드 등 |
| **방장 (`OWNER`)** | 방을 만든 사람. **생성 시** 멤버 INSERT |
| **`JOINED`** | 멤버 row는 있으나 **이 방 일정 확인 미완료** → **방 입장(상세 등) 불가** (주로 방장) |
| **`RESPONDED`** | 이 방 일정 확인·가입 완료 → 방 입장 가능 (전역 `canEnterRoom`도 만족해야 함) |
| **참여자 (`MEMBER`)** | join 완료 후 `RESPONDED`. 링크·일정만으로는 미등록 |
| **초대 코드** | 6자 Crockford Base32. 링크 `https://tripfit.online/room/{inviteCode}` |
| **일정 데이터** | User **전역** (`regular` + `personal`). 방마다 복사하지 않음 (BR-USER-008) |
| **`is_all_free`** | “넣을 일정이 없어 전부 가능” 선언 |
| **`canEnterRoom`** | 정기≥1 **또는** 개별≥1 **또는** `is_all_free` (전역) |
| **`needsScheduleConfirm`** | (API 파생) `status=JOINED` → 클라가 일정 플로우 강제 |

용어표([`glossary.md`](../glossary.md))는 **승인 후** JOINED 의미를 반영해 amend.

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
       → trip + OWNER + JOINED + inviteCode
  → [정기 일정] → [개별 일정]  (수정하면 patch / Skip 가능)
       ※ canEnterRoom이어도 이 플로우를 보여 줌 (강제)
  → POST /api/v1/trips/{tripId}/schedule/confirm  (가칭)
       → JOINED → RESPONDED (+ row0이면 is_all_free)
  → 방 상세 (입장 완료)
```

| 단계 | 하는 일 |
|------|---------|
| 생성 폼 | 이름 ≤15자, 기간(생성 후 불변), `durationNights`+`durationDays`(또는 미정), 인원 **1~10**, destination 선택 |
| `POST /trips` | `trip`(`ONGOING`) + owner **`JOINED`** + 6자 `inviteCode` + `last_activity_at` 초기화. **아직 방 안 입장 아님** |
| 정기→개별 | **이 방용 확인 플로우**. 전역 일정이 있어도 **매번** 노출 |
| 수정 | 정기 CRUD / 개별 bulk upsert — User 전역 |
| Skip | row≥1 유지 · 둘 다 0행이면 confirm 시 서버가 `is_all_free=true` |
| `confirm` | `RESPONDED` 전환 · 이후 상세·멤버·달력 API 허용 |

### 이탈·재진입 (방장)

- create 직후~confirm 전: **member(`JOINED`)이지만 여행방 입장 불가**
- 앱 종료 후 같은 방 진입 시도 → 다시 **일정 플로우(2~3)** 로보냄
- 홈 목록에 방이 보여도 탭 시 상세가 아니라 확인 플로우

### 생성 시 서버가 만드는 것

- `trip.status = ONGOING`
- `invite_code` UNIQUE 6자
- 방장 `role=OWNER`, `status=JOINED` (정원 카운트에는 포함 — 설계안 기본값)
- `is_all_free`는 **confirm 시점**에 row0이면 설정 (create 시점이 아님)

### 생성·확인 후

- `RESPONDED`가 되면 초대 공유·방 안 활동 (공유를 JOINED 단계부터 허용할지는 Open Question)
- 인원 가득·종료 시 공유 UI 비노출 + join 409 (D8) — 현행과 동일

---

## 2. 참여 플로우 (참여자)

### 사전 조건

- 소셜 로그인 필수 — **비회원 참여 없음** (BR-USER-002)
- 이름 완료 필수
- join 전 미리보기(#19)는 여전히 Out 가능

### 단계

```text
초대 링크 (…/room/{inviteCode})
  → (미로그인) 로그인·이름
  → [정기] → [개별]  (수정/Skip)
  → POST /api/v1/trips/join { "inviteCode": "A2B3C4" }
  → MEMBER + RESPONDED
  → 방 상세
```

| 상황 | 결과 |
|------|------|
| 일정 미완료·이탈 | **멤버 row 없음** — 방에 등록되지 않음. 재진입 시 일정 플로우부터 |
| 처음 join 성공 | INSERT `RESPONDED` · `last_activity_at` 갱신 |
| 이미 `RESPONDED` 멤버 | idempotent — 방 상세 직행 (BR-USER-010) |
| Skip + row 0 | join 시 서버가 `is_all_free=true` 후 INSERT |

멤버에게는 중간 `JOINED`를 두지 않는다 (설계안 A 기본). 정원 hold는 #35 후속.

### 모집 현황 숫자

| 필드 | 의미 |
|------|------|
| `memberCount` | 방장이 정한 정원 |
| `joinedMemberCount` | 멤버 수 (**JOINED 방장 포함** — 기본 제안) |
| `memberFillRate` | `joinedMemberCount / memberCount` |
| `respondedCount` | **`RESPONDED`만** 집계 (확인 완료 인원) |

→ 방장만 JOINED인 직후: `joined=1`, `responded=0` 가능.

---

## 3. 정책 체크리스트

### A. 이중 게이트 (제안)

```text
방 안 API 허용 =
  trip_member 존재
  AND status == RESPONDED          // trip별 일정 확인 완료
  AND canEnterRoom(user)           // 전역: 정기|개별|is_all_free
```

| 상태 | 방 입장 |
|------|---------|
| 비멤버 | ❌ (멤버는 join 전) |
| 방장 `JOINED` | ❌ `SCHEDULE_CONFIRM_REQUIRED` — 일정 플로우 강제 |
| `RESPONDED` + `canEnterRoom` false | ❌ `SCHEDULE_ENTRY_REQUIRED` |
| `RESPONDED` + `canEnterRoom` true | ✅ |

**강제 플로우:** 방장 신규 방에서는 `canEnterRoom == true`여도 `JOINED`이면 일정 UI를 보여 준다.  
서버는 status로 상세를 막아 우회를 차단한다.

**전역 free ≠ 신규 trip 프리패스** — 방장 create 후 확인 플로우, 멤버 join 전 확인 플로우 모두 유지.

### B. 정원 (D8)

- create/patch: `memberCount` **1~10**
- 신규 join: `joinedMemberCount >= memberCount` → `409 TRIP_MEMBER_FULL`  
  (JOINED 방장을 포함하면 create 직후부터 1자리 사용)
- MVP: INSERT 시점 검사 · hold는 #35

### C. 방 상태별 join / 수정

| `TripStatus` | 신규 join | 기존 `RESPONDED` 멤버 | 방장 PATCH |
|--------------|-----------|----------------------|------------|
| `ONGOING` | ✅ (정원·기간 OK) | 조회·활동 | ✅ (JOINED에게 메타 PATCH 허용 여부는 Open Q) |
| `CONFIRMED` | ❌ | 재접속 OK | ❌ |
| `CANCELED` | ❌ | 재접속 OK | ❌ |
| `TERMINATED` | ❌ | 조회 등 | ❌ |

방장 `JOINED`의 “재접속” = 일정 확인 플로우 재개 (상세 아님).

### D. 권한

| 행동 | 누가 |
|------|------|
| 방 생성 | 이름 완료한 로그인 유저 |
| 일정 confirm | 해당 trip `JOINED` 멤버(방장) |
| 메타 수정·삭제 | 방장 (JOINED 단계 허용 범위는 Open Q) |
| Pin·상세·그룹 달력 | **`RESPONDED` + canEnterRoom** |
| 참여자 내보내기 | Out (#20) |

### E. 초대 코드

- create 시 발급 (JOINED 단계에서도 코드는 존재)
- 잘못된 코드 → `404 INVITE_CODE_NOT_FOUND`
- JOINED 단계에서 공유 UI 노출 여부 — Open Question

### F. 주요 에러 코드 (요약 · 제안)

| HTTP | code | 조건 |
|------|------|------|
| 400 | `INVALID_INPUT` | 이름·인원·duration 등 |
| 403 | `PROFILE_NAME_REQUIRED` | 이름 미완료 |
| 403 | `SCHEDULE_CONFIRM_REQUIRED` | `JOINED` — 방 안 API (**신규**) |
| 403 | `SCHEDULE_ENTRY_REQUIRED` | `canEnterRoom` 불만족 |
| 403 | `TRIP_FORBIDDEN` / `TRIP_ACCESS_DENIED` | 권한·비참여자 |
| 404 | `TRIP_NOT_FOUND` / `INVITE_CODE_NOT_FOUND` | |
| 409 | `TRIP_MEMBER_FULL` | 정원 가득 (신규 join) |
| 409 | `TRIP_*` | CONFIRMED/CANCELED/TERMINATED 신규 join |
| 409 | `TRIP_NOT_ONGOING` | 비 ONGOING PATCH |

---

## 4. 방장 vs 참여자 비교

| | 방장 | 참여자 |
|--|------|--------|
| 진입 | 홈 「방 생성」 | 초대 링크 |
| 멤버십 생성 시점 | **생성 직후** (`JOINED`) | **일정 완료 후** join (`RESPONDED`) |
| 일정 확인 | 생성 **후** · 입장 **전** 강제 | join **전** |
| 완료 API | `POST .../schedule/confirm` | `POST /trips/join` |
| 미완료 이탈 | member이나 **입장 불가** | **member 아님** |
| 최종 status | `RESPONDED` | `RESPONDED` |

---

## 5. 유저 시나리오

### 시나리오 1 — 민수가 제주 방 만들기 (정상 · 제안)

1. 민수 로그인·이름 완료  
2. 「방 생성」→ 폼만 작성 (`제주 3박4일`, 8/1~8/10, 4일, 인원 6)  
3. `POST /trips` → OWNER **JOINED**, invite `K7M2N9` · 아직 상세 입장 아님  
4. 정기·개별 확인(이미 일정이 있어도 화면 강제) → 필요 시 patch  
5. `confirm` → **RESPONDED** → 방 상세  
6. 링크 공유 · 홈에 방 노출 · 모집 `joined=1`, `responded=1`

### 시나리오 2 — 민수가 생성 직후 앱 종료 (제안 핵심)

1. `POST /trips`까지 완료 (`JOINED`)  
2. 일정 플로우 중 이탈  
3. 재실행 → 홈에서 방 탭 또는 딥링크  
4. **상세 불가** · 일정 플로우로 복귀 → confirm 전까지 반복  
5. confirm 후에야 입장

### 시나리오 3 — 이미 `canEnterRoom`인 방장의 새 방

1. A방 참여로 전역 일정·`is_all_free` 충족  
2. B방 `POST /trips` → `JOINED`  
3. **그래도** 정기→개별 플로우 표시 (프리패스 없음)  
4. Skip만 해도 confirm → `RESPONDED` (row≥1이면 데이터 유지)

### 시나리오 4 — 지아가 링크로 처음 참여 (정상 · 멤버)

1. 링크 → 로그인·이름  
2. 정기·개별 → `POST /join` → MEMBER RESPONDED  
3. 플로우 중 이탈 → **미등록** · 재진입 시 일정부터  
4. 이후 같은 링크 → 이미 멤버면 상세 직행

### 시나리오 5 — 정원 마감 레이스 (멤버 · MVP)

1. 정원 6, responded/joined에 방장 포함해 5명  
2. A·B 동시 일정 플로우 → 먼저 join 성공한 쪽만 OK  
3. 나머지 `409 TRIP_MEMBER_FULL`  
4. hold는 #35

### 시나리오 6 — 확정·종료 방

1. `CONFIRMED`/`TERMINATED` 신규 join 409  
2. 기존 `RESPONDED` 멤버 재접속 OK  
3. 방장만 `JOINED`인 채 CONFIRMED가 되는 경로가 있는지는 제품상 막아야 함 (confirm 전 확정 금지 등 — Open Q)

### 시나리오 7 — 이름 없이 생성

1. `POST /trips` → `403 PROFILE_NAME_REQUIRED` (현행과 동일)

### 시나리오 8 — `RESPONDED`인데 전역 일정 CLEAR

1. 일정을 지워 `canEnterRoom` false  
2. 방 안 API → `403 SCHEDULE_ENTRY_REQUIRED`  
3. status는 `RESPONDED` 유지 가능 — 전역 게이트와 trip 확인은 별층

---

## 6. 의도적으로 빠진 것 / 현행 대비 미결

| 항목 | 비고 |
|------|------|
| 설계 대안·Open Q | [`trip-create-join-flow-redesign.md`](../../specs/trip-create-join-flow-redesign.md) |
| JOINED 단계 초대·PATCH | 미정 |
| join 전 미리보기 · 내보내기 · 푸시 · hold | #19 · #20 · #21 · #35 |
| 이 문서를 정식 SSOT로 승격 | 스펙 Approved 후 현행 guide와 교체 |

---

## 관련 문서

| 문서 | 역할 |
|------|------|
| [`trip-create-join-guide.md`](trip-create-join-guide.md) | **현행** 가이드 (비교) |
| [`../../specs/trip-create-join-flow-redesign.md`](../../specs/trip-create-join-flow-redesign.md) | 대안 A~D · 권장 A |
| [`../../specs/trip-room-api.md`](../../specs/trip-room-api.md) | 현행 API (승인 전 amend 대상) |
| [`../../specs/schedule-participation-onboarding.md`](../../specs/schedule-participation-onboarding.md) | #22 (승인 전 amend 대상) |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | **#39** — 대안 A SSOT (create JOINED → confirm RESPONDED) |
