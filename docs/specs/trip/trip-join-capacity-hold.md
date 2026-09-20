# 여행방 join 정원 선점·예약 (Capacity hold)

> wave: **4**
> 상태: **Superseded** (2026-08-19, `#114`) — 구현·배포됐던 기능이지만 [`trip-join-schedule-gate.md`](trip-join-schedule-gate.md) J-4가 Redis hold를 **DB 비관적 락**으로 대체하며 엔드포인트 2개와 Redis Lua·TTL 코드를 전부 삭제했다. 아래 본문은 이력으로만 읽을 것 — 현행 정원 보장은 `POST /trips/join`이 `trip` 행을 잠근 채 카운트+INSERT를 한 트랜잭션에서 처리한다
> MVP: Out — #22 새 join 모델에서 **동시 플로우 경쟁은 MVP 감수**
> 관련: [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) · [`trip-room-api.md`](trip-room-api.md) D8
> GitHub: **#35**
> 선행: #22 late-join · [`trip-room-api.md`](trip-room-api.md) D8 · **`#4`(RTR+Redis) — 같은 배포 이미지, `feat/4-rtr-redis-blacklist`에서 분기한 `feat/35-trip-join-capacity-hold`에서 개발. Redis 연결(`spring-boot-starter-data-redis` + `application.yml` `spring.data.redis.*`)을 재사용, `#4` merge 전까지 함께 대기**

## 목표

참여자가 **확인 플로우 마지막에야** `trip_member`를 생성하는 모델에서, 정원(`memberCount`) 자리를 **플로우 진입 시점에 예약**해 늦게 완료한 사용자가 억울하게 409로 튕기는 UX를 줄인다.

## 배경

#22 확정(참여자): 정기→개별 플로우 후 **가입 API 한 번**으로 멤버 INSERT.
정원 검사는 INSERT 시점 → 플로우 중 여러 명이 동시에 진행하면 **먼저 완료한 1명만 성공**, 나머지는 `TRIP_MEMBER_FULL`(409).

MVP는 이를 **감수**. 선점/예약은 wave 4.

**현재 코드 위치 (2026-08-08 재확인):** [`TripCommandService.joinTrip()`](../../../src/main/java/com/tripfit/tripfit/trip/service/TripCommandService.java) — `countByTripIdAndDeletedAtIsNull()`로 카운트 → `if (joinedMemberCount >= trip.getMemberCount())` 체크 → `tripJoinService.joinAsNewMember()` INSERT. 세 단계 사이 락 없음.

**서버가 관찰 가능한 "플로우 진입" 지점이 지금은 없음:** 정기·개별 일정 화면은 `tripId`와 무관한 User 전역 API(`/users/schedule/regular`, `/users/schedule/personal`)라서, 서버 입장에서 "이 사용자가 어느 여행방 플로우에 진입했는지" 알 방법이 `POST /trips/join` 호출 전까지 전혀 없다. 그래서 hold를 걸려면 **신규 엔드포인트가 필수**다 (2026-08-10 확인).

**배포 제약 (2026-08-10):** 앱은 이미 릴리즈된 상태. `#4`(RTR+Redis)는 프론트가 RTR 변경사항(refresh token 매 회 재발급 등)에 대응할 때까지 프로덕션 배포를 보류 중. Redis 서버 자체(EC2 D)는 이미 프로비저닝 완료([`010-redis-infra.md`](../../decisions/010-redis-infra.md))되어 있으나, Spring Boot 쪽 Redis 연결 코드는 아직 `main`이 아닌 `feat/4-rtr-redis-blacklist` 브랜치에만 있다. `#35`가 Redis 카운터 방식을 쓰기로 하면서, **`#35`의 배포 시점도 `#4`의 배포 시점(프론트 완료 대기)에 함께 묶이는 것을 인지하고 감수**하기로 함(같은 배포 이미지이므로).

## Must Have (wave 4)

