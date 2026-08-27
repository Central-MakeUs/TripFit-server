# Trip Architecture Audit — 2026-08-05

## 범위

- 패키지: `com.tripfit.tripfit.trip` (`config`, `controller`, `domain`, `dto`, `exception`, `repository`, `repository.projection`, `scheduler`, `service`) — 추천(recommendation) 로직은 별도 하위 패키지 없이 `service/`에 flat하게 포함
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP, `spring-boot-java.md`, `testing.md`
- 총 71개 파일(main 53개, test 16개, 참고용 common 4개) 전수 검토

## ✅ A. 반드시 수정해야 하는 사항

### A-1. 정기·개인 일정(및 Google Calendar busy) 조회가 멤버별로 개별 쿼리를 날리는 N+1 — 배치 조회 메서드가 이미 있는데도 사용하지 않음

- **Priority**: High
- **Category**: Performance / JPA
- **문제**: `TripServiceSupport.resolveMergedSchedule(...)`(`trip/service/TripServiceSupport.java:201-221`)는 `regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(userId)`와 `personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(userId, ...)`를 **단일 userId**로 호출한다. 이 메서드가 멤버 리스트를 순회하는 `for` 루프 안에서 멤버 수만큼 반복 호출된다:
  - `TripMemberQueryService.buildLive(...)` (`trip/service/TripMemberQueryService.java:113-146`) — `GET /trips/{tripId}/members/schedule-calendar` 호출마다.
  - `TripScheduleSnapshotService.freezeTrip(...)` (`trip/service/TripScheduleSnapshotService.java:48-84`) — 확정(`confirmSchedule`)·일 배치(`TripHomeMaintenanceService.runForDate`)마다. 여기서는 추가로 `googleCalendarService.findBusyDaysByUserId(userId, ...)`(단건, 라인 69)까지 멤버별로 호출된다.
- **왜 문제인가**: 같은 패키지의 `RecommendationEngine.loadContext(...)`(`trip/service/RecommendationEngine.java:299-333`)는 정확히 같은 데이터를 필요로 하면서도 `regularScheduleRepository.findByUserIdIn(userIds)`, `personalScheduleRepository.findByUserIdInAndScheduleDateBetween(userIds, ...)`, `googleCalendarService.findBusyDaysByUserIds(userIds, ...)` 배치 메서드로 **한 번에** 조회한 뒤 메모리에서 `groupingBy`로 나눈다 — 즉 배치 조회 인프라(레포지토리 메서드까지)는 이미 존재하고 검증돼 있는데, `resolveMergedSchedule` 경로만 이를 재사용하지 않고 멤버 수(N, 최대 10)만큼 쿼리를 반복한다. `getScheduleCalendar`는 방 멤버라면 누구나 호출할 수 있는 조회 API라 트래픽이 잦고, `freezeTrip`은 `TripHomeMaintenanceService`의 일 배치에서 만료된 방 전체 × 멤버 수만큼 반복돼 배치 규모가 커질수록 영향이 누적된다.
- **개선 방법**: `TripServiceSupport`에 배치 버전 `Map<UUID, List<CalendarDayResponse>> resolveMergedSchedules(..., List<UUID> userIds, LocalDate start, LocalDate end, Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser)`를 추가하고(`RecommendationEngine.loadContext`와 동일한 배치 조회 + `ScheduleCalendarResolver.resolve` 매핑 방식 재사용), `TripMemberQueryService.buildLive`와 `TripScheduleSnapshotService.freezeTrip`에서 루프 진입 전에 한 번만 호출하도록 변경한다. `freezeTrip`은 `googleCalendarService.findBusyDaysByUserIds(userIds, ...)`도 루프 밖으로 이동(이미 `buildLive`가 이렇게 하고 있으므로 동일 패턴 적용). 기존 단건 `resolveMergedSchedule`/`findBusyDaysByUserId`는 다른 도메인(`user/schedule` 등)에서 쓰일 수 있어 유지하되, 이 두 호출부만 배치로 교체.
- **API 영향**: No Impact — 응답 필드·형식·정렬 동일, 내부 쿼리 횟수만 감소.
- **예상 변경 파일**: `trip/service/TripServiceSupport.java`, `trip/service/TripMemberQueryService.java`, `trip/service/TripScheduleSnapshotService.java`
- **예상 변경 라인 수**: 60~90줄
- **위험도**: Low — `RecommendationEngine`에 이미 존재하는 배치 로직을 그대로 이식하는 수준. 결과 데이터는 동일(userId별로 나눠 쓰는 것만 다름).
- **테스트 영향도**: `TripMemberScheduleCalendarIntegrationTest`, `TripScheduleSnapshotServiceTest`, `TripScheduleSnapshotServiceIntegrationTest`, `TripHomeMaintenanceServiceTest`가 이미 결과값을 검증하므로 회귀 감지 가능. 추가로 쿼리 횟수 자체를 검증하는 테스트(Mockito `verify(times(1))`)를 넣으면 재발 방지에 도움.
- **예상 효과**: 멤버 수(최대 10)에 비례하던 왕복 쿼리(멤버당 정기 1 + 개인 1, freezeTrip은 +구글 1)가 상수 회수로 감소 — 조회 API 응답 지연 감소, 일 배치 실행 시간 감소(만료 방 수 × 멤버 수에 비례하던 쿼리가 만료 방 수에 비례로 축소).

