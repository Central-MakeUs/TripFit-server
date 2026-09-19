# Trip Architecture Audit — Round 3 (2026-08-26, SOLID/OOP 중심)

> **선행 문서 안내**: `docs/audits/trip/audit.md`(1차)와 `audit-round2.md`(2차, 2026-08-05)가 이미 존재하며, 두 라운드가 찾은 항목은 사용자 승인 후 구현·검증까지 끝났다(`refactor-log.md`). `auth`/`user`/`user-schedule` 3차 감사(`docs/audits/{auth,user,user-schedule}/audit-round3.md`)와 동일하게, 이번 3차는 **새로 요청받은 SOLID/OOP 관점**으로 `trip` 도메인 전체(main 70개 파일)를 다시 전수 검토한 결과다. 1·2차가 이미 다룬 항목(N+1 배치 조회, 방장 검증 헬퍼 통합, `TripQueryService.toDetail` 패스스루 제거, 추천 삭제 책임 이관, `SlotStatuses.get(TimeSlot)/set(TimeSlot,...)` 삭제, 초대코드 alphabet 명칭, `deleteTrip` cascade 개별 UPDATE, `requireValidFeedback`/`requireValidUnconfirmReason` 중복, `TripServiceSupport` 다책임, `RecommendationFeedback` FK 미설정 등)는 재검토만 하고 새 판단이 없으면 반복 서술하지 않는다.
>
> **1·2차 이후 가장 크게 바뀐 지점 — `trip/port/out` 포트/어댑터 폐기(2026-08-26, 커밋 `825fb00`).** `docs/audits/trip/audit.md`는 이 폐기 자체를 다룬 감사 문서였고(그 문서의 B-1이 포트 제거 제안), `refactor-log.md`에 따르면 이미 전부 반영됐다 — `trip/port/out` 패키지 자체가 삭제됐고(현재 코드에 `port` 패키지 없음, 직접 확인), `trip` 쪽 5개 파일(`RecommendationEngine`·`TripScheduleSnapshotService`·`TripMemberQueryService`·`TripServiceSupport`·`TripCommandService`)은 옛 `SchedulePort`/`GoogleCalendarPort`/`UserDirectoryPort` 대신 concrete 클래스 `user.schedule.service.ScheduleAvailabilityService`·`user.service.UserDirectoryService`를 직접 주입받는다 — 이번 3차 전수 읽기에서 그대로 확인했다.
>
> **2차 B-2 판단은 패키지 구조 변화로 전제가 바뀌었다(재검토 결과 갱신).** 2차는 `TripScheduleSnapshotService`가 "패키지 내부에서만 쓰이니 `public` → package-private로 낮추자"고 제안했다. 그러나 2차 이후 `membership`/`recommendation`/`schedule` feature 서브패키지 분리가 진행되면서, 지금은 `TripRecommendationService`(`trip.recommendation.service`)와 `TripHomeMaintenanceService`(`trip.service`)가 `TripScheduleSnapshotService`(`trip.schedule.service`)를 **서로 다른 패키지에서** 호출한다(직접 확인 — `grep` 결과 두 호출부 모두 다른 패키지). 즉 2차가 전제했던 "같은 패키지 내부 전용" 상황 자체가 사라져, package-private로 낮추면 오히려 컴파일이 깨진다 — 2차 B-2는 **더 이상 유효하지 않다(반영 여부와 무관하게 지금은 적용 불가)**. 별도 조치 불필요, 기록만 갱신.
>
> 이번 세션에서 이미 저장소 전역에 반영된 두 가지 공통 변경 — 모든 Service `@RequiredArgsConstructor` 사용, 모든 Entity **클래스 레벨** `@Setter` 제거(도메인 메서드로 상태 전이) — 은 전제로 두고 재발견하지 않았다. 확인 결과 `trip` 도메인의 모든 `@Service` 클래스(`TripService`·`TripCommandService`·`TripQueryService`·`TripJoinService`·`TripMemberQueryService`·`TripRecommendationService`·`TripHomeMaintenanceService`)는 이미 `@RequiredArgsConstructor`를 쓰고 있었고, `Trip`·`TripMember`·`Recommendation`·`RecommendationFeedback`·`TripMemberScheduleSnapshot` 모두 클래스 레벨 `@Setter` 없이 도메인 메서드(`touchLastActivity`·`applyPatch`·`confirm`·`unconfirm`·`activate`·`applyPin`·`applyFeedback` 등)로만 상태를 바꾼다. `Trip`/`TripMember`의 `id` 필드에 남은 **필드 레벨** `@Setter`는 `user`/`user-schedule` 3차 감사가 이미 "`@GeneratedValue` PK를 테스트 픽스처에서 세팅하기 위한 저장소 전역 공통 패턴"으로 범위 밖 처리한 것과 동일해 이번에도 다루지 않는다.