- [x] 참여자가 초대코드로 방에 진입할 때, 신규 API 호출로 **10분 TTL hold** 생성 (Redis)
- [x] `POST /trips/join` 성공 시 hold → `trip_member` 확정, hold 소비(제거)
- [x] hold 만료 시 자리 자동 반환 (Redis TTL 기반 — 별도 배치·정리 작업 불필요)
- [x] **플로우 첫 화면(정기 일정 입력 화면)에서 뒤로가기로 플로우 자체를 이탈할 때, 명시적 API 호출로 hold 즉시 반환** — 개별 일정 화면→정기 일정 화면처럼 플로우 **안**에서 이동하는 뒤로가기는 대상 아님(hold 유지). **범위:** 이 즉시 반환은 앱이 살아있는 상태의 명시적 이탈만 커버한다 — 앱 강제 종료·네트워크 단절처럼 클라이언트가 API를 호출할 기회 자체가 없는 경우는 여전히 **TTL(10분) 만료가 유일한 반환 경로**다(명시적 반환은 TTL의 대체가 아니라 추가 최적화)
- [x] hold 생성·소비는 **원자적**으로 처리 (Redis Lua 스크립트 등) — 동시 요청이 정원을 초과해 hold를 발급하지 않도록
- [x] 동시성 테스트 — 정원 1자리 남음 + N명이 동시에 hold 요청하는 시나리오
- [x] `./gradlew test`

## Could Have

- (해당 없음 — 명시적 hold 해제는 2026-08-10 Must Have로 승격)

## Out of Scope (MVP / #22 / 대안 기각)

- hold 없이 INSERT 시점 409만 사용하는 현재 방식 (MVP baseline)
- 방장이 `POST /trips`로 방 생성 시 owner를 즉시 멤버로 넣는 것(A안) — hold 대상 아님
- **Soft hold 테이블(MySQL)** — 기각. `#35`를 `#4`의 Redis 인프라와 병행 개발하기로 결정(2026-08-10)
- **`SCHEDULE_PENDING` 조기 INSERT** — 기각. #22에서 이미 폐기한 선점 모델로 회귀하는 방향이라 채택 불가

## 확정된 결정 사항 (2026-08-10)

| 항목 | 결정 |
|------|------|
| hold 방식 | **Redis 카운터** — `#4` Redis 인프라 재사용 |
| hold TTL | **10분** — 정기→개별 일정 입력 플로우 평균 소요 시간 기준 |
| 브랜치 | 신규 브랜치 분기 없이 **`feat/4-rtr-redis-blacklist` 위에서 병행** — `#4` merge 전까지 `#35`도 함께 대기 |
| 신규 엔드포인트 | **초대코드 미리보기 + hold 결합** — 방 정보 조회와 hold 생성을 한 번의 호출로 처리 |
| hold 실패(자리 없음) 응답 | **기존 `TRIP_MEMBER_FULL`(409) 재사용** — 신규 ErrorCode 없음 |

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips/join/hold` | JWT | **신규.** 초대코드로 방 정보를 미리보기하며 동시에 10분 hold 생성 |
| DELETE | `/api/v1/trips/{tripId}/join/hold` | JWT | **신규.** 플로우 첫 화면에서 이탈 시 hold 즉시 반환 |
| POST | `/api/v1/trips/join` | JWT | **기존, 내부 동작만 변경.** 요청/응답 shape·경로·ErrorCode 불변 — 호출자가 유효한 hold를 갖고 있으면 그대로 확정, 없으면(만료·미호출) 기존 카운트 체크로 폴백 |

### `POST /api/v1/trips/join/hold`

요청:

```json
{
  "inviteCode": "AB12CD"
}
```

성공 (200) — 기존 `TripDetailResponse`와 동일한 방 정보 구조를 재사용(멤버가 아직 아니므로 `myRole`/`myMemberStatus` 등은 참여 전 상태를 반영):

```json
{
  "data": {
    "tripId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "제주도 여행",
    "destination": "제주도",
    "startRange": "2026-08-01",
    "endRange": "2026-08-31",
    "durationDays": 4,
    "durationNights": 3,
    "memberCount": 6,
    "activeMemberCount": 3,
    "status": "ONGOING"
  }
}
```

실패 (409) — 이미 정원 가득(hold 포함 계산):

```json
{
  "code": "TRIP_MEMBER_FULL",
  "message": "참여 인원이 가득 찼습니다."
}
```

**기존 `TripErrorCode`(`INVITE_CODE_NOT_FOUND`·`TRIP_ALREADY_CONFIRMED`·`TRIP_EXPIRED`·`TRIP_MEMBER_FULL`)를 `joinTrip()`과 동일하게 재사용** — 신규 ErrorCode 없음.

**idempotent:** 같은 사용자가 재호출(앱 재시작, 뒤로가기 후 재진입 등)하면 기존 hold의 TTL을 10분으로 갱신할 뿐, 자리를 중복으로 잡지 않는다.

**이미 멤버인 사용자:** `joinTrip()`과 동일하게 hold를 만들지 않고 기존 상세를 반환(`support.requireActive`로 SCHEDULE_PENDING 우회 방지).

### `DELETE /api/v1/trips/{tripId}/join/hold`

**호출 시점:** 플로우 첫 화면(정기 일정 입력 화면)에서 뒤로가기로 플로우 자체를 벗어날 때만. 개별 일정 화면 → 정기 일정 화면처럼 플로우 **안**에서 이동하는 뒤로가기는 호출하지 않음(hold 유지).

성공 (204, body 없음).

**idempotent:** 이미 만료됐거나 `POST /trips/join`으로 이미 소비된 hold에 호출해도 안전(존재하지 않는 대상 `ZREM`은 무해한 no-op) — 항상 204.

## 데이터 모델

- DB 스키마 변경 없음 — Redis만 사용
- Key: `trip:{tripId}:holds` — **ZSET**, member=`{userId}`, score=hold 만료 시각(epoch millis)
- **자리 계산:** 만료된 member 제거(`ZREMRANGEBYSCORE key -inf now`) 후 `ZCARD key`(활성 hold 수) + `activeMemberCount`(확정 멤버 수)를 `memberCount`와 비교
- **원자성:** hold 생성(만료분 정리 → 카운트 확인 → `ZADD`)은 Lua 스크립트로 한 번의 Redis 호출 안에서 처리 — 두 명이 동시에 마지막 한 자리를 요청해도 하나만 성공
- 개별 key TTL 없음(ZSET score 자체가 만료 판정 기준) — Redis 서버가 죽어도 앱이 죽지 않는 fail-open 설계는 `#4`의 `RedisTokenRevocationChecker`와 동일한 원칙을 따름(Redis 장애 시 hold 없이 기존 카운트 체크로 폴백)

