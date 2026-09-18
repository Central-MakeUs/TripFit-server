# Trip Architecture Audit — 2026-08-26

## 범위

- 패키지: `com.tripfit.tripfit.trip` (+ 하위 패키지: recommendation, membership, schedule, scheduler, port)
- 감사자: 서브에이전트 (Agent 툴, 읽기 전용)
- 기준: audit-checklist.md 1~15항목, harness-workflow.md ⛔ STOP
- 가중치: 9번(패키지 구조)·14번(테스트 가능성)에 우선순위 — `trip/port/out` 3개 인터페이스(`SchedulePort`·`GoogleCalendarPort`·`UserDirectoryPort`) 제거 결정 때문

## ✅ A. 반드시 수정해야 하는 사항

### A-1. "구글 busy 조회 → 일정 병합" 2단계 호출 순서가 3곳에 그대로 복제돼 있고, 순서 보장이 주석에만 있다

- **Priority**: Medium
- **Category**: Correctness / Cleanup (checklist #1 중복 코드)
- **문제**: `SchedulePort.resolveMergedSchedules(...)`는 "호출부가 `GoogleCalendarPort.findBusyDaysByUserIds(...)`를 먼저 호출해서 그 결과를 인자로 넘겨야 한다"는 전제를 갖고 있다(`SchedulePort.java` 42번째 줄 주석). 이 2단계 호출(①구글 busy 조회 → ②그 결과를 인자로 merge 호출)이 아래 3곳에 토씨 하나 다르지 않게 복제돼 있다.
  - `TripMemberQueryService.buildLive(...)` (`trip/membership/service/TripMemberQueryService.java:107-114`)
  - `TripScheduleSnapshotService.freezeTrip(...)` (`trip/schedule/service/TripScheduleSnapshotService.java:48-55`)
  - `RecommendationEngine.loadContext(...)` (`trip/recommendation/algorithm/RecommendationEngine.java:618-622`)
- **왜 문제인가**: 이 순서 제약은 컴파일러가 강제하지 않고 Javadoc 주석에만 적혀 있다. 새 호출부가 추가될 때(또는 기존 3곳 중 하나를 수정하다가) 순서를 바꾸거나 googleBusyByUser를 빈 Map으로 잘못 넘기면, 구글 캘린더 연동 사용자의 바쁨 정보가 조용히 무시된 채로 "가능"으로 잘못 계산된다 — 예외 없이 통과하는 silent 데이터 오류라 테스트가 그 케이스를 놓치면 운영에서만 드러난다.
- **개선 방법**: 두 호출을 하나로 묶는 메서드(`resolveMergedSchedulesWithGoogleBusy(userIds, start, end)` 등)를 만들어 호출부가 순서를 신경 쓸 필요가 없게 한다. B-1(포트 제거)과 정확히 같은 3개 파일·같은 위치를 건드리는 변경이라 **B-1과 같은 PR에서 함께 처리하는 것을 권장** — 별도로 하면 같은 파일을 두 번 건드리게 된다.
- **API 영향**: No Impact
- **예상 변경 파일**: `TripMemberQueryService.java`, `TripScheduleSnapshotService.java`, `RecommendationEngine.java` + (신규 메서드를 어디에 둘지는 B-1의 최종 클래스 구성에 따름 — `ScheduleAvailabilityAdapter` 또는 `GoogleCalendarPortAdapter` 둘 중 하나)
- **예상 변경 라인 수**: 약 30~40줄 (3개 호출부 각 2줄 삭제 + 신규 메서드 1개 추가)
- **위험도**: Low — 반환값·계산 로직은 100% 동일, 호출 순서만 메서드 내부로 캡슐화
- **테스트 영향도**: 기존 7개 테스트 중 `RecommendationEngineTest`·`RecommendationEngineTestSetScenarioTest`·`TripScheduleSnapshotServiceTest`·`TripServiceTest`는 `googleCalendarService.findBusyDaysByUserIds(...)` stub이 여전히 호출되는지만 검증하면 되므로 assert 변경 없음
- **예상 효과**: 순서 제약이 타입 안전하게 강제되고, 신규 호출부를 추가할 때 실수로 순서를 어길 수 없어짐

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `trip/port/out` 3개 인터페이스 제거 → concrete 클래스 직접 주입 (Controller → Service → Repository)

- **Priority**: High
- **Category**: Architecture (checklist #9 패키지 구조, #14 테스트 가능성)
- **문제**: `SchedulePort`·`GoogleCalendarPort`·`UserDirectoryPort` 3개 인터페이스는 각각 구현체가 정확히 1개(`ScheduleAvailabilityAdapter`·`GoogleCalendarPortAdapter`·`UserDirectoryAdapter`, 전부 `user` 패키지)뿐이다. 헥사고날 포트/어댑터 패턴이 의미가 있으려면 (a) 구현체가 여러 개이거나 (b) 순환 의존을 실제로 끊어야 하는데, 이 저장소는 이미 `user` 패키지가 다른 경로로 `trip` 패키지 타입을 import하고 있어(예: 아래 확인) 포트가 패키지 레벨 순환을 막고 있지도 않다. 사용자가 검토 후 "포트 3개를 걷어내고 plain Controller→Service→Repository로 가되, `SchedulePort` 도입 전 여러 trip 서비스가 각자 user repository를 직접 조회해 로직을 중복시켰던 문제(`TripServiceSupport.resolveMergedSchedules`, `RecommendationEngine.loadContext` 등)는 재발하면 안 된다"는 제약을 걸었다.
- **왜 문제인가 (근거 확인)**:
  - **구현체가 1개뿐:** `SchedulePort`→`ScheduleAvailabilityAdapter`, `GoogleCalendarPort`→`GoogleCalendarPortAdapter`, `UserDirectoryPort`→`UserDirectoryAdapter`. 각 인터페이스 javadoc 자체가 "trip 패키지는 이 클래스의 존재 자체를 모른다"고 설명하는데, 테스트에서는 이미 이 전제가 깨져 있다(아래 테스트 절 참고) — trip 테스트 코드가 `user.schedule.service.ScheduleAvailabilityAdapter`·`user.googlecalendar.service.GoogleCalendarPortAdapter`를 이미 직접 import해서 씀.
  - **순환 의존을 막지 못함:** 포트가 있어도 `user` 도메인은 다른 경로로 이미 `trip` 타입에 의존한다 — 예: `TripAuthorizationInterceptor`가 `trip.service.TripServiceSupport`를 쓰고, `trip.membership.domain.TripMember`가 `user.domain.User`를 `@ManyToOne`으로 참조하는 등, "포트가 있어야만 컴파일된다" 수준의 순환 차단 효과가 실제로는 없다.
  - **유일하게 실재하는 이득:** 테스트 7개가 이 인터페이스를 mock 경계로 쓰고 있다는 것 — 이건 진짜 이득이라 제거 후에도 반드시 보존해야 한다(아래 테스트 절).
- **개선 방법 — 정확한 변경 대상**:

  **1) 삭제할 파일 (3개, 같은 PR에서 즉시 — STOP §4 레거시 절)**
  - `src/main/java/com/tripfit/tripfit/trip/port/out/SchedulePort.java`
  - `src/main/java/com/tripfit/tripfit/trip/port/out/GoogleCalendarPort.java`
  - `src/main/java/com/tripfit/tripfit/trip/port/out/UserDirectoryPort.java`
  - `trip/port/out/` 디렉터리 자체도 비워지므로 함께 삭제(빈 패키지 방치 금지)

  **2) 어댑터 3개는 그대로 유지 — `implements XxxPort`만 제거**
  이 3개 클래스는 여전히 "trip이 필요로 하는 조회를 user 도메인 repository/service 여러 개에서 모아주는 단일 호출 지점"이라는 실질적 역할을 하고 있고, 특히 `ScheduleAvailabilityAdapter.resolveMergedSchedules`는 `RegularScheduleRepository`+`PersonalScheduleRepository`+`UserRepository`+`HolidayProvider` 4개를 조합하는 진짜 로직(단순 위임이 아님)이다. 포트 이전에 있던 "trip 서비스마다 이 repository들을 각자 주입받아 중복 쿼리"하던 문제(과거 `TripServiceSupport.resolveMergedSchedules`, `RecommendationEngine.loadContext`가 각자 구현)는 **이 3개 클래스를 인터페이스 없이 concrete 클래스로 유지하고, trip 쪽이 그 클래스 하나만 주입받으면 그대로 재발하지 않는다** — "인터페이스를 지운다"와 "여러 곳에서 개별 조회로 되돌아간다"는 별개 문제다.
    - `src/main/java/com/tripfit/tripfit/user/schedule/service/ScheduleAvailabilityAdapter.java`: `implements SchedulePort` 제거, `import com.tripfit.tripfit.trip.port.out.SchedulePort;` 제거. 클래스 주석("trip.port.out.SchedulePort 구현체")도 "user.schedule이 소유한 사용자 일정 조회 서비스 — trip 쪽 여러 서비스가 각자 repository를 중복 조회하지 않도록 이 클래스 하나로 모은다"로 갱신.
    - `src/main/java/com/tripfit/tripfit/user/googlecalendar/service/GoogleCalendarPortAdapter.java`: `implements GoogleCalendarPort` 제거, import 제거. 이 클래스는 `GoogleCalendarService.findBusyDaysByUserIds`로 순수 위임만 하는 1메서드 클래스다 — 인터페이스가 사라지면 이 클래스를 유지할지, 아니면 trip이 `GoogleCalendarService`를 직접 주입받게 할지 **결정이 필요하다**(아래 "결정 필요" 참고).
    - `src/main/java/com/tripfit/tripfit/user/service/UserDirectoryAdapter.java`: `implements UserDirectoryPort` 제거, import 제거. 이 클래스는 `UserLookupService`+`UserRepository`+`UserProfileService`+`UserSummaryService` 4개를 trip이 쓰는 3개 메서드로 좁혀주는 역할을 유지한다 — trip이 이 4개 서비스를 각각 직접 주입받게 하면 trip이 몰라도 되는 `registerOnboardingName`·`updateProfile`·`connect`·`disconnect` 같은 user 전용 메서드까지 노출 표면이 넓어지므로, **어댑터 형태 유지를 권장**.

  **3) trip 쪽 호출부 5개 파일 — 필드·생성자 타입을 인터페이스 → concrete 클래스로 교체**

  | 파일 | 현재 타입 | 교체 타입 |
  |---|---|---|
  | `trip/recommendation/algorithm/RecommendationEngine.java` (44, 46, 51-52행) | `SchedulePort`, `GoogleCalendarPort` | `ScheduleAvailabilityAdapter`, `GoogleCalendarPortAdapter`(유지 시) |
  | `trip/schedule/service/TripScheduleSnapshotService.java` (29, 31행) | `SchedulePort`, `GoogleCalendarPort` | 동일 |
  | `trip/membership/service/TripMemberQueryService.java` (42, 44행) | `SchedulePort`, `GoogleCalendarPort` | 동일 |
  | `trip/service/TripServiceSupport.java` (53, 58행) | `UserDirectoryPort` | `UserDirectoryAdapter` |
  | `trip/service/TripCommandService.java` (25, 53행) | `UserDirectoryPort` | `UserDirectoryAdapter` |

  각 파일에서 `import com.tripfit.tripfit.trip.port.out.{Port};` 삭제, `import com.tripfit.tripfit.user.{...}.service.{Adapter};` 추가, 필드·생성자 파라미터 타입 교체 — 나머지 로직·메서드 호출은 100% 동일(메서드 시그니처가 바뀌지 않으므로).

  **4) 부수 정리(선택, 같은 PR 권장) — `TripCommandService`의 이중 접근 경로**
  `TripCommandService`는 `support.findUser(userId)`(내부적으로 `userDirectoryPort.requireUser` 위임)와 자기 필드 `userDirectoryPort.requireProfileNameComplete(...)`를 **둘 다** 쓴다(`createTrip`·`joinTrip`). 어댑터 타입 교체 김에 `TripServiceSupport`에 `requireProfileNameComplete(User)` 위임 메서드를 추가해 `TripCommandService`가 user 어댑터를 직접 주입받지 않고 `support`를 통해서만 접근하게 하면, trip 쪽에서 "user 도메인 접근 지점 = `TripServiceSupport` 하나"라는 규칙이 더 명확해진다. 필수는 아니라 사용자 승인 시에만 포함.