## 범위

- 패키지: `com.tripfit.tripfit.trip` 전체 — `config`, `controller`, `domain`, `dto`, `event`, `exception`, `membership/*`, `recommendation/*`, `repository`, `schedule/*`, `scheduler`, `service` (main 70개 파일 전수 재검토)
- 테스트: `src/test/java/com/tripfit/tripfit/trip/**` 20개 파일 참고용 확인(사용처 검증 — grep 기반 dead code 확인에 활용)
- 교차 확인: `user.schedule.service.ScheduleAvailabilityService`(trip이 유일하게 직접 주입받는 크로스 도메인 서비스), `user.schedule.domain.RegularSchedule`/`PersonalSchedule`(trip 소유 `SlotStatuses` 임베더블을 재사용), `user.domain.User`의 연차 정책 getter(`RecommendationEngine`이 직접 읽는 지점), `docs/decisions/003-architecture-guide.md` 결정 11(포트 폐기 확정 문구)
- 감사자: 현재 세션(신선한 컨텍스트, 이번 대화에서 `trip` 도메인 코드를 수정한 적 없음), 읽기 전용
- 기준: `audit-checklist.md` 1~15항목 + 사용자 지정 우선 렌즈(SRP·OCP·LSP·ISP·DIP·캡슐화·God class/method·feature envy·inappropriate intimacy), `harness-workflow.md` ⛔ STOP

## ✅ A. 반드시 수정해야 하는 사항

이번 라운드에서 A 항목 없음 — Critical/High급 구조적 결함(버그·성능 회귀·보안 문제·명백한 SOLID 위반)을 찾지 못했다. 포트/어댑터 폐기 이후 `trip`이 `user` 도메인 concrete 서비스 2개(`ScheduleAvailabilityService`·`UserDirectoryService`)만 명확한 경계로 주입받는 구조, `TripService`/`TripCommandService`/`TripQueryService`/`TripServiceSupport` 간 책임 분리, `RecommendationEngine`의 알고리즘 응집도, AOP(`@TripActivity`)·Interceptor(`@TripMemberOnly`/`@TripOwnerOnly`/`@TripMembershipOnly`) 구조 모두 재검토 결과 건전하게 유지되고 있음을 확인했다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `SlotStatuses` — 개별 필드 setter 3개가 임베더블 값 타입의 캡슐화를 우회 가능하게 열어둔 채 아무 데서도 호출되지 않음

