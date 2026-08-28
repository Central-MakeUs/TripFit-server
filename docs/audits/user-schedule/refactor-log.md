# user-schedule Refactor Log

## 2026-08-05 — 2차 감사(audit-round2.md) A-1, B-1 반영

1차 반영(아래 절) 이후 재검사(`audit-round2.md`)에서 새로 찾은 A 1건, B 1건을 반영. C/D는 이번 라운드에서도 보류(스펙·API 계약 변경 필요 — `audit-round2.md` 참고).

### 반영 항목

- **A-1 (High, Performance)**: `upsertPersonal()`이 반영된 날짜(`items`)만 필요한데도 `ScheduleCalendarResolver.resolve()`를 `minDate~maxDate` **전체 구간**으로 호출해 그 사이 모든 날짜를 순회하던 것을 제거. `ScheduleCalendarResolver`에 연속 구간이 아니라 **요청받은 날짜 집합**(`Collection<LocalDate>`)만 순회하는 오버로드를 추가하고, 기존 `LocalDate startDate, LocalDate endDate` 오버로드는 내부적으로 그 날짜 집합을 만들어 새 오버로드로 위임하도록 재구성(로직 중복 없이 공유). `getCalendar()`(연속 구간이 실제로 필요한 API)는 기존 오버로드를 그대로 호출해 영향 없음. `upsertPersonal()`의 `googleCalendarService.findBusyDaysByUserId(userId, minDate, maxDate)` 호출은 범위 그대로 유지(이번 라운드 범위 밖 — 단일 쿼리라 순회 비용 문제와 무관).
- **B-1 (Low, Performance/Readability)**: `upsertPersonal()`에서 항목별 값 검증(`validatePersonalItem`)이 구간 SELECT **이후** 루프 안에서 실행되던 것을, 구간 SELECT 전에 전체 항목을 먼저 검증하도록 순서 변경. 잘못된 입력(400 경로)에서 불필요한 DB 조회를 피함.

### 변경 파일

```
 src/main/java/.../user/schedule/service/ScheduleCalendarResolver.java | 17 ++++++++++++++++-
 src/main/java/.../user/schedule/service/ScheduleService.java          | 17 +++++++++++------
 2 files changed, 27 insertions(+), 7 deletions(-)
```

### 검증

- `./gradlew test --tests "com.tripfit.tripfit.user.schedule.*"` — 통과
- `./gradlew test` (전체, ArchitectureTest 포함) — 통과
- `./gradlew test --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"` → `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` — **"No changes detected"**, API 계약 diff 0건 확인

### 남겨둔 항목 (C/D — 이번 라운드 보류)

- **C**: `PATCH /users/schedule/personal`의 `scheduleDate` 날짜 범위 검증 부재(`GET /calendar`와 비대칭) — API 계약 변경(신규 400)이 필요해 무손실 리팩토링 원칙과 충돌, 별도 스펙·승인 필요. 1차 C 항목(요청 record 구조 중복, resolve 4-인자 오버로드, 전역 `@Setter`)도 재검증 결과 여전히 유효.
- **D**: `requireSlotStatus()`의 2-value enum 조건(향후 enum 확장 대비 의도적 방어 코드) 등 — `audit-round2.md` 참고. 1차 D 항목도 재검증 결과 여전히 유효.

---

## 2026-08-05 — A-1, B-1~B-4 반영 (1차)

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
