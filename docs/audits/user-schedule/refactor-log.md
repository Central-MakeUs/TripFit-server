# user-schedule Refactor Log

사용자 일정(`user.schedule`) 도메인 아키텍처 감사에서 승인된 항목을 실제로 반영한 이력이다. 라운드별 수정 사항과 검증 결과를 기록한다. 감사 결과 원본은 `audit.md` 및 `audit-round2.md`에 있다.

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

## 2026-08-26 — Round 3 (SOLID/OOP 중심) B-1~B-3 반영

감사([`audit-round3.md`](audit-round3.md)) 기준 A 항목 없음, B(유지보수성) 3개 전부 반영. 사용자 승인: "전체 B 승인".

### 쉽게 설명하면 (`core-reporting.md`)

- **B-1:** 최근(2026-08-26) `trip` 도메인의 "포트/어댑터" 구조를 걷어내는 작업으로 `ScheduleAvailabilityService`라는 클래스가 이 도메인으로 새로 옮겨왔는데, 옮겨오기 전 스타일(수동으로 생성자를 써서 부품을 조립하는 방식)이 그대로 남아 있었어요. 다른 모든 서비스 클래스가 쓰는 자동 조립 방식(`@RequiredArgsConstructor`)으로 통일했어요 — 동작은 똑같습니다.
- **B-2:** 정기 일정을 만들거나 수정할 때 "무슨 요일에 반복되는지" 문자열을 검증할 때 한 번, 실제로 저장할 때 또 한 번, 총 두 번 해석하고 있었어요. 한 번만 해석해서 그 결과를 그대로 재사용하도록 정리했어요.
- **B-3:** `ScheduleAvailabilityService`(B-1의 그 클래스)는 "구글 캘린더 일정 + 개인 일정을 합쳐서 보여주는" 핵심 로직을 담당하는데, 정작 이 클래스 하나만 콕 집어 확인하는 테스트가 없어서 다른 3개 기능의 테스트가 우연히 이 로직도 같이 검증해주는 상태였어요. 이 클래스를 바꿨을 때 문제가 생기면 바로 알 수 있도록 전용 테스트를 새로 만들었어요.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| B-1 | `ScheduleAvailabilityService` — `@Component` + 수동 생성자를 `@Service` + `@RequiredArgsConstructor`로 전환(필드 순서 유지로 생성자 시그니처 불변) | `ScheduleAvailabilityService.java` |
| B-2 | `ScheduleService` — `validateRegularTimes()`·`normalizeDaysOfWeek()`가 각자 `Weekday.normalizeCsv()`를 호출해 `daysOfWeek`를 두 번 파싱하던 것을 `validateAndNormalizeRegularTimes()` 하나로 통합, `createRegular()`/`updateRegular()`가 반환값을 그대로 재사용 | `ScheduleService.java` |
| B-3 | `ScheduleAvailabilityServiceTest` 신규 — batch grouping(`findRegularSchedulesByUserIds`/`findPersonalSchedulesByUserIds`)과 `resolveAvailability()`의 구글 busy 병합·조회 대상 외 유저 폴백(sparse) 계약을 직접 검증 | `ScheduleAvailabilityServiceTest.java`(신규) |

### 변경 규모

- 기존 파일 수정 2개 (main): `ScheduleAvailabilityService.java`(-19줄, 순감), `ScheduleService.java`(중복 파싱 메서드 통합)
- 신규 파일 1개 (test): `ScheduleAvailabilityServiceTest.java`
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — Controller·DTO·`ErrorCode` enum·`@Operation`/`@Schema` 파일 전부 미변경

### 검증 결과

- `./gradlew compileTestJava` — 통과
- `./gradlew test`(전체, Testcontainers 실제 MySQL 8 컨테이너 포함, `ArchitectureTest` 포함) — **514개 전체 통과, 0개 실패**
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **"No changes"**(가장 엄격한 확인)

**결론: user-schedule 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목 (Round 3)

`audit-round3.md`의 C(1·2차 C 재검증, 변경 없음), D(2건 신규 + 1·2차 D 재검증) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round3.md` 해당 절 참고.

### Later 후속 제안 (audit-round3.md §15)

이번 라운드에서도 제안할 항목 없음(YAGNI) — 1차 판단 유지.
