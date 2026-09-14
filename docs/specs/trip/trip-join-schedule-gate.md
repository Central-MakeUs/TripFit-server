# 참여자 join을 SCHEDULE_PENDING으로 — 방 입장 일정 확인 서버 강제

> 상태: **Implemented** (2026-09-13 — `#113`으로 J-7·J-8, `#114`로 J-1·J-3·J-4·J-6·J-9 구현 완료)
> MVP: In scope (방 입장 플로우)
> 관련 BR: BR-USER-006 · BR-USER-007 · BR-USER-011 (**BR 개정 포함** — J-7)
> 흡수한 스펙: [`user-schedule/schedule-state-response.md`](../user-schedule/schedule-state-response.md) (Superseded)

## 목표

"방에 새로 입장할 때마다 일정 재확인을 강제한다(건너뛰기 불가)"는 규칙을 **서버가 보장**하게 한다. 지금은 이 강제가 전적으로 프론트 책임이라, 프론트 조건식이 한 번만 틀려도 일정 없이 방에 들어가진다.

## 반드시 준수해야 하는 규칙 (2026-08-17 사용자 확정 — 타협 불가)

> 방 입장 시, 정기 일정/개별 일정이 있든 없든, 일정 재확인 및 입력을 강제한다. "건너뛰기"는 불가하다. 방에 새로 입장할 때마다 이 플로우가 호출된다.
>
> 1. **정기 일정이 없는 경우** — "사전 일정 입력이 필요해요" 모달 → 확인 → "정기 일정이 있나요?" → 네/아니오 대답 후 회원가입에서 쓴 기존 플로우 그대로 → 방 입장
> 2. **정기 일정이 있는 경우** — "입력하신 일정을 확인해주세요" 모달 → 확인 → 정기 일정 GET → 필요하면 수정(연차 포함) → 개별 일정 → 방 입장
>
> 두 경우 모두 "건너뛰기" 버튼이 없다. 회원가입과 달리 일정을 필수로 모두 확인해야 들어갈 수 있다.

### 적용 범위 — "매번"의 뜻 (2026-08-18 사용자 확정)

**"방에 새로 입장할 때마다" = 새 방에 참여할 때마다 1회.** 이미 들어간 방을 홈 목록에서 다시 열 때는 이 플로우가 뜨지 않는다.

| | 플로우 강제 | 서버 판정 |
|---|---|---|
| 새 방 참여(초대 링크·방 생성) | **강제** | 멤버 row가 `SCHEDULE_PENDING`으로 생성됨 |
| 이미 `ACTIVE`인 방 재진입 | 없음 | `myMemberStatus=ACTIVE` — 방 안 API 그대로 통과 |

이 해석을 명시해 두는 이유: "방에 들어갈 때마다 매번(재진입 포함)"으로 읽으면 설계가 완전히 달라진다 — 진입 시마다 멤버를 `SCHEDULE_PENDING`으로 되돌리는 장치가 추가로 필요하고, 그 되돌리는 시점을 서버가 알 방법이 없다(방 상세 조회가 곧 진입은 아니다). 그 설계는 채택하지 않는다.

## 배경

- **현행 구조로는 강제가 불가능하다.** 참여자는 `POST /trips/join` 한 번으로 곧바로 `ACTIVE`가 되고(`TripJoinService.joinAsNewMember`), 서버에는 "일정 확인을 마쳤다"는 신호가 남지 않는다(구 `POST .../schedule/submit` 폐기 — D-SUBMIT-2). 프론트가 일정 화면을 건너뛰고 `join`을 호출하면 서버는 그대로 방에 넣어준다. QA 이슈 2(P1)가 정확히 이 경로였다.
- **방장은 이미 강제되고 있다.** `POST /trips` → `SCHEDULE_PENDING` → 일정 플로우 → `POST .../activate` → `ACTIVE`. 입장 전에는 방 안 API가 403으로 막힌다.
- **이미 갖춰진 장치도 있다.** 코드 확인 결과:

| 필요한 것 | 현재 상태 |
|---|---|
| 멤버도 `activate` 호출 가능 | **이미 됨** — `TripController.activateMembership`에 역할 어노테이션 없음, `TripCommandService.activateMembership`도 `requireOwner`를 부르지 않음 |
| 확인 전에는 방 안 기능 차단 | **이미 됨** — `TripAuthorizationInterceptor`가 `ACTIVE` 요구, 아니면 `SCHEDULE_ACTIVATION_REQUIRED` |
| `join` 재호출로 우회 불가 | **이미 됨** — `TripCommandService.joinTrip`이 기존 멤버십에 `requireActive`를 걸어 `SCHEDULE_PENDING`이면 예외 |

이 세 장치는 그대로 재사용한다. 다만 **2026-08-17 사용자 확정**으로 `join`을 일정 플로우 **앞**으로 옮기기로 하면서(J-1), `join` 자체에 정원 체크 로직(DB 비관적 락, J-4)을 새로 넣고 기존 Redis `hold`를 완전히 걷어내는 작업이 추가로 필요해졌다 — "한 줄만 바꾸면 된다"던 최초 판단보다 범위가 커졌다.

**강제의 한계 (정직하게 명시):** 프론트가 `join` 직후 곧바로 `activate`를 호출하면 여전히 우회된다. 서버는 사용자가 화면을 실제로 봤는지 알 수 없어, 어떤 REST 설계로도 이건 막지 못한다. 이 스펙이 확실히 막는 것은 **실수로 건너뛰는 경우**(이슈 2의 실제 원인인 조건식 오류)다 — 잘못 구현하면 조용히 통과하는 대신 방 안 API가 전부 403이라 즉시 드러난다.

## 변경 범위 (기존 Approved 스펙 amend)

### ADDED

- 없음 (새 API·컬럼·ErrorCode 없음 — 기존 장치를 연결하는 변경)

### MODIFIED

