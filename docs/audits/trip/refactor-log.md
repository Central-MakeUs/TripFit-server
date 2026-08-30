# trip Refactor Log

## 2026-08-05 — 2차 라운드 B-1·B-2 반영

2차 감사([`audit-round2.md`](audit-round2.md)) 기준 A 없음, B(유지보수성) 2건 전부 반영. 사용자 승인: "B-1·B-2 전부".

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **B-1**: `SlotStatuses`(정기·개인 일정의 오전/오후/저녁 상태를 담는 값 타입)에 아무도 안 쓰는 접근 방법이 하나 더 있었어요 — 오전/오후/저녁 상태를 각각 이름으로 꺼내 오는 방법(`getMorningStatus()` 등)과, 슬롯을 파라미터로 넘겨서 꺼내 오는 방법(`get(TimeSlot)`) 두 가지가 있었는데, 실제 코드는 전부 앞의 방법만 쓰고 있었습니다. 안 쓰는 길을 남겨두면 나중에 코드를 보는 사람이 "이건 언제 쓰는 거지?" 헷갈릴 수 있어서 지웠어요.
- **B-2**: `TripScheduleSnapshotService`(일정 확정 시 멤버들의 일정을 스냅샷으로 굳혀 저장하는 서비스)가 실제로는 같은 폴더 안 다른 코드에서만 불려 쓰이는데, 다른 폴더에서도 자유롭게 갖다 쓸 수 있게 열려 있었어요(`public`). 실제로 그렇게 쓰는 곳은 없었기 때문에, 앞으로 실수로 엉뚱한 곳에서 이 서비스를 직접 가져다 쓰는 걸 막기 위해 접근 범위를 같은 폴더 안으로 좁혔습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| B-1 | `SlotStatuses`의 미사용 제네릭 접근자 `get(TimeSlot)`/`set(TimeSlot, ScheduleStatus)` 삭제. 개별 getter/setter(`getMorningStatus()` 등)는 유지 | `SlotStatuses.java` |
| B-2 | `TripScheduleSnapshotService` 클래스·생성자 가시성을 `public` → package-private으로 축소(같은 패키지 내 서비스 계층 가시성 컨벤션과 통일) | `TripScheduleSnapshotService.java` |

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test` (전체) — 통과, 실패 0건
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (diff 0건)

**결론: trip 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목

`audit-round2.md`의 C 2개(`RecommendationEngine.loadContext`/`resolveMergedSchedules` 조회 패턴 유사성, `CreateTripRequest`/`PatchTripRequest` 필드 중복), D 2개(엔티티 `equals`/`hashCode` 미구현, `TripMemberScheduleSnapshot.frozenAt` 별도 컬럼 유지) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round2.md` 해당 절 참고.

## 2026-08-05 — A-1, A-2, B-1~B-3 반영

`audit.md`의 A(반드시 수정) 2건, B(유지보수성) 3건을 전부 반영. C/D는 이번 라운드에서 보류.

### 반영 항목

