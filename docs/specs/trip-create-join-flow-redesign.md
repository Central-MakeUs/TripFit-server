# 여행방 생성·참여 플로우 재설계 — 설계 대안

> 상태: **Accepted / Implemented (core)** — 대안 A · [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39) · 브랜치 `feat/39-trip-schedule-confirm-joined`  
> 관련: [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) (#22), [`trip-room-api.md`](trip-room-api.md),  
> 가이드: [`../product/flows/trip-create-join-guide.md`](../product/flows/trip-create-join-guide.md)  
> 작성: 2026-07-21  
> **이슈:** [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)

---

## 의도 (사용자 요청 미러)

**현행 (#22):** `일정 확인 → 방 생성/join` (멤버십 INSERT와 일정 확인이 한 이벤트, 또는 생성 전 일정).

**목표:** `방 생성(+멤버) → 일정 입력 → 방 생성(입장) 완료` 로 순서를 바꾼다.

| 역할 | 원하는 동작 |
|------|-------------|
| **방장** | ① 여행방 A 생성 **+ 동시에** member INSERT → ② 방 진입 시 **일정 입력 강제** (`canEnterRoom`이어도 플로우 노출) → ③ patch(+필요 시) + `canEnterRoom` 후 입장. ②~③ 미완료면 **member여도 방 입장 불가** (이탈 후 재진입 동일) |
| **멤버** | ① 초대 링크로 입장 시도 → ② 일정 입력 완료 → ③ 그때 `member` 등록. ② 미완료면 **member 아님 · 방에 등록 안 됨** |

---

## 현행과의 핵심 차이

| 축 | 현행 (#22) | 제안 |
|----|------------|------|
| 방장 일정 | 생성 **전** 플로우 → `POST /trips` 시 `RESPONDED` | 생성 **후** 플로우 → 입장 전 **trip별 확인** |
| 방장 멤버십 | create와 동시에 “입장 가능”까지 완료 | create 시 멤버는 생기지만 **입장 게이트 추가** |
| `canEnterRoom` | 전역 입장 조건 | **유지** + **trip별 확인 완료**가 추가로 필요 (방장) |
| 멤버 | 일정 → `POST /join` (INSERT) | **거의 동일** (일정 → join). 변경 폭은 방장이 큼 |
| `JOINED` | 신규 미사용 (deprecated) | **부활 후보** 또는 대체 플래그 |

---

## 설계상 반드시 풀어야 할 문제

1. **전역 `canEnterRoom` ≠ trip별 “이번 방 일정 확인 완료”**  
   이미 A방에서 일정이 있어도 B방 생성 후 **강제 플로우**가 필요하므로, 서버가 막을 수 있는 **trip 스코프 플래그**가 필요하다. (클라만 강제하면 재진입·deep link로 우회 가능)

2. **방장: “멤버인데 입장 불가”**  
   `trip_member` 존재와 “방 안 API 허용”을 **분리**해야 한다.

3. **멤버: INSERT 시점 = 일정 완료 후**  
   현행과 동일 축. 정원 레이스(#35)는 그대로 MVP 감수 또는 hold 후속.

4. **모집 카운트·홈 노출**  
   방장이 JOINED(미확인)인 동안 `joinedMemberCount`에 넣을지, 홈 카드에 방을 띄울지, 초대 공유를 허용할지.

5. **#22 / BR-USER-007 / D-JOIN-TRIP-FLOW amend**  
   “create/join = 즉시 RESPONDED · submit 삭제”와 충돌 → 스펙·BR 동시 amend 필수.

---

## 대안 비교

### 대안 A — `JOINED` → `RESPONDED` 상태 머신 부활 (**권장**)

**아이디어:** deprecated `TripMemberStatus.JOINED`를 다시 켠다.

| 역할 | create / join | 일정 확인 완료 |
|------|---------------|----------------|
| 방장 | `POST /trips` → OWNER + **`JOINED`** | `POST .../schedule/confirm`(가칭) → **`RESPONDED`** (+ row0이면 `is_all_free`) |
| 멤버 | (멤버 row 없음) → 일정 플로우 → `POST /join` → **`RESPONDED`** 한 번에 | 중간 `JOINED` 없음 |

**입장 게이트 (서버):**

```text
방 안 API 허용 =
  trip_member 존재
  AND status == RESPONDED
  AND canEnterRoom(user)   // 전역: 정기|개별|is_all_free
```

- 방장 `JOINED`: 홈 목록에는 보일 수 있음 · **상세·멤버·그룹 달력 등은 403** (예: `SCHEDULE_CONFIRM_REQUIRED`)
- 강제 UX: `myMemberStatus == JOINED` 또는 `needsScheduleConfirm == true` 이면 클라가 정기→개별 플로우로 라우팅 (`canEnterRoom`이어도)

**장점**

- 기존 enum·`respondedCount` 의미와 맞음 (`RESPONDED` = 확인 완료)
- “멤버이지만 미완료”를 DB로 명확히 표현
- 방장/멤버 비대칭을 상태로 설명하기 쉬움

**단점**

- #22에서 폐기한 submit/JOINED 모델을 **부분 회귀** (방장만)
- confirm API 재도입 (이름은 submit 대신 `confirm` 권장)
- `JOINED`가 정원·미리보기·알림에 포함되는지 정책 표 필요

**정원·카운트 제안 (A 기본값):**

| 필드 | JOINED(방장) | RESPONDED |
|------|--------------|-----------|
| `joinedMemberCount` | **포함** (자리 선점) | 포함 |
| `respondedCount` | **미포함** | 포함 |
| `membersPreview` | OWNER면 포함 가능 / 또는 RESPONDED만 — **[미정]** | 포함 |

---

### 대안 B — `RESPONDED` 유지 + `schedule_confirmed_at` (또는 `entry_ready`) 컬럼

**아이디어:** status는 create부터 `RESPONDED`(또는 단일 상태)로 두고,  
`trip_member.schedule_confirmed_at` nullable로 “이번 방 일정 확인”을 표시.

| 역할 | INSERT | 확인 완료 |
|------|--------|-----------|
| 방장 | create 시 member + `schedule_confirmed_at = null` | confirm API → `now()` |
| 멤버 | join 시에만 INSERT, 이때 `schedule_confirmed_at = now()` | join과 동일 |

**입장 게이트:**

```text
방 안 API =
  member 존재
  AND schedule_confirmed_at IS NOT NULL
  AND canEnterRoom(user)
```

**장점**

- status enum 회귀 최소화 (`JOINED` 안 씀)
- “확인 시각” 감사용으로 유용

**단점**

- `RESPONDED` 글로서리와 어긋남 (응답 완료인데 미확인 가능)
- `respondedCount` 정의를 컬럼 기준으로 바꿔야 함 → API·프론트 혼동 위험
- 사실상 JOINED/RESPONDED와 동일한 정보를 **다른 이름**으로 둠

---

### 대안 C — 클라 전용 강제 플로우 + 서버는 `canEnterRoom`만

**아이디어:** create 시 바로 `RESPONDED`. 클라만 “생성 직후 일정 화면”을 띄움.

**장점:** 서버 변경 최소.

**단점 (기각 권장):**

- `canEnterRoom`이 true인 유저는 **상세 API를 바로 호출**해 플로우 스킵 가능
- “이탈 후 재진입 시 입장 불가”를 **서버가 보장 못 함** → 요청 조건 불만족

---

### 대안 D — 방장도 멤버 INSERT를 일정 후로 미룸 (대칭 모델)

**아이디어:** create는 `trip`만 만들고 owner member는 confirm/join 성격 API에서 INSERT.

**장점:** 방장·멤버 대칭, “미완료면 등록 안 됨”이 단순.

**단점:**

- 요청의 “생성과 동시에 member insert”와 **충돌**
- 방장 없는 trip, 초대 코드만 있는 고아 방, 권한(PATCH/DELETE) 주체 모호
- **요청과 불일치 → 기각**

---

## 권장안

**대안 A (`JOINED` → `RESPONDED`)** 를 권장한다.

이유:

1. 요청의 “member인데 입장 불가”를 상태로 자연스럽게 표현  
2. 멤버 플로우는 현행 유지(일정 → `POST /join` → `RESPONDED`)라 변경 범위가 방장·게이트에 집중  
3. `respondedCount` / 모집 UX와 정합  
4. 대안 C는 서버 강제 불가, B는 의미 중복

멤버 쪽은 **현행과 동일**하게 두고, 문서·카피만 “입장 시도 → 일정 → 등록”으로 설명하면 충분하다.

---

## 권장안(A) 상세 스케치

### 방장

```text
[방 생성 폼] → POST /trips
  → trip + OWNER + JOINED + inviteCode
  → (응답에 needsScheduleConfirm=true)

→ [정기] → [개별] (수정 시 patch / Skip)
→ POST /trips/{tripId}/schedule/confirm  (가칭)
  → JOINED→RESPONDED
  → row0이면 is_all_free=true
  → canEnterRoom 불만족이면 403 (confirm 거부 또는 입장만 거부 — 아래 [미정])

→ [여행방 상세]
```

이탈·재진입: `myMemberStatus=JOINED`이면 상세 대신 일정 플로우로.  
홈에 방이 보여도 카드 탭 시 동일.

### 멤버

```text
초대 링크 → (로그인·이름)
→ [정기] → [개별]
→ POST /trips/join { inviteCode }
  → MEMBER + RESPONDED (+ row0 → is_all_free)
→ [여행방 상세]
```

이탈 시 member row 없음 — 현행과 동일.

### API 후보

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/trips` | 변경: owner **`JOINED`** INSERT |
| POST | `/api/v1/trips/{tripId}/schedule/confirm` | **신규** — OWNER(및 향후 JOINED 멤버) → `RESPONDED` |
| POST | `/api/v1/trips/join` | 유지 — 비멤버 → `RESPONDED` |
| GET | 상세·members·calendar 등 | `RESPONDED` + `canEnterRoom` 필수 |

구 `schedule/submit` 이름 재사용은 비권장 (폐기 이력·클라 혼동). `confirm` 권장.

### 에러 코드 후보

| HTTP | code | 조건 |
|------|------|------|
| 403 | `SCHEDULE_CONFIRM_REQUIRED` | member이지만 `JOINED` — 방 안 API |
| 403 | `SCHEDULE_ENTRY_REQUIRED` | `canEnterRoom` 불만족 (현행 유지) |
| 409 | `ALREADY_RESPONDED` | confirm 재호출 — idempotent 200도 가능 **[미정]** |

### 홈·초대 (제안 기본값 — 확정 전)

| 항목 | 제안 |
|------|------|
| 홈 `scope=ongoing` | JOINED 방장 방 **노출** (미완료 카드 배지 UX는 FE) |
| 초대 공유 | JOINED여도 코드 발급됨 → **공유 허용**할지 **RESPONDED 후만**할지 **[미정]** |
| Pin / PATCH trip | JOINED 방장에게 PATCH 허용? → 메타만 허용·상세 거절 등 **[미정]** |

---

## 대안 vs 요청 매핑

| 요청 문장 | A | B | C | D |
|-----------|---|---|---|---|
| 방장: 생성 + member 동시 INSERT | ✅ JOINED | ✅ null confirmed | ✅ RESPONDED | ❌ |
| 방장: canEnterRoom이어도 일정 플로우 | ✅ trip status | ✅ confirmed_at | ⚠️ 클라만 | — |
| 방장: 미완료면 입장 불가 (서버) | ✅ | ✅ | ❌ | — |
| 멤버: 일정 후 member 등록 | ✅ | ✅ | ✅ | ✅ |
| #22 정합 비용 | 중 (amend) | 중 | 하 (단 요구 불충족) | 상 |

---

## BR · 스펙 영향 (승인 시)

| 문서 | 변경 |
|------|------|
| BR-USER-007 | 방장 = 생성 후 confirm · 멤버 = join |
| D-JOIN-TRIP-FLOW / D-JOIN-MEMBER | 순서·상태 amend |
| `trip-room-api.md` D1 | create = JOINED, confirm = RESPONDED |
| `glossary.md` | 참여자 = RESPONDED · JOINED = 미확인 멤버(방장) |
| `trip-create.md` / guide | 제안본으로 치환 후 현행 가이드 deprecate |

---

## Open Questions (`[미정]`)

논의 후 스펙 Approved 전에 닫을 것:

1. **confirm 시점 `canEnterRoom`:** confirm API에서 강제 vs confirm은 status만 바꾸고 다음 상세에서 `SCHEDULE_ENTRY_REQUIRED`?
2. **Skip + row0:** confirm에서 `is_all_free=true` (현행 create/join과 동일)로 둘지?
3. **JOINED 방장의 초대 공유·PATCH·DELETE** 허용 범위?
4. **`membersPreview` / 멤버 목록에 JOINED 노출?**
5. **멤버도 언젠가 JOINED를 쓰는지** (예: hold #35와 결합) vs 멤버는 계속 RESPONDED-only?
6. **이미 RESPONDED인 방 재입장** 시 일정 플로우 재노출? (요청은 “생성/최초 입장” 강제 — **재입장 프리패스**로 보는 게 자연스러움)
7. **기존 운영 데이터:** 전원 RESPONDED면 마이그레이션 불필요(상용 데이터 없음 · ddl-auto). 코드 경로만 전환.

---

## 다음 단계

1. ~~대안 선택~~ → **A 확정**
2. ~~Plan · GitHub #39~~ → **Implemented (core)**
3. ~~스펙 amend~~ → `schedule-participation-onboarding.md` · `trip-room-api.md` · 가이드 반영 완료
4. ~~구현·`./gradlew test`~~ → 완료 (구 Superpowers plan 문서는 삭제 — SSOT는 본 스펙·가이드)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | Draft — 대안 A~D · 권장 A · 가이드 비교본 링크 |
| 2026-07-21 | 대안 A 선택 · 구현 계획 링크 · 새 이슈 권장 |
| 2026-07-21 | Implemented (core) · 완료된 Superpowers plan 문서 삭제 |