- **Priority**: Low
- **Category**: Dead Code / Architecture (캡슐화)
- **문제**: `trip/schedule/domain/SlotStatuses.java:66-76`의 `setMorningStatus`/`setAfternoonStatus`/`setEveningStatus` 3개 public setter는 `grep -rn "setMorningStatus\|setAfternoonStatus\|setEveningStatus" src/` 결과 선언부(`SlotStatuses.java` 자신) 외 호출부가 0건이다(`main`·`test` 전체). 이 클래스를 쓰는 3개 엔티티(`trip.schedule.domain.TripMemberScheduleSnapshot`, `user.schedule.domain.RegularSchedule`, `user.schedule.domain.PersonalSchedule`) 전부 상태를 바꿀 때 `new SlotStatuses(morning, afternoon, evening)` 또는 `SlotStatuses.fromTimeRange(...)`로 **객체 전체를 새로 만들어 필드째 교체**한다(`RegularSchedule.java:83,96`, `PersonalSchedule.java:76,86`, `TripMemberScheduleSnapshot.java:84`) — 개별 setter로 한 슬롯만 바꾸는 경로는 어디에도 없다.
- **왜 문제인가**: `SlotStatuses`는 3개 슬롯이 항상 함께 재계산되는 값 타입(오전/오후/저녁을 시간 범위 하나에서 동시에 도출)인데, 개별 setter가 열려 있으면 "슬롯 하나만 따로 바꿔도 되는 것처럼" 보여 향후 개발자가 이 setter로 부분 수정을 시도할 위험이 생긴다. 실제로 그렇게 하면 나머지 두 슬롯과의 정합성(같은 `fromTimeRange` 호출에서 나온 값이라는 전제)이 깨질 수 있다. 이는 auth 3차 감사 A-1(`RefreshToken.setRevokedAt` — 범용 setter가 "폐기 시각은 항상 now()"라는 불변식을 호출부 관례에만 의존하게 함)과 같은 패턴의 캡슐화 결함이며, 2차 감사 B-1이 이미 `SlotStatuses.get(TimeSlot)/set(TimeSlot,...)`(파라미터화 접근자)을 dead code로 제거했을 때 놓친, 같은 클래스의 또 다른 미사용 표면이다.
- **개선 방법**: `setMorningStatus`/`setAfternoonStatus`/`setEveningStatus` 3개 메서드를 삭제한다. 개별 getter(`getMorningStatus()` 등)와 생성자·`empty()`·`fromTimeRange()` 정적 팩터리는 그대로 유지 — 이 조합만으로 기존 3개 호출부(엔티티 3종)가 이미 상태를 전부 교체하고 있어 동작 변화가 없다.
- **API 영향**: No Impact — `SlotStatuses`는 API DTO가 아닌 내부 `@Embeddable`이고, 삭제 대상 메서드는 어떤 Controller·DTO 변환에도 관여하지 않는다.
- **예상 변경 파일**: `trip/schedule/domain/SlotStatuses.java`
- **예상 변경 라인 수**: 11줄 삭제
- **위험도**: Low — 호출부 자체가 없어 컴파일 영향 없음(Hibernate가 `@Embeddable` 필드 값을 읽는 데는 getter만 있으면 충분 — JPA는 생성자·필드 리플렉션으로도 값을 채울 수 있고, 이 클래스는 이미 protected no-arg 생성자를 갖고 있어 setter 제거가 영속화 동작에 영향을 주지 않음).
- **테스트 영향도**: 없음 — 해당 setter를 검증하는 테스트가 없어 삭제해도 회귀 없음.
- **예상 효과**: 값 타입의 "전체 교체만 허용"이라는 실제 사용 패턴이 코드(공개 API 표면)로도 명확히 드러나고, 불필요한 부분 수정 경로가 컴파일 타임에 원천 차단된다.

### B-2. `TripMemberRepository.existsByTripIdAndUserIdAndDeletedAtIsNull` — 선언만 있고 어디서도 호출되지 않는 Dead Code