- `TripJoinService.joinAsNewMember`: 새 멤버 상태 `ACTIVE` → **`SCHEDULE_PENDING`**, 호출 시점도 일정 플로우 **끝 → 맨 앞**으로 이동 (J-1)
- `joinAsNewMember` 내부: 정원 체크를 **`Trip` 행 비관적 락(`SELECT ... FOR UPDATE`) + 같은 트랜잭션 내 카운트·INSERT**로 처리 (J-4, B안 확정)
- `POST /api/v1/trips/join` 응답: `TripDetailResponse`(초대 코드 포함 상세) → **create와 동일한 축소 응답** (J-3)
- `CreateTripResponse` → 이름을 **`TripEntryResponse`**로 바꿔 create·join이 함께 사용 (J-3)
- **`AllMembersSubmittedEvent` 발행 지점: `joinTrip` → `activateMembership`**, 판정 기준도 "전체 멤버 수" → **"ACTIVE 멤버 수"** (J-6)
- **`TripJoinCompletedEvent` 발행 지점: `joinTrip` → `activateMembership`** — 링크만 연 사람이 방장 알림을 트리거하지 않도록 (J-6, 2026-09-13)
- **`joinTrip` 재호출 동작: 403 → 현재 `myMemberStatus`를 담은 200** (J-3 멱등, 2026-09-13)
- **`TripJoinService.joinAsNewMember`의 `@TripActivity` 제거** — touch는 `activate` 한 곳으로 (J-9, 2026-09-13)
- **`trip-last-activity-at.md` L1 표** — "신규 참여 (join) ✓" → touch 안 함 (J-9)
- **BR-USER-006(방 입장 가능 조건) 개정** — `정기≥1 OR 개별≥1 OR is_all_free` → **`myMemberStatus = ACTIVE`**(방별 판정 단일화, J-7)
- **BR-USER-007** — "방 안 = `ACTIVE` ∧ `canEnterRoom`" → **"방 안 = `ACTIVE`"** (J-7)
- `TripAuthorizationInterceptor` `@TripMemberOnly` 검사: 멤버 + `ACTIVE` + `canEnterRoom` → **멤버 + `ACTIVE`** (J-7)
- **`TripServiceSupport.requireActiveMember` → `requireMembership` 리네임 (J-8, 2026-08-18 완료)** — 이 메서드는 멤버십 존재만 확인하고 상태는 보지 않는데(상태 검사는 바로 아래 `requireActive`), 이름이 ACTIVE를 검사하는 것처럼 읽힌다. J-1로 참여자도 `activate` 경로를 쓰게 되면서 `activateMembership` 안의 이 호출이 "이미 ACTIVE 검사가 걸려 있나?"로 오해될 여지가 커져 함께 정리
- **`TripController.activateMembership` Javadoc** — "멤버는 이 API를 쓰지 않고 join으로 바로 ACTIVE가 된다"는 서술이 J-1 이후 정면으로 틀려진다. `therapi-runtime-javadoc`을 통해 Swagger 설명으로 그대로 나가므로 같은 턴에 교체 (`@Operation(summary = "여행방 멤버십 활성화")`는 역할 중립이라 유지)
- `docs/specs/trip/schedule-participation-onboarding.md` D-JOIN-MEMBER 표 · `.claude/rules/client-platform.md` 멤버십 행

### REMOVED

- **전역 입장 게이트 전체 (J-7, 2026-08-18 확정 — 초안의 "응답에서만 제거"를 뒤집음)**
  - `users.is_all_free` **컬럼** · `User.isAllFree` 필드 · `User.applyAllFree`
  - `UserSummaryService.canEnterRoom` · `requireCanEnterRoom`(User/UUID 오버로드 2개) · `markAllFreeIfNoSchedules` · `clearAllFreeOnScheduleAdded`
  - `UserDirectoryPort.requireCanEnterRoom` · `markAllFreeIfNoSchedules` + `UserDirectoryAdapter` 구현
  - `UserErrorCode.SCHEDULE_ENTRY_REQUIRED` (403)
  - `TripAuthorizationInterceptor`의 `requireCanEnterRoom` 호출 · `ScheduleService`의 `clearAllFreeOnScheduleAdded`/`markAllFreeIfNoSchedules` 호출 3곳
  - `UserSummaryResponse.isAllFree` 필드 + `@Schema` + Controller `@ApiResponse` 예시 JSON 6종
- **BR-USER-011**(일정↔전부 free: `0행→is_all_free=true`, `추가→false`) — 규칙 자체가 소멸 (J-7)
- **`TripActivity.tripIdFromReturn` 옵션 + `TripActivityAspect`의 해당 분기** — J-9로 사용처가 0이 됨 (2026-09-13)
- **`TripJoinPreviewResponse` DTO** — hold 엔드포인트와 함께 소멸, 대체 API 없음 (J-1, 2026-09-13 확정)
- 스펙 문구 "멤버 신규 INSERT는 `ACTIVE`만 · 중간 `SCHEDULE_PENDING` 없음" · 규칙 문구 "SCHEDULE_PENDING = 방장 create 직후만(멤버 아님)"
- **`POST /api/v1/trips/join/hold` · `DELETE /api/v1/trips/{tripId}/join/hold` 엔드포인트 + 관련 Redis Lua 원자 체크·TTL 코드 전체** (J-4, 2026-08-17 확정 — B안 채택으로 Redis 없이 DB 락만으로 대체)

> **hold 처리 방식 확정 (2026-08-17):** 초안 시점엔 "hold는 건드리지 않는다"였으나, 사용자가 `join`을 플로우 맨 앞으로 옮기는 안을 채택하면서 hold의 존재 이유(①②)가 다른 방식으로 흡수된다 — ①은 J-4 DB 락, ②는 "나"(그대로 둔다, 자동 회수 없음) 결정으로 애초에 불필요. 남겨둘 이유가 없어 완전히 삭제한다(레거시 유지 금지 원칙).

## 요구사항

### Must Have

