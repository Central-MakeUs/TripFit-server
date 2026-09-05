# Trip Refactor Log

여행(`trip`) 도메인 아키텍처 감사에서 승인된 항목을 실제로 반영한 이력이다. 포트/어댑터 폐기 및 라운드별 리팩토링 수정 사항과 검증 결과를 기록한다. 감사 결과 원본은 `audit.md` 및 `audit-round2.md`에 있다.

## 2026-08-26 — 포트/어댑터(`trip/port/out`) 폐기 → concrete 클래스 직접 주입

### 배경

`docs/audits/trip/audit.md` B-1 감사 결과 + 사용자 논의를 거쳐, `trip/port/out`의 인터페이스 3개(`SchedulePort`·`GoogleCalendarPort`·`UserDirectoryPort`)를 걷어내고 Controller → Service → Repository로 단순화하기로 결정. 근거:

- 인터페이스마다 구현체가 항상 1개뿐이었다(구현체를 갈아끼우는 헥사고날 본래 목적 없음).
- `user` 도메인이 다른 경로로 이미 `trip` 타입에 의존하고 있어, 이 포트가 패키지 레벨 순환 의존을 실제로 막지도 못했다.
- 테스트 7개 중 `SchedulePort`·`GoogleCalendarPort`는 어디서도 인터페이스로 mock된 적이 없었다(전부 concrete 어댑터를 실제로 생성해서 씀) — `UserDirectoryPort`만 4개 테스트에서 진짜 mock됐는데, Mockito가 concrete 클래스도 그대로 mock할 수 있어 이 이점도 유지 가능했다.

### 반영한 항목 (A-1, B-1 — 감사 문서 기준)

1. **B-1 — 포트 3개 삭제, concrete 클래스로 전환**
   - 삭제: `trip/port/out/{SchedulePort,GoogleCalendarPort,UserDirectoryPort}.java` + 빈 `trip/port/out/`·`trip/port/` 디렉터리
   - 삭제: `GoogleCalendarPortAdapter`(순수 1메서드 위임 클래스) — trip이 필요로 하는 구글 busy 조회는 새 `ScheduleAvailabilityService.resolveAvailability(...)`가 내부적으로 `GoogleCalendarService`를 직접 호출하도록 흡수. trip 쪽에서 `GoogleCalendarService`를 직접 주입받을 필요 자체가 없어짐
   - 개명(사용자 결정 — "지금 개명"): `ScheduleAvailabilityAdapter` → `ScheduleAvailabilityService`, `UserDirectoryAdapter` → `UserDirectoryService`. `implements XxxPort` 제거, 클래스 역할 주석을 "포트 구현체" 서술에서 "trip이 필요로 하는 조회를 이 도메인 서비스 여러 개에서 모아주는 단일 호출 지점"으로 갱신
   - trip 쪽 호출부 5개 파일의 필드 타입을 인터페이스 → concrete 클래스로 교체(로직 변경 없음, 순수 타입 치환): `RecommendationEngine`, `TripScheduleSnapshotService`, `TripMemberQueryService`, `TripServiceSupport`, `TripCommandService`

2. **A-1 — "구글 busy 조회 → 일정 병합" 중복 2단계 호출 통합** (B-1과 같은 턴에 함께 처리 — 파일이 겹쳐서)
   - `ScheduleAvailabilityService`에 `resolveAvailability(userIds, start, end)` 신설 — 내부에서 busy 조회 → merge 순서를 캡슐화하고, 원본 busy 맵과 병합 결과를 함께 담은 `ScheduleAvailability` record를 반환
   - `TripMemberQueryService.buildLive`, `TripScheduleSnapshotService.freezeTrip`은 `.mergedByUser()`만 사용
   - `RecommendationEngine.loadContext`는 원본 busy 맵(연차 시뮬레이션에 필요)과 병합 결과를 모두 이 메서드 하나로 받음 — 기존에 `GoogleCalendarPort`를 별도로 주입받아 raw busy를 조회하던 코드가 사라지고, `ScheduleAvailabilityService` 의존 하나로 줄어듦
   - 순서 보장이 이제 컴파일러가 강제하는 메서드 경계 안에 있어, 새 호출부가 추가돼도 순서를 어길 수 없음

3. **부수 정리(선택, 사용자 승인 — 포함) — `TripCommandService`의 이중 접근 경로 제거**
   - `TripServiceSupport`에 `requireProfileNameComplete(User)` 위임 메서드 추가
   - `TripCommandService`가 `UserDirectoryService`를 직접 주입받지 않고, `support.requireProfileNameComplete(...)`를 거치도록 변경(`createTrip`·`joinTrip` 2곳) — trip 쪽 "user 도메인 접근 지점은 `TripServiceSupport` 하나"라는 규칙이 더 명확해짐

### 변경 파일

- 삭제 6개: `SchedulePort.java`, `GoogleCalendarPort.java`, `UserDirectoryPort.java`, `GoogleCalendarPortAdapter.java`, `ScheduleAvailabilityAdapter.java`(→ 아래 신규로 대체), `UserDirectoryAdapter.java`(→ 아래 신규로 대체)
- 신규 2개: `user/schedule/service/ScheduleAvailabilityService.java`, `user/service/UserDirectoryService.java`
- 수정(main) 5개: `RecommendationEngine.java`, `TripScheduleSnapshotService.java`, `TripMemberQueryService.java`, `TripServiceSupport.java`, `TripCommandService.java`
- 수정(test) 8개: `RecommendationEngineTest.java`, `RecommendationEngineTestSetScenarioTest.java`, `TripScheduleSnapshotServiceTest.java`, `TripServiceSupportTest.java`, `TripAuthorizationInterceptorTest.java`, `TripRecommendationServiceTest.java`, `TripServiceTest.java`, `TripMemberScheduleCalendarIntegrationTest.java`(주석만 — 이전에도 이미 stale했던 `TripServiceSupport.resolveMergedSchedules` 참조를 `ScheduleAvailabilityService.resolveAvailability`로 갱신)
- 문서 3개: `docs/decisions/003-architecture-guide.md`(결정 11 폐기 amend), `docs/architecture.md`(Package Layout에서 `port/out/` 제거), `docs/specs/trip/package-structure-refactor.md`(변경 이력에 폐기 항목 추가, 원 설계는 이력으로 유지)

