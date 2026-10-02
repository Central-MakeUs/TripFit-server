# 여행방 join 정원 보장 — 낙관적 락 전환

> 상태: **Approved** (2026-10-02, `#130`) — 남은 일은 [완료 기준](#완료-기준) 참고
> MVP: In scope (기존 `POST /trips/join` 기능의 내부 방식 교체 — 새 기능 아님)
> 관련 BR: BR-TRIP-001 (정원 1~10) · BR-USER-007 (정원 409) — **규칙 자체는 바뀌지 않는다**

여행방 정원을 지키는 방식을 DB 비관적 락에서 낙관적 락으로 바꾸는 설계다. 어떤 컬럼이 추가되고, 어떤 코드 경로를 함께 고쳐야 하며, 그 근거가 된 실험 결과가 무엇인지 이 문서에서 확인할 수 있다.

이 문서에서 **정원**은 방장이 정한 최대 인원(`trip.member_count`)이고, **`joined_member_count`**는 지금 자리를 차지한 멤버 수(삭제되지 않은 `trip_member` 행 수, `SCHEDULE_PENDING` 포함)다.

## 목표

동시에 여러 명이 초대 링크로 `join`해도 정원을 넘기지 않는다는 보장을 유지하면서, 요청을 줄 세우는 잠금(`SELECT ... FOR UPDATE`) 대신 버전 비교와 재시도로 처리한다.

## 배경

- 현행은 [`trip-join-schedule-gate.md`](trip-join-schedule-gate.md) J-4의 B안이다. `join`이 `trip` 행을 잠근 채 멤버 수를 세고 INSERT한다.
- 2026-10-02 사용자 결정으로 낙관적 락으로 전환한다. 판단 근거는 "같은 방에 동시에 `join`하는 충돌은 드물다"이다.
- `join`은 `trip` 행을 고치지 않고 `trip_member`에 행을 추가하므로, 버전을 비교할 대상이 없다. 그래서 모든 `join`이 함께 고치는 값(`joined_member_count`)을 `trip`에 둔다.
- 재시도는 Spring Framework 7.0.8에 내장된 `@Retryable`(`org.springframework.resilience.annotation`)을 쓴다. 로컬 `spring-context-7.0.8.jar`에서 클래스와 속성(`includes`·`maxRetries`·`delay`·`jitter`)을 확인했다. 의존성 추가는 없다.

### 설계 근거가 된 실험 (2026-10-02)

실험용 엔티티로 측정했고, 실험 코드는 저장소에 남기지 않았다. 재현 조건은 MySQL 8.0.46(Testcontainers `mysql:8.0`), 기본 격리 수준(`REPEATABLE READ`), Hibernate 7.4.1, Spring Framework 7.0.8이다.

정원 3명(방장 1 + 빈자리 2) 방에 8명이 동시에 `join`하는 상황을 30회 반복한 결과다.

| 구조 | 정원 초과 | 재시도 없음 | 재시도 최대 5회 |
|------|-----------|-------------|-----------------|
| `trip`에 버전+`joined_member_count`, 멤버 INSERT 후 `trip` UPDATE | 0회 | 실패 196건 중 183건이 데드락 | 매회 2명 성공. 재시도 227번 중 214번이 데드락 |
| `trip`에 버전+`joined_member_count`, **`trip` UPDATE 후 멤버 INSERT** | 0회 | 매회 1명만 성공 (버전 충돌 210건, 데드락 0건) | 매회 2명 성공, 6명 `TRIP_MEMBER_FULL`. 데드락 0건 |
| 버전을 검증만 하는 모드(`LockModeType.OPTIMISTIC`) | **30회 전부 초과** (최대 9명) | — | — |

데드락의 원인은 순서다. 자식 행(`trip_member`)을 INSERT하면 MySQL이 부모 행(`trip`)에 공유 잠금을 걸고, 두 요청이 그 상태에서 서로 `trip` UPDATE를 기다린다.

`trip`을 고치는 다른 경로와 섞었을 때의 결과다.

| 확인한 것 | 결과 |
|-----------|------|
| 버전이 있는 엔티티가 전체 컬럼을 UPDATE할 때, 그 사이 직접 UPDATE된 활동 시각(`trip.last_activity_at`) | 옛 값으로 되돌아감 (999 → 0) |
| 같은 상황에서 `@DynamicUpdate` 적용 | 값 유지 (999) |
| 활동 시각에 `@OptimisticLock(excluded = true)`만 붙이고 엔티티로 갱신 | 동시 `join`이 있으면 버전 충돌로 실패 |
| 최종 구조로 `join` 6 · 나가기 2 · 활동 시각 갱신 9 · 방 수정 1을 동시에 30회 | 실패 0, 데드락 0, `joined_member_count`와 실제 멤버 수 불일치 0, 정원 초과 0 |
| 정원 10명 방에 9명 동시 `join` 30회, 재시도 최대 5회 (대기 50~150ms) | 전원 성공, 재시도 소진 0건 |

### 현행 코드의 방 수정 덮어쓰기 결함

`@TripActivity`는 `Trip`을 불러와 활동 시각을 고치고, Hibernate는 이때 전체 컬럼을 UPDATE한다. 그래서 다음 순서로 방장의 수정이 사라진다.

1. 한 요청이 `Trip`을 읽어 둔다 (예: 일정 확인 완료 `POST /trips/{tripId}/activate`).
2. 그 요청이 끝나기 전에 방장의 방 정보 수정이 커밋된다.
3. 먼저 읽어 둔 요청이 활동 시각을 저장하면서 전체 컬럼을 옛 값으로 다시 쓴다.

실제 `Trip` 엔티티로 재현했다(이름·정원 수정이 원래 값으로 복귀). O-6과 O-7이 이 결함도 함께 없앤다.

## 변경 범위

### ADDED

- `trip.joined_member_count`, `trip.version` 컬럼
- `CommonErrorCode.CONCURRENT_MODIFICATION` (409) + `GlobalExceptionHandler` 매핑
- `@EnableResilientMethods` 설정(`ResilienceConfig`), 합성 어노테이션 `@RetryOnVersionConflict`, 실행기 `VersionConflictRetryExecutor` — 적용 지점은 [재시도 적용 대상](#재시도-적용-대상)
- 활동 시각 직접 UPDATE용 `TripRepository` 쿼리
- `TripMember`의 `@DynamicUpdate` (O-14)

### MODIFIED

- `join` 정원 판정: (변경 전) `trip` 행을 잠그고 멤버 수를 셈 → (변경 후) `joined_member_count`와 정원을 비교하고 버전과 함께 +1
- 방 생성: (변경 전) 현재 멤버 수를 저장하지 않음 → (변경 후) `joined_member_count`를 1(방장)로 시작
- 나가기·내보내기: (변경 전) 멤버만 삭제 표시 → (변경 후) 멤버 삭제 표시를 먼저 flush한 뒤 `joined_member_count` -1 (O-15)
- 방 삭제: (변경 전) `trip` 삭제 표시 후 멤버 삭제 표시 → (변경 후) 멤버 삭제 표시를 먼저 flush한 뒤 `trip` 삭제 표시 (O-15)
- `TripActivityAspect`: (변경 전) `Trip`을 불러와 엔티티로 수정 → (변경 후) 직접 UPDATE 쿼리
- [`trip-join-schedule-gate.md`](trip-join-schedule-gate.md) J-4 ①: (변경 전) B안(비관적 락) → (변경 후) 이 스펙. ②(이탈자 자리 미회수)와 카운트 기준은 그대로다

### REMOVED

- `TripRepository.findByInviteCodeForUpdate` (잠금 조회)
- `TripMemberRepository.countByTripIdAndDeletedAtIsNull` (호출부가 `join` 하나뿐이라 함께 삭제)
- `Trip.touchLastActivity()` 엔티티 메서드
- `TripCommandService`의 "Pessimistic Lock" 주석과 `findLockedTripByInviteCode` 이름

## 요구사항

### Must Have

- [x] `Trip`에 `joinedMemberCount` · `version` · `@DynamicUpdate` 추가, 두 필드에 `@Schema` 작성
- [x] 정원 검사와 +1을 `Trip`의 도메인 메서드 하나로 묶고, `join`이 O-2 순서로 호출
- [x] 방 생성 시 1로 시작, 나가기·내보내기에서 -1
- [x] `TripMember`에 `@DynamicUpdate` 추가 (O-14)
- [x] 나가기·내보내기·방 삭제는 멤버 행을 먼저 flush (O-15)
- [x] `Trip.releaseSeat()` 하한 검사 (O-16)
- [x] `TripActivityAspect`를 직접 UPDATE로 전환하고 `Trip.touchLastActivity()` 삭제
- [x] [재시도 적용 대상](#재시도-적용-대상) 전부에 O-3 재시도 적용
- [x] `CONCURRENT_MODIFICATION` 추가, 대상 API의 `@ApiResponse` 409 설명에 코드명 기재
- [x] `REMOVED` 항목 삭제
- [ ] dev DB 리셋 (배포 시점에 수행)
- [ ] 커밋에 `Breaking-Change-Reason:` 트레일러 (신규 ErrorCode)

### Nice to Have

- 없음

### Out of Scope (이번 스펙에서 하지 않음)

- 정원을 `joined_member_count`보다 작게 줄이는 것을 막는 규칙 — 현행에 없고 이번에도 추가하지 않는다
- 이탈한 `SCHEDULE_PENDING` 멤버의 자리 자동 회수 — J-4 ② 그대로
- 다른 조회(`membersPreviewOverflow` 등)를 `joined_member_count`로 바꾸는 것

## 설계

### 결정 사항

구조와 규칙을 결정 단위로 정리한 표다. O-1·O-9·O-10은 사용자 확정이고 나머지는 실험과 코드 확인에서 나왔다.

| ID | 결정 | 이유 |
|----|------|------|
| O-1 | `trip`에 `joined_member_count`와 `version`(`@Version`)을 추가한다. 전용 테이블은 만들지 않는다 | 실험상 O-2 순서만 지키면 데드락이 없다 |
| O-2 | `join`은 **`joined_member_count` +1을 먼저 flush**하고 그다음 멤버를 INSERT한다 | 반대 순서는 데드락이 난다 |
| O-3 | 재시도 대상은 버전 충돌(`OptimisticLockingFailureException`과 그 하위 예외)뿐이다 | 데드락까지 재시도하면 O-2가 깨져도 테스트가 알아채지 못한다. 실제로 O-2의 flush를 빼고 돌리면 동시성 테스트가 데드락 예외로 실패한다 |
| O-4 | 재시도는 최대 5회, 50~150ms 무작위 대기로 한다 (`delay = 50`, `jitter = 100`) | 이 값으로 정원 10명·9명 동시 `join` 실험에서 소진이 0건이었다. Spring의 지터는 `delay`보다 짧아지지 않으므로 하한을 `delay`로 둔다 |
| O-5 | 재시도는 트랜잭션을 여는 빈의 **바깥 빈**에 둔다. `@Retryable`과 `@Transactional`을 같은 메서드에 두지 않는다 | 실패한 트랜잭션 안에서 다시 시도하면 소용이 없고, 두 어노테이션의 적용 순서에 기대지 않기 위해서다 |
| O-6 | 활동 시각 갱신은 버전을 거치지 않는 직접 UPDATE 쿼리로 바꾼다 | 엔티티로 고치면 `join`이나 다른 멤버의 요청과 버전이 충돌한다 |
| O-7 | `Trip`에 `@DynamicUpdate`를 붙인다 | 없으면 `join`의 저장이 활동 시각을 옛 값으로 되돌린다 |
| O-8 | `joined_member_count` -1(나가기·내보내기)도 엔티티 수정으로 버전을 거친다 | 직접 UPDATE로 빼면, `join`이 읽어둔 옛 값에 +1한 결과가 그 차감을 덮어쓸 수 있다. 실험 30회에서는 재현되지 않았지만 순서상 가능하다 |
| O-9 | 재시도를 다 쓰고도 실패하면 공통 에러 `CONCURRENT_MODIFICATION`(409)을 돌려준다 | 대상 API가 여럿이라 join 전용 코드로는 부족하다. `TRIP_MEMBER_FULL`을 재사용하면 자리가 남았는데 가득 찼다고 안내하게 된다 |
| O-10 | dev DB를 리셋한다. 마이그레이션은 작성하지 않는다 | 기존 방은 `joined_member_count`가 0으로 채워져 실제 멤버 수와 맞지 않는다 |
| O-11 | `joined_member_count`는 API에 노출하지 않는다 | J-4의 카운트 기준과 `joinedMemberCount` API 미노출 결정을 그대로 유지한다 |
| O-12 | 추천 생성도 충돌 시 처음부터 다시 계산한다 | 결과는 같고 응답만 늦어진다 |
| O-13 | 직접 UPDATE는 `trip.updated_at`을 바꾸지 않는다 | 이 컬럼을 읽는 코드가 없다 |
| O-14 | `TripMember`에도 `@DynamicUpdate`를 붙인다 | 없으면 일정 확인 완료·Pin 변경이 전체 컬럼을 다시 써서, 그 사이 내보내거나 나간 멤버의 삭제 시각을 지우고 멤버를 되살린다. `joined_member_count`는 이미 줄어 있어 숫자가 어긋난다 (테스트로 재현: 실제 멤버 2명, `joined_member_count` 1) |
| O-15 | 기존 멤버 행과 `trip` 행을 함께 고치는 유스케이스는 **멤버 행 → `trip` 행** 순서로 DB에 보낸다. 나가기·내보내기·방 삭제는 멤버 삭제 표시를 먼저 flush한다 | 일정 확인 완료가 멤버 행을 고친 뒤 `trip`의 활동 시각을 갱신한다. 순서가 엇갈린 유스케이스끼리 겹치면 데드락이 난다. 방 삭제만 순서가 반대였을 때 내보내기와 삭제를 동시에 실행하면 매번 데드락이 났다 |
| O-16 | `Trip.releaseSeat()`은 `joined_member_count`가 1 이하이면 `IllegalStateException`을 던진다 | 방장은 항상 자리를 차지하므로 정상이면 일어나지 않는다. 숫자가 이미 어긋난 상태를 음수로 숨기면 정원 검사가 계속 느슨해진다. dev DB를 리셋하지 않은 기존 방(값 0)에서는 나가기·내보내기·탈퇴가 이 예외로 실패하므로 O-10이 배포의 전제다 |

### 재시도 적용 대상

`@Version`은 행 전체에 걸리므로, `trip`을 엔티티로 고치는 경로가 모두 버전 충돌 대상이 된다. 그 경로와 재시도를 두는 위치다.

| 경로 | `trip`을 고치는 내용 | 재시도 위치 |
|------|----------------------|-------------|
| `POST /trips/join` | `joined_member_count` +1 | `TripService` facade |
| `PATCH /trips/{tripId}` | 방 정보 | `TripService` facade |
| `DELETE /trips/{tripId}` | 삭제 표시 | `TripService` facade |
| `DELETE /trips/{tripId}/members/{userId}` | `joined_member_count` -1 | `TripService` facade |
| `DELETE /trips/{tripId}/members/me` | `joined_member_count` -1 | `TripService` facade |
| `POST /trips/{tripId}/recommendations` | 마지막 추천 모드 | `TripService` facade |
| `POST /trips/{tripId}/confirm` · `/unconfirm` | 상태·확정 값 | `TripService` facade |
| `DELETE /users/me` (탈퇴) | 여러 방의 -1·삭제를 한 트랜잭션에서 | `finalizeWithdrawal` 트랜잭션 바깥 |
| 만료 스케줄러 (매일 00:05) | 만료된 방 전체의 상태를 한 트랜잭션에서 | `runForDate` 트랜잭션 바깥 |

재시도 설정은 합성 어노테이션 `@RetryOnVersionConflict`(`common/retry`) 하나에 모았다. facade 메서드에는 이 어노테이션을 붙이고, 탈퇴와 스케줄러처럼 어노테이션을 붙일 메서드가 없는 자리는 `VersionConflictRetryExecutor.run(...)`으로 감싼다.

탈퇴는 외부 연동 해제(카카오·구글·애플)가 DB 정리보다 먼저 끝난다. DB 정리만 실패하면 계정이 어중간한 상태로 남으므로 재시도가 반드시 필요하다.

`POST /trips/{tripId}/activate`와 `PATCH /trips/{tripId}/pin`은 O-6 이후 `trip`을 엔티티로 고치지 않으므로 대상이 아니다.

## API / 인터페이스

엔드포인트·요청·응답 형태는 바뀌지 않는다. 아래 API에 실패 응답 `CONCURRENT_MODIFICATION`(409) 하나가 추가된다.

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/trips/join` | JWT | 초대 코드로 참여 |
| PATCH | `/api/v1/trips/{tripId}` | JWT | 방 정보 수정 |
| DELETE | `/api/v1/trips/{tripId}` | JWT | 방 삭제 |
| DELETE | `/api/v1/trips/{tripId}/members/{userId}` | JWT | 참여자 내보내기 |
| DELETE | `/api/v1/trips/{tripId}/members/me` | JWT | 방 나가기 |
| POST | `/api/v1/trips/{tripId}/recommendations` | JWT | 추천 생성 |
| POST | `/api/v1/trips/{tripId}/confirm` | JWT | 일정 확정 |
| POST | `/api/v1/trips/{tripId}/unconfirm` | JWT | 확정 취소 |
| DELETE | `/api/v1/users/me` | JWT | 회원 탈퇴 |

실패 (409) — 재시도를 다 쓰고도 동시 수정과 계속 부딪힌 경우의 응답이다. 메시지 문구의 SSOT는 `CommonErrorCode` enum이다.

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "요청이 동시에 처리되어 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
}
```

정원이 실제로 찬 경우는 지금과 같이 `TRIP_MEMBER_FULL`(409)이다.

## 데이터 모델

- ERD 참조: [`docs/architecture/erd.md`](../../architecture/erd.md) `trip`
- 신규 컬럼:

```
trip
  + joined_member_count  int     NOT NULL  -- 삭제되지 않은 멤버 수 (SCHEDULE_PENDING 포함)
  + version              bigint  NOT NULL  -- @Version
```

- 정원은 기존 `member_count` 그대로다. 이름이 비슷하므로 두 필드의 `@Schema`에 차이를 적는다.
- 마이그레이션은 작성하지 않는다 (O-10).

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 |
|----|-----------|------------------|
| BR-TRIP-001 | 정원 1~10. 판정 기준만 "멤버 수 세기"에서 `joined_member_count`로 바뀐다 | `Trip.tryOccupySeat()` |
| BR-USER-007 | 정원 초과 시 409 `TRIP_MEMBER_FULL` — 변경 없음 | `TripJoinService` |

## 검증 시나리오

### 정상

- [x] 기존 `TripJoinConcurrencyIntegrationTest`(정원 3, 동시 8명)가 같은 기대값으로 통과: 정확히 2명 성공, 나머지 전부 `TRIP_MEMBER_FULL`
- [x] 방 생성 직후 `joined_member_count` = 1
- [x] `join` → 내보내기 → 다시 `join` 후 `joined_member_count`가 실제 멤버 수와 일치
- [x] 일정 확인 완료·방 수정 후 `last_activity_at`이 DB에서 갱신됨 (실제 빈으로 확인하는 통합 테스트)

### 엣지 · 실패

- [x] `join`·나가기·활동 시각 갱신·방 수정을 동시에 실행해도 데드락 예외가 0건이고, `joined_member_count`가 실제 멤버 수와 일치
- [x] 같은 사용자가 `join`을 동시에 두 번 호출해도 멤버 행은 1개, `joined_member_count`는 +1만
- [x] 방장이 확정(또는 삭제)한 직후 들어온 `join`은 `TRIP_ALREADY_CONFIRMED`(또는 `INVITE_CODE_NOT_FOUND`)
- [x] 일정 확인 완료 도중 방 정보 수정이 커밋돼도 수정 내용이 유지됨 (현행 결함의 회귀 테스트)
- [x] 일정 확인 완료 도중 그 멤버가 내보내지거나 나가도 멤버는 삭제된 채로 남고 `joined_member_count`가 실제 멤버 수와 일치 (O-14)
- [x] `join`이 여행방을 읽은 뒤 커밋된 활동 시각 갱신을 `join`의 저장이 되돌리지 않음 (O-7)
- [x] 같은 멤버에 내보내기와 일정 확인 완료가 동시에 들어와도 데드락 예외가 0건 (O-15)
- [x] 방 삭제가 나가기·내보내기·일정 확인 완료와 동시에 들어와도 데드락 예외가 0건 (O-15)
- [x] 방장 자리만 남은 방에서 `releaseSeat()`을 호출하면 예외가 나고 값은 그대로다 (O-16)
- [x] 탈퇴와 만료 스케줄러가 버전 충돌을 한 번 만나면 새 트랜잭션으로 다시 실행
- [x] 재시도를 다 쓰면 409 `CONCURRENT_MODIFICATION`
- [x] 탈퇴 중 대상 방에서 `join`이 일어나도 탈퇴가 완료됨
- [x] 6명이 동시에 `join`하면 전원 성공 — 경쟁자가 5명이면 최대 5번 밀리므로 재시도 5회 안에 반드시 끝난다
- [x] 정원 10명 방에 9명이 동시에 `join`해도 데드락·정원 초과가 없고 `joined_member_count`가 실제 멤버 수와 일치 — 9명이면 이론상 8번까지 밀릴 수 있어 전원 성공은 보장하지 않는다 (실험에서는 전원 성공)

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] 검증 시나리오 전부 테스트로 존재 — `TripSeatOptimisticLockIntegrationTest` · `TripInterleavedRequestIntegrationTest` · `TripJoinConcurrencyIntegrationTest` · `TripSeatTest` · `VersionConflictRetryTest` · `GlobalExceptionHandlerTest`
- [x] `REMOVED` 항목 실제 삭제 확인
- [x] OpenAPI에 `CONCURRENT_MODIFICATION` 노출 확인 — `OpenApiSpecExportTest`가 만든 `build/openapi/openapi.json`에서 대상 API 전부 확인. `oasdiff breaking` 0건
- [x] 멤버를 저장소로 직접 넣는 테스트 준비 코드가 `joined_member_count`와 맞는지 확인 — `RecommendationControllerSwaggerConsistencyTest` 3곳 수정
- [ ] 같은 PR에서 문서 개정:
  - [x] [`trip-join-schedule-gate.md`](trip-join-schedule-gate.md) J-4
  - [x] [`trip-last-activity-at.md`](trip-last-activity-at.md) 갱신 방식
  - [x] [`trip-room-api.md`](trip-room-api.md) 에러 표
  - [x] [`api-response.md`](../../architecture/api-response.md) · [`package-layout.md`](../../architecture/package-layout.md)
  - [x] [`trip-create-join-guide.md`](../../product/flows/trip-create-join-guide.md) 정원 보장 문구
  - [x] [`spring-boot-java.md`](../../../.claude/rules/spring-boot-java.md) Isolation 절
  - [ ] [`erd.md`](../../architecture/erd.md) — `trip`에 `joined_member_count` · `version` 추가
  - [ ] [`user-account-withdrawal.md`](../user/user-account-withdrawal.md) — DB 정리 단계의 버전 충돌 재시도
  - [ ] [`010-redis-infra.md`](../../decisions/010-redis-infra.md) — "DB 비관적 락으로 대체" 언급에 낙관적 락 재대체 추가

- [ ] 커밋에 `Breaking-Change-Reason:` 트레일러
- [ ] dev DB 리셋 (배포 시점)

`erd.md` · `user-account-withdrawal.md` · `010-redis-infra.md`는 구현 시점에 커밋되지 않은 다른 수정이 들어 있던 파일이라 손대지 않았다 (2026-10-02 사용자 지시). 그 수정이 정리된 뒤 개정한다.

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| GitHub 이슈 번호 · Milestone · `priority:` 라벨 | 확정 | [`#130`](https://github.com/Central-MakeUs/TripFit-server/issues/130) · `출시 이후` · `priority: could` (2026-10-02 사용자 지정) |
| `@Retryable`이 재시도 소진 후 던지는 예외 타입 | 확정 | 감싸지 않은 원래 예외를 그대로 던진다. `spring-context-7.0.8` 소스(`AbstractRetryInterceptor`의 `throw ex.getCause()`)와 `VersionConflictRetryTest`로 확인했다. 핸들러는 `OptimisticLockingFailureException`을 409로 바꾼다 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-10-02 | **구현 리뷰 반영** — O-14(`TripMember` `@DynamicUpdate`)·O-15(멤버 행 → `trip` 행 잠금 순서, 방 삭제 포함)·O-16(`releaseSeat` 하한 검사) 추가, 9명 동시 `join` 시나리오를 "전원 성공"에서 "데드락·정원 초과 없음"으로 정정하고 보장 가능한 6명 시나리오 추가. 전체 542개 통과 |
| 2026-10-02 | **구현** — 코드·테스트 완료(전체 529개 통과). O-3 예외 범위와 O-4 설정값을 구현에 맞춰 구체화, 재시도 소진 예외 타입 확정. 문서 3곳(`erd.md` 등)과 dev DB 리셋은 미완 |
| 2026-10-02 | **Approved** — 사용자 승인, 이슈 `#130` 생성 (`출시 이후` · `priority: could`) |
| 2026-10-02 | 초안 — 구조(O-1)·공통 에러(O-9)·dev DB 리셋(O-10) 사용자 확정, 나머지는 실험 결과로 도출 |