- **Priority**: Low
- **Category**: Dead Code
- **문제**: `trip/membership/repository/TripMemberRepository.java:19`의 `existsByTripIdAndUserIdAndDeletedAtIsNull(UUID tripId, UUID userId)`는 `grep -rn "existsByTripIdAndUserIdAndDeletedAtIsNull" src/` 결과 선언부 1건 외 호출부가 `main`·`test` 어디에도 없다. 같은 조건(멤버십 존재 확인)이 실제로 필요한 모든 지점(`TripServiceSupport.requireMembership`, `TripCommandService.joinTrip`의 기존 멤버 체크 등)은 이미 `findByTripIdAndUserIdAndDeletedAtIsNull(...)`(같은 파일 `:21`, `Optional` 반환)의 `.isPresent()`/`.orElseThrow()`로 처리하고 있어, 이 boolean 전용 메서드는 애초에 채택된 적이 없는 것으로 보인다.
- **왜 문제인가**: Spring Data JPA는 이 시그니처만 보고도 매 애플리케이션 기동 시 쿼리를 파생시켜 유효성을 검증하므로, 호출되지 않는 메서드도 컴파일·부팅 비용을 유발하고 리포지토리 인터페이스를 읽는 개발자에게 "언제 `exists`를 쓰고 언제 `find`를 쓰는지" 불필요한 판단을 요구한다.
- **개선 방법**: `existsByTripIdAndUserIdAndDeletedAtIsNull` 선언을 삭제한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `trip/membership/repository/TripMemberRepository.java`
- **예상 변경 라인 수**: 1줄 삭제
- **위험도**: Low — 호출부 없음, 컴파일 영향 없음.
- **테스트 영향도**: 없음.
- **예상 효과**: 리포지토리 표면이 실제 사용 경로(`find...().isPresent()`)만 남아 더 명확해짐.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **1·2차 audit.md/audit-round2.md의 C 항목 재검증 결과 — 대부분 여전히 유효, 변경 없음.** `RecommendationEngine.loadContext`와 (구 )`TripServiceSupport.resolveMergedSchedules`의 조회 패턴 유사성 논의는 포트 폐기로 두 메서드 모두 `ScheduleAvailabilityService`(`loadContext`→`resolveAvailability`+원본 조회 2건, 그 외 호출부→`resolveAvailability().mergedByUser()`)로 흡수돼 비교 대상 자체가 사라졌지만 결론(반환 형태가 달라 통합 안 함)은 그대로 승계됐다. `CreateTripRequest`/`PatchTripRequest` 필드 중복(계약이 달라 통합 안 함), `TripMemberQueryService.buildLive`의 `CalendarDayResponse`→`CalendarDay` 수동 필드 매핑 보일러플레이트(2차 C-2와 동일 패턴, 여전히 유효 — 두 레코드가 사실상 같은 필드라 정적 팩터리로 줄일 수 있지만 요청 범위 밖), `requireValidFeedback`/`requireValidUnconfirmReason`(1차 C, `TripRecommendationService.java:284-304`) 구조적 유사 중복(사유 검증 로직이 enum 타입만 다르고 나머지가 같지만, 제네릭화하면 도메인 enum 2종을 모두 아는 헬퍼가 되어 오히려 응집도 하락) — 모두 코드를 다시 읽어 확인했으며 판단이 그대로 유효하다.
- **`TripServiceSupport`가 DTO 매핑·N+1 배치 조회·메타 검증·초대코드 생성·권한 가드·User 위임 6가지 서로 다른 관심사를 한 클래스에 모아둠(SRP 관점) — 2차가 이미 "다책임"으로 짚었고 이번 SOLID 렌즈 재검토에서도 결론 불변.** `toHomeCard`/`toEntry`/`toDetail`(매핑), `loadMemberCountsByTripIds`/`loadMemberPreviewsByTripIds`(N+1 방지 배치 조회), `validateTripMeta`/`resolveDurationDays`(입력 검증), `generateUniqueInviteCode`(초대코드), `requireActiveTrip`/`requireMembership`/`requireActive`/`requireOwner`/`requireOngoingForMutation`(권한·상태 가드), `findUser`/`requireProfileNameComplete`(User 위임) — 6가지 축으로 쪼개면 이론적으로 SRP에는 더 맞지만, 이 6가지 전부가 `TripCommandService`·`TripQueryService`·`TripMemberQueryService`·`TripRecommendationService`·`TripAuthorizationInterceptor` 5곳에서 **여러 축을 함께** 필요로 한다(예: `activateMembership`은 가드+매핑, `patchTrip`은 가드+검증+매핑). 쪼개면 이 5개 호출부가 지금의 의존성 1개(`support`) 대신 최대 6개 헬퍼 빈을 주입받아야 해, 클래스 수는 늘고 호출부 생성자는 더 길어지는데 실제로 얻는 테스트 격리 이득은 낮다(이미 Mockito가 `support`를 하나의 mock으로 다루는 것으로 충분 — `TripServiceSupportTest`가 이 6가지 관심사를 이미 구분해서 테스트 중임을 확인). 새로운 근거 없이 재상정하지 않는다.
- **`SlotStatuses`가 `trip.schedule.domain` 패키지에 있지만 `user.schedule.domain.RegularSchedule`/`PersonalSchedule`이 직접 import해 재사용하는 크로스 도메인 값 타입 공유 — 분리하지 않음.** 얼핏 도메인 경계를 넘는 결합처럼 보이지만, 오전/오후/저녁 3슬롯이라는 개념 자체가 trip 희망 일정과 user 개인 일정 양쪽에서 완전히 동일한 계약(같은 `TimeSlot` 경계, 같은 `POSSIBLE`/`IMPOSSIBLE` 의미)이라 값 타입을 복제하면 두 정의가 갈라질 위험만 생긴다. `common` 패키지로 옮기는 방안도 검토했으나, 이 값 타입은 애초에 trip이 "희망 여행 기간 슬롯"이라는 자기 개념으로 만든 것이고 user가 그 계약을 그대로 빌려 쓰는 관계라(반대가 아님) 소유권을 옮기면 trip 쪽 참조가 어색해진다 — 현재 소유 방향이 더 자연스럽다는 판단을 유지, 새 이슈로 제기하지 않는다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`TripService`가 18개 메서드로 `TripCommandService`/`TripQueryService`/`TripMemberQueryService`/`TripRecommendationService` 4개를 위임하는 단일 파사드로 남아 있고, 3개 Controller(`TripController`·`TripMemberController`·`RecommendationController`)가 각자 필요한 서브셋만 쓰면서도 전부 이 하나의 파사드에 의존 — 컨트롤러별 파사드로 쪼개지 않는다.** ISP 원칙만 보면 "각 컨트롤러가 실제로 쓰지 않는 14개 메서드까지 있는 클래스에 의존한다"는 지적이 가능하지만, `TripService`의 모든 메서드는 1줄짜리 순수 위임(`return xxxService.xxx(...)`)이라 쪼개도 위임 코드의 총량은 그대로고, 대신 3개 컨트롤러가 각각 1개(`TripService`) 대신 2~4개(예: `TripMemberController`는 멤버 조회 담당 `TripMemberQueryService` + 내보내기/나가기를 갖고 있는 `TripCommandService`)의 서비스 빈을 직접 주입받게 된다. 컨트롤러의 생성자 의존성 개수만 늘고, 리팩터 범위(테스트 3개 파일의 mock 대상 전환 포함)에 비해 실질적으로 줄어드는 결합은 없어 보류.
- **`RecommendationEngine`(721줄)을 여러 클래스로 쪼개지 않는다 — 1차 D-1 판단 재확인, 여전히 유효.** 연차/반차 자동 전환 시뮬레이션(`collectVacationOptions`·`addShiftUnits`·`openSlots`·`applyVacationSimulation`)이 파일의 상당 부분을 차지하지만, `#105` 정책과 1:1로 얽힌 비트마스크 완전탐색 알고리즘이라 분리하면 미묘한 동작 차이를 낼 위험이 무손실 리팩토링 원칙과 충돌한다. 각 private 메서드가 이미 역할 주석으로 잘 구획돼 있어 파일 크기 자체가 가독성을 해치지 않는다.
- **`RecommendationEngine`이 `User.getMaxVacationDays()`/`isHalfVacationAvailable()`/`isHolidayRest()`/`RegularSchedule.getSlotStatuses()`를 직접 읽는 구조를 유지 — feature envy 아님.** `user-schedule` 3차 감사 D 섹션이 이미 같은 논리로 `RegularSchedule`/`PersonalSchedule.getSlotStatuses()` 직접 접근을 검토했다 — 이 엔티티·값들은 `ScheduleAvailabilityService.findRegularSchedulesByUserIds`라는 정식 배치 조회 경로로만 `RecommendationEngine`에 전달되고, 연차 시뮬레이션은 "이 슬롯이 정기 근무 때문에 막혔는지, 개별 일정·구글 때문에 막혔는지"를 원본 값으로 구분해야 해서(병합된 `CalendarDayResponse`는 이미 출처 정보를 잃음) 원시 접근이 실제로 필요하다. `User`의 연차 정책 getter 3개도 마찬가지로 `TripMember.getUser()`를 통해 정식 연관관계로 얻은 값이라 도메인 경계 위반이 아니다.
- **`TripAuthorizationInterceptor`가 `TripServiceSupport`(멤버십 검증)와 `TripRepository`(존재·방장 여부)를 둘 다 직접 주입받는 이중 접근 경로 유지 — 통합하지 않는다.** `TripServiceSupport.requireActiveTrip`을 쓰면 `Trip` 엔티티 전체를 로드하게 되는데, 인터셉터는 매 요청 hot path에서 존재·방장 여부만 boolean으로 확인하면 충분하다(`existsByIdAndDeletedAtIsNull`/`existsByIdAndOwner_IdAndDeletedAtIsNull`). Support로 통일하면 인터셉터가 필요 이상으로 무거워지므로, 지금처럼 "boolean 게이트는 Repository 직접, 멤버십 객체가 필요한 부분만 Support"로 나눈 구조가 더 낫다.