### 검증

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test` — 전체 통과(ArchitectureTest 포함)
- `./gradlew test --tests OpenApiSpecExportTest` + `oasdiff breaking`/`diff` `docs/api/openapi.json` vs `build/openapi/openapi.json` — **diff 0건**(API 계약·엔드포인트·DTO·ErrorCode 전부 동일, 순수 내부 구조 리팩터)
- `./gradlew build` — 전체 통과

### 남겨둔 항목 (C — 이번 라운드에 포함하지 않음)

- 어댑터 개명(이번에 이미 반영했으므로 해당 없음 — 원래 "다음 후속" 후보였던 개명을 사용자가 이번 턴에 포함하기로 결정해 위 B-1에서 함께 처리)
- `CalendarDayResponse` → `CalendarDay` 수동 필드 매핑 보일러플레이트 정적 팩터리 추출 (C-2, 감사 문서 참고) — 이번 요청 범위 밖
- `RecommendationEngine` 연차 시뮬레이션 부분 클래스 분리 (D-1, 감사 문서 참고 — 분리하지 않는 게 낫다고 판단)

## 2026-08-26 — Round 3 (SOLID/OOP 중심) B-1, B-2 반영

감사([`audit-round3.md`](audit-round3.md)) 기준 A 항목 없음, B(유지보수성) 2개 전부 반영. 사용자 승인: "전체 B 승인".

### 쉽게 설명하면 (`core-reporting.md`)

- **B-1:** 여행 희망 일정·개인 일정이 공유하는 "오전/오후/저녁 가능 여부" 값 하나(`SlotStatuses`)에, 슬롯 하나만 따로 바꿀 수 있는 기능(setter)이 3개 붙어 있었는데 실제로는 어디서도 쓰이지 않고 있었어요. 이 값은 항상 세 슬롯이 한 번에 같이 계산되는 성격이라, 이 기능이 남아 있으면 나중에 누군가 슬롯 하나만 따로 고치려다 나머지 두 슬롯과 안 맞는 상태를 만들 위험이 있었어요 — 삭제했어요.
- **B-2:** 여행방 멤버십 존재 여부를 확인하는 조회 기능 하나가 선언만 되어 있고 실제로는 아무도 안 쓰고 있어서 삭제했어요.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| B-1 | `SlotStatuses`의 미사용 `setMorningStatus`/`setAfternoonStatus`/`setEveningStatus` 3개 삭제 — 3개 소비 엔티티(`TripMemberScheduleSnapshot`·`RegularSchedule`·`PersonalSchedule`) 모두 생성자/`fromTimeRange()`로 전체 교체만 사용 | `trip/schedule/domain/SlotStatuses.java` |
| B-2 | `TripMemberRepository.existsByTripIdAndUserIdAndDeletedAtIsNull` 미사용 선언 삭제 — 같은 조건이 필요한 지점은 이미 `findByTripIdAndUserIdAndDeletedAtIsNull(...).isPresent()`로 처리 중 | `trip/membership/repository/TripMemberRepository.java` |

### 변경 규모

- 기존 파일 수정 2개 (main): `SlotStatuses.java`(11줄 삭제), `TripMemberRepository.java`(1줄 삭제)
- 신규 파일 없음
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — `SlotStatuses`는 API DTO가 아닌 내부 `@Embeddable`, 삭제한 메서드 모두 Controller·DTO 변환에 관여하지 않음

### 검증 결과

- `./gradlew compileTestJava` — 통과
- `./gradlew test`(전체, Testcontainers 실제 MySQL 8 컨테이너 포함, `ArchitectureTest` 포함) — **514개 전체 통과, 0개 실패**
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **"No changes"**(가장 엄격한 확인)

**결론: trip 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 기록 갱신 (구현 아님)

2차 B-2("`TripScheduleSnapshotService`를 package-private로 낮추자")는 그 뒤 `membership`/`recommendation`/`schedule` feature 서브패키지 분리로 전제가 바뀌어(지금은 `TripRecommendationService`·`TripHomeMaintenanceService`가 서로 다른 패키지에서 호출) 더 이상 적용 불가능해졌다 — `audit-round3.md` 선행 문서 안내 참고. 코드 변경은 없음, 과거 판단이 stale해졌다는 사실만 기록.

### 남겨둔 C/D 항목 (Round 3)

`audit-round3.md`의 C 3개(1·2차 C 재검증 + `SlotStatuses` 크로스 도메인 공유 재검토), D 4개(`TripService` 파사드 미분할·`RecommendationEngine` 미분할 재확인·feature envy 아님 재확인·`TripAuthorizationInterceptor` 이중 접근 경로 유지) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round3.md` 해당 절 참고.

### Later 후속 제안 (audit-round3.md §15, 상태 변화 없음)

1·2차 §15의 5개 제안(연차 시뮬레이션 계측·스케줄러 병렬화·읽기 replica·cursor pagination·idempotency key) 재확인 결과 상태 변화 없음 — 판단 그대로 유지.