### A-2. `SaveRecommendationFeedbackRequest`의 `@Schema` 설명에 실제와 다른 HTTP 메서드가 적혀 있음

- **Priority**: Low
- **Category**: Readability / Cleanup (문서 정확성)
- **문제**: `trip/dto/SaveRecommendationFeedbackRequest.java:9`의 클래스 `@Schema(description = "... PUT /trips/{tripId}/recommendations/{rank}/feedback ...")`는 **PUT**이라고 적혀 있지만, 실제 매핑은 `RecommendationController.java:256`의 `@PatchMapping("/recommendations/{rank}/feedback")`이다.
- **왜 문제인가**: 이 설명 문자열은 springdoc이 그대로 Swagger UI에 노출한다. `spring-boot-java.md` 컨벤션상 Swagger 설명은 "독자: 프론트·신규 서버 개발자"를 위한 정확한 호출 가이드여야 하는데, 여기서는 문서와 실제 엔드포인트 메서드가 어긋나 프론트가 잘못된 HTTP 메서드로 연동을 시도할 수 있다.
- **개선 방법**: `@Schema` 설명의 `PUT`을 `PATCH`로 수정.
- **API 영향**: No Impact — 실제 매핑·동작은 변경 없음, 문서 문자열만 수정.
- **예상 변경 파일**: `trip/dto/SaveRecommendationFeedbackRequest.java`
- **예상 변경 라인 수**: 1줄
- **위험도**: Low
- **테스트 영향도**: 없음(문서 문자열 변경, 별도 검증 테스트 불필요). 원하면 `RecommendationControllerSwaggerConsistencyTest`류에 문자열 검증 추가 가능.
- **예상 효과**: Swagger 문서와 실제 API 계약 일치 — 프론트 연동 실수 예방.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `trip 로드 → 방장 검증(→ ONGOING 검증)` 3~2줄 시퀀스가 9곳에서 그대로 반복됨 — Support 헬퍼로 통합

