# 여행방 생성→일정 confirm (대안 A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 방장은 `POST /trips`로 `JOINED` 멤버까지 만든 뒤 `POST .../schedule/confirm`으로 `RESPONDED`가 되어야 방 안에 들어가고, 참여자는 현행처럼 일정 후 `POST /join`으로만 `RESPONDED` 등록한다.

**Architecture:** `TripMemberStatus.JOINED` 부활 + `TripAuthorizationInterceptor`에 **RESPONDED 게이트** 추가 + `schedule/confirm` API. 전역 `canEnterRoom`은 유지하되, 방 안 API는 `RESPONDED ∧ canEnterRoom`만 통과. create 시 `markAllFreeIfNoSchedules`는 **confirm/join으로 이동**.

**Tech Stack:** Java 21 · Spring Boot · JUnit 5 · 기존 `TripCommandService` / `TripJoinService` / `TripAuthorizationInterceptor` 패턴

**Spec / 가이드:** [`trip-create-join-flow-redesign.md`](../../specs/trip-create-join-flow-redesign.md) (대안 A) · [`trip-create-join-guide-proposed.md`](../../product/flows/trip-create-join-guide-proposed.md)

**GitHub:** [#39](https://github.com/Central-MakeUs/TripFit-server/issues/39) · 브랜치: `feat/39-trip-schedule-confirm-joined`

## Global Constraints

- harness ⛔: Approved 스펙과 다른 값 금지 → **구현 전** `#22`/`trip-room-api` D1 · BR-USER-007 amend 후 **Approved** (Task 0)
- Flyway/`V*__*.sql` **작성 금지** (ddl-auto · 상용 데이터 없음)
- 구 path `.../schedule/submit` **재사용 금지** — `confirm`만
- 멤버 신규 INSERT는 **`RESPONDED`만** (JOINED는 방장 create 경로만)
- `./gradlew test` 통과 없이 완료 선언 금지
- 커밋은 사용자 요청 시에만 · Type: 한글 (CONTRIBUTING)

### Plan defaults (Open Q 잠금 — 구현 전 사용자 번복 가능)

| # | 항목 | 이 플랜의 기본값 |
|---|------|------------------|
| 1 | confirm × `canEnterRoom` | confirm에서 `markAllFreeIfNoSchedules` → `requireCanEnterRoom` 후 `RESPONDED` |
| 2 | Skip + row0 | confirm/join에서 `is_all_free=true` (현행 join과 동일) |
| 3 | JOINED 방장 PATCH/DELETE | **허용** (RESPONDED·canEnterRoom 게이트 **면제**). 상세·members·calendar·pin은 **거부** |
| 4 | `membersPreview` | JOINED OWNER **포함** |
| 5 | 멤버 JOINED | **미사용** (hold #35와 무관하게 유지) |
| 6 | RESPONDED 재입장 | 일정 플로우 **재강제 없음** |
| 7 | confirm 재호출 | **idempotent 200** + detail (ALREADY_RESPONDED 409 안 씀) |
| 8 | create 응답 | `myMemberStatus=JOINED`, `needsScheduleConfirm=true` 추가 (클라 라우팅) |
| 9 | 홈 목록 | JOINED 방 **노출** (`GET /trips`는 멤버십만 · RESPONDED 불요) |
| 10 | 초대 공유 | JOINED여도 `inviteCode` 발급·응답 — **공유 허용** (FE 정책) |

---

## 이슈 전략

### 확정: **[#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)** (기존 chore 이슈 내용·브랜치명 교체)

| 선택 | 결과 |
|------|------|
| #39 재사용 ✅ | Hidden/`personal-summary` chore는 완료됨 → 본문을 JOINED·confirm으로 교체. 브랜치 `feat/39-trip-schedule-confirm-joined` |
| #22 reopen ❌ | 닫힌 정책 이슈와 완료 기준 충돌 — #22에는 후속 댓글만 |
| 완전 신규 이슈 | #39로 대체함 |

Task 0: 스펙 Approved 후 `feat/39-trip-schedule-confirm-joined`에서 구현.

---

## File map (예상)

| 파일 | 역할 |
|------|------|
| `trip/domain/TripMemberStatus.java` | JOINED deprecation 제거 · 의미 갱신 |
| `user/exception/UserErrorCode.java` 또는 `trip/exception/TripErrorCode.java` | `SCHEDULE_CONFIRM_REQUIRED` |
| `trip/config/TripAuthorizationInterceptor.java` | RESPONDED 검사 · PATCH/DELETE 면제 분기 |
| `trip/config/TripMemberOnly.java` / `TripOwnerOnly.java` | 주석 갱신 |
| `trip/service/TripCommandService.java` | create=`JOINED` · `markAllFree` 제거 · confirm 추가 |
| `trip/service/TripScheduleConfirmService.java` (또는 Command에 메서드) | JOINED→RESPONDED |
| `trip/controller/TripController.java` 또는 `TripMemberController` | `POST .../schedule/confirm` |
| `trip/dto/CreateTripResponse.java` | status 필드 확장 |
| `docs/specs/schedule-participation-onboarding.md` | D-JOIN-* amend |
| `docs/specs/trip-room-api.md` | D1 · API 표 · 에러 |
| `docs/product/business-rules/user.md` | BR-USER-007 |
| `docs/product/flows/*` | guide 승격·요약 동기화 |
| Tests | `TripServiceTest` · `TripAuthorizationInterceptorTest` · `TripControllerTest` |

---

## Task 0: 이슈 · 스펙 Approved (구현 차단 해제)

**Files:**
- Create: GitHub issue (gh)
- Modify: `docs/specs/trip-create-join-flow-redesign.md` (상태·선택 A·이슈#)
- Modify: `docs/specs/schedule-participation-onboarding.md`, `docs/specs/trip-room-api.md`, `docs/product/business-rules/user.md`
- Modify: `docs/product/flows/trip-create-join-guide.md` ← proposed 내용으로 교체 또는 proposed를 SSOT로 승격 후 현행 deprecate 표기

- [ ] **Step 1:** Plan defaults 표(위) 사용자 확인 — 이의 없으면 진행
- [ ] **Step 2:** ~~`gh issue create`~~ → **#39** 본문·브랜치 확정됨 · #22에 후속 댓글 완료
- [ ] **Step 3:** redesign 스펙 → **Alternative A Accepted** · 이슈 **#39** · Open Q를 Plan defaults로 닫기
- [ ] **Step 4:** `#22`/`trip-room-api` D1 amend — create=`JOINED`, confirm API, 에러 `SCHEDULE_CONFIRM_REQUIRED`
- [ ] **Step 5:** 사용자 **Approved** 회신 받기 (이 단계 전 코드 금지)
- [ ] **Step 6:** 브랜치 `feat/39-trip-schedule-confirm-joined`에서 작업 (`main`에서 checkout)

---

### Task 1: 에러 코드 · enum · create=`JOINED` (단위 테스트)

**Files:**
- Modify: `TripMemberStatus.java`
- Modify: `UserErrorCode.java` 또는 `TripErrorCode.java` (`SCHEDULE_CONFIRM_REQUIRED`)
- Modify: `TripCommandService.java` create 경로
- Modify: `CreateTripResponse.java` (+ 필요 시 mapper)
- Test: `TripServiceTest.java`

**Interfaces:**
- Produces: `createTrip` → owner `TripMemberStatus.JOINED`, **create에서 `markAllFreeIfNoSchedules` 호출 안 함**
- Produces: `CreateTripResponse`에 `myMemberStatus`, `needsScheduleConfirm` (boolean)

- [ ] **Step 1:** `TripServiceTest` — create 후 captore status == `JOINED` · `markAllFree` **미호출** mock verify (failing)
- [ ] **Step 2:** Run `./gradlew test --tests com.tripfit.tripfit.trip.service.TripServiceTest` → FAIL
- [ ] **Step 3:** enum 주석 갱신 · ErrorCode 추가 · create를 `JOINED`로 · response 필드 추가 · create의 `markAllFreeIfNoSchedules` 제거
- [ ] **Step 4:** 동일 테스트 PASS
- [ ] **Step 5:** Commit (사용자 요청 시) `Feat: 방장 create 시 JOINED 멤버십`

---

### Task 2: `POST /trips/{tripId}/schedule/confirm`

**Files:**
- Create/Modify: confirm 서비스 메서드 (`TripCommandService` 또는 전용 클래스)
- Modify: Controller + DTO (detail 재사용)
- Test: `TripServiceTest` / `TripControllerTest`

**Interfaces:**
- Consumes: tripId, userId
- Produces: `TripDetailResponse`
- Behavior:
  1. 멤버십 필수 (없으면 ACCESS_DENIED)
  2. 이미 `RESPONDED` → idempotent detail
  3. `JOINED`만 전환 대상 (그 외 status면 400/409 — 현재 enum은 2개뿐)
  4. `markAllFreeIfNoSchedules` → `requireCanEnterRoom`
  5. status=`RESPONDED` · `@TripActivity`로 `last_activity_at` touch

- [ ] **Step 1:** 실패 테스트 — JOINED→confirm→RESPONDED · row0이면 is_all_free · canEnterRoom false면 SCHEDULE_ENTRY_REQUIRED · 재호출 idempotent
- [ ] **Step 2:** Run 해당 테스트 FAIL
- [ ] **Step 3:** 최소 구현 + OpenAPI `@Operation`
- [ ] **Step 4:** PASS
- [ ] **Step 5:** Commit `Feat: trip schedule confirm API`

**Note:** confirm 엔드포인트는 `@TripMemberOnly`를 쓰되, 인터셉터에서 **confirm path는 RESPONDED 게이트 스킵**(JOINED 허용) — Task 3과 함께 설계. 또는 confirm에 어노테이션 없이 서비스에서 멤버십만 검사.

---

### Task 3: Interceptor — RESPONDED 게이트

**Files:**
- Modify: `TripAuthorizationInterceptor.java`
- Modify: `TripMemberOnly` / `TripOwnerOnly` 주석
- Test: `TripAuthorizationInterceptorTest.java`

**Interfaces:**
- After membership / owner check:
  - **Room APIs** (`@TripMemberOnly` 기본): `status != RESPONDED` → `SCHEDULE_CONFIRM_REQUIRED` → 그다음 `requireCanEnterRoom`
  - **Owner meta** (`@TripOwnerOnly` on PATCH/DELETE): JOINED 허용 · **RESPONDED 검사 스킵** · `canEnterRoom` 스킵 (Plan default #3)
- `GET /trips` (목록) · `POST /trips` · `POST /join` · `POST .../confirm`: 인터셉터 대상 아님 또는 confirm 예외

구현 팁: `@TripOwnerOnly`면 RESPONDED/`canEnterRoom` 스킵; `@TripMemberOnly`면 둘 다 적용. confirm은 `@TripMemberOnly` 없이 Controller에서 직접 멤버 로드.

- [ ] **Step 1:** 테스트 — JOINED+canEnterRoom true여도 detail 403 CONFIRM_REQUIRED · RESPONDED+canEnterRoom false면 ENTRY_REQUIRED · JOINED owner PATCH 통과
- [ ] **Step 2:** FAIL 확인
- [ ] **Step 3:** 인터셉터 구현
- [ ] **Step 4:** PASS
- [ ] **Step 5:** Commit `Feat: RESPONDED 입장 게이트`

---

### Task 4: join · 집계 · 홈/상세 DTO 정합

**Files:**
- Modify: `TripJoinService.java` — 현행 RESPONDED 유지 (`markAllFree` 유지)
- Verify: `TripMemberRepository` native `joinedMemberCount` / `respondedCount` (JOINED는 joined에만)
- Modify: 필요 시 `membersPreview` 쿼리 (OWNER JOINED 포함 — default)
- Test: join·list 관련 테스트 갱신 (`TripServiceTest`, controller tests)

- [ ] **Step 1:** create 직후 list — `joinedMemberCount=1`, `respondedCount=0`, `myMemberStatus=JOINED`
- [ ] **Step 2:** confirm 후 `respondedCount=1`
- [ ] **Step 3:** join 신규는 RESPONDED · 정원 검사에 JOINED 포함
- [ ] **Step 4:** `./gradlew test` 전체 PASS
- [ ] **Step 5:** Commit `Test: JOINED/RESPONDED 집계·join 정합`

---

### Task 5: 문서 승격 · 이슈 체크리스트

**Files:**
- `trip-create-join-guide.md` / proposed · `trip-create.md` · `trip-join.md` · `glossary.md`
- `docs/specs/README.md` · GitHub issue checklist `[x]`
- `docs/decisions/008-trip-authorization-guard.md` (RESPONDED 게이트 한 줄 amend — 있으면)

- [ ] **Step 1:** 현행 guide를 제안 플로우로 교체(또는 proposed rename) · redesign 스펙 Implemented
- [ ] **Step 2:** OpenAPI/에러 표와 코드 일치 검수
- [ ] **Step 3:** `./gradlew test` 재확인
- [ ] **Step 4:** 이슈 본문 완료 기준 체크 · PR 준비 (사용자 요청 시)

---

## 검증 시나리오 (수동·자동)

1. 방장 create → JOINED · detail 403 CONFIRM_REQUIRED  
2. 일정 Skip(row0) → confirm → is_all_free · RESPONDED · detail 200  
3. 이미 canEnterRoom인 유저 create → 여전히 CONFIRM_REQUIRED until confirm  
4. confirm 전 앱 재진입 → 동일 403 · 홈에는 방 보임  
5. 멤버: 일정 전 join 없이 상세 불가 · join 후 RESPONDED  
6. JOINED 방장 PATCH name 성공 · Pin/members 실패  
7. confirm 두 번 → 200 idempotent  

---

## Out of scope

- #35 capacity hold  
- 멤버 JOINED 중간 상태  
- join 전 미리보기 #19 · 내보내기 #20  
- submit path 부활  

---

## 실행 순서 요약

```text
Task 0 (이슈+스펙 Approved) → 1 (JOINED create) → 2 (confirm) → 3 (interceptor) → 4 (집계) → 5 (docs)
```

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-21 | 초안 — 대안 A · 새 이슈 권장 · Plan defaults |
