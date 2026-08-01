# TripMemberStatus를 respondedAt 파생값으로 통합

> 상태: Approved / Implemented
> MVP: 해당 없음 (내부 리팩터 — 제품 범위 변경 아님)
> 관련 BR: N/A (BR-USER-002, BR-USER-007이 규정하는 SCHEDULE_PENDING/ACTIVE 의미·전이는 변경 없음)

## 목표

`TripMember.status`(SCHEDULE_PENDING/ACTIVE)와 `TripMember.respondedAt`이 항상 lockstep으로 갱신되는 중복 저장 상태를 없애고, `respondedAt` 하나만 저장한 뒤 상태를 파생 계산해 스키마를 단순화한다.

## 배경

- `TripMember(...)` 생성자: `status == ACTIVE`면 `respondedAt = joinedAt`, 아니면 `respondedAt`은 null로 남는다.
- `markResponded()`: `status = ACTIVE`와 `respondedAt = now()`를 항상 동시에 세팅한다.
- 즉 `respondedAt == null` 여부만으로 `status`를 100% 복원할 수 있어 `status` 컬럼은 파생값을 별도 컬럼에 중복 저장하고 있는 것과 같다.
- 관련 이슈: [#54](https://github.com/Central-MakeUs/TripFit-server/issues/54)
- 관련 규칙: `.claude/rules/harness-follow-up.md` 💡 ERD (스키마는 고정 아님, 파생→저장 전환·역방향 모두 적극 제안 대상)

## 요구사항

### Must Have

- [x] `TripMember` 엔티티에서 `status` 컬럼(`@Enumerated` 필드)을 제거한다.
- [x] `TripMember.getStatus()`를 `respondedAt == null ? SCHEDULE_PENDING : ACTIVE`로 계산하는 파생 메서드로 대체한다 (Lombok `@Getter`가 아닌 수동 구현). `setStatus()`는 제공하지 않는다 (기존에도 외부 호출 없음 — `markResponded()`가 유일한 전이 경로).
- [x] `markResponded()`는 `respondedAt = LocalDateTime.now()`만 세팅하도록 단순화한다.
- [x] 생성자 시그니처(`TripMember(Trip, User, TripMemberRole, TripMemberStatus, LocalDateTime)`)는 **그대로 유지** — 파라미터로 받은 `status`에 따라 `respondedAt` 초기값만 결정 (호출부·테스트 변경 없음).
- [x] `TripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(tripId, status)`를 `countByTripIdAndRespondedAtIsNotNullAndDeletedAtIsNull(tripId)`로 교체하고, `TripServiceSupport.toDetail`·`toSummary`(respondedCount 계산부, 81·95행 부근)의 호출부를 갱신한다.
- [x] `TripAuthorizationInterceptor`, `TripCommandService`, `TripMemberQueryService` 등 기존 `membership.getStatus()` 호출부는 **수정하지 않는다** — 파생 메서드가 동일 시그니처를 유지하므로 동작 변경 없음.
- [x] `docs/architecture/erd.md` `trip_member` 테이블(컬럼 표 + mermaid 블록)에서 `status` 행 제거, `responded_at` 설명에 "상태는 이 값의 null 여부로 파생 — 별도 컬럼 없음" 명시.
- [x] 로컬/dev DB는 엔티티 변경에 맞춰 리셋 (`docker compose down -v` 등) — Flyway/마이그레이션 작성 금지 (하네스 STOP §3). *DB 스키마는 `ddl-auto`로 다음 로컬 기동 시 자동 반영, 별도 데이터 없어 리셋 불필요*

### Nice to Have

- [ ] 없음

### Out of Scope (이번 스펙에서 하지 않음)

- SCHEDULE_PENDING/ACTIVE가 표현하는 비즈니스 규칙(방장 일정 확인 게이트, `SCHEDULE_CONFIRM_REQUIRED` 차단 조건) 자체 변경 — 이번 작업은 순수 내부 표현 단순화이며 상태 전이 정책은 동일하게 유지.
- (당시) FE에 노출되는 API 계약(`CreateTripResponse.status`, 방 상세·멤버 목록의 `status` 필드) 변경 — `getStatus()`가 동일한 `TripMemberStatus` enum 값을 반환하므로 Swagger 스키마·필드명·값 모두 동일했음.
- (당시) `TripMemberStatus` enum 자체(값, `@Schema` 설명) 변경.

> **후속 amend (2026-07-27):** 위 두 줄은 **이 스펙 구현 시점** 기준이며, 이후 별도 후속 작업에서 `JOINED`→`SCHEDULE_PENDING`, `RESPONDED`→`ACTIVE`로 enum 값 자체를 개명하고 DTO 필드명(`TripMembersResponse.status`→`memberStatus`)을 통일했다 (신규 이름 부여 이유: 의미 전달력 — 아래 변경 이력 참고). 최신 계약은 `TripMemberStatus.java`·`/v3/api-docs`가 SSOT.

## API / 인터페이스

API 없음 — 내부 엔티티 리팩터, 요청/응답 계약 변경 없음.

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` `trip_member` 테이블
- 변경 컬럼:

```
trip_member
  - status (varchar, NOT NULL)  ← 컬럼 제거
    responded_at (timestamptz, NULL)  ← 유지, 파생 SSOT로 승격
```

- Soft delete·FK 정책 변경 없음 (`deleted_at` 등 기존 그대로)
- Enum `TripMemberStatus`(SCHEDULE_PENDING/ACTIVE)는 Java 코드에 그대로 남고, DB에는 저장되지 않음 (Entity 파생 getter → DTO 매핑 시점에만 계산)

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-USER-002/007 관련 상태 의미 | SCHEDULE_PENDING=방장 confirm 전, ACTIVE=입장 가능 — **의미·전이 조건 변경 없음**, 저장 방식만 변경 | `TripMember.getStatus()` (파생), `markResponded()` |

## 검증 시나리오

### 정상

- [x] 방 생성 직후 방장 멤버 `getStatus() == SCHEDULE_PENDING`, `respondedAt == null`
- [x] `confirmSchedule` 호출 후 방장 멤버 `getStatus() == ACTIVE`, `respondedAt`이 호출 시각으로 세팅
- [x] 신규 멤버 `join` 시 즉시 `getStatus() == ACTIVE`, `respondedAt == joinedAt`
- [x] 방 상세 응답의 `respondedCount`가 변경 전과 동일한 값 산출 (신규 repository 메서드 기준)

### 엣지 · 실패

- [x] SCHEDULE_PENDING 상태 멤버가 방 안 API 호출 시 여전히 `SCHEDULE_CONFIRM_REQUIRED` (인터셉터 동작 동일)
- [x] SCHEDULE_PENDING 방장이 방 메타 PATCH/DELETE는 여전히 허용 (ownerOnly 분기 영향 없음)
- [x] 이미 ACTIVE인 멤버가 `confirmSchedule` 재호출 시 idempotent 동작 유지

### 수동 / 통합

- [x] 기존 `TripAuthorizationInterceptorTest`, `TripControllerTest`, `TripMemberControllerTest`, `TripServiceTest`, `TripScheduleSnapshotServiceTest`가 코드 수정 없이 통과 (동작 동일성 검증용 회귀 기준) — `TripServiceTest`는 삭제된 repository 메서드명 참조 7건만 신규 메서드명으로 갱신, 로직·기대값은 무수정

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] `docs/architecture/erd.md` 동기화
- [x] (당시) Swagger `/v3/api-docs` 상 `TripMemberStatus` 계약(값 목록) 변경 없음 확인 — `getStatus()`가 필드와 동일한 이름·타입·`@Schema`를 유지하는 springdoc bean property이므로 스키마 동일. `TripMemberStatus` enum 자체는 무수정이었으나, 후속 amend(2026-07-27)로 값 자체가 SCHEDULE_PENDING/ACTIVE로 바뀜 — 최신 값은 `TripMemberStatus.java` 참고

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 향후 3단계 이상 진행 상태 추가 가능성 | 확정 아님 | 현재는 2-상태뿐이라 타임스탬프 파생으로 충분. 3단계+ 필요해지면 그때 다시 명시적 enum 컬럼으로 되돌리는 결정 필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-27 | 초안 |
| 2026-07-27 | 구현 완료 — `TripMember.status` 컬럼 제거, `getStatus()` 파생 메서드·`countByTripIdAndRespondedAtIsNotNullAndDeletedAtIsNull` 적용. `./gradlew test`·`build` 통과 |
| 2026-07-27 | **후속 amend** — enum 값 `JOINED`→`SCHEDULE_PENDING`, `RESPONDED`→`ACTIVE` 개명(이름만으로 "일정 확인 대기중/방 활동 가능"이 드러나도록). DTO 필드명 `TripMembersResponse.status`→`memberStatus`로 통일(`myMemberStatus`/`memberStatus` 두 갈래로 정리, `MemberCalendar.memberStatus`는 기존 유지). 계기: `TripMemberStatus.java`가 별도 문서 없이도 신규 개발자·프론트가 이름만으로 의미를 알 수 있어야 한다는 피드백(`.claude/rules/spring-boot-java.md` Comments/OpenAPI 절 강화와 동반) |
