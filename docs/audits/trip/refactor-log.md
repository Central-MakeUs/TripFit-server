# Trip Refactor Log

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