- **테스트 7개 — 각각 어떻게 바뀌는지**

  | 파일 | 현재 패턴 | 변경 후 |
  |---|---|---|
  | `RecommendationEngineTest.java` | `SchedulePort schedulePort = new ScheduleAvailabilityAdapter(...)` — **이미 concrete 어댑터를 실제로 생성**해서 씀(포트 자체를 mock하지 않음) | 로컬 변수 선언 타입만 `ScheduleAvailabilityAdapter`/`GoogleCalendarPortAdapter`로 교체. 동작 변화 없음 |
  | `RecommendationEngineTestSetScenarioTest.java` | 위와 동일 패턴 | 동일 |
  | `TripScheduleSnapshotServiceTest.java` | `@Mock private UserDirectoryPort userDirectoryPort;`(진짜 인터페이스 mock) + Schedule/GoogleCalendar는 concrete 어댑터 생성 | `@Mock private UserDirectoryAdapter userDirectoryAdapter;`로 교체(Mockito가 concrete 클래스도 mock 가능 — 이 파일 안에서 이미 `TripScheduleSnapshotService` 자체도 다른 테스트에서 concrete mock되는 선례 있음). Schedule/GoogleCalendar 부분은 타입만 교체 |
  | `TripServiceSupportTest.java` | `UserDirectoryPort userDirectoryPort = mock(UserDirectoryPort.class);` (2개 테스트 메서드) | `mock(UserDirectoryAdapter.class)`로 교체 |
  | `TripAuthorizationInterceptorTest.java` | `@Mock private UserDirectoryPort userDirectoryPort;` | `@Mock private UserDirectoryAdapter userDirectoryAdapter;` |
  | `TripRecommendationServiceTest.java` | `@Mock private UserDirectoryPort userDirectoryPort;`(`RecommendationEngine` 자체는 이미 `@Mock`) | `@Mock private UserDirectoryAdapter userDirectoryAdapter;` |
  | `TripServiceTest.java` | `UserDirectoryPort userDirectoryPort = new UserDirectoryAdapter(...)` — **이미 mock이 아니라 real 어댑터 생성** + Schedule/GoogleCalendar도 real 어댑터 생성 | 로컬 변수 선언 타입만 concrete로 교체. 동작 변화 없음 |

  **핵심 확인 사항:** `SchedulePort`·`GoogleCalendarPort`는 7개 테스트 어디에서도 인터페이스 자체를 `@Mock`/`mock()`한 적이 없다 — 전부 concrete 어댑터를 실제로 생성해서 그 내부의 repository/service를 mock했다. 즉 이 두 포트는 "narrow test double"로서의 실사용 실적이 0건이다. `UserDirectoryPort`만 4개 테스트(5개 호출 지점)에서 진짜로 인터페이스를 mock했는데, `UserDirectoryAdapter`가 `final`이 아니고 `final` 메서드도 없으므로 Mockito가 concrete 클래스를 그대로 mock할 수 있다(이 저장소 테스트에서 `RecommendationEngine`·`TripScheduleSnapshotService`도 이미 동일하게 concrete mock되고 있어 선례 확인됨). **7개 테스트 전부 assert·given/when/then 로직 변경 없이 타입 선언만 바뀐다.**