```
trip:{tripId}:holds (ZSET)
  member: userId (string)
  score:  holdExpiresAtEpochMillis
```

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| D8(`trip-room-api.md`) | `joinedMemberCount >= memberCount` → 409 | hold 카운트가 이 체크를 **선행** — hold 단계에서 이미 자리를 배정받으면 join 단계 재확인 생략 |
| D-JOIN-MEMBER(`schedule-participation-onboarding.md`) | 참여자 INSERT는 `ACTIVE`만, `POST /trips/join` | 변경 없음 — hold는 join **이전** 단계에만 개입 |

## 검증 시나리오

### 정상

- [x] hold 생성 성공 → 10분 내 `POST /trips/join` → hold 소비, 정상 가입
- [x] hold 재호출(같은 유저) → TTL만 갱신, 자리 중복 소비 없음
- [x] hold 없이(구 클라이언트 등) `POST /trips/join` 직접 호출 → 기존 카운트 체크로 정상 동작(폴백)

### 엣지 · 실패

- [x] 정원 1자리 남음 + N명이 동시에 hold 요청 → 정확히 1명만 hold 성공, 나머지 409 `TRIP_MEMBER_FULL`
- [x] hold 발급 후 10분 경과, join 미호출 → hold 만료(자동 반환), 다른 사용자가 그 자리로 hold/join 가능 (score 기반 만료 판정 로직으로 검증 — 실제 10분 대기 대신 만료 score 직접 주입)
- [x] hold 만료 후 뒤늦게 `POST /trips/join` 호출 → hold가 없으므로 기존 카운트 체크로 폴백, 자리 있으면 성공·없으면 409
- [x] 잘못된 `inviteCode` → 404 `INVITE_CODE_NOT_FOUND`
- [x] `CONFIRMED`/`EXPIRED` 방에 hold 시도 → 409 `TRIP_ALREADY_CONFIRMED` / `TRIP_EXPIRED`
- [x] 이미 `ACTIVE` 멤버가 hold 재호출 → hold 생성 없이 기존 상세 반환
- [x] Redis 장애 시 → hold 없이 기존 카운트 체크 경로로 폴백(fail-open), 앱 자체는 정상 동작
- [x] hold 생성 후 즉시 `DELETE .../join/hold` 호출 → 자리 즉시 반환, 다른 사용자가 바로 그 자리로 hold 가능
- [x] 이미 만료·소비된 hold에 `DELETE .../join/hold` 호출 → 여전히 204(안전한 no-op)