- **Priority**: Medium
- **Category**: Cleanup (중복 코드 제거) / Architecture (Support 재사용 컨벤션)
- **문제**: 동일한 가드 시퀀스가 반복된다.
  - **`trip+owner+ongoing` (3줄, 4곳)**: `TripCommandService.patchTrip`(146-148), `TripCommandService.removeMember`(260-262), `TripRecommendationService.generateRecommendations`(85-87), `TripRecommendationService.confirmSchedule`(206-208) — 전부 `Trip trip = support.requireActiveTrip(tripId); support.requireOwner(trip, id); support.requireOngoingForMutation(trip);` 동일 3줄.
  - **`trip+owner` (2줄, 5곳)**: `TripCommandService.deleteTrip`(192-193), `TripRecommendationService.listRecommendations`(118-119), `getRecommendationDetail`(127-128), `saveFeedback`(179-180), `unconfirm`(243-244) — 전부 `Trip trip = support.requireActiveTrip(tripId); support.requireOwner(trip, id);` 동일 2줄.
- **왜 문제인가**: `spring-boot-java.md`의 "Support 헬퍼 재사용" 규칙이 정확히 이 상황(같은 조회·검증을 다른 Service 메서드에서 인라인 재구현하지 말고 Support로 통일)을 명시하고 있는데도, 검증 시퀀스 자체는 이미 Support 메서드 3개(`requireActiveTrip`/`requireOwner`/`requireOngoingForMutation`) 호출 조합으로 9번 반복되고 있다. 향후 "방장 검증 실패 시 로깅 추가" 등 정책이 하나 생기면 9곳을 전부 고쳐야 한다.
- **개선 방법**: `TripServiceSupport`에 `Trip requireOwnedTrip(UUID tripId, UUID userId)`(2줄 시퀀스 대체)와 `Trip requireOwnedOngoingTrip(UUID tripId, UUID userId)`(내부에서 `requireOwnedTrip` 호출 후 `requireOngoingForMutation` 추가) 두 메서드를 추가하고, 9개 호출부를 이걸로 교체. 예외 타입·순서(NOT_FOUND → FORBIDDEN → NOT_ONGOING)는 그대로 보존.
- **API 영향**: No Impact — 예외 종류·발생 순서·메시지 동일, 내부 호출 경로만 통합.
- **예상 변경 파일**: `trip/service/TripServiceSupport.java`, `trip/service/TripCommandService.java`, `trip/service/TripRecommendationService.java`
- **예상 변경 라인 수**: 40~60줄(9개 호출부 축소 + Support에 2개 메서드 추가)
- **위험도**: Low — 순수 위임 추출, 조건·예외 동일.
- **테스트 영향도**: 기존 `TripServiceTest`, `TripRecommendationServiceTest`의 "notOwner_throwsForbidden"/"notOngoing_throwsNotOngoing"류 테스트가 그대로 회귀 검증 역할을 함. 신규 Support 메서드에 대한 단위 테스트 1~2개 추가 권장.
- **예상 효과**: 가드 로직 SSOT화, 향후 정책 변경 시 1곳만 수정하면 됨.

### B-2. `TripQueryService.toDetail(...)`이 `support.toDetail(...)`로의 1줄 패스스루일 뿐 — 불필요한 간접 의존성 제거