- **API 영향**: No Impact — Controller·DTO·ErrorCode 어떤 것도 바뀌지 않음, 순수 내부 리팩토링
- **예상 변경 파일**: 삭제 3개(포트 인터페이스) + 수정 3개(어댑터, `implements` 제거) + 수정 5개(trip 호출부) + 수정 7개(테스트) = 총 18개 파일 변경, 삭제 3개
- **예상 변경 라인 수**: 약 120~160줄 (대부분 import·타입 선언 교체, 로직 변경 없음)
- **위험도**: Low — 메서드 시그니처·반환값·트랜잭션 경계 전부 동일, 컴파일러가 타입 교체 누락을 즉시 잡아줌
- **테스트 영향도**: 7개 파일 수정하지만 전부 "무엇을 mock하는지"만 바뀌고 "무엇을 검증하는지"는 그대로 — 회귀 위험 낮음
- **예상 효과**: 패키지 3개(`SchedulePort`+`ScheduleAvailabilityAdapter` 등) → 1개로 줄어 "이 인터페이스는 왜 있고 구현체를 어디서 찾아야 하나"를 매번 되짚을 필요가 없어짐. `trip/port/out` 패키지 자체가 사라져 트리 구조가 요청한 대로 plain Controller→Service→Repository에 가까워짐

