# user-schedule Architecture Audit — 2026-08-05

`com.tripfit.tripfit.user.schedule` 패키지를 대상으로 진행한 1차 아키텍처 감사 문서다. 반드시 수정해야 하는 A 항목 1건과 유지보수성 리팩토링 B 항목 4건(참고 C 3건, 비권장 D 4건)을 도출했다. 도출된 항목은 승인 대기 상태로 정리되었으며, 이후 반영 내역은 `refactor-log.md`에 기록되었다.

## 범위

- 패키지: `com.tripfit.tripfit.user.schedule` (`controller`, `domain`, `dto`, `exception`, `repository`, `service`)
- 테스트: `src/test/java/com/tripfit/tripfit/user/schedule/**` (controller 2개, service 2개, domain 1개)
- 교차 참조(감사만, 수정 대상 아님): `trip/service/ScheduleCalendarResolver` 소비처(`RecommendationEngine`, `TripServiceSupport`), `user/service/UserSummaryService`·`UserLookupService`, `user/googlecalendar/service/GoogleCalendarService`, `notification/service/NotificationEventListener`(`ScheduleService.displayName` 호출)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP
- main 15개 파일, test 6개 파일 전수 검토 (`UserScheduleController`, `PersonalSchedule`, `RegularSchedule`, `VacationApplyPeriod`, `Weekday`, `CreateRegularScheduleRequest`, `PersonalScheduleResponse`, `RegularScheduleResponse`, `ScheduleCalendarResponse`, `UpdatePersonalScheduleRequest`, `UpdateRegularScheduleRequest`, `ScheduleErrorCode`, `PersonalScheduleRepository`, `RegularScheduleRepository`, `ScheduleService`, `ScheduleCalendarResolver`)

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `ScheduleService.upsertPersonal()` — bulk upsert가 항목 수만큼 개별 SELECT를 반복 실행(N+1)