- **Priority**: Low
- **Category**: Cleanup (보일러플레이트 제거) / Architecture
- **문제**: `TripQueryService.toDetail(Trip, TripMember)`(`trip/service/TripQueryService.java:85-87`)는 `return support.toDetail(trip, membership);` 한 줄뿐인 패스스루 메서드다. 이 메서드는 `TripCommandService`에서 4번(`activateMembership`, `patchTrip`, `joinTrip`의 idempotent 분기, `updatePin`), `TripJoinService`에서 1번 호출된다. 그런데 `TripCommandService`는 이미 생성자에 `TripServiceSupport support`를 직접 주입받고 있어 `support.toDetail(...)`을 바로 호출할 수 있다.
- **왜 문제인가**: `TripCommandService`가 `TripQueryService`를 의존성으로 갖는 유일한 이유가 이 패스스루 메서드 하나뿐이다(`grep` 결과 다른 용도로 쓰이지 않음). `TripJoinService`도 `TripQueryService`를 오직 이 메서드 하나 때문에 주입받는다. 실질적 이득 없는 간접 계층이 생성자 의존성 그래프만 늘리고 있다.
- **개선 방법**: `TripCommandService`의 4개 호출부를 `tripQueryService.toDetail(...)` → `support.toDetail(...)`로 교체하고 생성자에서 `TripQueryService` 의존성 제거. `TripJoinService`는 생성자 의존성을 `TripQueryService` 대신 `TripServiceSupport`로 바꾸고 `support.toDetail(...)` 호출. 이후 `TripQueryService.toDetail(...)` 패스스루 메서드 자체를 삭제.
- **API 영향**: No Impact — 최종적으로 호출되는 로직(`TripServiceSupport.toDetail`)은 동일.
- **예상 변경 파일**: `trip/service/TripCommandService.java`, `trip/service/TripJoinService.java`, `trip/service/TripQueryService.java`
- **예상 변경 라인 수**: 15~25줄
- **위험도**: Low — 순수 위임 경로 단축, 로직 변경 없음.
- **테스트 영향도**: `TripServiceTest`가 `TripJoinService`/`TripCommandService`를 직접 `new`로 구성하므로(수동 와이어링), 생성자 시그니처가 바뀌면 테스트의 생성자 호출부도 함께 수정 필요(C 항목 참고).
- **예상 효과**: `TripCommandService`·`TripJoinService`의 생성자 의존성 1개씩 감소, 불필요한 간접 계층 제거로 가독성 향상.

### B-3. `TripCommandService.patchTrip`이 `RecommendationRepository`를 직접 호출 — 추천 도메인 책임을 우회(이미 코드 주석으로 인지된 부채)