**결정이 필요한 지점 (사용자 확인 요망):**
1. `GoogleCalendarPortAdapter`(순수 1메서드 위임 클래스)를 그대로 유지할지, 아니면 없애고 trip이 `GoogleCalendarService`를 직접 주입받게 할지 — 후자는 클래스 하나가 더 줄지만 trip이 `connect`/`disconnect`/`syncUser` 같은 OAuth 메서드까지 볼 수 있는 넓은 표면이 노출된다. **유지를 기본 권장.**
2. 어댑터 3개의 이름(`XxxAdapter`)을 포트 제거 후에도 그대로 둘지, 아니면 "Port"·"Adapter"라는 헥사고날 용어가 더 이상 맞지 않으니 개명할지 — 개명하면 스펙·문서·다른 참조까지 같은 턴에 맞춰야 해서 범위가 커진다(`spring-boot-java.md` 네이밍 우선 원칙). **이번 PR에서는 이름 유지, 개명은 별도 후속으로 미루는 것을 권장** (C-1 참고).
3. A-1(중복 2단계 호출 통합)을 이 PR에 함께 포함할지 — 파일이 겹치므로 함께 하는 것을 권장하지만, 범위를 최소화하고 싶으면 분리 가능.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

### C-1. 어댑터 3개 개명 (`ScheduleAvailabilityAdapter`·`GoogleCalendarPortAdapter`·`UserDirectoryAdapter`)
포트가 사라지면 "Adapter"라는 이름이 가리키던 헥사고날 문맥이 없어진다. 특히 `GoogleCalendarPortAdapter`는 이름에 "Port"가 그대로 박혀 있어 포트 제거 후엔 이름만 보고 오해하기 쉽다. 다만 개명은 `spring-boot-java.md` 네이밍 규칙상 "같은 턴에 전부 최신화"(테스트·문서 포함) 대상이라 B-1과 범위가 겹치지 않게 **별도 PR로 분리**하는 것을 권장. 지금 하지 않는 이유: B-1 자체의 위험도를 낮게 유지하기 위해 "타입만 바꾸는 변경"과 "이름을 바꾸는 변경"을 섞지 않는 편이 리뷰하기 쉽다.