- **A-1 (High, Performance)**: `TripServiceSupport.resolveMergedSchedule(...)`(단건 userId)를 멤버 리스트 순회 루프 안에서 멤버 수만큼 반복 호출하던 것을 제거. `resolveMergedSchedules(...)`(복수 userId 배치 버전)를 신설해 `RegularScheduleRepository.findByUserIdIn(...)`/`PersonalScheduleRepository.findByUserIdInAndScheduleDateBetween(...)`로 한 번에 조회한 뒤 `userId`별로 `groupingBy`해 나눈다(`RecommendationEngine.loadContext`와 동일 패턴 재사용). `TripMemberQueryService.buildLive(...)`와 `TripScheduleSnapshotService.freezeTrip(...)` 양쪽 호출부를 루프 진입 전 단 한 번 호출로 교체. `freezeTrip`은 `googleCalendarService.findBusyDaysByUserId(userId, ...)`(단건)도 `findBusyDaysByUserIds(userIds, ...)`(배치, `buildLive`가 이미 쓰던 패턴)로 함께 교체해 루프 밖으로 이동. 기존 단건 `resolveMergedSchedule`은 다른 도메인(`user/schedule`)에서 쓰이므로 유지, 이 두 호출부만 배치로 전환.
- **A-2 (Low, Readability)**: `SaveRecommendationFeedbackRequest`의 `@Schema` 설명 문자열이 실제 매핑(`@PatchMapping`)과 다르게 "PUT"이라고 적혀 있던 것을 "PATCH"로 수정 — Swagger 문서와 실제 API 계약 불일치 해소.
- **B-1 (Medium, Cleanup/Architecture)**: `trip 로드 → 방장 검증(→ ONGOING 검증)` 반복 시퀀스(2~3줄, 9곳)를 `TripServiceSupport`의 `requireOwnedTrip(tripId, userId)`/`requireOwnedOngoingTrip(tripId, userId)` 두 헬퍼로 통합. `TripCommandService`(`patchTrip`, `deleteTrip`, `removeMember`)와 `TripRecommendationService`(`generateRecommendations`, `listRecommendations`, `getRecommendationDetail`, `saveFeedback`, `confirmSchedule`, `unconfirm`)의 9개 호출부를 교체. 예외 타입·발생 순서(NOT_FOUND → FORBIDDEN → NOT_ONGOING)는 동일하게 보존.
- **B-2 (Low, Cleanup)**: `TripQueryService.toDetail(Trip, TripMember)`가 `support.toDetail(...)`로의 1줄 패스스루일 뿐이라 삭제. `TripCommandService`(4곳)와 `TripJoinService`(1곳)의 호출부를 `support.toDetail(...)`로 직접 교체하고, 두 클래스 모두 오직 이 메서드 하나 때문에 갖고 있던 `TripQueryService` 생성자 의존성을 제거(`TripJoinService`는 대신 `TripServiceSupport`를 주입받도록 변경).
- **B-3 (Low, Architecture/Legacy)**: `TripCommandService.patchTrip`이 `RecommendationRepository`를 직접 호출해 `deleteByTripId(tripId)`를 실행하던 것("추천 서비스와 통합은 추후"라는 기존 TODO 주석으로 인지돼 있던 부채)을 `TripRecommendationService.deleteRecommendationsForTrip(tripId)`(패키지 전용 신설 메서드)로 위임하도록 변경. `TripCommandService`의 `RecommendationRepository` 직접 의존성 제거 — 추천 후보 hard delete 책임이 `TripRecommendationService` 한 곳으로 모임.

### 변경 파일

```
 src/main/java/.../trip/dto/SaveRecommendationFeedbackRequest.java  |  2 +-
 src/main/java/.../trip/service/TripCommandService.java             | 35 +++++---------
 src/main/java/.../trip/service/TripJoinService.java                |  8 ++--
 src/main/java/.../trip/service/TripMemberQueryService.java         | 18 +++----
 src/main/java/.../trip/service/TripQueryService.java                |  5 --
 src/main/java/.../trip/service/TripRecommendationService.java      | 25 +++++-----
 src/main/java/.../trip/service/TripScheduleSnapshotService.java    | 22 +++++----
 src/main/java/.../trip/service/TripServiceSupport.java             | 56 +++++++++++++++-------
 src/test/java/.../trip/controller/TripMemberScheduleCalendarIntegrationTest.java | 2 +-
 src/test/java/.../trip/service/TripScheduleSnapshotServiceTest.java |  10 ++--
 src/test/java/.../trip/service/TripServiceTest.java                 | 32 ++++++-------
 11 files changed, 121 insertions(+), 94 deletions(-)
```

### 검증

- `./gradlew test --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"` → `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` — **"No breaking changes to report"**
- `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` — 유일한 diff는 A-2가 의도한 `SaveRecommendationFeedbackRequest` 설명 문자열(`PUT` → `PATCH`) 변경뿐, 그 외 필드·엔드포인트·ErrorCode 변경 없음을 확인
- `./gradlew test` (전체, ArchitectureTest 포함) — 통과

### 남겨둔 항목 (C/D — 이번 라운드 보류)

- **C**: 초대 코드 alphabet 명칭 부정확(`InviteCodeGenerator`), `TripCommandService.deleteTrip`의 멤버 cascade 개별 UPDATE 루프(정원 상한 10으로 영향 미미), `requireValidFeedback`/`requireValidUnconfirmReason` 구조 중복(다른 enum·DTO라 제네릭 추출 시 YAGNI 위반 소지), `TripServiceSupport` 335줄 다책임(프로젝트 전역 "Support 헬퍼 재사용" 컨벤션과 상충), `TripHomeMaintenanceService.runForDate` 단일 트랜잭션 처리(현재 규모에선 문제 아님), `TripAuthorizationInterceptor`의 EXISTS 쿼리 2회, `TripDetailResponse`/`TripHomeCardResponse` 필드 중복(계약이 다른 DTO), `TripServiceTest`의 수동 조립 구조(AOP 커버리지 유지 목적) — `audit.md` C 참고
- **D**: 서비스 레이어 `requireOwner` 재검증(defense-in-depth, 추가 쿼리 없음), `Trip` 엔티티 anemic 모델(코드베이스 전역 컨벤션과 일관), `TripJoinService` 별도 빈 분리(AOP self-invocation 한계 회피 목적), `RecommendationFeedback.recommendationId` FK 미설정(재추천 시 피드백 보존 의도) — `audit.md` D 참고