- **Priority**: Low
- **Category**: Architecture (패키지 구조) / Legacy
- **문제**: `TripCommandService.patchTrip`(`trip/service/TripCommandService.java:177-180`)은 희망 일수가 바뀌면 `recommendationRepository.deleteByTripId(tripId)`를 **직접** 호출한다. 정작 추천 후보의 생성·삭제·조회를 담당하는 `TripRecommendationService`는 이 호출에 관여하지 않는다. 코드에 이미 `// 추천 입력이 바뀌면 후보를 hard DELETE — 추천 서비스와 통합은 추후`라는 주석으로 이 사실이 인지돼 있다.
- **왜 문제인가**: "추천 후보를 언제 hard delete하는가"라는 책임이 `TripRecommendationService`(생성 시 재계산, unconfirm 시)와 `TripCommandService`(메타 변경 시) 두 곳에 나뉘어 있어, 향후 추천 삭제 정책이 바뀌면(예: soft delete로 전환, 삭제 시 이벤트 발행 추가) 두 클래스를 함께 찾아 고쳐야 한다.
- **개선 방법**: `TripRecommendationService`에 패키지 전용 메서드(예: `void deleteRecommendationsForTrip(UUID tripId)`)를 추가하고 `TripCommandService.patchTrip`이 `recommendationRepository`가 아니라 이 메서드를 호출하도록 변경. `TripCommandService`의 `RecommendationRepository` 직접 의존성은 제거.
- **API 영향**: No Impact.
- **예상 변경 파일**: `trip/service/TripCommandService.java`, `trip/service/TripRecommendationService.java`
- **예상 변경 라인 수**: 10~15줄
- **위험도**: Low
- **테스트 영향도**: `TripServiceTest.patchTrip_deletesRecommendationsWhenDurationChanges`가 그대로 회귀 검증.
- **예상 효과**: 추천 후보 생명주기 책임이 `TripRecommendationService` 한 곳으로 모임 — 이미 저자가 남긴 TODO성 부채 해소.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **초대 코드 alphabet을 "Crockford Base32"라고 부르는 주석/독스트링이 부정확함** — `InviteCodeGenerator.ALPHABET`(`23456789ABCDEFGHJKMNPQRSTUVWXYZ`, 31자)은 진짜 Crockford Base32(0·1은 유지하고 I·L·O·U를 제외해 32자)와 다르게 0/O/I/1을 제외한 커스텀 31자 alphabet이다. `JoinTripRequest.@Schema`·`InviteCodeGenerator` 주석·`InviteCodeGeneratorTest` 테스트명(`generate_producesSixCharCrockfordCode`)에 모두 이 명칭이 쓰인다. 동작에는 전혀 문제 없고 순수 명칭 정확성 문제라, 이번 라운드(동작 불변 리팩터)에서 이름을 바꾸면 `docs/specs/`·Swagger 설명까지 동시 갱신해야 하는 범위 확장이 발생해 넘어간다.
- **`TripCommandService.deleteTrip`의 멤버 cascade soft-delete가 개별 엔티티 루프**(`for (TripMember member : ...) member.setDeletedAt(now);`, `trip/service/TripCommandService.java:196-199`)라 멤버 수만큼 UPDATE가 나간다. 같은 리포지토리의 `clearExpiredPins`는 이미 `@Modifying` 벌크 쿼리 패턴을 쓰고 있어 방식이 일관되지 않지만, `memberCount`가 최대 10으로 캡돼 있어(`CreateTripRequest.memberCount` `@Max(10)`) 실질적 성능 영향이 미미해 이번엔 넘어간다.
- **`TripRecommendationService.requireValidFeedback`(피드백 OTHER 사유 검증)와 `requireValidUnconfirmReason`(확정취소 OTHER 사유 검증)의 구조가 거의 동일**하다("OTHER면 reasonDetail 필수" 패턴). 그러나 각각 다른 enum 타입(`RecommendationFeedbackReason` vs `UnconfirmReason`)과 다른 요청 DTO를 다루고 있어, 제네릭 추출은 함수형 인터페이스나 리플렉션이 필요해 2회 중복치고는 과한 추상화(YAGNI 위반 소지)라 넘어간다.
- **`TripServiceSupport`가 DTO 매핑(`toHomeCard`/`toDetail`) + 검증(`validateTripMeta`) + 권한 가드(`requireOwner`/`requireActive`/`requireActiveMember`) + N+1 방지 배치 로더(`loadMemberCountsByTripIds` 등) + 잡다한 유틸(초대코드, destination 정규화)까지 담당해 335줄짜리 다책임 클래스가 됐다. 역할별로 `TripMapper`/`TripValidator`/`TripGuard`로 쪼갤 수도 있지만, 프로젝트 전역 컨벤션(`spring-boot-java.md` "Support 헬퍼 재사용" — `{Domain}ServiceSupport`가 SSOT)이 도메인당 단일 Support 클래스를 명시적으로 요구하고 있어, 이 컨벤션과 어긋나는 분리를 이번 감사에서 제안하지 않는다.
- **`TripHomeMaintenanceService.runForDate`가 만료된 방 전체를 하나의 트랜잭션 안에서 처리**한다(청크 없음). MVP 규모(도메인당 방 수가 아직 적음)에서는 문제가 되지 않지만, 방 수가 크게 늘면 긴 트랜잭션이 락을 오래 쥐게 될 수 있다. §15에 "Later" 항목으로 기록.
- **`TripAuthorizationInterceptor`의 `@TripOwnerOnly` 경로가 `existsByIdAndDeletedAtIsNull`과 `existsByIdAndOwner_IdAndDeletedAtIsNull` 두 번의 EXISTS 쿼리를 날린다.** 하나로 합치려면 `Trip` 엔티티 전체를 로드해야 하는데, 가벼운 boolean EXISTS 쿼리 2번이 엔티티 풀 로드 1번보다 오히려 저렴할 수 있어 "합치기"가 실제 개선이라 보기 어렵다 — 그대로 둔다.
- **`TripDetailResponse`와 `TripHomeCardResponse`가 필드 15개 가량을 그대로 중복**하고 있다(이름·목적지·기간·정원·상태·`lastActivityAt`·pin·역할·상태·활성인원·충원율·미리보기 등). 목록(카드) vs 상세라는 서로 다른 API 계약이라 통합하면 Swagger 스키마가 불필요하게 얽히고 API 계약(필드 존재 유무)이 바뀌므로 그대로 둔다 — D 항목과 동일한 이유로 "계약 다른 DTO는 공통화하지 않는다".
- **`TripServiceTest`가 `TripCommandService`/`TripQueryService`/`TripJoinService`/`TripMemberQueryService`/`TripRecommendationService`/`TripScheduleSnapshotService`/`RecommendationEngine`을 전부 `new`로 수동 조립하고, `AspectJProxyFactory`로 `TripActivityAspect`까지 직접 프록시로 감싸서 구성**한다(`trip/service/TripServiceTest.java:108-183`). 생성자 시그니처가 하나만 바뀌어도 이 테스트의 조립 코드를 함께 고쳐야 해서 테스트 자체의 유지보수 비용이 높다(위 B-1/B-2로 생성자가 바뀌면 여기도 수정 필요). 다만 이 테스트가 `@TripActivity` AOP 동작까지 실제로 검증하는 유일한 단위 테스트 경로라 순수 Mockito 목킹으로 단순화하면 그 커버리지를 잃는다 — 이번 라운드(프로덕션 코드 리팩터)에서는 테스트 구조 자체를 바꾸지 않고, B-1/B-2 적용 시 이 파일의 조립 코드만 최소 수정한다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **서비스 레이어의 `support.requireOwner(trip, userId)` 재검증**(예: `RecommendationController`의 모든 엔드포인트는 `@TripOwnerOnly` 인터셉터를 이미 통과했는데도 `TripRecommendationService`가 다시 방장 여부를 확인함)은 중복처럼 보이지만 의도적 defense-in-depth다. 인터셉터는 HTTP 계층 게이트(가벼운 EXISTS 쿼리)이고, 서비스는 이미 로드한 `Trip` 엔티티의 `getOwner().getId()`를 비교하는 것뿐이라 추가 쿼리 비용이 없다(지연 로딩 프록시의 ID 접근은 초기화를 트리거하지 않음). 이 재검증을 지우면 서비스 메서드가 인터셉터 없이 호출될 경우(예: 향후 배치·이벤트 리스너·테스트에서 직접 호출) 권한 검증이 완전히 사라지므로 지우지 않는다.
- **`Trip` 엔티티가 `@Setter`로 공개 세터를 노출하고, 서비스가 `trip.setStatus(...)`/`trip.setConfirmedStartDate(...)` 등을 직접 호출하는 anemic 모델**은 리치 도메인(`trip.confirm(...)`, `trip.unconfirm(...)`) 스타일로 바꿀 수도 있어 보이지만, `spring-boot-java.md`가 "풀 DDD 미적용 — JPA 연관관계·객체 그래프 탐색 허용"을 명시적으로 선언하고 있고 코드베이스 전역(다른 도메인 포함)이 이 스타일로 일관돼 있다. 이 도메인만 리치 엔티티로 바꾸면 오히려 코드베이스 전체 컨벤션과 어긋나므로 손대지 않는다.
- **`TripJoinService`가 메서드 하나(`joinAsNewMember`)만 가진 별도 `@Service`로 분리돼 있는 것**은 불필요한 클래스 분할처럼 보이지만, Spring AOP(`@TripActivity`)는 같은 빈 내부의 self-invocation(`this.method()`)에는 프록시가 적용되지 않는 근본적 한계가 있다. `TripCommandService.joinTrip`이 신규 멤버 등록 로직을 별도 빈(`TripJoinService`)의 메서드로 호출해야만 `@TripActivity(tripIdFromReturn = true)` 어드바이스가 정상 적용된다(`config/TripActivity.java`, `config/TripActivityAspect.java`). `TripCommandService`로 합치면 신규 join 시 `last_activity_at` 갱신이 조용히 동작하지 않게 된다 — 병합하지 않는다.
- **`RecommendationFeedback.recommendationId`가 FK 제약 없는 soft reference로 저장**돼 있는 것은 정합성이 느슨해 보이지만, `RecommendationFeedback.java:52-54`의 `@Schema` 설명대로 재추천 시 `Recommendation` 행은 hard delete되는 반면 피드백은 분석 목적으로 계속 남아야 하는 명시적 설계 의도다. FK를 걸면 재추천 때마다 피드백까지 cascade delete되거나 삭제 자체가 막혀 버려 요구사항과 충돌하므로 그대로 둔다.