### C-2. `CalendarDayResponse` → `CalendarDay` 수동 필드 매핑 보일러플레이트
`TripMemberQueryService.buildLive`가 `user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse`를 `trip.schedule.dto.MemberScheduleCalendarResponse.CalendarDay`로 필드 5개를 하나씩 옮겨 담는다(107-128행). 두 레코드가 사실상 같은 필드 구성이라 정적 팩터리 메서드(`CalendarDay.from(CalendarDayResponse)`) 하나로 줄일 수 있다. 지금 하지 않는 이유: 요청 범위(포트 제거) 밖이고, 코드량이 크지 않아 우선순위가 낮음 — 다음에 이 파일을 건드릴 일이 생기면 같이 정리 권장.

### C-3. `RecommendationEngine`(725줄) 중 연차 시뮬레이션 부분(약 200줄) 분리
`collectVacationOptions`·`addShiftUnits`·`openSlots`·`applyVacationSimulation`이 파일의 상당 부분을 차지한다. 클래스가 커 보이지만(#9 God Object 체크), 후보 스코어링이라는 하나의 알고리즘 흐름 안에서 단계별로 나뉜 것이라 응집도는 높다. 지금 하지 않는 이유: 비트마스크 완전탐색 로직은 `#105` 정책(연차/반차 자동 전환)과 1:1로 얽혀 있어 분리하다 미묘한 동작 차이를 만들 위험이 무손실 리팩토링 원칙에 안 맞음 — 아래 D-1 참고.

### C-4. `TripCommandService`의 `UserDirectoryPort`/`UserDirectoryAdapter` 이중 접근 경로 완전 제거
B-1의 "부수 정리(선택)" 항목(위 참고)을 별도로 분리해 진행할 수도 있다. 지금 포함하지 않는 이유: B-1 승인 범위를 명확히 하기 위해 필수 변경과 선택 변경을 나눠 제시함 — 사용자가 B-1 승인 시 함께 할지 정할 수 있음.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

### D-1. `RecommendationEngine`을 여러 클래스로 쪼개지 않는다
725줄이라는 크기만 보고 분리하면, 이미 테스트로 촘촘히 검증된 단일 알고리즘(후보 윈도우 생성 → 연차 시뮬레이션 → 3분류 → 4종 패널티 스코어링)이 여러 파일에 흩어져 오히려 "이 계산이 어디서 끝나고 어디서 시작하는지" 추적이 더 어려워진다. 각 private 메서드가 이미 역할 주석으로 잘 구획돼 있어 크기 자체가 가독성을 해치지 않는다 — 파일 하나 vs 클래스 여러 개 사이의 이동 비용(테스트 재배치, private 메서드 접근성 조정)이 얻는 이득보다 크다.

### D-2. 포트 3개를 없앤 자리에 "통합 게이트웨이" 하나로 다시 묶지 않는다
`SchedulePort`+`GoogleCalendarPort`+`UserDirectoryPort`를 없애면서 "trip이 user 도메인에 접근하는 통로"를 다시 하나의 큰 Facade/Gateway 클래스로 합치고 싶은 유혹이 있을 수 있는데, 이러면 정확히 지금 없애려는 것(구현체 1개짜리 불필요한 추상화 레이어)을 이름만 바꿔 재도입하는 셈이다. 사용자가 요청한 "plain Controller→Service→Repository"는 trip 서비스가 필요한 concrete 클래스(`ScheduleAvailabilityAdapter` 등) 각각을 직접 주입받는 것이지, 새 중간 계층을 만드는 게 아니다.

### D-3. `GoogleCalendarPortAdapter`·`ScheduleAvailabilityAdapter`에 캐싱을 추가하지 않는다
정기/개별 일정·구글 busy 조회는 매 요청마다 DB·외부 API를 다시 조회한다. 캐싱하면 빨라질 여지는 있지만, 지금 이 조회들이 실제로 느리다는 근거(APM·로그)가 없고, 캐시 무효화 정책(일정이 바뀌면 언제 갱신?)까지 설계해야 해서 복잡도만 늘고 실효는 불확실 — "최신 기술이라서"에 해당하는 과잉 설계.

### D-4. 연차 시뮬레이션의 완전탐색(비트마스크)을 더 "정교한" 알고리즘으로 교체하지 않는다
`MAX_CONVERSION_UNITS = 20`으로 상한을 걸어둔 완전탐색 방식은 코드만 보고도 정확히 어떤 조합을 시도하는지 감사할 수 있다는 장점이 있다. 그리디·동적계획법 등으로 바꾸면 이론상 더 빠를 수 있지만, `#105` 정책의 "동점이면 연차를 덜 쓰는 조합 우선" 같은 세부 규칙까지 100% 동일하게 재현해야 하는 무손실 리팩토링 제약 아래에서는 위험 대비 이득이 낮다.

## 15. 백엔드 아키텍처 개선 제안

| 카테고리 | 제안 | Now/Later/Never | 이유 |
|---|---|---|---|
| Monitoring | `RecommendationEngine.applyVacationSimulation`의 실행 시간 계측(로그 또는 메트릭) 추가 | Later | 연차 전환 완전탐색은 `MAX_CONVERSION_UNITS=20` 상한이 있어도 최악의 경우 멤버 1명·후보 구간 1개당 최대 2^20(약 100만) 조합을 순회할 수 있다. 지금은 상한값이 방어선 역할을 하고 있고 실제로 느리다는 신고·로그 근거가 없어 지금 당장 계측 코드를 추가하는 건 이르지만, 참여 인원(최대 10명)·정기 일정 행 수가 늘어나는 실사용 데이터가 쌓이면 이 지점부터 먼저 의심해야 하므로 "느려졌다"는 신고가 들어오면 가장 먼저 열어볼 계측을 지금 설계해두는 정도만 권장 |
| Async Processing | `TripHomeScheduler` 일 배치(`runForDate`)를 방 단위 비동기 병렬 처리로 전환 | Never | 지금은 만료 대상 방 목록을 순차 for문으로 처리한다. 방 개수가 아직 배치가 느려질 정도로 많지 않고, 순차 처리라 실패 시 원인 추적이 쉽다 — 비동기화하면 부분 실패·순서 보장 문제가 새로 생기는데 얻는 이득(배치 시간 단축)이 지금은 필요 없음 |
| Database | 추천 계산(`RecommendationEngine.generate`)용 읽기 전용 replica 분리 | Never | MVP 트래픽 규모에서 근거 없는 조기 최적화 — 지금 병목은 DB 읽기 처리량이 아니라(이미 N+1 없이 배치 쿼리로 조회) 위 Monitoring 항목의 CPU 바운드 계산 쪽에 있을 가능성이 높음 |
| Resilience | `GoogleCalendarService` 외부 API 호출(연동 사용자 busy 조회)에 서킷 브레이커·타임아웃 정책 명시 | Later | 트립 추천·달력 조회가 구글 캘린더 응답 지연에 얼마나 민감한지 이번 감사 범위(trip 패키지) 밖이라 `user.googlecalendar` 도메인 자체 감사에서 다루는 게 맞음 — 이번 trip 감사에서는 "고려는 필요하지만 지금 결정할 사안 아님"으로만 기록 |

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