### 수동 / 통합 (해당 시)

- [x] 로컬에서 Redis 끄고 `POST /trips/join/hold` 및 `POST /trips/join` 호출 — fail-open 확인 (Testcontainers에서 잘못된 포트로 연결 불가 상황을 재현해 검증, `TripJoinHoldServiceTest.unreachableRedis_failsOpenInsteadOfThrowing`)

## 완료 기준

- [x] 스펙 Approved
- [x] `POST /trips/join/hold` · `DELETE /trips/{tripId}/join/hold` 구현 + OpenAPI 반영
- [x] hold·만료·409·폴백·명시적 해제 시나리오 테스트
- [x] 동시성 테스트(N명 동시 hold) 통과
- [x] `./gradlew test`

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `Breaking-Change-Reason` 트레일러 대상 여부 | 확정 — **불필요** | 신규 엔드포인트 추가만으로는 기존 계약 breaking 아님. `POST /trips/join` 자체의 요청/응답 shape·ErrorCode는 불변(내부 동작만 변경), 프론트가 신규 엔드포인트를 연동 안 해도 기존 동작 그대로 유지(폴백). 단, hold 카운터가 UX 개선 효과를 내려면 프론트가 신규 엔드포인트를 실제로 붙여야 함 — 이건 breaking 이슈가 아니라 **신규 연동 필요 안내** |
| Wave 4 Backlog(`#32`) 체크 | 확정 — 완료 | 2026-08-10 `#35` 항목 `[x]` 체크 완료 |
| "플로우 첫 화면" 실제 라우팅 명세 | 프론트 확인 전제 | 스펙은 "정기 일정 입력 화면 = 플로우 진입점"으로 가정 — 실제 프론트 네비게이션 구조와 다르면 `DELETE` 호출 지점 재조정 필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-10 | **Implemented** — `TripJoinHoldService`(Redis ZSET + Lua 원자적 hold) · `POST /trips/join/hold` · `DELETE /trips/{tripId}/join/hold` · `joinTrip()` hold 소비/폴백 로직 구현. 단위·통합 테스트(동시성 포함, 실제 Redis 컨테이너) 전부 통과. 구현 중 발견한 무관한 기존 버그(테스트 fixture의 trip 종료일이 하드코딩돼 있어 시스템 날짜가 그 값을 지나면서 ONGOING 판정이 깨지던 문제, `TripServiceTest`)도 사용자 승인 하에 함께 수정(`LocalDate.now()` 기준 상대 날짜로 전환) |
| 2026-08-10 | **Approved** — 전체 설계 최종 승인. 브랜치는 `feat/4-rtr-redis-blacklist`에서 분기한 `feat/35-trip-join-capacity-hold` 사용(직접 병행이 아닌 별도 분기로 확정) |
| 2026-08-10 | **Amend** — 명시적 hold 해제(`DELETE /trips/{tripId}/join/hold`)를 Nice→**Must Have**로 승격. 플로우 첫 화면(정기 일정 입력)의 뒤로가기에서만 호출, 플로우 내부 단계 이동은 대상 아님. idempotent(무해한 no-op). 앱 강제종료·네트워크 단절 시엔 여전히 TTL만이 유일한 반환 경로임을 명시 |
| 2026-08-10 | **설계 확정 (승인)** — hold 방식=Redis 카운터, TTL=10분, `#4` 브랜치 병행, 신규 엔드포인트(`POST /trips/join/hold`, 미리보기+hold 결합), 실패 응답=기존 `TRIP_MEMBER_FULL` 재사용, ZSET 데이터 모델·원자적 Lua 처리·폴백 경로 명시 |
| 2026-07-21 | Draft — #22 late-join MVP 감수 후속 |
