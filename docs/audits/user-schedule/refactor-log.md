# user-schedule Refactor Log

## 2026-08-05 — A-1, B-1~B-4 반영

`audit.md`의 A(반드시 수정) 1건, B(유지보수성) 4건을 전부 반영. C/D는 이번 라운드에서 보류.

### 반영 항목

- **A-1 (High, Performance)**: `ScheduleService.upsertPersonal()`이 항목마다 개별 SELECT를 반복하고, 루프 종료 후 같은 구간을 다시 SELECT하던 것을 제거. 진입 시 `[minDate, maxDate]` 구간을 한 번만 조회해 `Map<LocalDate, PersonalSchedule>`로 인덱싱하고, 루프 안에서는 이 맵을 조회·갱신(신규는 `save()` 후 맵에 반영)한 뒤 `buildPersonalResponse()`에 그대로 넘겨 재사용. bulk upsert 1회당 DB 왕복이 `O(items)` → `O(1)`로 감소.
  - 부수 정리: `upsertPersonal` 변경으로 더 이상 프로덕션에서 호출되지 않게 된 `PersonalScheduleRepository.findByUserIdAndScheduleDate(UUID, LocalDate)`도 같은 턴에 삭제 (레거시 즉시 삭제 원칙).
- **B-1 (Medium, Dead Code)**: `Weekday.parseToDayOfWeekSet()` 삭제 — 프로덕션 미사용, `ScheduleCalendarResolver.parseDaysOfWeek()`와 에러 처리 시맨틱이 달라 혼동 유발. `WeekdayTest`의 해당 테스트도 함께 제거.
- **B-2 (Low, 패키지 구조)**: `ScheduleService.displayName(User)` static 유틸을 `User.displayName()` 인스턴스 메서드로 이동. 호출부(`NotificationEventListener`, 그리고 감사에서 못 찾았던 `TripDisplayNameHelper.assignDisplayNames()`)를 `User::displayName`으로 갱신.
- **B-3 (Low, 보일러플레이트)**: `validateCreateRegular()`/`validateUpdateRegular()` 1줄 위임 래퍼 2개 제거 — `createRegular()`/`updateRegular()`에서 `validateRegularTimesAndVacation(...)`을 직접 호출.
- **B-4 (Low, 성능)**: `ScheduleCalendarResolver.resolve()`의 날짜별 선형 탐색(O(days×personals), O(days×regulars))을 진입 시 `personals`를 `Map<LocalDate, PersonalSchedule>`로, `regulars`를 요일별 `Map<DayOfWeek, List<RegularSchedule>>`로 미리 인덱싱하도록 변경. 더 이상 쓰이지 않게 된 `findPersonal()`·`matchingRegulars()` private 헬퍼는 같은 턴에 제거.

### 변경 파일

```
 src/main/java/.../notification/service/NotificationEventListener.java         |   3 +-
 src/main/java/.../trip/service/TripDisplayNameHelper.java                     |   3 +-
 src/main/java/.../user/domain/User.java                                       |  11 +++
 src/main/java/.../user/schedule/domain/Weekday.java                           |  21 -----
 src/main/java/.../user/schedule/repository/PersonalScheduleRepository.java    |   3 -
 src/main/java/.../user/schedule/service/ScheduleCalendarResolver.java         |  39 ++++----
 src/main/java/.../user/schedule/service/ScheduleService.java                  | 104 ++++++++++-----------
 src/test/java/.../user/schedule/domain/WeekdayTest.java                       |   7 --
 src/test/java/.../user/schedule/service/ScheduleServiceTest.java              |  90 +++++-------------
 9 files changed, 103 insertions(+), 178 deletions(-)
```

### 검증

- `./gradlew test` — 전체 통과 (ArchitectureTest 포함)
- `./gradlew test --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"` → `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` — **"No changes detected"**, API 계약 diff 0건 확인
- `ScheduleServiceTest`의 `upsertPersonal_*` 8개 테스트: 이제 단일 `findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc` 스텁만으로 동작하도록 재구성(결과 검증 로직은 동일하게 유지)

### 남겨둔 항목 (C/D — 이번 라운드 보류)

- **C**: `CreateRegularScheduleRequest`/`UpdateRegularScheduleRequest` 구조 중복(record 상속 불가로 공통화 시 오히려 복잡해짐), `ScheduleCalendarResolver.resolve()` 4-인자 오버로드(테스트 전용, 제거 시 10곳 넘는 호출부 수정 필요 대비 실익 낮음), `PersonalSchedule`/`RegularSchedule`의 전역 `@Setter` 컨벤션(저장소 전역 문제라 도메인 단위로 좁혀 고치면 일관성 저하) — audit.md 참고
- **D**: `ScheduleErrorCode` 단일 상수 유지, `ScheduleCalendarResolver` 비-Bean 유지, `ScheduleService` 미분리 유지, `requireOwnedRegularSchedule()` 미추출 유지 — 전부 현재 구조가 더 낫다는 판단, audit.md 참고