- [x] `joinAsNewMember`를 일정 플로우 **맨 앞**으로 이동 — 초대 링크 진입 직후 호출, `SCHEDULE_PENDING` 멤버 생성 (J-1)
- [x] `joinAsNewMember`가 `Trip` 행 비관적 락(`SELECT ... FOR UPDATE`) 하에서 정원 체크 + 멤버 INSERT를 한 트랜잭션으로 처리 (J-4, B안)
- [x] `POST /api/v1/trips/join/hold` · `DELETE .../join/hold` 엔드포인트 + Redis 원자 체크·TTL 코드 **완전 삭제** (J-4)
- [x] **전역 입장 게이트 완전 삭제 (J-7)** — `users.is_all_free` 컬럼 · `canEnterRoom`/`requireCanEnterRoom` · `markAllFreeIfNoSchedules`/`clearAllFreeOnScheduleAdded` · `SCHEDULE_ENTRY_REQUIRED` · `UserDirectoryPort` 두 메서드 · 인터셉터 호출 · `ScheduleService` 호출 3곳까지 한 번에 (STOP §4 — "응답에서만 제거" 금지)
- [x] `join` 응답을 `TripEntryResponse`(`tripId` · `status` · `myMemberStatus`)로 축소 — 입장 전 참여자에게 `inviteCode`가 나가지 않을 것 (J-3)
- [x] `CreateTripResponse` → `TripEntryResponse` 리네임을 같은 턴에 전부 반영(DTO·Controller·테스트·스펙·fe-context)
- [x] **`join` 멱등** — 이미 멤버면 새 row·이벤트 없이 현재 `myMemberStatus`를 담아 200 (J-3, 2026-09-13)
- [x] **`TripJoinCompletedEvent`도 `activate`로 이동** — `SCHEDULE_PENDING → ACTIVE` 전이가 실제로 일어난 호출에서만 발행 (J-6)
- [x] **정원 카운트 기준 = `SCHEDULE_PENDING` 포함 전체 멤버 row** · `activate`에는 정원 체크를 넣지 않음 (J-4)
- [x] **`last_activity_at` touch를 `activate` 한 곳으로** — `joinAsNewMember`의 `@TripActivity` 제거 + `trip-last-activity-at.md` L1 amend (J-9)
- [x] **`TripActivity.tripIdFromReturn` 옵션·Aspect 분기 삭제** — 사용처 0 (J-9, STOP §4)
- [x] **`AllMembersSubmittedEvent`를 `activate`에서 ACTIVE 멤버 수 기준으로 발행** — 일정 미제출 멤버로 정원이 찼을 때 방장에게 "전원 제출 완료" 알림이 가지 않을 것 (J-6)
- [x] `UserSummaryResponse`에서 `isAllFree` 제거 + Controller `@ApiResponse` 예시 JSON 6종(`AuthController` 2 · `UserController` 2 · `GoogleCalendarController` 2)에서도 제거 (J-5·J-7)
- [x] **BR-USER-006·007 개정 · BR-USER-011 삭제**를 `docs/product/business-rules/user.md`에 같은 턴에 반영 (J-7)
- [x] `trip-recommendation-algorithm.md`의 "응답 참여자 판정" 근거 문장에서 `isAllFree` 의존 서술 교체 — **판정 로직·결과는 불변**, 근거만 "ACTIVE 멤버 전원 = 응답 참여자"로 정리 (J-7)
- [x] `hasRegularSchedule`은 **유지** — 규칙 1·2 분기에 필요한 유일한 값 (이미 구현됨)
- [x] 이탈자(일정 미완료 후 방치된 `SCHEDULE_PENDING`) 자리는 **자동 회수하지 않는다** — 별도 TTL·배치 로직 추가 금지, 방 나가기로만 해제 (J-4 ②, "나" 확정)
- [x] `Breaking-Change-Reason:` 트레일러 (join 위치·정원 판정 방식 변경 + 응답 축소 + 엔드포인트 2개 삭제 + 필드 제거)
- [x] `./gradlew test` 통과

### Nice to Have

- 없음

## 설계

### J-1: `join`을 플로우 맨 앞으로 이동 (2026-08-17 최종 확정 — 초안 뒤집힘)

```
변경 전  초대링크 → hold → [일정 화면] → POST /trips/join → 즉시 ACTIVE
변경 후  초대링크 → POST /trips/join → SCHEDULE_PENDING → [일정 화면] → POST /trips/{id}/activate → ACTIVE
```

방장 흐름(`POST /trips` → `SCHEDULE_PENDING` → 일정 화면 → `activate` → `ACTIVE`)과 **완전히 같은 모양**이 된다. 프론트 라우팅은 방장·참여자 구분 없이 `myMemberStatus` 하나로 통일된다.

**초안 정정 이력:** 최초 초안은 "`join`을 앞으로 옮기면 hold와 시점이 겹쳐 정원 보장 장치를 통째로 재설계해야 한다"는 이유로 이 안을 기각했다. 그러나 재설계 비용은 **DB 비관적 락 하나 추가**로 충분히 감당할 수 있다는 사용자 판단에 따라(J-4), 다음 이유로 이 안을 채택한다.