## 15. 백엔드 아키텍처 개선 제안 (선택 — 실제로 이 도메인에 적용 가치가 있는 경우만)

- **Resilience — 초대 코드 생성의 TOCTOU 레이스**: `TripServiceSupport.generateUniqueInviteCode()`(`trip/service/TripServiceSupport.java:314-322`)는 `existsByInviteCode(code)`로 사전 확인 후 값을 반환하지만, 실제 `INSERT`(트랜잭션 커밋) 시점까지는 별도 락이 없다. 동시에 같은 6자 코드가 생성되는 극히 드문 경우(31^6 ≈ 8.87억 조합, 트립 생성 트래픽 규모 대비 확률 매우 낮음) DB `UNIQUE(invite_code)` 제약 위반이 `GlobalExceptionHandler.handleUnexpectedException`으로 흘러 500(INTERNAL_ERROR)이 되고 재시도되지 않는다. **Now/Later**: **Later** — 현재 트래픽·코드 공간을 고려하면 발생 확률이 무시할 만한 수준이라 지금 당장 재시도 로직을 추가할 필요는 없지만, 방 생성 트래픽이 크게 늘어나는 시점에는 `save()` 주변에 `DataIntegrityViolationException` 캐치 후 재시도를 추가할 가치가 있다.
- **Database/Batch — `TripHomeMaintenanceService.runForDate`의 단일 트랜잭션 처리**: 만료된 방 전체(`findExpiredOngoing`)를 하나의 트랜잭션에서 순회하며 `freezeTrip` + 상태 전환을 수행한다(스냅샷 INSERT까지 포함). **Now/Later**: **Later** — MVP 규모(도메인 활성 방 수가 적음)에서는 트랜잭션 길이가 문제되지 않지만, 방 수가 대규모로 늘어나면 방 단위로 트랜잭션을 쪼개는 청크 처리를 고려할 만하다.
- **Monitoring — `RecommendationEngine.generate`의 실행 시간 계측 부재**: 희망 기간이 길어지면(예: 30일) 날짜 윈도우 수 × 멤버 수만큼 `classifyOneMember`가 반복 호출돼 계산량이 늘어난다. 현재 데이터 규모(정원 최대 10명, 희망 기간도 통상 짧음)에서는 문제가 되지 않지만 계측이 전혀 없어 회귀 시 발견이 늦을 수 있다. **Now/Later**: **Later** — 실제 지연이 관측되기 전에는 계측 추가보다 A-1(N+1 제거)이 우선.
- **Concurrency/Redis/Async — 이 도메인에 적용할 가치 있는 항목 없음**: 추천 계산(`RecommendationEngine`)과 홈 배치(`TripHomeScheduler`)는 CPU 바운드이고 이미 이벤트(`ApplicationEventPublisher`)로 알림 도메인과 디커플링돼 있다. 정원 상한(10명)·희망 기간 상한을 고려하면 큐잉·비동기화·캐싱을 도입할 만한 규모의 병목이 관측되지 않는다. **Now/Later/Never**: **Never**(현재 규모 기준) — 트래픽·데이터 규모가 실제로 문제를 일으키기 전에는 트렌디하다는 이유만으로 도입하지 않는다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