## 15. 백엔드 아키텍처 개선 제안

1·2차 §15의 제안들을 재확인한 결과 상태 변화 없음 — 새 SOLID/OOP 렌즈에서도 이 도메인에 새로 제안할 아키텍처 카테고리는 없다.

- **Monitoring — `RecommendationEngine.applyVacationSimulation` 실행 시간 계측**: 1차 판단(`MAX_CONVERSION_UNITS=20` 상한이 현재 방어선, 느려졌다는 신고·로그 근거 없음) 유지. **Later**.
- **Async — `TripHomeScheduler` 일 배치 병렬화**: 1차 판단(순차 처리로 실패 원인 추적이 쉬움, 방 개수가 아직 배치를 느리게 할 규모가 아님) 유지. **Never**.
- **Database — 추천 계산용 읽기 전용 replica**: 1차 판단(N+1 없이 배치 쿼리로 이미 조회 중, 병목은 DB가 아니라 CPU 바운드 계산일 가능성) 유지. **Never**.
- **API — `GET /trips` Cursor Pagination**: 2차 판단(현재 MVP 규모에서 사용자당 여행방 누적 수가 페이지네이션을 정당화할 만큼 크지 않음, 도입 시 계약 변경 비용) 유지. **Later**.
- **Security/API — `POST /trips` Idempotency Key**: 2차 판단(중복 생성 사례 미관측, 도입 비용 대비 지금 이득 불확실) 유지. **Later**.
- **Resilience — `GoogleCalendarService` 외부 호출 서킷 브레이커·타임아웃**: 1차 판단대로 이 항목은 `trip` 패키지 범위 밖이라 `user.googlecalendar` 도메인 자체 감사에서 다루는 것이 맞다 — `user` 3차 감사가 이미 이 항목을 확인했고(Resilience4j 의존성 없음, Later 유지) 결론이 같다. 새로 추가할 내용 없음.

## 승인 대기

사용자 승인 후 B-1(`SlotStatuses` 미사용 setter 3개 삭제)·B-2(`TripMemberRepository` 미사용 `exists` 메서드 삭제)만 우선순위 순으로 구현합니다(A 없음). C/D는 이번 라운드에서 수정하지 않습니다.