- **Priority**: High
- **Category**: Performance
- **문제**: `upsertPersonal()`(`ScheduleService.java:147-195`)은 `items` 리스트를 순회하며 항목마다 `personalScheduleRepository.findByUserIdAndScheduleDate(userId, item.scheduleDate())`(`:165-167`)를 개별 호출해 존재 여부를 확인한다. 이어서 `buildPersonalResponse()`(`:198-238`)가 반영된 날짜들의 `minDate~maxDate` 구간을 `findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc`로 **다시 통째로 SELECT**한다(`:207-211`) — 방금 루프에서 이미 조회·수정한 행들을 한 번 더 조회하는 것이다.
- **왜 문제인가**: 이 API는 명시적으로 "여러 날짜에 슬롯을 upsert"하는 bulk 엔드포인트다(Swagger 예시에도 여러 `items` 케이스가 문서화돼 있음). `items`가 늘어날수록 DB 왕복 횟수가 그대로 비례해서 늘어나는 전형적인 N+1이며, 여기에 더해 루프 종료 후 동일 데이터 범위를 재조회하는 중복 SELECT까지 얹혀 있다. 인메모리 반복이 아니라 **DB 라운드트립**이 반복되는 것이라 checklist 6번(중복 DB 쿼리)·7번(JPA 점검)에 정면으로 해당한다.
- **개선 방법**: 루프 진입 전에 `items`의 `scheduleDate` 최소~최대 구간으로 `findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(userId, minDate, maxDate)`를 **한 번만** 호출해 `Map<LocalDate, PersonalSchedule>`로 인덱싱하고, 루프 안에서는 이 맵을 조회·갱신한다(신규 항목은 `save()` 후 맵에도 추가). 이렇게 하면 (1) 항목별 개별 SELECT가 사라지고, (2) 루프에서 이미 로드·수정된 관리 엔티티 리스트를 `buildPersonalResponse()`에 그대로 넘겨 재사용할 수 있어 두 번째 range SELECT도 제거된다. 정확히 동일한 업서트 시맨틱(slots/uncertain 부분 갱신, row 삭제 없음)을 유지하므로 비즈니스 로직·API 응답은 변하지 않는다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/schedule/service/ScheduleService.java`
- **예상 변경 라인 수**: ~30~40줄
- **위험도**: Medium — 루프 안에서 신규 저장 엔티티를 맵/리스트에 즉시 반영하는 로직이 새로 필요해, `existing == null` 분기의 흐름이 바뀐다. `ScheduleServiceTest`의 `upsertPersonal_*` 8개 테스트가 각 Mockito 스텁을 정확한 호출 인자로 검증하므로(`findByUserIdAndScheduleDate`, `findByUserIdAndScheduleDateBetween...`), 리팩터링 후 스텁 재구성 필요.
- **테스트 영향도**: `ScheduleServiceTest`의 `upsertPersonal_*` 8개 테스트 전부 스텁 방식 조정 필요(동작 결과 자체는 동일해야 함). 통합 테스트(`PersonalScheduleOverrideIntegrationTest`)는 실제 MySQL 대상이라 변경 없이 회귀 검증 가능.
- **예상 효과**: bulk upsert 요청 1회당 DB 왕복 횟수가 `O(items)`에서 `O(1)`로 감소 — 프론트가 한 화면에서 여러 날짜를 한 번에 저장하는 시나리오(달력 UI)에서 체감 지연이 커질수록 효과가 커진다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `Weekday.parseToDayOfWeekSet()` — 프로덕션 코드에서 미사용(dead code), 유사 파싱 로직이 이미 별도로 존재

- **Priority**: Medium
- **Category**: Dead Code / Cleanup
- **문제**: `Weekday.parseToDayOfWeekSet(String)`(`Weekday.java:64-81`)는 잘못된 토큰을 만나면 `IllegalArgumentException`을 던지는 CSV 파서인데, 실제 호출부는 자기 자신의 테스트(`WeekdayTest.parseToDayOfWeekSet_acceptsLongNames`)뿐이다. 프로덕션 경로(달력 계산)는 전부 `ScheduleCalendarResolver.parseDaysOfWeek(String)`(`ScheduleCalendarResolver.java:147-159`, package-private)를 쓰는데, 이쪽은 잘못된 토큰을 **조용히 스킵**한다는 점에서 시맨틱도 다르다. 즉 같은 목적(CSV→`Set<DayOfWeek>`)의 파서가 서로 다른 에러 처리로 두 벌 존재하고, 그중 하나는 실사용처가 없다.
- **왜 문제인가**: 신규 개발자가 "요일 CSV를 파싱하려면 어느 메서드를 써야 하나"를 판단할 때 두 개의 유사 API가 서로 다른 예외 시맨틱을 갖고 있어 혼동을 유발한다. 실제로 쓰이지 않는 공개 API 표면(`public static`)이 늘어나 있는 상태다.
- **개선 방법**: `Weekday.parseToDayOfWeekSet()`을 삭제하고 `WeekdayTest`의 해당 테스트 메서드도 함께 제거한다(같은 파일의 `normalizeCsv`가 저장 시점 검증을, `ScheduleCalendarResolver.parseDaysOfWeek`가 조회 시점 파싱을 각각 SSOT로 담당하는 구조는 그대로 유지).
- **API 영향**: No Impact — private/protected API 정리, HTTP 계약과 무관.
- **예상 변경 파일**: `user/schedule/domain/Weekday.java`, `src/test/java/.../domain/WeekdayTest.java`
- **예상 변경 라인 수**: ~20줄(삭제 위주)
- **위험도**: Low
- **테스트 영향도**: `WeekdayTest`에서 해당 테스트 1개 제거, 나머지(`normalizeCsv`, `fromToken`)는 영향 없음.
- **예상 효과**: 공개 API 표면 축소, "어느 파서를 써야 하는가" 혼동 제거.

### B-2. `ScheduleService.displayName()` — 일정 도메인과 무관한 사용자 표시명 로직이 `ScheduleService`에 위치

- **Priority**: Low
- **Category**: 패키지 구조 / Readability
- **문제**: `ScheduleService.displayName(User)`(`ScheduleService.java:392-401`, `public static`)는 "성+이름 → nickname → 기본값 '사용자'"를 계산하는 순수 사용자 표시명 로직으로, 정기·개별 일정과는 관련이 없다. 실제 호출부도 `notification/service/NotificationEventListener.java:76`(여행방 참여 알림 메시지 조립) 단 한 곳뿐이며, `ScheduleService` 내부에서는 사용되지 않는다.
- **왜 문제인가**: `user/schedule` 패키지는 "정기·개별 일정 CRUD와 합산 달력"이 책임인데, 이 static 유틸이 여기 얹혀 있어 다른 도메인(`notification`)이 스케줄 서비스를 import하게 만든다. `spring-boot-java.md`의 패키지 레이아웃 원칙(도메인별 책임 분리)과 어긋나고, 향후 `ScheduleService`를 리팩터링할 때 이 무관한 static 메서드 때문에 영향 범위 분석이 헷갈릴 수 있다.
- **개선 방법**: `displayName()`을 `user/domain/User`의 도메인 메서드(예: `User.displayName()`) 또는 `user/service/UserSummaryService`로 이동하고, `NotificationEventListener`의 import·호출부를 갱신한다.
- **API 영향**: No Impact — 순수 내부 리팩터링, HTTP 계약 없음.
- **예상 변경 파일**: `user/schedule/service/ScheduleService.java`, `user/domain/User.java`(또는 `user/service/UserSummaryService.java`), `notification/service/NotificationEventListener.java`
- **예상 변경 라인 수**: ~15줄
- **위험도**: Low — 순수 함수 이동, 로직 변경 없음.
- **테스트 영향도**: 이 메서드에 대한 전용 단위 테스트는 없음(간접적으로 `TripServiceTest`가 `TripMember.displayName()`이라는 별개 메서드를 검증 — 혼동하지 않도록 주의). `NotificationEventListener` 관련 테스트가 있다면 import만 갱신.
- **예상 효과**: 도메인 책임 경계 명확화, `user.schedule` 패키지가 실제로 일정 관련 로직만 담게 됨.

### B-3. `ScheduleService.validateCreateRegular()`/`validateUpdateRegular()` — 값어치 없는 1줄 위임 래퍼 2개

- **Priority**: Low
- **Category**: 보일러플레이트 제거
- **문제**: `validateCreateRegular(CreateRegularScheduleRequest)`(`:279-286`)와 `validateUpdateRegular(UpdateRegularScheduleRequest)`(`:288-295`)는 각각 자기 타입의 필드 5개를 추출해 공통 `validateRegularTimesAndVacation(...)`(`:297-318`)에 그대로 전달하는 것 외에 아무 로직이 없다. 호출부도 `createRegular()`/`updateRegular()` 각 1곳뿐이다.
- **왜 문제인가**: 추가 로직 없는 순수 위임 메서드가 한 단계 더 들어가 있어 `createRegular()`/`updateRegular()`를 읽을 때 불필요한 간접 참조가 생긴다.
- **개선 방법**: 두 래퍼를 제거하고 `createRegular()`/`updateRegular()`에서 `validateRegularTimesAndVacation(request.title(), request.daysOfWeek(), request.startTime(), request.endTime(), request.maxVacationDays())`를 직접 호출한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/schedule/service/ScheduleService.java`
- **예상 변경 라인 수**: ~10줄(삭제 위주)
- **위험도**: Low
- **테스트 영향도**: 없음 — `ScheduleServiceTest`는 `createRegular`/`updateRegular` 퍼블릭 API만 호출하므로 내부 private 메서드 구조 변경과 무관.
- **예상 효과**: 미세한 가독성 개선.