1. **방장 흐름과 통일** — 두 역할이 같은 상태 전이를 거쳐 프론트 분기가 단순해진다.
2. **API 감소** — `hold` 엔드포인트 2개(`POST`/`DELETE .../join/hold`)가 완전히 사라진다.
3. **덤으로 해결되는 문제** — [`trip-calendar-window-pre-join.md`](trip-calendar-window-pre-join.md)([#110](https://github.com/Central-MakeUs/TripFit-server/issues/110))의 원인("참여자가 일정 플로우 시점에 멤버 row가 없다")이 `join`이 맨 앞으로 옮겨지면서 자연히 사라진다. 초안 작성 시점엔 이 이득을 포기하는 쪽을 택했으나, 재검토 결과 포기할 필요가 없었다.

**입장 전 방 정보 화면은 없다 (2026-09-13 사용자 확정).** hold 엔드포인트가 반환하던 `TripJoinPreviewResponse`(방 이름·여행지·희망 기간·정원·ACTIVE 인원)는 **대체 API 없이 그대로 사라진다** — 피그마에 참여자가 일정 확인 전에 방 정보를 보는 화면이 없다. 따라서 J-3의 축소 응답으로 충분하고, `SCHEDULE_PENDING` 참여자를 위한 별도 조회 API는 만들지 않는다.

**정원 보장은 J-4(DB 비관적 락)가 대체한다** — Redis hold 없이도 동시 요청에서 정원이 뚫리지 않는다. **이탈자 자리는 자동 회수하지 않는다**(J-4 ②, "나" 확정) — hold의 10분 TTL이 하던 자동 회수 기능은 없어지고, 링크만 열어본 사람이 자리를 오래 차지할 수 있다는 리스크를 감수한다.

### J-2: ~~`is_all_free` 설정을 activate 한 곳으로~~ → **J-7에 흡수 (2026-08-18)**

초안은 "`join`·`activate` 두 곳에서 부르던 `markAllFreeIfNoSchedules`를 `activate` 한 곳으로 줄인다"였다. J-7에서 `is_all_free`와 그 설정 로직을 **통째로 삭제**하므로 이 절은 별도 작업으로 남지 않는다.

### J-3: join 응답 축소

`TripServiceSupport.toDetail`은 `inviteCode`를 무조건 담는다. 입장 전(`SCHEDULE_PENDING`) 참여자에게 초대 코드가 나가면 "초대 공유는 방장 ∧ ACTIVE만"(`kakao-invite-share.md` S-1·S-2)을 어긴다. create와 같은 축소 응답을 쓴다.

두 응답이 **같은 의미**(방 진입 상태)이므로 DTO를 하나로 합치고 이름을 `TripEntryResponse`로 통일한다 — 같은 개념에 같은 이름(`spring-boot-java.md` 네이밍 우선 원칙).

**join 재호출은 예외가 아니라 현재 상태를 반환한다 (2026-09-13 확정 — 초안 뒤집힘).** 현행 `joinTrip`은 이미 멤버인 호출자에게 `support.requireActive`를 적용해, `SCHEDULE_PENDING`이면 403 `SCHEDULE_ACTIVATION_REQUIRED`를 던진다. J-1 이후 `join`은 **초대 링크를 여는 순간** 호출되므로, 일정 입력 중 앱을 껐다가 링크를 다시 타면 매번 403이 된다. 그러면 프론트는 그 에러 코드를 "일정 화면으로 보내라"로 해석해야 하는데, 이는 J-5가 없애려는 **"에러 코드로 라우팅"** 패턴 그 자체다.

| 호출자 상태 | 변경 전 | 변경 후 |
|---|---|---|
| 비멤버 | INSERT → `ACTIVE` 상세 | INSERT → `TripEntryResponse(myMemberStatus = SCHEDULE_PENDING)` |
| `SCHEDULE_PENDING` | 403 `SCHEDULE_ACTIVATION_REQUIRED` | **200 `TripEntryResponse(SCHEDULE_PENDING)`** — 새 row를 만들지 않음 |
| `ACTIVE` | 상세 반환 | **200 `TripEntryResponse(ACTIVE)`** |

방장·참여자·재진입이 전부 `myMemberStatus` 하나로 라우팅된다(J-5). 정원 체크와 이벤트 발행은 **신규 INSERT 경로에서만** 수행하므로, 재호출이 자리를 두 번 소비하거나 알림을 중복 발송하지 않는다.

### J-4: 정원 보장 — hold가 하던 두 가지 일을 분리해서 본다

**초안 정정 (2026-08-17):** 최초 작성 시 "join이 hold와 같은 시점으로 앞당겨지므로 hold는 중복 — 삭제한다"고 적었으나 **틀렸다.** 겹치는 것은 **시점**뿐이고, hold가 제공하던 보장은 `join`이 대체하지 못한다. 이 절은 그 정정 결과다.

hold는 실제로 두 가지 일을 하고 있었다.

| | hold가 하던 일 | `join`(멤버 row INSERT)이 대신할 수 있나 |
|---|---|---|
| **① 동시 요청에도 정원 초과 없음** | Redis Lua로 "확인 + 기록"을 원자적으로 처리 | **못 한다** — 카운트 조회와 INSERT 사이가 벌어져 있어 동시 요청이 모두 "자리 있음"으로 판단할 수 있다 |
| **② 이탈자 자리 자동 회수** | 10분 TTL로 자연 소멸 | **못 한다** — 멤버 row는 명시적으로 지워야 사라진다 |

또한 이 저장소에는 **락이 전혀 없다**(`@Version`·비관적 락 사용례 0건, `TripRepository`는 평범한 조회뿐). 즉 **정원을 지키는 장치는 현재 hold가 유일**하며, 대체재 없이 지우면 정원 초과가 실제로 발생한다.

**참고 — ①은 원래 MVP 필수 요건이 아니었다:** [`trip-join-capacity-hold.md`](trip-join-capacity-hold.md)(hold 스펙, wave 4)의 배경에 "MVP는 [동시 요청 시 정원 초과]를 감수. 선점/예약은 wave 4"라고 명시돼 있다. 즉 hold는 버그를 막으려고 만든 게 아니라, "일정을 다 채운 뒤 늦게 도착한 사람이 억울하게 409를 받는" UX를 개선하려고 나중에(2026-08-10) 추가한 wave 4 편의 기능이었다. 이번 B안(DB 비관적 락) 채택은 그 개선 수준을 **그대로 유지**하는 것뿐, MVP 요건을 새로 만드는 것이 아니다.

**①이 오히려 악화되는 이유:** 기존에는 `join`이 플로우 **끝**에 있어 여러 명이 같은 순간 도달할 확률이 낮았다. J-1로 `join`을 플로우 **시작**으로 옮기면 "여러 명이 동시에 초대 링크를 여는" 흔한 상황이 그대로 경합이 된다. 따라서 "hold 도입 이전 수준으로 회귀"가 아니라 **그보다 나쁜 상태**가 된다.

#### ① 정원 초과 방지 — **B안(DB 비관적 락) 확정 (2026-08-17)**

| 안 | 방식 | 트레이드오프 |
|----|------|--------------|
| A. hold 로직 유지 (기각) | 엔드포인트 2개만 없애고 Redis 원자 체크는 `join` 내부에서 계속 사용 | 검증된 보장을 유지하지만, 정답이 되는 데이터가 Redis 카운터·DB 멤버 row **두 곳**으로 남는다 — 두 값이 어긋날 위험(예: Redis 체크는 통과했는데 DB INSERT가 실패하면 카운터를 되돌리는 보정 로직이 별도로 필요, Redis 장애 시 join 전체가 막힐 새 위험 추가) |
| **B. DB 비관적 락 (채택)** | `trip` 행을 잠그고 카운트+INSERT를 한 트랜잭션에서 처리 | 정답이 DB **한 곳**뿐이라 어긋날 일이 없다. hold 코드(Redis Lua·TTL) 전부 삭제 가능. 이 저장소에 락 사용례가 없어 새 패턴이 하나 생기지만, 방 정원 규모(수십 명 미만)에서 락 경합은 무시할 수준 |

**카운트 기준 (2026-09-13 명시):** 락 아래에서 세는 대상은 **삭제되지 않은 전체 멤버 row — `SCHEDULE_PENDING` 포함**이다(`countByTripIdAndDeletedAtIsNull`, 현행과 동일). ②가 "이탈자 자리를 회수하지 않는다"로 확정된 이상, `SCHEDULE_PENDING`도 자리를 차지한다는 것이 정의다. 반대로 `activate`에는 정원 체크를 **넣지 않는다** — 이미 자리를 확보한 사람의 상태 전이일 뿐이라 여기서 409가 새로 생기면 안 된다(②에서 "가"안을 기각한 이유와 동일).

**채택 이유:** ②를 "나"(자동 회수 없음)로 정하면서 Redis가 원래 하던 두 가지 일 중 하나(TTL 자동 회수)가 애초에 필요 없어졌다. 남은 이유(원자적 체크)만으로 별도 인프라(Redis)를 계속 끌고 갈 근거가 부족해, DB 트랜잭션 하나로 대체한다.

**"Redis 코드 전부 삭제"의 범위 (2026-09-13 정정):** 삭제 대상은 **hold 관련 코드뿐**이다 — `TripJoinHoldService`(Lua·TTL·ZSET)와 그 호출부. Redis 인프라 자체는 JWT 토큰 무효화(`RedisTokenRevocationChecker`, [`decisions/010`](../../decisions/010-redis-infra.md))가 계속 사용하므로 남는다.

#### ② 이탈자 자리 — **"나" (그대로 둔다) 확정 (2026-08-17)**

`join`이 맨 앞으로 옮겨지고 멤버 row가 곧바로 생기므로, 링크만 열어보고 일정 확인을 끝내지 않은 사람이 자리를 계속 차지할 수 있다. **자동 회수 로직(TTL 배치 등)은 추가하지 않는다** — 자리 해제는 기존 "방 나가기" API로만 가능하다.

- **API 영향 없음** — `activate`에 새 실패 케이스가 생기지 않는다(기각한 "가"안이었다면 `TRIP_MEMBER_FULL`(409)이 `activate`에 새로 생겼을 것).
- **감수하는 리스크** — 링크만 열어본 사람들이 방치된 `SCHEDULE_PENDING`으로 자리를 채우면, 실제로 들어오려는 사람이 정원 초과로 막힐 수 있다. 발생 빈도가 실제로 문제가 되면 후속 이슈로 "가"안(오래된 대기자 제외)을 다시 검토한다.

### J-6: "전원 일정 제출" 알림 기준 변경

현재 `joinTrip` 말미에서 정원이 차면 방장에게 `ALL_MEMBERS_SUBMITTED` 알림("모든 참여자의 일정이 제출되었어요! 추천 일정을 받아보세요")을 보낸다. 근거는 코드 주석 그대로 **"멤버는 join 즉시 ACTIVE라 정원 도달이 곧 전원 제출 완료"**다.

J-1 이후 이 전제가 깨진다 — `SCHEDULE_PENDING` 멤버로 정원이 차면 **아무도 일정을 확인하지 않았는데** 방장에게 "전원 제출 완료" 알림이 가고, 방장이 그 상태로 추천을 돌리게 된다.

| | 변경 전 | 변경 후 |
|---|---|---|
| 발행 지점 | `TripCommandService.joinTrip` | **`activateMembership`** |
| 판정 기준 | 전체 멤버 수 + 1 ≥ 정원 | **ACTIVE 멤버 수 ≥ 정원** |

이 변경은 알림을 **더 정확하게** 만든다 — 지금은 "자리가 찼다"를 "다 제출했다"로 간주하지만, 변경 후에는 실제로 전원이 일정 확인을 마쳤을 때만 발송된다. 방장 자신도 `activate`를 거치므로 카운트에 자연스럽게 포함된다.

**`TripJoinCompletedEvent`도 같이 옮긴다 (2026-09-13 확정).** 같은 자리에서 발행되는 "OO님이 참여했어요"(방장 알림)도 J-1 이후에는 **초대 링크만 열어본 사람**까지 트리거한다. 발행 지점을 `activateMembership`으로 옮겨, 일정 확인을 마치고 실제로 방에 들어온 시점에만 발송한다. 두 이벤트 모두 **`SCHEDULE_PENDING → ACTIVE` 전이가 실제로 일어난 호출에서만** 발행한다 — 이미 `ACTIVE`인 사람이 `activate`를 다시 불러도(idempotent) 재발송되지 않는다.

관련: BR-NOTI-002 · `NotificationType.ALL_MEMBERS_SUBMITTED`

### J-5: 프론트가 보는 값

| 값 | 답하는 질문 | 비고 |
|---|---|---|
| `myMemberStatus` | 이 방에서 일정 확인을 끝냈나 | 방별 상태 — 라우팅의 기준 |
| `hasRegularSchedule` | 정기 일정이 있나 | 규칙 1 vs 2 분기 |
| `hasPreSchedule` | 일정을 뭐라도 넣었나 | 마이페이지 표시용 |

세 값이 **각각 하나의 질문에만 답한다.** 프론트가 두 값을 `||`로 엮어야 하는 자리가 남지 않는 것이 이 표의 목적이다 — QA 이슈 1·2의 원인이 정확히 그 조합식(`hasPreSchedule || isAllFree`)이었다.

`canEnterRoom`은 **노출하지 않는다.** J-1 이후 방 입장 여부는 `myMemberStatus`가 답하고, J-7로 전역 게이트 자체가 사라져 노출할 값이 없다. `isAllFree`도 응답·컬럼 모두에서 사라진다.

### J-9: `last_activity_at` touch 위치 + Aspect 수정 (2026-09-13 확정)

현재 touch는 `TripJoinService.joinAsNewMember`에 `@TripActivity(tripIdFromReturn = true)`로 걸려 있고, `TripActivityAspect.resolveTripId`는 **반환값이 `TripDetailResponse`일 때만** tripId를 꺼낸다.

**① J-3과 충돌해 조용히 깨진다.** join 응답이 `TripEntryResponse`가 되면 `instanceof TripDetailResponse`가 실패해 `resolveTripId`가 `null`을 반환하고, Aspect는 **예외 없이 touch를 건너뛴다**. 컴파일도 테스트도 통과하므로 발견되지 않는다.

**② touch는 `activate` 한 곳으로 (제안·채택).**

| 이벤트 | 변경 전 | 변경 후 |
|---|---|---|
| `POST /trips/join` | touch | **touch 안 함** |
| `POST /trips/{tripId}/activate` | touch | touch (그대로) |

근거: J-1 이후 `join`은 "초대 링크를 열었다"에 불과하다 — 방에 실질적 변화가 없는데도 링크만 열어보고 이탈한 사람 때문에 그 방이 방장 홈 목록 맨 위로 올라간다. `last_activity_at`은 **홈 정렬에만** 쓰이므로(EXPIRED 전환은 `end_range` 기준이라 무관 — [`trip-home-schedulers.md`](trip-home-schedulers.md) S1) 영향 범위는 정렬 하나다. 참여자는 반드시 `activate`를 거치므로 "신규 참여" touch는 **사라지지 않고 시점만 정확해진다**(자리만 잡은 시점 → 일정 확인을 마친 시점). 개인 일정 수정으로 참여 중인 방이 전부 상단에 올라가는 것을 막은 L2 결정과 같은 축이다.

**③ 그 결과 `tripIdFromReturn`은 사용처가 0이 된다 → 옵션과 Aspect 분기를 함께 삭제한다** (STOP §4 — 이번 교체로 죽은 코드는 같은 PR에서 제거). 남는 touch 지점은 전부 `tripIdParam` 방식이라, "반환 타입이 바뀌면 조용히 null이 되는" 함정 자체가 사라진다.

[`trip-last-activity-at.md`](trip-last-activity-at.md)(Approved) L1 표의 **"신규 참여 (join) ✓" 행을 같은 턴에 amend**해야 한다 — L1이 touch 이벤트의 SSOT다.

### J-7: 전역 입장 게이트 삭제 — 게이트를 하나로 (2026-08-18 사용자 확정, A안)

지금 "방에 들어갈 수 있나"를 판단하는 장치가 **두 개**다.

| | 판정 | 실패 시 | 단위 |
|---|---|---|---|
| 전역 게이트 | `is_all_free OR 일정 1건 이상` (`canEnterRoom`) | 403 `SCHEDULE_ENTRY_REQUIRED` | 사용자 |
| 방별 게이트 | `myMemberStatus == ACTIVE` | 403 `SCHEDULE_ACTIVATION_REQUIRED` | 방 |

**전역 게이트는 이미 아무것도 막지 못한다 (코드 확인, 2026-08-18).** `@TripMemberOnly`는 `ACTIVE`를 먼저 요구하고(`TripAuthorizationInterceptor`), `ACTIVE`가 되는 유일한 경로인 `activate`·`join`이 둘 다 `markAllFreeIfNoSchedules`를 호출해 전역 조건을 **무조건 참으로 만들어 놓는다**(`TripCommandService.activateMembership`, `TripJoinService.joinAsNewMember`). 정기 일정을 전부 삭제해도 `ScheduleService.deleteRegular`가 다시 켜준다. 즉 `ACTIVE`인 멤버에게 이 게이트가 거짓이 되는 경로가 **없다** — `requireCanEnterRoom` 호출부도 `@TripMemberOnly` 분기 한 곳뿐이다.

**그런데 이 죽은 게이트가 실제 사고의 원인이었다.** `is_all_free`는 사용자가 켜는 값이 아니라 첫 입장 때 서버가 자동으로 켜는 값이라, **두 번째 입장부터 프론트가 보는 값이 달라진다.** QA 이슈 1이 "방을 두 번 입장해야 재현된다"는 기묘한 조건을 가진 이유가 정확히 이것이다. 값을 노출하든(폐기된 `canEnterRoom` 안) 숨기든, 게이트가 둘로 남아 있는 한 프론트는 다시 조합식을 만들 표면을 갖는다.

**따라서 응답에서만 빼지 않고 장치째 삭제한다.** 삭제 목록은 위 REMOVED 절.

**BR 개정이 포함된다 (기획 문서 변경 — 승인 필요):**

| BR | 변경 전 | 변경 후 |
|---|---|---|
| BR-USER-006 방 입장 가능 조건 | 정기≥1 OR 개별≥1 OR `is_all_free` | **`myMemberStatus = ACTIVE`** (= 그 방의 일정 확인을 마침) |
| BR-USER-007 방 안 접근 | `ACTIVE` ∧ `canEnterRoom` | **`ACTIVE`** |
| BR-USER-011 일정↔전부 free | 0행→`true`, 추가→`false` | **삭제** (규칙이 가리키던 컬럼이 없어짐) |

**추천 알고리즘에는 영향이 없다.** `trip-recommendation-algorithm.md`의 "응답 참여자 = 이 방의 ACTIVE 멤버 전원" 판정은 `isAllFree`를 **읽지 않는다**(코드 확인: 추천 패키지에 `isAllFree` 참조 0건). 스펙 본문이 그 판정의 *근거*로 `isAllFree`를 언급할 뿐이라, 근거 문장만 고치면 되고 결과는 동일하다 — 일정 0건 멤버는 "resolve 결과가 비어 있음 = 모든 슬롯 가능"으로 지금과 똑같이 계산된다.

**남는 것:** 일정을 하나도 입력하지 않은 사용자를 "전부 가능"으로 취급한다는 **동작 자체는 그대로**다. 사라지는 것은 그 사실을 별도 컬럼에 기록하고 게이트로 재검사하던 층뿐이다.

## API

| Method | Path | 변경 |
|--------|------|------|
| `POST` | `/api/v1/trips/join` | 멤버를 `SCHEDULE_PENDING`으로 생성(호출 시점: 일정 플로우 진입 **전**) · 비관적 락으로 정원 체크 · 응답 축소 · **이미 멤버면 현재 `myMemberStatus`로 200(멱등)** |
| `POST` | `/api/v1/trips/{tripId}/activate` | 계약 불변 — 참여자도 호출한다는 점만 문서화. 알림 2종(`ALL_MEMBERS_SUBMITTED`·참여 완료) 발행 지점이 여기로 이동 |
| `POST` | `/api/v1/trips/join/hold` | **삭제** |
| `DELETE` | `/api/v1/trips/{tripId}/join/hold` | **삭제** |
| `POST` | `/api/v1/auth/login` 외 `UserSummaryResponse` 응답 6종 | `isAllFree` 제거 (J-5·J-7) — `login` · `GET /auth/me` · `PATCH /users/profile` · `PATCH /users/onboarding/name` · `POST`/`DELETE /users/google-calendar` |

### 에러

일정 확인 전 방 안 API 호출은 기존 `SCHEDULE_ACTIVATION_REQUIRED`(403)가 그대로 처리한다. 신규 `ErrorCode`는 없고, **`SCHEDULE_ENTRY_REQUIRED`(403)는 삭제된다**(J-7) — 이 코드가 실제로 발생하는 경로가 없었고, 남은 방 접근 실패는 전부 `SCHEDULE_ACTIVATION_REQUIRED`가 답한다. Controller `@ApiResponse` description 2곳(`TripController` · `TripMemberController`)에서도 같은 턴에 제거한다. 정원이 찬 방에 대한 `TRIP_MEMBER_FULL`(409)은 **호출 지점만 이동**한다 — 기존에는 `hold` 획득 시점에서 던졌다면, hold 삭제 후에는 `POST /api/v1/trips/join`이 비관적 락 하에 카운트 체크를 하면서 동일 코드로 던진다(`activate`에는 새 실패 케이스가 추가되지 않는다 — J-4 ② "나" 확정).

## 완료 기준

- [x] `./gradlew test` 통과
- [x] join 직후 방 상세·멤버·달력·추천 호출이 **403 `SCHEDULE_ACTIVATION_REQUIRED`**로 막히는 통합 테스트
- [x] join → activate 순서를 거치면 정상 입장되는 통합 테스트
- [x] join 응답에 `inviteCode`가 **없음**을 확인하는 테스트
- [x] 이미 `SCHEDULE_PENDING`인 사람이 join을 다시 호출하면 **200 + `myMemberStatus=SCHEDULE_PENDING`**, 멤버 row가 늘지 않고 이벤트도 재발행되지 않음 (J-3 멱등)
- [x] `ACTIVE`인 사람이 join을 다시 호출해도 **200 + `myMemberStatus=ACTIVE`** (J-3 멱등)
- [x] **`SCHEDULE_PENDING` 멤버로 정원이 차도 `ALL_MEMBERS_SUBMITTED` 알림이 발송되지 않고, 전원 `activate` 후에야 발송되는 테스트** (J-6)
- [x] 정원이 찬 방에 **동시 join 요청**이 들어와도 비관적 락으로 정원을 넘기지 않는 테스트 — 마지막 1자리를 다수가 동시에 요청하는 케이스 포함 (J-4 ①)
- [x] `join`은 `last_activity_at`을 갱신하지 않고 `activate`가 갱신함 (J-9)
- [x] `TripActivity.tripIdFromReturn` 참조가 코드에 0건 (J-9)
- [x] 링크만 연(`SCHEDULE_PENDING`) 사람 때문에 방장에게 참여 완료 알림이 가지 않고, `activate` 후에 발송됨 (J-6)
- [x] `SCHEDULE_PENDING`으로 자리를 차지한 채 방치된 멤버가 **자동으로 제외·삭제되지 않음**을 확인하는 회귀 테스트 (J-4 ②, "나" 확정 — 나중에 실수로 TTL 로직이 추가되는 것을 막기 위함)
- [x] **`SCHEDULE_ENTRY_REQUIRED`·`canEnterRoom`·`is_all_free` 참조가 코드에 0건**임을 grep으로 확인 (J-7)
- [x] 일정 0건인 사용자가 `join` → `activate` → 방 안 API까지 정상 통과하는 통합 테스트 (전역 게이트 삭제 후에도 막히지 않을 것)
- [x] 정기 일정을 전부 삭제한 `ACTIVE` 멤버가 기존 방 API를 계속 쓸 수 있음을 확인 (J-7 회귀 — 삭제 전에는 `deleteRegular`의 `markAllFreeIfNoSchedules`가 보장하던 동작)
- [x] 추천 결과가 J-7 전후로 동일함을 확인 — 일정 0건 멤버가 여전히 "전부 가능" 응답 참여자로 계산될 것
- [x] 생성된 `/v3/api-docs`에서 hold 엔드포인트 2개와 `isAllFree`가 **사라진 것** 확인
- [x] `REMOVED` 항목(hold 엔드포인트·Redis 코드 포함)이 코드·문서에서 실제 삭제됐는지 (STOP §4)

## 영향받는 문서

| 문서 | 갱신 |
|------|------|
| `trip/schedule-participation-onboarding.md` | D-JOIN-MEMBER 표 · D-JOIN-TRIP-FLOW 대상 표 · 변경 이력 |
| `trip/trip-join-capacity-hold.md` | **Superseded** 전환 (hold 완전 폐지 확정 — B안) |
| `trip/trip-calendar-window-pre-join.md` ([#110](https://github.com/Central-MakeUs/TripFit-server/issues/110)) | **범위 축소** — 본래 증상(미가입 참여자가 초대받은 방 기간을 조회 못 함)은 J-1로 해소되므로 삭제. 근거: `TripMemberRepository.findMaxOngoingEndRangeByUserId`가 멤버 **상태를 보지 않아** `SCHEDULE_PENDING` row도 C1 상한 계산에 포함된다(코드 확인 2026-08-18). 해결안 A/B/C도 전부 불필요(특히 B는 hold 의존). **남는 범위는 "`GET /calendar`는 윈도우를 검증하는데 `PATCH /personal`은 안 하는 비대칭" 하나** — 이슈를 닫지 말고 이 항목만 남길 것 |
| `trip/trip-room-api.md` | join/activate 계약 |
| `trip/kakao-invite-share.md` | 초대 코드 노출 시점 재확인 |
| `.claude/rules/client-platform.md` | 멤버십 상태 행 ("멤버 join=`ACTIVE` 즉시" → `SCHEDULE_PENDING` 경유) |
| `.claude/rules/spring-boot-java.md` | Javadoc 예시의 "멤버는 이 API를 쓰지 않고 join으로 바로 ACTIVE가 된다" 문구 교체 (Controller Javadoc과 동일 내용) |
| `fe-context/trip/trip-room-create-join.md` | 규칙 1·5·6 · 위반 2건 수정 지침 |
| `fe-context/trip/trip-activate-api.md` | 참여자도 activate를 호출하도록 갱신 + `trip-owner-activate-api.md`에서 rename(2026-09-13 완료) — 방장 전용 API가 아니게 됨 |
| `fe-context/user/user-onboarding.md` | 응답 예시에서 `isAllFree` 제거 |
| [`trip-last-activity-at.md`](trip-last-activity-at.md) | L1 표 "신규 참여 (join)" → touch 안 함, `activate`로 단일화 (J-9) |
| `product/design/figma-wireframe-v1.md` · `auth/auth-social-login.md` · `user/user-onboarding.md` | 응답 예시 |
| `architecture/erd.md` | **`users.is_all_free` 컬럼 삭제** (J-7 — 초안의 "변경 없음"을 뒤집음) |
| `product/business-rules/user.md` | **BR-USER-006·007 개정 · BR-USER-011 삭제** + 확정 이력 문구(`Skip+0행 → activate/join 시 is_all_free=true`) amend (J-7) |
| `trip/trip-recommendation-algorithm.md` | "응답 참여자 판정" 근거 문장에서 `isAllFree` 의존 서술 교체 — 판정 결과 불변 (J-7) |
| `decisions/008-trip-authorization-guard.md` | `@TripMemberOnly` 정의에서 `canEnterRoom` 조건 제거 (J-7) |
| `architecture/api-response.md` · `product/glossary.md` | `SCHEDULE_ENTRY_REQUIRED` · "전부 free" 용어 항목 삭제 (J-7) |
| `product/flows/trip-create-join-guide.md` · `flows/schedule-edit.md` · `fe-context/user-schedule/vacation-policy.md` · `fe-context/trip/trip-recommendation-confirm-flow.md` · `specs/trip/trip-member-leave.md` · `specs/trip/kakao-invite-share.md` · `specs/user/user-account-withdrawal.md` · `specs/user-schedule/vacation-policy-user-migration.md` · `specs/trip/trip-room-api.md` · `.claude/rules/spring-boot-java.md` | `isAllFree`/`SCHEDULE_ENTRY_REQUIRED` 언급 정리 (J-7 — grep 기준 전수) |

## 미해결 / 확인 필요

- ~~`#35`(정원 hold) 이슈 처리~~ → **불필요.** `#35`는 2026-08-14에 이미 Closed였다(구현 완료 시점). hold 코드·엔드포인트는 `#114`에서 삭제됐고 스펙은 Superseded로 전환했다
- 프론트 배포 순서: join 위치 이동·응답 축소·hold 엔드포인트 삭제는 클라이언트 대응이 끝난 뒤 배포해야 한다 — 특히 **`join`을 호출하는 시점 자체가 바뀌므로**(맨 앞으로) 프론트가 이 변경을 놓치면 "화면을 다 채운 뒤에만 join 호출"이라는 예전 가정을 그대로 구현할 위험이 있다
- `activate` 응답(`TripDetailResponse`)에는 `inviteCode`가 포함돼, 참여자도 입장 직후 초대 코드를 받는다 — `kakao-invite-share.md` S-1·S-2("공유는 방장 ∧ ACTIVE만")와 DTO 레벨에서 어긋난다. **현행 동작 유지**이므로(기존에도 join 응답으로 나갔다) 본 스펙 범위에 넣지 않고, 별도 판단 대상으로 남긴다 (2026-09-13 발견)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-13 | **구현 완료 (`#114`)** — J-1(`join`을 플로우 맨 앞·`SCHEDULE_PENDING`) · J-3(`TripEntryResponse` 축소 + 멱등) · J-4(초대코드 조회를 `SELECT ... FOR UPDATE`로 잠그고 카운트+INSERT를 한 트랜잭션에서 처리, hold 코드 전체 삭제) · J-6(알림 2종을 `activate`로 이동) · J-9(touch를 `activate`로 일원화, `tripIdFromReturn` 삭제). 구현 중 확인: 락을 트랜잭션의 **첫 조회**로 두지 않으면 REPEATABLE READ 스냅샷 때문에 정원 카운트가 옛 값을 읽어 동시 join 8건 중 5건이 통과했다 — `findByInviteCodeForUpdate`를 join 트랜잭션의 첫 쿼리로 고정해 해결. 알림은 `TripMemberRole.MEMBER`일 때만 참여 완료 이벤트를 발행(방장 자기 방 제외) |
| 2026-09-13 | **재검토 후 6건 확정 (사용자 결정)** — ① 입장 전 방 정보 화면은 없다(피그마 기준) → `TripJoinPreviewResponse` 대체 API 없이 삭제 ② `join` **멱등화** — 이미 멤버면 403이 아니라 현재 `myMemberStatus`로 200(에러 코드로 라우팅하는 구조 제거, J-5와 정합) ③ `TripJoinCompletedEvent`도 `activate`로 이동 ④ **J-9 신설** — `last_activity_at` touch를 `activate` 한 곳으로 모으고, `@TripActivity(tripIdFromReturn)`이 J-3의 응답 축소로 조용히 깨지는 문제를 옵션·Aspect 분기 삭제로 해소 ⑤ 정원 카운트 기준(`SCHEDULE_PENDING` 포함)·`activate` 정원 미체크 명시 ⑥ "Redis 코드 전부 삭제"의 범위를 hold 코드로 한정(토큰 무효화는 Redis 계속 사용) |
| 2026-08-18 | **J-7 추가 + 적용 범위 확정 (사용자 결정, A안)** — ① "매 방 입장" 해석을 **(가) 새 방 참여 시 1회**(재진입 제외)로 확정 ② 전역 입장 게이트(`is_all_free` 컬럼·`canEnterRoom`·`SCHEDULE_ENTRY_REQUIRED`·`markAllFreeIfNoSchedules`)를 **응답에서만 제거 → 장치째 삭제**로 전환. 근거: `ACTIVE` 멤버에게 항상 참이라 아무것도 막지 못하는 죽은 게이트인데, 자동으로 켜지는 특성 때문에 QA 이슈 1의 "두 번째 입장부터 달라짐" 재현 조건을 만들고 있었음 ③ 선행 검토였던 `user-schedule/schedule-state-response.md`를 **Superseded**로 전환하고 유효한 진단만 이관 — `regularScheduleState`·선언 저장·`canEnterRoom` 노출은 모두 폐기 ④ J-2는 J-7에 흡수 |
| 2026-08-17 | **J-1·J-4 최종 확정 (사용자 결정)** — 초안이 기각했던 "`join`을 플로우 맨 앞으로 이동"을 채택으로 뒤집음. 이유: 방장 흐름과의 통일성, hold 엔드포인트 2개 삭제, `#110`(달력 조회 윈도우 공백) 부수 해결. J-4 ①은 B안(DB 비관적 락) 확정 — Redis hold 코드 전체 삭제. J-4 ②는 "나"(이탈자 자리 자동 회수 안 함) 확정 — `activate`에 새 실패 케이스가 추가되는 "가"안은 기각 |
| 2026-08-17 | **J-4 정정 + J-6 추가** — ① "join이 hold와 중복이므로 삭제"는 오판이었음을 정정(겹치는 것은 시점뿐, 정원 보장 능력은 이전되지 않음). 이 저장소에 락 사용례가 0건이라 hold가 유일한 정원 보장 장치임을 확인하고, 대체 방식을 미결정 사항으로 분리 ② `AllMembersSubmittedEvent`가 "정원 도달 = 전원 제출"을 전제로 `joinTrip`에서 발행되고 있어, J-1 적용 시 일정 미제출 상태로 방장에게 알림이 가는 문제를 발견 — `activate`·ACTIVE 카운트 기준으로 이동 |
| 2026-08-17 | 최초 작성. 선행 검토였던 `user-schedule/schedule-state-response.md`(정기 "없어요" 선언 저장 + `canEnterRoom` 노출)는 **폐기** — 분기는 정기 일정 유무 하나로 충분하고, 선언 저장은 "이미 답했으니 건너뛰어도 된다"는 방향이라 위 절대 규칙과 어긋남 |
