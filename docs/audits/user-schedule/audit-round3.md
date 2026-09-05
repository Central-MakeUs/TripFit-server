# User-Schedule Architecture Audit — Round 3 (2026-08-26, SOLID/OOP 중심)

> **선행 문서 안내**: `docs/audits/user-schedule/audit.md`(1차, 2026-08-05)와 `audit-round2.md`(2차, 2026-08-05)가 이미 존재하며, 두 라운드의 A(2개)·B(5개) 전부 사용자 승인 후 구현·검증까지 끝났다(`refactor-log.md`). 이번 3차는 `auth`/`user` 도메인 3차 감사(`docs/audits/auth/audit-round3.md`, `docs/audits/user/audit-round3.md`)와 동일하게 **새로 요청받은 SOLID/OOP 관점**으로 현재 코드를 다시 전수 검토한 결과이며, 1·2차가 이미 다룬 항목(N+1 SELECT, `Weekday.parseToDayOfWeekSet` dead code, `displayName` 위치, 1줄 위임 래퍼, `ScheduleCalendarResolver` 선형 탐색, `CreateRegularScheduleRequest`/`UpdateRegularScheduleRequest` 구조 중복, `scheduleDate` 범위 검증 부재 등)은 재검토만 하고 새 판단이 없으면 반복 서술하지 않는다.
>
> 1·2차 이후 코드베이스가 크게 바뀐 지점이 하나 있다 — 2026-08-26 `trip` 포트/어댑터 폐기 리팩터(`docs/decisions/003-architecture-guide.md` 결정 11 폐기, 커밋 `825fb00`)로 옛 `trip/port/out/SchedulePort`의 어댑터였던 클래스가 `user/schedule/service/ScheduleAvailabilityService`로 개명되어 이 패키지에 새로 편입됐다. 1·2차 감사 시점에는 이 클래스가 `trip` 도메인 소속(`SchedulePortAdapter`)이라 감사 범위 밖이었으나, 지금은 `user.schedule` 패키지 소속이라 이번 3차부터 정식 감사 대상이다.
>
> 이번 세션에서 이미 저장소 전역에 반영된 두 가지 공통 변경 — 모든 Service `@RequiredArgsConstructor` 사용, 모든 Entity **클래스 레벨** `@Setter` 제거(도메인 메서드로 상태 전이) — 은 전제로 두고 재발견하지 않았다. 확인 결과 `ScheduleService`는 이미 `@RequiredArgsConstructor`를 쓰고 있었고, `PersonalSchedule`/`RegularSchedule`은 `create`/`applySlots`/`applyUncertain`/`applyUpdate` 도메인 메서드로만 상태를 바꾼다(Tell, Don't Ask 준수). 단, `ScheduleAvailabilityService` 하나가 이 컨벤션에서 벗어나 있음을 새로 발견했다(B-1 참고 — 포트/어댑터 시절 스타일이 개명 후에도 남은 것).

## 범위

- 패키지: `com.tripfit.tripfit.user.schedule` (`controller`, `domain`, `dto`, `exception`, `repository`, `service`) — main 18개 파일 전수 재검토
- 테스트: `src/test/java/com/tripfit/tripfit/user/schedule/**` 참고용 확인
- 교차 확인: `trip/recommendation/algorithm/RecommendationEngine`, `trip/membership/service/TripMemberQueryService`, `trip/schedule/service/TripScheduleSnapshotService`(`ScheduleAvailabilityService` 소비처), `user/service/UserWithdrawalPersistenceService`, `user/googlecalendar/service/GoogleCalendarService`(협력 관계)
- 감사자: 서브에이전트(`Agent` 툴, 읽기 전용) + 상위 세션에서 핵심 주장(파일 경로·줄번호·중복 파싱·전용 테스트 부재·`decisions/003` 인용) 재확인
- 기준: `audit-checklist.md` 1~15항목 + 사용자 지정 우선 렌즈(SRP·OCP·LSP·ISP·DIP·캡슐화·God class/method·feature envy·inappropriate intimacy), `core-guardrails.md` ⛔ STOP

## ✅ A. 반드시 수정해야 하는 사항

이번 라운드에서 A 항목 없음 — Critical/High급 구조적 결함(버그·성능 회귀·보안 문제·명백한 SOLID 위반)을 찾지 못했다. `PersonalSchedule`/`RegularSchedule`의 상태 캡슐화, `ScheduleService`/`ScheduleCalendarResolver`/`ScheduleAvailabilityService` 간 책임 분리, 크로스 도메인 협력 경계 모두 건전하게 유지되고 있음을 확인했다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `ScheduleAvailabilityService` — 포트/어댑터 폐기 리팩터의 잔재로 `@Component` + 수동 생성자만 남음

- **Priority**: Low
- **Category**: Spring Best Practice / Consistency
- **문제**: `ScheduleAvailabilityService.java:21,25,38-49`는 `@Component`로 선언돼 있고 5개 필드(`regularScheduleRepository`, `personalScheduleRepository`, `userRepository`, `holidayProvider`, `googleCalendarService`)를 수동 생성자로 대입한다. 같은 패키지의 `ScheduleService`(`@Service` + `@RequiredArgsConstructor`)나 `user/googlecalendar/service/GoogleCalendarService`(`@Service` + `@RequiredArgsConstructor`)와 스타일이 다르다.
- **왜 문제인가**: 이 클래스는 2026-08-26 `trip` 포트/어댑터 폐기 리팩터(`docs/decisions/003-architecture-guide.md` 결정 11 폐기, 커밋 `825fb00`)로 옛 `SchedulePortAdapter`가 개명된 것이다 — 인터페이스 구현체였을 때 남아 있던 `@Component` + 수동 생성자 스타일이 개명 이후에도 그대로 남아, 이 저장소가 이번 세션에 전역으로 통일한 "Service는 `@RequiredArgsConstructor`" 컨벤션에서 벗어난 유일한 예외가 됐다. 신규 개발자가 "왜 이 클래스만 다른 패턴인가"를 오해할 소지가 있다.
- **개선 방법**: `@Component` → `@Service`로 변경하고 수동 생성자를 삭제해 `@RequiredArgsConstructor`를 적용한다. 필드 선언 순서가 기존 수동 생성자의 인자 순서와 동일해 생성되는 생성자 시그니처는 변하지 않는다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/schedule/service/ScheduleAvailabilityService.java`
- **예상 변경 라인 수**: ~15줄(삭제 위주)
- **위험도**: Low — `RecommendationEngineTest`, `RecommendationEngineTestSetScenarioTest`, `TripScheduleSnapshotServiceTest`, `TripServiceTest` 등이 `new ScheduleAvailabilityService(regularScheduleRepository, personalScheduleRepository, userRepository, holidayProvider, googleCalendarService)`로 직접 인스턴스화하지만, 필드 순서가 그대로 유지되므로 컴파일 영향 없음.
- **테스트 영향도**: 없음 — 생성자 시그니처 불변.
- **예상 효과**: Service 레이어 어노테이션 컨벤션 100% 일관성 회복, 포트/어댑터 폐기가 스타일까지 완전히 정리됐음을 코드로 확인 가능해짐.

### B-2. `ScheduleService` — 정기 일정 생성·수정마다 `daysOfWeek` 문자열을 같은 요청 안에서 두 번 파싱

- **Priority**: Low
- **Category**: Performance / Cleanup (중복 코드)
- **문제**: `validateRegularTimes()`(`ScheduleService.java:317-333`)는 검증을 위해 `Weekday.normalizeCsv(daysOfWeek)`(`:329`)를 호출하고 반환값은 버린다(예외 발생 여부만 확인). 곧이어 `createRegular()`(`:87-93`)·`updateRegular()`(`:104-114`)는 실제 저장값을 얻기 위해 `normalizeDaysOfWeek(daysOfWeek)`(`:341-347`)를 호출하는데, 이 메서드도 내부적으로 `Weekday.normalizeCsv(daysOfWeek)`(`:343`)를 **다시** 호출한다. 결과적으로 같은 입력 문자열을 한 요청 처리 안에서 두 번 파싱하고, `IllegalArgumentException → TripFitException(CommonErrorCode.INVALID_INPUT)` 변환 catch 블록도 두 메서드에 동일하게 중복돼 있다.
- **왜 문제인가**: checklist 1번(중복 validation)·6번(동일 데이터 반복 계산)에 해당한다. 파싱 비용 자체는 작지만(최대 7개 토큰), "검증은 검증대로, 정규화는 정규화대로 각자 다시 파싱한다"는 이원화된 구조가 두 private 메서드 사이에 암묵적 결합(같은 입력을 서로 몰래 다시 파싱해야 한다는 전제)을 만들어 유지보수 시 혼동을 유발한다.
- **개선 방법**: `validateRegularTimes`와 `normalizeDaysOfWeek`를 하나로 합쳐 `daysOfWeek`를 **한 번만** 파싱하고 정규화된 값을 반환하도록 재구성한다(예: `private String validateAndNormalizeRegularTimes(title, daysOfWeek, startTime, endTime)`가 title/시간 검증 + `Weekday.normalizeCsv` 1회 호출 + 정규화된 문자열 반환). `createRegular()`/`updateRegular()`는 이 반환값을 그대로 `RegularSchedule.create()`/`applyUpdate()`에 전달한다. 2차 감사에서 확립된 "검증이 `userLookupService.requireUser(userId)`(DB 조회)보다 먼저 실행되는" fail-fast 순서는 그대로 유지한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/schedule/service/ScheduleService.java`
- **예상 변경 라인 수**: ~20줄
- **위험도**: Low — 순수 내부 리팩터링, 입출력·예외 타입(`TripFitException(CommonErrorCode.INVALID_INPUT)`) 동일.
- **테스트 영향도**: `ScheduleServiceTest`의 `createRegular`/`updateRegular` 관련 테스트는 public API만 호출하므로 영향 없음.
- **예상 효과**: 중복 파싱·중복 catch 블록 제거, "검증 결과를 실제로 재사용한다"는 구조가 코드에 명확히 드러남.

### B-3. `ScheduleAvailabilityService` — 전용 단위 테스트 없이 3개 하류 소비자 테스트로만 간접 검증됨

- **Priority**: Low
- **Category**: 테스트 가능성 (checklist 14)
- **문제**: `find src/test -iname "*ScheduleAvailability*"` 결과 전용 테스트 파일이 없다. `findRegularSchedulesByUserIds`/`findPersonalSchedulesByUserIds`의 batch grouping 결과, `resolveAvailability()`가 캡슐화하는 "구글 busy 조회 → 정기·개별 일정 병합" 순서·우선순위 계약이 `RecommendationEngineTest`·`TripMemberQueryServiceTest`(또는 `TripScheduleSnapshotServiceTest`) 3곳에 각각 mock으로 우회되거나 간접적으로만 검증된다.
- **왜 문제인가**: 이 클래스는 포트/어댑터 폐기 이후 "trip이 필요로 하는 일정 조회"의 유일한 진입점이 됐는데, 정작 자기 자신의 계약(N+1 방지를 위한 batch grouping, 조회 순서, 조회 대상에 없는 유저에 대한 폴백 처리 등)을 검증하는 테스트가 하류 소비자 테스트 안에 흩어져 있다. 이 클래스 자체를 바꿔도 회귀가 하류 테스트들에서만 산발적으로 잡히는 구조라, 실패 원인 추적이 이 클래스가 아니라 여러 파일을 오가며 이뤄진다.
- **개선 방법**: `ScheduleAvailabilityServiceTest`를 신설해 `findRegularSchedulesByUserIds`/`findPersonalSchedulesByUserIds`의 grouping 결과와 병합 로직(구글 busy 우선순위, 조회 대상 외 유저 폴백)을 Mockito로 직접 검증한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `src/test/java/com/tripfit/tripfit/user/schedule/service/ScheduleAvailabilityServiceTest.java`(신규)
- **예상 변경 라인 수**: ~100~150줄(신규 테스트, 프로덕션 코드 변경 없음)
- **위험도**: Low
- **테스트 영향도**: 순증가만, 기존 테스트 변경 없음.
- **예상 효과**: 이 클래스를 바꿀 때 하류 테스트 파일들을 뒤져보지 않아도 자체 계약이 깨졌는지 바로 확인 가능.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **1·2차 audit.md/audit-round2.md의 C 항목 재검증 결과 — 여전히 유효, 변경 없음.** `CreateRegularScheduleRequest`/`UpdateRegularScheduleRequest` 구조 중복(record 상속 불가로 공통화 시 오히려 복잡해짐), `ScheduleCalendarResolver.resolve()` 4-인자 오버로드(테스트 전용, 실사용처 없음), `PersonalSchedule`/`RegularSchedule`의 전역 `@Setter`(저장소 전역 이슈), `PATCH /users/schedule/personal`의 `scheduleDate` 범위 검증 부재(API 계약 변경이 필요해 무손실 리팩토링 원칙과 충돌, 별도 스펙·승인 필요) — 모두 코드를 다시 읽어 확인했으며 판단이 그대로 유효하다. 새로 추가할 C 항목은 없다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`user/schedule`이 `trip/membership/repository/TripMemberRepository`를 직접 참조(`ScheduleService.java:8,62`)하는 구조 유지.** DIP 관점에서 "user 도메인이 trip 도메인 repository에 직접 의존"하는 것으로 보일 수 있으나, 이 정확한 패턴은 2026-08-26 포트/어댑터 폐기 결정(`docs/decisions/003-architecture-guide.md` 결정 11)의 근거로 이미 명시적으로 검토됐다 — 해당 문서는 "`user` 도메인이 다른 경로로 이미 `trip` 타입에 의존하고 있어 이 포트가 순환 의존을 막는 효과도 없었다"고 명시한다. 지금 이걸 다시 인터페이스로 감싸면 바로 직전에 사용자가 명시적으로 폐기한 결정을 되돌리는 것이므로 손대지 않는다.
- **`RecommendationEngine`이 `RegularSchedule.getSlotStatuses()`/`PersonalSchedule.getSlotStatuses()`를 직접 읽는 구조 유지.** 얼핏 feature envy로 보이지만, 이 엔티티들은 오직 `ScheduleAvailabilityService.findRegularSchedulesByUserIds`/`findPersonalSchedulesByUserIds`라는 정식 배치 조회 경로로만 `RecommendationEngine`에 전달되므로 캡슐화 경계 위반이 아니다. 연차/반차 자동 전환 시뮬레이션은 "이 슬롯이 정기 근무 때문에 막혔는지, 개별 일정·구글 때문에 막혔는지"를 슬롯 단위로 구분해야 하는데, `ScheduleCalendarResolver`가 만드는 병합된 `CalendarDayResponse`는 이미 출처 정보를 잃은 최종값이라 이 목적에 쓸 수 없다 — 원시 엔티티 접근이 실제로 필요하므로 새 파사드 메서드를 억지로 추가하지 않는다.
- **1차 audit.md/2차 audit-round2.md의 D 항목 재검증 결과 — 여전히 유효.** `ScheduleErrorCode` 단일 상수 유지(`spring-boot-java.md` feature별 ErrorCode 컨벤션), `ScheduleCalendarResolver` 비-Bean 유지(순수 정적 유틸), `ScheduleService` 미분리 유지(단일 응집 도메인), `requireOwnedRegularSchedule()` 미추출 유지(호출부 2곳뿐), `requireSlotStatus()`의 2-value enum 방어 코드(향후 enum 확장 대비 의도적 코드) — 모두 재검토 결과 현재 구조가 더 낫다는 판단이 그대로 유효하다.

## 15. 백엔드 아키텍처 개선 제안

이번 라운드에서도 제안할 항목 없음(YAGNI) — 1차 감사(`audit.md`)의 결론이 그대로 유효하다. 이 도메인 자체(정기/개별 일정 CRUD·합산 달력·`ScheduleAvailabilityService`의 배치 조회)에는 외부 provider 호출이나 비동기 처리가 필요한 지점이 없다. Google Calendar 연동은 `user/googlecalendar` 도메인이 이미 별도로 감사·처리했고, 이 도메인은 그 결과(`GoogleCalendarService.findBusyDaysByUserId`)를 순수 조회로만 소비한다. `trip` 포트/어댑터 폐기로 `ScheduleAvailabilityService`가 이 패키지에 새로 편입됐지만, 이 변화는 의존성 주입 경로만 바꿨을 뿐 이 도메인의 지연·장애 전파 프로파일에는 영향이 없어 Redis/Circuit Breaker/Async 같은 인프라 도입을 정당화하지 않는다.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다(B-1: `ScheduleAvailabilityService` `@RequiredArgsConstructor` 전환, B-2: `daysOfWeek` 중복 파싱 제거, B-3: `ScheduleAvailabilityServiceTest` 신설). C/D는 이번 라운드에서 수정하지 않습니다.
