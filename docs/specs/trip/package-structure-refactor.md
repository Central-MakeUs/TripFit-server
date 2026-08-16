# 패키지 구조 리팩터 — trip 도메인 포트/어댑터 재설계

> 상태: Implemented (2026-08-06 구현, `#100` → PR #101로 2026-08-08 `main` merge 완료. `./gradlew build` 그린, `oasdiff breaking`/`diff` 0건, dev-login 기반 API 스모크 테스트(생성~참여~추천~피드백~확정) 실통과로 검증)
> 유형: 구조+의존성 리팩터 (API 응답·엔드포인트·DB 스키마 변경 없음. 단, **패키지·클래스 내부 의존 관계는 변경** — 순수 이동 리팩터 아님)
> 관련 BR: N/A
> 관련 결정: [`docs/decisions/003-architecture-guide.md`](../../decisions/003-architecture-guide.md) (확정 — "풀 DDD 미적용·단일 모듈"). 이 스펙이 채택한 설계는 003의 일부 문구와 다르다 — **구현 착수 전 003을 amend하는 후속 커밋이 필요** (사실 확인, 아래 "다음 절차" 참고).

## 배경 — 이 문서가 겪은 두 번의 재검토

1. **초안(2026-07-30):** `trip` 패키지 flat화 문제 제기, feature 서브패키지 분리안(대안 A) 제시.
2. **1차 재검토(2026-07-31):** 코드베이스 재조사로 스펙 수치가 하루 만에 stale해진 것 확인 + "폴더만 쪼개서는 안 풀리는 문제" 4가지 식별 + 003을 전제하지 않고 대안 A/B/C 비교.
3. **2차 재검토(이번, 2026-07-31):** 사용자가 "A는 근본 해결이 안 되니 제외, B·C 중 선택은 위임, 구현 비용은 무관, 우선순위는 (1) API 정상 동작 (2) 코드 이해 용이성 (3) 기술적 트렌드·메리트"라고 지시 → **아래 "결정"에서 B를 전체 도메인으로 확장한 설계를 채택**하고 C(애그리거트 강제)는 기각.

## 구조적 문제 (계속 유효 — 재확인 완료)

`trip` 패키지는 여전히 `dto/`(21) `domain/`(16) `service/`(14)가 레이어 폴더 하나에 flat하게 쌓여 있고, 아래 4가지는 폴더를 나누는 것만으로는 안 풀린다.

1. **Flat화가 구조적으로 재발한다** — 스펙 초안 후 하루 만에 `dto`가 13→21개로 늘었다. 유스케이스 축으로 경계를 정의하지 않으면 서브패키지도 다시 flat해진다.
2. **공유 커널(`TripServiceSupport`, 335줄)이 feature 경계를 가로지른다** — `TripCommandService`·`TripQueryService`·`TripMemberQueryService`·`TripRecommendationService`·`TripScheduleSnapshotService` 6개가 의존.
3. **레이어 이름이 클래스의 실제 성격과 안 맞는다** — `RecommendationEngine`(순수 계산), `RecommendationCandidate`/`MemberAttendanceDetail`(값 객체)이 `service/`에 섞여 있음.
4. **숨은 크로스 도메인 결합이 패키지 구조로는 안 보인다** — 아래 재조사로 범위를 전부 확인함(1차 재검토 때보다 더 넓음).

### §4 재조사 — 실제로 `trip → user`를 참조하는 지점 전부

```
trip/config/TripAuthorizationInterceptor  → user.service.UserSummaryService                                    (1개)
trip/service/TripJoinService              → user.service.UserSummaryService                                    (1개)
trip/service/TripCommandService           → user.service.{UserProfileService,UserSummaryService}               (2개)
trip/service/TripMemberQueryService       → user.googlecalendar.service.GoogleCalendarService,
                                             user.schedule.repository.{Personal,Regular}ScheduleRepository       (3개)
trip/service/TripScheduleSnapshotService  → user.googlecalendar.service.GoogleCalendarService,
                                             user.schedule.repository.{Personal,Regular}ScheduleRepository       (3개)
trip/service/RecommendationEngine         → user.googlecalendar.service.GoogleCalendarService,
                                             user.schedule.repository.{Personal,Regular}ScheduleRepository,
                                             user.schedule.service.ScheduleCalendarResolver                     (4개)
trip/service/TripServiceSupport           → user.service.UserLookupService, user.repository.UserRepository,
                                             user.schedule.repository.{Personal,Regular}ScheduleRepository,
                                             user.schedule.service.ScheduleCalendarResolver,
                                             user.googlecalendar.domain.GoogleCalendarBusyDay                    (6개+, 6개 서비스가 공유하는 커널)
```

(`Trip`/`TripMember`/`TripMemberScheduleSnapshot` 엔티티의 `User` 연관관계, `TripDisplayNameHelper`의 `User` 참조는 JPA FK 레벨이라 포트 대상 아님 — 2026-08-02 재확인 시점에 `TripDisplayNameHelper`는 `user.schedule.service.ScheduleService` 의존이 이미 제거돼 있어 변환 대상에서 빠짐.)

즉 §4는 `RecommendationEngine` 하나의 문제가 아니라 **`trip/service/`·`trip/config/`의 7개 클래스**가 `user.schedule`·`user.googlecalendar`·`user.service`를 직접 참조하는, 도메인 전체에 걸친 문제다. 1차 재검토가 "대안 B는 `RecommendationEngine`만 겨냥한다"고 좁게 잡았던 건 재조사 결과 **과소평가**였다 — 이번 결정에서 범위를 전체로 넓힌다.

**참고 (2026-08-02 재확인):** `TripServiceSupport`는 최근 `refactor-audit round2` 커밋에서 이미 배치 조회 메서드(`resolveMergedSchedules(List<UUID>, ...)`, `GoogleCalendarService.findBusyDaysByUserIds(...)`)를 갖추게 됐다 — 포트 인터페이스 설계 시 이 배치 시그니처를 그대로 채택하고, 단건 버전을 새로 포트에 노출하지 않는다.

**추가로 발견한 문제 (§5, 이번 재조사에서 새로 확인):** 트립이 발행하는 이벤트(`TripInfoChangedEvent`, `AllMembersSubmittedEvent`, `TripJoinCompletedEvent`, `TripConfirmedEvent`, `TripConfirmCanceledEvent`)의 **클래스 정의가 발행 주체가 아닌 `notification/event/`에 있다.** `trip/service/TripCommandService`·`TripRecommendationService`가 자신이 발행하는 이벤트 타입을 얻으려고 `notification.event.*`를 import하는, 의존 방향이 뒤집힌 상태 — 이벤트는 그걸 일으키는 도메인(`trip`)이 정의하고, 소비자(`notification`)가 그걸 import하는 게 맞다.

## 결정 — 대안 B를 전체 도메인으로 확장해 채택 (C는 기각)

### 왜 B인가 (C를 기각한 이유)

**우선순위 1 — "구현 완료 시 API가 정상 동작"이 최우선.** C(애그리거트 기반 전면 전환)를 실제로 하려면 `Trip`이 `TripMember`/`Recommendation`을 컬렉션으로 소유하도록 엔티티 관계 자체를 바꾸고(현재 `Trip`에는 `@OneToMany`가 없음 — 자식이 부모를 `@ManyToOne`으로만 참조), 지금 `TripMemberRepository`·`RecommendationRepository`를 **직접 주입**받아 쓰는 여러 서비스(`TripMemberQueryService`, `TripRecommendationService`, `TripJoinService` 등)의 접근 경로를 전부 애그리거트 경유로 바꿔야 한다. 이건 "의존성을 인터페이스로 감싸는" 수준이 아니라 **비즈니스 규칙이 어디서 강제되는지 자체를 재배치**하는 변경이라, 비용을 아무리 들여도(=시간을 아무리 써도) 회귀 위험이 구조적으로 크다 — 트랜잭션 경계·캐스케이드·지연 로딩 순서가 지금과 달라지기 때문에, 테스트를 아무리 촘촘히 짜도 놓치는 경로가 나올 가능성이 B보다 훨씬 높다. "비용은 상관없다"는 지시를 "시간을 더 쓴다"로는 해석해도, "정상 동작"이라는 결과 자체의 리스크를 낮추는 것과는 별개 문제라 판단했다.

**우선순위 2 — 이해 용이성.** B(포트/어댑터, 애그리거트 강제 없음)는 기존 Spring 관례(`controller/service/domain/repository`)를 그대로 유지하면서 `port/`만 추가한다 — Spring 개발자에게 익숙한 어휘 그대로 "이 서비스가 뭘 갖다 쓰는지"만 인터페이스로 명시하는 것이라 학습 곡선이 낮다. C는 `application/adapter/web/persistensce` 같은 새 어휘 + 애그리거트 불변식 개념까지 들여와야 해서, 오히려 "왜 `TripMember`를 직접 못 불러오고 `Trip`을 거쳐야 하지?"라는 의문을 도메인 곳곳에서 유발할 수 있다.

**우선순위 3 — 기술적 트렌드·메리트.** 둘 다 유효하지만, B도 충분히 "트렌디하다" — Ports & Adapters(헥사고날)의 핵심 아이디어(의존 역전으로 도메인 경계를 인터페이스로 명시)를 그대로 적용하면서, 애그리거트 강제라는 가장 논쟁적이고 비용 대비 효과가 불확실한 부분만 뺀 형태다. 실무에서도 "풀 DDD 없는 헥사고날"은 흔히 쓰이는 절충 형태다.

**결론: B를 `RecommendationEngine` 한 곳이 아니라 §4 재조사에서 확인한 `trip/service/` 전체로 확장 적용한다.** 애그리거트·ID-only 참조·모듈 분리는 하지 않는다.

### 채택한 설계

```
trip/
├── config/                    TripActivity, TripActivityAspect, TripWebConfig,
│                               TripMemberOnly, TripMembershipOnly, TripOwnerOnly,
│                               TripAuthorizationInterceptor                    # port.out.UserDirectoryPort만 의존하도록 변경
├── domain/                    Trip, TripStatus                                # 공용 — room 자체. User 연관관계(@ManyToOne)는 유지 (엔티티 레벨 FK는 포트 대상 아님)
├── controller/                TripController
├── dto/                       CreateTripRequest/Response, PatchTripRequest, TripDetailResponse,
│                               TripHomeCardResponse, TripListQuery/Response/Scope, UpdateTripPinRequest
├── service/                   TripCommandService, TripQueryService, TripService,
│                               TripServiceSupport, TripDisplayNameHelper, TripHomeMaintenanceService
├── repository/                TripRepository
├── scheduler/                 (그대로 유지)
├── exception/                 (그대로 유지 — 공용 TripErrorCode 등)
│
├── event/                     # ← notification/event/에서 이관 (발행 주체가 소유)
│                               TripInfoChangedEvent, AllMembersSubmittedEvent, TripJoinCompletedEvent,
│                               TripConfirmedEvent, TripConfirmCanceledEvent
│
├── port/
│   └── out/                   # trip이 다른 도메인에게 "이것만 달라"고 정의하는 인터페이스
│       ├── SchedulePort.java          # 개인/정기 일정 조회, 캘린더 병합 — user.schedule 대체
│       ├── GoogleCalendarPort.java    # 바쁜 날짜 조회 — user.googlecalendar 대체
│       └── UserDirectoryPort.java     # 프로필·요약 조회 — user 루트 서비스 대체
│
├── membership/                 # 참여·멤버 관리
│   ├── domain/ TripMember, TripMemberRole, TripMemberStatus
│   ├── controller/ TripMemberController
│   ├── dto/ JoinTripRequest, MemberPreviewResponse, TripMembersResponse
│   ├── service/ TripJoinService, TripMemberQueryService, InviteCodeGenerator
│   └── repository/ TripMemberRepository
│
├── recommendation/             # 추천 + 피드백
│   ├── domain/ Recommendation, RecommendationMode, RecommendationFeedback,
│   │           RecommendationFeedbackReason, RecommendationFeedbackStatus, AttendanceType, UnconfirmReason
│   ├── controller/ RecommendationController
│   ├── dto/ GenerateRecommendationsRequest, MemberAttendanceResponse, RecommendationDetailResponse,
│   │        RecommendationFeedbackResponse, RecommendationItemResponse, RecommendationListResponse,
│   │        SaveRecommendationFeedbackRequest, ConfirmTripRequest, UnconfirmTripRequest
│   ├── service/ TripRecommendationService
│   ├── algorithm/ RecommendationEngine, RecommendationCandidate, MemberAttendanceDetail   # §3 수정 — "service"가 아닌 순수 계산은 별도 폴더
│   └── repository/ RecommendationRepository, RecommendationFeedbackRepository
│
└── schedule/                   # 여행방 내 스케줄 합산/스냅샷
    ├── domain/ ScheduleStatus, SlotStatuses, TimeSlot, TripMemberScheduleSnapshot
    ├── dto/ MemberScheduleCalendarResponse
    ├── service/ TripScheduleSnapshotService
    └── repository/ TripMemberScheduleSnapshotRepository, projection/

user/schedule/service/
└── ScheduleAvailabilityAdapter implements trip.port.out.SchedulePort
    # 내부에서 PersonalScheduleRepository, RegularScheduleRepository, ScheduleCalendarResolver, ScheduleService 사용

user/googlecalendar/service/
└── GoogleCalendarPortAdapter implements trip.port.out.GoogleCalendarPort
    # 내부에서 기존 GoogleCalendarService 사용

user/service/
└── UserDirectoryAdapter implements trip.port.out.UserDirectoryPort
    # 내부에서 UserProfileService, UserSummaryService, UserLookupService, UserRepository 사용

notification/service/NotificationEventListener → trip.event.* import로 변경 (역방향 수정)
```

**배치 규칙 (§2·§3 대응, 구현 전 확정):**

- `TripServiceSupport`는 membership/recommendation/schedule이 공유하는 커널이라 루트 `trip/service/`에 의도적으로 남긴다. 계속 커지면 책임별 분리는 별도 스펙.
- `RecommendationEngine`/`RecommendationCandidate`/`MemberAttendanceDetail`은 `recommendation/algorithm/`으로 이동 — 이제 "service"가 아니라 "port로 주입받는 순수 계산"이라는 성격이 폴더명에 드러난다.
- `UpdateTripPinRequest`는 room 자체 속성이라 루트 `dto/`에 유지.
- 포트는 `trip` 루트에 둔다(멤버십·추천·스케줄 서브패키지 전부가 공유하므로 — `TripServiceSupport`와 같은 이유).

**풀리는 문제:** §1(feature 서브패키지로 flat 해결), §3(algorithm/ 폴더로 완화), §4(포트로 전체 도메인 해결), §5(이벤트 소유권 정상화). §2(공유 커널)는 여전히 규칙으로만 완화.

## 범위

### Must Have

- [x] `trip` 도메인을 `membership`/`recommendation`/`schedule` 기능 서브패키지 + 공용 루트로 분리
- [x] `trip/port/out/{SchedulePort,GoogleCalendarPort,UserDirectoryPort}` 인터페이스 정의
- [x] `user/schedule`, `user/googlecalendar`, `user` 각각에 어댑터 구현체 추가, 기존 `trip → user.*` 직접 import 전부 제거 (예외 2건 — 완료 기준 참고)
- [x] `TripInfoChangedEvent` 등 5개 이벤트 클래스를 `notification/event/` → `trip/event/`로 이관, `notification`이 `trip.event.*`를 import하도록 변경
- [x] `RecommendationEngine`/`RecommendationCandidate`/`MemberAttendanceDetail`을 `recommendation/algorithm/`으로 이동
- [x] 이동·변경된 모든 클래스의 import 경로 갱신
- [x] `./gradlew build`, `./gradlew test` 그린 유지
- [x] **완료 후 API 스모크 테스트** — 여행방 생성 → activate → 참여 → 멤버 일정 달력 → 추천 생성 → 상세 → 피드백 → 확정 → 확정 후 스냅샷 조회까지 `dev-login`으로 실제 호출, 전부 정상 응답 확인 (§"완료 기준" 참고)
- [x] Swagger(`@Schema`, controller `@Tag` 등) 패키지 스캔 영향 없음 확인 (`./gradlew test` 내 `OpenApiSpecExportTest`·`oasdiff` 통과로 검증)

### Nice to Have

- [ ] `auth`/`notification` 도메인 서브패키지 분리 — 우선순위 낮음(현재 파일 수 적음), 이번 PR과 분리
- [ ] ArchUnit 등으로 `trip` 패키지의 `user.*` 직접 import 금지(포트 제외)를 CI에서 강제 — 이번 스펙 Must는 아니지만 devDependency 추가에 사용자 동의 시 같이 진행 가능

### Out of Scope

- API 응답 필드·엔드포인트 경로 변경
- Entity/컬럼 변경 (ERD 불변 — `Trip`에 `@OneToMany` 컬렉션 추가하는 애그리거트화는 하지 않음)
- 애그리거트 불변식 강제, ID-only 참조, Gradle 멀티모듈 분리 (C의 기각된 부분)
- 클래스명 변경 (포트·어댑터 신규 클래스 제외 — 기존 클래스는 경로만 이동)

## 다른 도메인 (Nice — 우선순위 낮음, 2026-07-31 재확인)

| 도메인 | 현재 상태 | 제안 |
|--------|-----------|------|
| `auth` | `jwt/`(8)·`oauth/`(13)·`security/`(3)·`dev/`로 이미 기술축 분리돼 있음. `dto/`(7)·`service/`(4)·`domain/`(2)·`repository/`(1)·`controller/`(1)만 flat이지만 각 폴더가 여전히 작음 | 그대로 둬도 무방 |
| `notification` | `dto/`(2), `domain/`(5), `event/`(1 — 5개는 trip으로 이관 후), `service/`(4), `controller/`(2) | 리팩터 불필요 |
| `user` (schedule/googlecalendar 제외 루트) | `controller/`(1), `domain/`(2), `dto/`(3), `exception/`(1), `repository/`(1), `service/`(4+어댑터 1) | 어댑터 추가 외 리팩터 불필요 |

## 구현 가이드

1. **GitHub 이슈 생성 + 브랜치 분리** — `refactor/{issue-number}-trip-port-adapter`. 이 리팩터는 기능 브랜치와 섞지 않는다.
2. **decision 003 amend** — "포트(`port/out`)를 도메인 루트에 둘 수 있다", "이벤트는 발행 도메인이 소유한다" 두 항목을 003에 추가(또는 003을 대체하는 004... 문서로 별도 기록 — 기존 번호 체계상 `docs/decisions/`에 다음 번호로).
3. **순서 — 위험이 작은 것부터:**
   1. 이벤트 클래스 이관 (`notification/event/Trip*.java` → `trip/event/`) — 가장 기계적. `./gradlew build/test`
   2. `recommendation/algorithm/` 폴더 생성 + `RecommendationEngine`/`RecommendationCandidate`/`MemberAttendanceDetail` 이동 (아직 `user.*` 참조는 유지한 채 폴더만) — `./gradlew build/test`
   3. 포트 인터페이스 3개(`SchedulePort`, `GoogleCalendarPort`, `UserDirectoryPort`) 정의 — 7개 소비 클래스가 실제 호출하는 메서드를 추려 최소 시그니처로 설계, `TripServiceSupport.resolveMergedSchedules(List<UUID>,...)`/`GoogleCalendarService.findBusyDaysByUserIds(...)` 등 **이미 있는 배치 시그니처를 그대로 채택**(단건 버전은 포트에 새로 노출하지 않음). 이 시점엔 아직 아무도 구현하지 않아 컴파일만 됨
   4. 어댑터 구현체 3개 작성 (`user/schedule`, `user/googlecalendar`, `user` 각각) — 기존 서비스를 감싸기만 하므로 로직 변경 없음
   5. 소비 클래스 7개를 포트 의존으로 **복잡도 낮은 것 → 높은 것 순**으로 하나씩 전환, 클래스 하나 바꿀 때마다 `./gradlew test`:
      `TripAuthorizationInterceptor`(1개 의존) → `TripJoinService`(1개) → `TripCommandService`(2개) → `TripMemberQueryService`(3개) → `TripScheduleSnapshotService`(3개) → `RecommendationEngine`(4개) → `TripServiceSupport`(6개+, 가장 많이 공유되는 커널이라 마지막 — 패턴이 낮은 리스크 클래스에서 먼저 검증된 뒤에 가장 파급 범위가 큰 클래스를 바꾸는 순서)
   6. 모든 `trip → user.*` 직접 import가 제거됐는지 `grep -rn "import com.tripfit.tripfit.user\." src/main/java/com/tripfit/tripfit/trip` 로 확인 (엔티티 `User` 연관관계 import는 예외 — Out of Scope)
   7. `membership/` → `recommendation/` → `schedule/` 순서로 feature 서브패키지 이동 (파일 `git mv` + import 갱신)
4. **각 단계 후 확인:** `auth/config/OpenApiConfig.java`(2026-08-05 cross-cutting 리팩토링에서 `common/config/`에서 이동됨 — `WebConfig.java`는 죽은 CORS 코드로 판명돼 삭제됨, `docs/audits/cross-cutting/refactor-log.md` 참고) base-package 스캔 영향 없는지, Swagger `@Tag` 정상 노출되는지, 테스트 코드도 동일 구조로 미러링
5. **완료 후 API 스모크 테스트** — `dev` 프로필 `POST /api/v1/auth/dev-login`으로 토큰 발급 → 여행방 생성 → 멤버 참여 → 추천 생성(`GenerateRecommendationsRequest`) → 추천 상세·피드백 → 확정/확정취소까지 실제 HTTP 호출로 왕복 확인 (Swagger 또는 curl)
6. **커밋 단위** — 단계별로 나눠 커밋 (이벤트 이관 / algorithm 폴더 / 포트+어댑터 / 서비스 전환 / feature 서브패키지, 5묶음 권장)
7. **완료 후** — `docs/architecture.md` Package Layout을 새 구조로 갱신, `docs/decisions/003-architecture-guide.md`에 포트·이벤트 소유권 규칙 반영, 이 스펙 상태를 `Implemented`로 갱신

## 다음 절차 (완료 — 구현 착수 전 필요했던 절차)

- [x] GitHub 이슈 생성 — [`#100`](https://github.com/Central-MakeUs/TripFit-server/issues/100)
- [x] `docs/decisions/003-architecture-guide.md` amend (결정 11·12: 포트 위치, 이벤트 소유권 규칙 추가)
- [x] 브랜치 분기(`refactor/100-trip-port-adapter`) 후 위 "구현 가이드" 순서대로 구현 완료

**남은 절차 (사용자 확인 필요):** 커밋 분할·`gh pr create`는 아직 진행하지 않음 — `.claude/rules/harness-workflow.md` "새 PR 생성은 항상 먼저 확인" 절.

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `SchedulePort`/`GoogleCalendarPort`/`UserDirectoryPort` 3개로 나눈 경계가 적절한지 | 결정됨 | 실제 구현 결과: `SchedulePort`(2메서드) / `GoogleCalendarPort`(1메서드) / `UserDirectoryPort`(5메서드). `UserDirectoryPort`가 프로필·요약·조회를 한 인터페이스에 담고 있어 다소 넓지만, 실제 사용 패턴상 분리 이득이 크지 않아 유지 |
| `TripMemberStatus`를 `membership/`으로 옮길지 | 결정됨 | `membership/domain/`으로 이동 완료. room 상태(`TripStatus`)와는 별도 enum이라 참조 관계 문제 없었음 |
| `TripServiceSupport`를 루트에 남기는 것 | 결정됨 | 공유 커널 — §2 배치 규칙대로 `trip/service/`에 유지. `resolveMergedSchedules`(옛 메서드)는 `SchedulePort`로 완전히 대체되어 삭제(레거시 제거) |
| ArchUnit 등 CI 강제 도구 도입 여부 | [미정] | Nice — 이번 리팩터에 포함하지 않음. 별도 요청 시 진행 |
| decision 003 amend 문구 | 결정됨 | 003 결정 11(포트)·12(이벤트 소유권) + 도메인 구조 트리·패키지 배치 표 갱신 완료 |

## 완료 기준

- [x] `./gradlew test` 통과, `./gradlew build` 성공
- [x] `trip` 패키지 내 어떤 레이어 폴더도 파일 10개 이상 flat하게 있지 않음 (membership/recommendation/schedule로 분리 완료)
- [x] `grep -rn "import com.tripfit.tripfit.user\." src/main/java/com/tripfit/tripfit/trip`에서 `User` 엔티티 연관관계 import를 제외하고 결과 없음 — **예외 2건, 의도적:**
  - `TripServiceSupport`의 `import com.tripfit.tripfit.user.exception.UserErrorCode;` — `requireActive()`가 던지는 `UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED`는 API 응답의 `code` 필드 값 자체라, 포트로 감싸거나 다른 값으로 바꾸면 계약이 바뀐다(이번 리팩터가 절대 하면 안 되는 일). 서비스 호출이 아닌 에러 코드 상수 참조라 포트 대상으로 보지 않음
  - `RecommendationEngine.vacationDaysForSpan`의 `ScheduleCalendarResolver.combineImpossibleWins(...)` 직접 호출 — 인자로 받은 `List<RegularSchedule>`만 다루는 순수·정적 함수(부작용·상태 없음)라 포트가 감싸야 할 "서비스 협력자"가 아니라고 판단. 포트로 감싸면 오히려 이 메서드 하나만을 위한 부자연스러운 포트 메서드가 생김
- [x] `notification`이 `trip.event.*`를 import, 역방향(`trip`이 `notification.event.*` import) 없음
- [x] API 응답/엔드포인트 diff 없음 — `oasdiff breaking`/`diff` 매 단계 0건 확인(이벤트 이관 후, algorithm 이동 후, 포트+어댑터 후, 7개 클래스 전환 후, feature 서브패키지 이동 후 총 5회 체크포인트)
- [x] **API 스모크 테스트 통과** — 여행방 생성 → activate → 참여 → 멤버 일정 달력 조회 → 추천 생성 → 추천 상세 → 피드백 → 확정 → 확정 후 스냅샷 기반 달력(readOnly=true) 재조회까지 로컬(`local` 프로필)에서 `dev-login` 발급 토큰으로 실제 HTTP 호출, 전부 정상 응답 확인
- [x] 구 패키지 경로(`notification/event/Trip*.java`, `trip/service/RecommendationEngine.java` 등)에 잔존 파일 없음 (`git status` 확인 — `git mv`로 이동해 히스토리 보존)
- [x] `docs/architecture.md` Package Layout이 새 구조와 일치, `docs/decisions/003-architecture-guide.md`에 포트/이벤트 소유권 규칙(결정 11·12) 반영

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-30 | 초안 — 다음 세션에서 구현 착수 예정, 이번 세션에서는 구현하지 않음 |
| 2026-07-31 | 현재 코드베이스로 재측정, 구조적 문제 4가지 비판 추가, 대안 1(원안 보강)·대안 2(포트 추출) 두 갈래로 재구성, DDD/헥사고날 전환 검토 추가(decision 003과 충돌 명시, 지금은 비채택 권장) |
| 2026-07-31 (1차 재검토) | decision 003의 제약을 전제하지 않고 대안 A/B/C 3갈래로 재비교. C를 실제 마이그레이션 형태·이 코드베이스 고유 제약까지 근거로 재분석 |
| 2026-07-31 (2차 재검토 — 최종 결정) | 사용자가 A 제외, B/C 중 선택 위임, 우선순위(API 정상 동작 > 이해 용이성 > 기술 트렌드) 지정 → **B를 `RecommendationEngine` 하나가 아니라 §4 재조사로 확인한 `trip/service/` 7개 클래스 전체로 확장 적용, C(애그리거트 강제)는 정식 기각.** 이벤트 소유권 역전(§5) 신규 발견·이관 계획 추가. 구체적 포트 3개(Schedule/GoogleCalendar/UserDirectory) 설계, 단계별 구현 가이드·API 스모크 테스트 기준 확정. 구현은 아직 하지 않음 — GitHub 이슈·decision 003 amend 선행 필요 |
| 2026-08-06 (구현 완료) | `#100` 이슈 생성, `refactor/100-trip-port-adapter` 브랜치에서 구현 완료. decision 003 amend(결정 11·12) → 이벤트 이관 → algorithm 폴더 이동 → 포트 3개 정의 → 어댑터 3개 구현 → 7개 소비 클래스 전환(복잡도 낮은 순, `TripServiceSupport` 마지막) → feature 서브패키지 이동(membership/recommendation/schedule) 순으로 5단계 모두 완료, 매 단계 `./gradlew build/test` 그린 + `oasdiff` 0건 확인. 이동 중 발견한 실제 이슈: (1) `TripMember` 접두사가 `TripMemberScheduleSnapshot`을 오염시키는 sed prefix-collision 버그 수정, (2) 패키지 이동으로 package-private이던 클래스·메서드 다수가 cross-package 접근이 필요해져 `public`으로 승격(예: `TripServiceSupport`의 검증·조회 메서드 대부분), (3) `TripServiceSupport.resolveMergedSchedules`는 `SchedulePort` 어댑터로 완전히 대체되어 삭제(레거시 제거). 완료 후 로컬에서 `dev-login` 기반 API 스모크 테스트(생성~activate~참여~달력~추천~피드백~확정~확정후재조회) 9개 엔드포인트 전부 정상 응답 확인. `docs/architecture.md` Package Layout 갱신. 커밋·PR은 아직 진행하지 않음 |