### B-4. `ScheduleCalendarResolver.resolve()` — 날짜별 선형 탐색(O(days × personals), O(days × regulars))

- **Priority**: Low
- **Category**: 성능 최적화
- **문제**: `resolve()`(`ScheduleCalendarResolver.java:35-55`)는 구간의 각 날짜마다 `findPersonal(personals, date)`(`:57-64`)로 `personals` 리스트를 선형 탐색하고, `matchingRegulars(regulars, dayOfWeek)`(`:161-171`)로 `regulars` 리스트를 매번 선형 탐색한다.
- **왜 문제인가**: `getCalendar()`는 최대 today+2년(약 730일) 구간을 허용하고, 그 구간에 개별 일정 오버라이드가 날짜마다 있을 수 있다. 현재 구조에서는 날짜 수와 오버라이드 수가 둘 다 커지면 비교 횟수가 곱으로 늘어난다(현재 규모에서는 체감 지연을 유발할 정도는 아니지만, `audit-checklist.md` 6번 "O(n²) 코드"에 해당하는 패턴이다).
- **개선 방법**: 진입 시 `personals`를 `Map<LocalDate, PersonalSchedule>`로 한 번만 인덱싱하고, `regulars`는 요일별로 미리 그룹핑(`Map<DayOfWeek, List<RegularSchedule>>`)해 각 날짜에서 O(1)/그룹 크기만큼만 조회하도록 바꾼다. 로직·결과값은 동일하게 유지 가능.
- **API 영향**: No Impact — `ScheduleCalendarResolver`는 내부 헬퍼 클래스, DTO 필드·순서에 영향 없음.
- **예상 변경 파일**: `user/schedule/service/ScheduleCalendarResolver.java`
- **예상 변경 라인 수**: ~25줄
- **위험도**: Low — 순수 함수 로직 변경, 입출력 동일.
- **테스트 영향도**: `ScheduleCalendarResolverTest`의 12개 테스트가 입력·출력 기준이라 그대로 통과해야 함. `RecommendationEngine`/`TripServiceSupport`가 재사용하는 `resolve()`·`combineImpossibleWins()`·`matchesDayOfWeek()`의 시그니처는 변경하지 않으므로 두 소비처 테스트(`RecommendationEngineTest` 등)에도 영향 없음.
- **예상 효과**: 큰 달력 구간·많은 오버라이드가 쌓인 계정에서의 CPU 사용량 절감. 현재 규모에서는 체감 효과가 크지 않지만 A-1과 달리 DB 왕복이 아닌 CPU 비용이라 위험 대비 이득이 낮아 B(권장)로 분류.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`CreateRegularScheduleRequest`와 `UpdateRegularScheduleRequest`가 필드 8개·검증 어노테이션까지 완전히 동일한 구조** — 공통 상위 타입으로 묶으면 좋아 보이지만 Java record는 상속을 지원하지 않아, 공통화하려면 인터페이스+접근자 중복 또는 별도 "필드 홀더" 클래스가 필요해 오히려 간접 참조만 늘어난다. 두 DTO는 지금 우연히 같아 보여도 "생성 시 필수 vs 수정 시 일부 필드만 허용" 같은 방향으로 독립적으로 진화할 가능성이 있는 API 계약이라, 지금 합치면 나중에 계약이 갈릴 때 다시 쪼개야 한다 — YAGNI 관점에서 보류.
- **`ScheduleCalendarResolver.resolve(regulars, personals, start, end)` 4-인자 오버로드가 프로덕션에서는 전혀 호출되지 않고 `ScheduleCalendarResolverTest`에서만 10회 이상 사용됨** — 완전한 dead code는 아니고 테스트 가독성(구글 신호 없는 케이스에서 빈 `Map.of()`를 매번 안 써도 됨)을 위한 의도적인 편의 오버로드로 보인다. 지우면 10곳 넘는 테스트 호출부를 전부 5-인자로 바꿔야 해서 비용 대비 실익이 낮다 — 보류.
- **`PersonalSchedule`/`RegularSchedule` 엔티티의 클래스 레벨 `@Setter`가 `id`·`user` 등 모든 필드에 무분별한 setter를 노출** — `applySlots`/`applyUpdate` 같은 의도된 도메인 메서드가 있음에도 `setId`/`setSlotStatuses(null)` 같은 우회 경로가 테스트에서 실제로 쓰이고 있다(`ScheduleServiceTest`, `ScheduleCalendarResolverTest`). 다만 이는 이 도메인만의 문제가 아니라 `Trip`·`TripMember`·`Recommendation` 등 저장소 전역에서 반복되는 컨벤션이다 — user-schedule 하나만 좁혀서 고치면 오히려 코드베이스 전체와의 일관성이 깨지고, 프로덕션 코드가 아닌 테스트 편의를 위해 존재하는 setter까지 걷어내려면 전역 컨벤션 논의가 선행돼야 한다. 이번 도메인 단위 라운드에서는 범위 밖으로 보류.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`ScheduleErrorCode`가 상수 1개(`REGULAR_SCHEDULE_NOT_FOUND`)뿐인 작은 enum** — `spring-boot-java.md`가 명시한 "feature별 `{Feature}ErrorCode`" 컨벤션(`ScheduleErrorCode`가 그 예시로 직접 언급됨)을 그대로 따른 것이며, `CommonErrorCode`와 합치면 도메인 경계가 흐려진다. 상수가 적다고 공용 enum에 합치는 것은 오히려 컨벤션 위반이라 바꾸지 않는다.
- **`ScheduleCalendarResolver`를 Spring 빈으로 승격하지 않음** — 의존성 없는 순수 정적 유틸(`private` 생성자 + `static` 메서드)이며, `RecommendationEngine`·`TripServiceSupport`가 이미 정적 메서드로 재사용 중이다. 빈으로 바꾸면 DI 컨테이너 관리 오버헤드만 추가되고 실익이 없다.
- **일정 CRUD·달력 조회를 각각 별도 Service로 쪼개지 않음** — `ScheduleService`가 정기(create/update/delete/list)·개인(upsert)·합산 달력(getCalendar) 3가지 유스케이스를 갖고 있어 얼핏 커 보이지만, 실제 라인 수(~400줄)와 책임(모두 "본인 일정 관리"라는 단일 응집된 도메인 개념)을 볼 때 `auth`/`user` 감사에서 지적된 God Service 패턴(여러 무관한 책임 혼재)에는 해당하지 않는다. 지금 쪼개면 오히려 3개 유스케이스가 공유하는 `regularScheduleRepository`/`personalScheduleRepository`/`googleCalendarService` 접근이 여러 클래스에 흩어져 응집도가 떨어진다.
- **`requireOwnedRegularSchedule()`을 별도 Support 클래스로 추출하지 않음** — 호출부가 `updateRegular()`/`deleteRegular()` 2곳뿐이고 같은 클래스 안에서 이미 재사용 중이다. `spring-boot-java.md`의 "Support 헬퍼 재사용" 원칙은 여러 Service에 걸친 중복을 막기 위한 것이지, 단일 Service 내부의 2회 재사용까지 별도 클래스로 승격하라는 뜻은 아니다 — 지금 추출하면 파일만 늘어나는 과도한 추상화(YAGNI 위반)다.

## 15. 백엔드 아키텍처 개선 제안

이 도메인 자체(정기/개별 일정 CRUD·합산 달력)는 외부 provider 호출이나 비동기 처리가 필요한 지점이 없다 — Google Calendar 연동은 `user/googlecalendar` 도메인이 이미 별도로 감사·처리했고(`docs/audits/user/audit.md` A-1), 이 도메인은 그 결과(`GoogleCalendarService.findBusyDaysByUserId`)를 순수 조회로만 소비한다. Redis/Circuit Breaker/Async 같은 인프라 도입이 정당화될 만한 지연·장애 전파 지점이 없어 카테고리 15는 제안할 항목이 없다(YAGNI) — A-1·B-4의 쿼리/알고리즘 최적화만으로 이 도메인의 성능 리스크는 충분히 해소된다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
</content>
