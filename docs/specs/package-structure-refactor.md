# 패키지 구조 리팩터 — 도메인 내 기능 서브패키지 정리

> 상태: Draft
> 유형: 구조 리팩터 (API/DB 계약 변경 없음 — 표준 스펙 템플릿의 API/데이터모델 섹션 N/A)
> 관련 BR: N/A
> 관련 결정: [`docs/decisions/003-architecture-guide.md`](../decisions/003-architecture-guide.md) (**확정** — "풀 DDD는 적용하지 않음", "단일 모듈 모노리스"). **아래 대안 비교는 사용자 요청에 따라 003의 제약을 전제로 깔지 않고 순수 엔지니어링 트레이드오프로 재검토한 것** — 003과의 정합 여부·amend 필요성은 최종 채택 단계에서 별도로 판단한다 (각 대안 하단 "003과의 관계" 참고).

## 목표

`trip`, `auth`, `notification` 등 일부 도메인 패키지에 파일이 flat하게(`dto/`, `service/`, `domain/` 등 레이어 폴더 하나에 10개 이상) 쌓여 있어 코드를 찾는 데 시간이 걸린다. `user/schedule`, `user/googlecalendar`가 이미 쓰고 있는 `{domain}/{feature}/{layer}` 패턴을 다른 도메인에도 일관되게 적용해서 탐색성을 개선한다.

이번 개정에서는 여기서 한 걸음 더 나가, **"폴더 몇 개로 쪼갤까"보다 먼저 "왜 flat해졌는가"를 비판적으로 짚고**, 순수 이동 리팩터(대안 A)부터 포트/어댑터(대안 B), 애그리거트 기반 DDD/헥사고날 전면 전환(대안 C)까지 003의 제약을 전제하지 않고 순수 트레이드오프로 저울질한다.

## 배경

- 컨벤션 근거: `AGENTS.md` Conventions — `{domain}/controller|dto|service|domain|repository|client`, **필요 시** `{domain}/{feature}/…`
- 이미 이 패턴을 따르는 예시: `user/schedule/*`, `user/googlecalendar/*` (각각 6~9개 파일, feature 경계가 명확)
- 아직 안 따르는 곳: `trip/*`, `auth/*`, `notification/*`
- 순수 패키지 이동 + import 정리라면 API 계약·DB 스키마·비즈니스 로직 변경 **없음** (STOP §5 Breaking-Change-Reason 대상 아님). 단, 아래 대안 B·C는 이 전제를 깬다 — 채택 시 이 스펙의 "유형"·Out of Scope를 다시 써야 한다.

## 범위 (이번 리팩터가 다루는 것)

### Must Have

- [ ] `trip` 도메인을 기능 서브패키지로 분리 (대안 A 채택 시 아래 "대안 A" 참조)
- [ ] 이동한 클래스를 참조하는 모든 import 경로 갱신
- [ ] `./gradlew build`, `./gradlew test` 그린 유지
- [ ] Swagger(`@Schema`, controller `@Tag` 등) 패키지 스캔에 영향 없는지 확인 (`common/config/OpenApiConfig.java`, `WebConfig.java`의 base-package 설정 점검)

### Nice to Have

- [ ] `auth` 도메인 서브패키지 분리 (`oauth`/`jwt`/`dev`/`security`는 이미 기술축으로 분리돼 있어 우선순위 낮음 — 아래 참고)
- [ ] `notification` 도메인 서브패키지 분리 (dto 2개·domain 5개로 아직 flat이 심하지 않아 우선순위 낮음)

### Out of Scope (이번 리팩터에서 하지 않음 — 대안 A 기준)

- API 응답 필드·엔드포인트 경로 변경
- Entity/컬럼 변경 (ERD 불변)
- `common/`, `user/repository`, `user/dto` 등 이미 파일 수가 적은(3개 이하) 폴더 재편
- 클래스명 변경 (경로만 이동 — import만 바뀌고 참조 클래스명은 그대로 유지하는 것을 원칙으로 함. 클래스명까지 바꾸고 싶다면 이 스펙과 별도로 논의)
- **크로스 도메인 결합 제거** (`RecommendationEngine`이 `user.schedule`/`user.googlecalendar`를 직접 참조하는 것) — 대안 A는 이 문제를 그대로 옮기기만 한다. 손대려면 대안 B 참고, 별도 스펙 필요

## 현재 구조 (문제 지점) — 2026-07-31 재측정

> 스펙 초안(2026-07-30)이 그렸던 수치(dto 13·domain 11·service 11)는 **하루 만에 이미 stale**해졌다 — 추천 피드백(`RecommendationFeedback*`)·출석(`AttendanceType`, `MemberAttendanceResponse` 등)·확정취소(`ConfirmTripRequest`, `UnconfirmTripRequest`) 기능이 그 사이 추가됐다. 아래는 실제 코드 기준.

```
trip/
├── config/            TripActivity, TripActivityAspect, TripAuthorizationInterceptor,
│                       TripMemberOnly, TripMembershipOnly, TripOwnerOnly, TripWebConfig (7)
├── controller/         RecommendationController, TripController, TripMemberController (3)
├── domain/            AttendanceType, Recommendation, RecommendationFeedback,
│                       RecommendationFeedbackReason, RecommendationFeedbackStatus, RecommendationMode,
│                       ScheduleStatus, SlotStatuses, TimeSlot, Trip, TripMember, TripMemberRole,
│                       TripMemberScheduleSnapshot, TripMemberStatus, TripStatus, UnconfirmReason (16)
├── dto/               ConfirmTripRequest, CreateTripRequest/Response, GenerateRecommendationsRequest,
│                       JoinTripRequest, MemberAttendanceResponse, MemberPreviewResponse,
│                       MemberScheduleCalendarResponse, PatchTripRequest, RecommendationDetailResponse,
│                       RecommendationFeedbackResponse, RecommendationItemResponse, RecommendationListResponse,
│                       SaveRecommendationFeedbackRequest, TripDetailResponse, TripHomeCardResponse,
│                       TripListQuery/Response/Scope, TripMembersResponse, UnconfirmTripRequest,
│                       UpdateTripPinRequest (21)
├── exception/         (1)
├── repository/        RecommendationFeedbackRepository, RecommendationRepository, TripMemberRepository,
│                       TripMemberScheduleSnapshotRepository, TripRepository, projection/ (5+)
├── scheduler/          (1)
└── service/           InviteCodeGenerator, MemberAttendanceDetail, RecommendationCandidate,
                        RecommendationEngine, TripCommandService, TripDisplayNameHelper,
                        TripHomeMaintenanceService, TripJoinService, TripMemberQueryService,
                        TripQueryService, TripRecommendationService, TripScheduleSnapshotService,
                        TripService, TripServiceSupport (14)
```

레이어 폴더 하나에 서로 다른 기능(방 CRUD, 멤버 관리, 추천, 출석/확정, 스케줄 스냅샷, 홈 유지보수)이 섞여 있어서 "추천 관련 코드 다 보여줘" 같은 탐색이 `dto/`, `service/`, `domain/`, `repository/`를 각각 열어서 이름으로 골라내야 한다.

## 구조적 문제 — 폴더당 파일 개수만이 문제가 아님

폴더를 4개로 쪼개면 위 표는 좋아 보이지만, 그것만으로는 안 풀리는 문제가 최소 4가지 있다.

1. **Flat화는 구조적으로 재발한다.** 스펙 초안 작성 후 하루 만에 `dto`가 13→21개로 늘었다. "지금 파일 목록"을 기준으로 서브패키지 경계를 그으면, 새 기능이 추가될 때마다 그 경계 중 하나가 다시 flat해지거나(예: `recommendation/`이 피드백·출석까지 흡수하며 다시 커짐) 새 서브패키지가 필요해진다. 파일 개수가 아니라 **유스케이스 축(무엇을 변경/조회하는가)**을 경계 기준으로 문서에 명시해 둬야 재발을 늦출 수 있다.

2. **공유 커널(`TripServiceSupport`)이 feature 경계를 가로지른다.** `TripServiceSupport`(335줄)는 `TripCommandService`, `TripQueryService`, `TripMemberQueryService`, `TripRecommendationService`, `TripScheduleSnapshotService` 등 **6개** 서비스가 의존하는 공용 헬퍼다. 원래 제안한 "membership/recommendation/schedule 4분할"은 이 클래스를 어디 둘지 답이 없다 — `membership/`으로 옮기면 `recommendation/`·`schedule/`이 `membership/service/TripServiceSupport`를 역참조하게 되어, 정작 분리하려던 결합이 서브패키지 사이로 옮겨갈 뿐이다.

3. **레이어 이름이 클래스의 실제 성격과 안 맞는 경우가 이미 있다.** `RecommendationEngine`(순수 계산 로직), `RecommendationCandidate`/`MemberAttendanceDetail`(계산 결과를 담는 `record` 값 객체)이 전부 `trip/service/`에 있다. Repository도 Entity도 트랜잭션 유스케이스도 아닌데 "service"라는 이름 하나로 뭉뚱그려져 있다 — 폴더만 옮기면 `recommendation/service/RecommendationEngine`처럼 같은 혼동이 서브패키지마다 반복된다.

4. **숨은 도메인 간 결합은 패키지 구조로는 안 보인다.** `RecommendationEngine`이 `user.schedule.repository.{Personal,Regular}ScheduleRepository`와 `user.googlecalendar.service.GoogleCalendarService`를 **직접 import**한다. `trip/**`에서 `user.*`를 참조하는 파일이 11개, `notification.*` 참조가 2개. `trip`을 4개로 쪼개도 이 참조는 그대로 남고, 오히려 `recommendation/service/RecommendationEngine.java`처럼 경로가 깊어지면서 "이 클래스가 사실은 두 도메인을 넘나든다"는 사실이 더 눈에 안 띄게 될 위험이 있다.

이 중 1·2·3은 "파일을 옮기고 import만 고친다"는 순수 이동 리팩터(대안 A) 안에서도 명시적 배치 규칙으로 완화할 수 있다. 4는 구조적으로 인터페이스 경계를 만들어야 풀리는 문제라 로직 변경 없이는 해결이 안 된다 — 대안 B·C에서 다룬다.

## 대안 재검토 — 003의 제약을 전제하지 않고 순수 트레이드오프로 비교

아래 A/B/C는 "003이 허용하는가"가 아니라 "이 코드베이스의 실제 문제(§구조적 문제 1~4)를 얼마나 풀고, 그 대가로 무엇을 치르는가"만으로 다시 비교한 것이다. 003과의 관계는 각 대안 끝에 사실만 짧게 적었다 — 그게 대안을 배제하는 근거로 쓰이지는 않는다.

### 대안 A — Feature 서브패키지 (레이어 유지, 최소 변경)

`trip` 도메인을 4개 기능 서브패키지 + 공용(`trip/` 루트)으로 분리. 레이어(`controller/dto/service/domain/repository`) 자체는 그대로 두고 그 안을 기능별로 나눈다.

```
trip/
├── config/, scheduler/, exception/    (그대로 유지)
├── domain/                           Trip, TripStatus                       (공용 — room 자체)
├── controller/                       TripController
├── dto/                               CreateTripRequest/Response, PatchTripRequest,
│                                       TripDetailResponse, TripHomeCardResponse,
│                                       TripListQuery/Response/Scope, UpdateTripPinRequest
├── service/                           TripCommandService, TripQueryService, TripService,
│                                       TripServiceSupport ⚠, TripDisplayNameHelper, TripHomeMaintenanceService
├── repository/                       TripRepository
│
├── membership/                        # 참여·멤버 관리
│   ├── domain/ TripMember, TripMemberRole, TripMemberStatus
│   ├── controller/ TripMemberController
│   ├── dto/ JoinTripRequest, MemberPreviewResponse, TripMembersResponse
│   ├── service/ TripJoinService, TripMemberQueryService, InviteCodeGenerator
│   └── repository/ TripMemberRepository
│
├── recommendation/                    # 추천 + 피드백
│   ├── domain/ Recommendation, RecommendationMode, RecommendationFeedback,
│   │           RecommendationFeedbackReason, RecommendationFeedbackStatus, AttendanceType, UnconfirmReason
│   ├── controller/ RecommendationController
│   ├── dto/ GenerateRecommendationsRequest, MemberAttendanceResponse, RecommendationDetailResponse,
│   │        RecommendationFeedbackResponse, RecommendationItemResponse, RecommendationListResponse,
│   │        SaveRecommendationFeedbackRequest, ConfirmTripRequest, UnconfirmTripRequest
│   ├── service/ TripRecommendationService, RecommendationEngine ⚠, RecommendationCandidate ⚠, MemberAttendanceDetail ⚠
│   └── repository/ RecommendationRepository, RecommendationFeedbackRepository
│
└── schedule/                          # 여행방 내 스케줄 합산/스냅샷
    ├── domain/ ScheduleStatus, SlotStatuses, TimeSlot, TripMemberScheduleSnapshot
    ├── dto/ MemberScheduleCalendarResponse
    ├── service/ TripScheduleSnapshotService
    └── repository/ TripMemberScheduleSnapshotRepository, projection/
```

**⚠ 표시 — 구현 전 확정할 명시적 배치 결정:**

- `TripServiceSupport`(335줄, 6개 서비스가 의존)는 3개 서브패키지 모두의 공유 커널이라 루트 `trip/service/`에 의도적으로 남긴다. 계속 커지면 책임별 분리는 별도 스펙.
- `RecommendationEngine`/`RecommendationCandidate`/`MemberAttendanceDetail`은 `recommendation/service/`로 그대로 이동 — 이들이 "service"가 아니라는 문제(§3), `user.schedule`/`user.googlecalendar` 직접 참조 문제(§4)는 여기서 고치지 않는다.

**풀리는 문제:** §1(flat 폴더) 완전 해결, §3(레이어 이름 불일치)은 완화(적어도 같은 기능끼리는 묶임).
**안 풀리는 문제:** §2(공유 커널 배치)는 규칙으로 명시할 뿐 구조적으로 없어지지 않음, §4(숨은 크로스 도메인 결합)는 그대로 이동만 됨.
**비용:** 낮음 — 파일 이동 + import 정리, `./gradlew build/test`로 검증 가능한 순수 리팩터.
**003과의 관계:** 그대로 정합 (`{domain}/{feature}/{layer}` 패턴을 이미 허용).

### 대안 B — 포트/어댑터로 크로스 도메인 경계 명시 (모듈러 모노리스, 단일 Gradle 모듈 유지)

대안 A가 못 푸는 §4를 표적으로 겨냥. `trip`이 다른 도메인을 **읽는 지점**마다 인터페이스를 두고, 물리적 모듈 분리 없이 단일 모듈 안에서 의존 방향만 강제한다.

```
trip/recommendation/
├── service/
│   ├── RecommendationEngine.java          # ScheduleAvailabilityPort, ExternalBusyDayPort만 의존
│   └── TripRecommendationService.java
└── port/
    ├── ScheduleAvailabilityPort.java      # trip이 필요로 하는 조회만 정의 (interface)
    └── ExternalBusyDayPort.java

user/schedule/service/ScheduleAvailabilityAdapter.java   # implements ScheduleAvailabilityPort
user/googlecalendar/service/GoogleCalendarBusyDayAdapter.java  # implements ExternalBusyDayPort
```

- 의존 방향이 `trip → user.schedule`(직접 import)에서 `trip.port ← user.schedule.adapter`(구현)로 뒤집힌다 — `trip`은 `user` 패키지를 더 이상 모른다.
- `RecommendationEngine`을 포트 mock만으로 단위 테스트 가능해진다 (지금은 실제 repository 빈이 있어야 통합 테스트로만 검증 가능).
- 의존 방향을 **강제**하려면 컴파일 경계(멀티모듈)가 없는 한 규율에 의존하게 되는데, 이는 ArchUnit 같은 아키텍처 테스트(`checkNoCycle`, `trip 패키지는 user.* import 금지 — port 제외` 등)를 CI에 추가하면 실질적으로 강제 가능 — 별도 라이브러리 도입(build.gradle 의존성 추가) 필요.
- **순수 이동이 아니다.** 인터페이스 추출 + 구현체 작성 + DI 배선 변경 — 로직에 손을 댄다. 이번 스펙의 "API/DB 계약 변경 없음" 전제는 유지되지만(포트는 내부 설계일 뿐 API 아님), "로직/의존성 무변경"은 깨진다.

**풀리는 문제:** §4 실질적으로 해결. §2(TripServiceSupport)는 별개 — 자동으로 안 풀림.
**비용:** 중간 — 포트 1~2개, 어댑터 1~2개, 관련 서비스의 생성자 주입 변경. ArchUnit 도입까지 포함하면 CI 설정 추가.
**003과의 관계:** "auth 기술축 예외"만 명시돼 있어 trip에 `port/`를 신설하는 것 자체가 003 문서에 없는 패턴 — 실제로 적용하려면 003에 이 패턴을 추가하는 amend가 필요(내용상 충돌은 크지 않음, 001~010 결정 어디에도 "포트를 두지 마라"는 금지는 없다).

### 대안 C — 애그리거트 기반 DDD/헥사고날 전면 전환

가장 큰 폭. `trip`을 애그리거트(`Trip`이 루트, `TripMember`/`Recommendation`/`TripMemberScheduleSnapshot`은 애그리거트 내부 엔티티)로 재설계하고, 포트/어댑터를 전체 도메인 경계에 일반화한다.

```
trip/
├── domain/            Trip(AggregateRoot), TripMember, Recommendation, ...  # 순수 모델 여부는 아래 참고
├── application/        CreateTripUseCase, JoinTripUseCase, GenerateRecommendationsUseCase, ...
├── port/
│   ├── in/             (선택) 위 UseCase 인터페이스
│   └── out/            TripRepository(도메인이 정의하는 인터페이스), ScheduleAvailabilityPort, NotificationPublisherPort
└── adapter/
    ├── web/             TripController, TripMemberController — port.in 호출
    ├── persistence/      TripJpaRepository, TripRepositoryAdapter implements port.out.TripRepository
    └── external/         ScheduleAvailabilityAdapter, GoogleCalendarAdapter
```

**실제로 확인한 이 코드베이스의 제약 — 겉핥기가 되기 쉬운 지점:**

- `Trip` 엔티티에는 `TripMember`/`Recommendation`에 대한 `@OneToMany` 컬렉션이 없다. 지금은 자식이 부모를 `@ManyToOne`으로 참조하는 단방향 구조이고, `TripMemberRepository`·`RecommendationRepository`를 `TripMemberQueryService`·`TripRecommendationService` 등 여러 서비스가 **직접** 주입받아 쓴다. "애그리거트 루트를 통해서만 자식에 접근"을 실제로 강제하려면 이 직접 주입을 전부 걷어내고 `Trip` 애그리거트 경유로 바꿔야 하는데, 파급 범위가 서비스 계층 전체다 — 이번 요청(패키지 정리)의 스코프를 완전히 벗어난다.
- JPA 지연 로딩·연관관계 탐색에 의존하는 스타일이 이미 굳어 있어(`architecture.md`가 명시적으로 "JPA 연관관계를 자유롭게 활용"이라 적어둔 것도 이 스타일을 반영), ID-only 참조로 바꾸려면 `trip`·`user` 양쪽의 조회 쿼리 상당수를 다시 짜야 한다.
- 순수 도메인 모델(JPA 무의존)까지 갈지, JPA 엔티티를 그대로 도메인 모델로 쓰되 레이어·포트만 도입할지에 따라 비용이 크게 갈린다 — 전자는 Entity ↔ Domain 모델 매핑 계층이 추가로 필요(상당한 보일러플레이트), 후자는 "이름만 헥사고날"이 되어 이점이 줄어든다.
- 물리적 강제(별도 Gradle 모듈)와 논리적 강제(단일 모듈 + ArchUnit)는 비용이 크게 다르다. 전자는 빌드·배포 파이프라인, IDE 프로젝트 구조까지 건드리는 큰 변경. 후자는 대안 B의 확장판 정도 비용으로 "경계 위반 시 빌드 실패"라는 실질적 효과를 얻을 수 있어, 실무적으로는 이쪽이 훨씬 현실적인 절충안이다.

**풀리는 문제:** §1~4 전부 구조적으로 해결 가능(제대로 하면). 도메인 로직을 인프라 없이 단위 테스트할 수 있게 되고, 향후 `recommendation`처럼 무거운 계산 로직을 별도 서비스로 분리할 때 재작성 비용이 크게 준다.
**비용:** 높음 — 사실상 `trip` 전체 재작성. 애그리거트 경계를 지키려면 서비스 계층 대부분을 다시 설계해야 하고, "패키지만 정리"로 시작한 작업이 몇 주 단위 재작성으로 커질 위험이 크다. 어중간하게 적용하면(애그리거트라면서 자식 repository를 여전히 직접 주입) 오히려 레이어드보다 이해하기 어려운 결과물이 된다.
**003과의 관계:** 003의 결정 다수(단일 모듈, ID-only 참조 미강제, 풀 DDD 미적용)와 직접 충돌 — 이 대안을 실제로 채택하면 003을 대체하는 새 decision 문서가 필요하다.

### 비교

| | A. Feature 서브패키지 | B. 포트/어댑터 (모듈러 모노리스) | C. 애그리거트 DDD/헥사고날 |
|---|---|---|---|
| §1 flat 폴더 | 해결 | 해결(A 포함) | 해결 |
| §2 공유 커널 배치 | 규칙으로만 완화 | 규칙으로만 완화 | 구조적으로 해결 가능 |
| §3 레이어=성격 불일치 | 완화 | 완화 | 해결 가능 |
| §4 숨은 크로스 도메인 결합 | 안 풀림 | 해결 | 해결 |
| 로직 변경 여부 | 없음 (순수 이동) | 있음 (인터페이스 추출) | 있음 (재설계 수준) |
| 예상 비용 | 낮음 (일~이틀) | 중간 (수일) | 높음 (수 주) |
| 되돌리기 난이도 | 쉬움 (다시 이동) | 보통 | 어려움 |

### 재검토된 권장

세 대안 모두 나름의 정당성이 있어 "무조건 A"는 아니다 — 실제로 겪고 있는 문제가 §1(탐색성)뿐이면 A로 충분하고, §4(숨은 결합)가 더 아프다면 B가 정확히 그 문제를 겨냥한다. C는 지금 겪는 통증에 비해 재설계 범위가 커서, 팀이 실제로 애그리거트 불변식을 지켜야 하는 버그를 겪었거나 `trip` 도메인을 별도 배포 단위로 쪼갤 계획이 이미 있는 게 아니라면 지금 시점에 정당화하기 어렵다.

1. **A를 먼저 진행.** 리스크가 가장 낮고, §1을 확실히 해결하며, B·C로 가더라도 A의 기능별 폴더 구조는 그대로 재사용 가능한 발판이 된다.
2. **B는 A와 분리해 별도 스펙으로.** `RecommendationEngine`의 크로스 도메인 참조가 실제로 불편했던 적이 있는지(테스트 작성 시 어려움, 순환 의존 우려 등) 먼저 확인하고 착수 여부 결정.
3. **C는 지금 시작하지 않는다.** 다만 "겉핥기 리스크"를 인지하고 있어야 하고, 트리거(팀·배포 단위 분리 필요, 애그리거트 불변식 위반으로 인한 실제 버그)가 생기면 그때 별도 decision 문서로 재논의.

**절차 메모 (배제 근거 아님, 사실 확인):** `docs/decisions/003-architecture-guide.md`는 현재 "확정" 상태이며 B·C 모두 그 결정 내용 일부와 다르다. 위 권장은 003의 존재와 무관하게 순수 엔지니어링 트레이드오프로 도출한 것이고, B나 C를 실제로 구현 단계까지 가져가려면 착수 전에 003을 amend하거나 대체하는 절차가 별도로 필요하다는 점만 남겨둔다.

## 다른 도메인 (Nice — 우선순위 낮음, 2026-07-31 재확인)

| 도메인 | 현재 상태 | 제안 |
|--------|-----------|------|
| `auth` | `jwt/`(8)·`oauth/`(13)·`security/`(3)·`dev/`로 이미 기술축 분리돼 있음. `dto/`(7)·`service/`(4)·`domain/`(2)·`repository/`(1)·`controller/`(1)만 flat이지만 각 폴더가 여전히 작음 | 그대로 둬도 무방. 굳이 하면 `dto/`를 로그인(`Login*`, `Refresh*`)과 Apple 알림(`AppleNotificationRequest`)으로 나눌 수 있으나 효과 작음 |
| `notification` | `dto/`(2), `domain/`(5), `event/`(6), `service/`(4), `controller/`(2)로 이미 파일 수가 적당 | 리팩터 불필요. `event/`가 늘어나면 `event/trip/`, `event/schedule/`로 나누는 것 검토 |
| `user` (schedule/googlecalendar 제외 루트) | `controller/`(1), `domain/`(2), `dto/`(3), `exception/`(1), `repository/`(1), `service/`(4) — 각 폴더 1~4개 | 리팩터 불필요 (이미 작음) |

## 구현 가이드 (다음 세션에서 따라 할 순서 — 대안 A 기준)

1. **브랜치 분리** — 이 리팩터는 기능 브랜치와 섞지 않는다. `main`에서 `refactor/{issue-number}-trip-package-structure` 새로 분기 (GitHub 이슈 먼저 생성 — `.claude/rules/harness-workflow.md` 브랜치 규칙)
2. **한 서브패키지씩 순서대로 이동** — 한 번에 다 옮기지 말고 `membership/` → `recommendation/` → `schedule/` 순으로 하나씩:
   1. 대상 클래스 파일들을 `git mv`로 새 경로로 이동 (히스토리 보존)
   2. `package` 선언 갱신
   3. IDE/`grep -rl`로 구 FQCN(`com.tripfit.tripfit.trip.dto.JoinTripRequest` 등) 참조하는 모든 import 갱신
   4. `./gradlew build` — 컴파일 에러(누락된 import) 전부 해소
   5. `./gradlew test` 그린 확인 후 다음 서브패키지로
3. **각 이동 후 확인할 것**
   - `common/config/OpenApiConfig.java`, `WebConfig.java`에 패키지 경로를 하드코딩한 base-package/scan 설정이 있는지 확인 (Spring Boot는 보통 `@SpringBootApplication` 루트 기준 자동 스캔이라 문제 없을 가능성이 높지만 명시적으로 확인)
   - Swagger 그룹핑(`@Tag`)이 패키지가 아니라 애노테이션 기준이라 영향 없는지 확인
   - 테스트 코드(`src/test/java/.../trip/**`)도 프로덕션과 동일한 서브패키지 구조로 같이 이동 (mirrored structure 유지)
4. **커밋 단위** — 서브패키지 1개 이동 = 커밋 1개 권장 (리뷰 용이). `harness-workflow.md`의 "커밋 최대 3개" 규칙은 기능 구현 기준이므로, 순수 구조 리팩터 PR은 이동 단위로 나누는 것을 우선한다 (PR 전체는 1개로 묶어도 무방 — 사용자와 상의)
5. **레거시 정리** — 이동 후 구 패키지 경로에 파일이 남아있지 않은지(`git status`), 빈 폴더가 안 남았는지 확인
6. **완료 후** — `docs/architecture/erd.md`는 패키지 구조와 무관하므로 갱신 불필요. `docs/architecture.md`의 Package Layout 예시(trip 부분)를 새 구조로 갱신. 이 스펙 문서 상태를 `Implemented`로 갱신

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `TripMemberStatus`를 `membership/`으로 옮길지, room 상태와 결합돼 루트에 남길지 | [미정] | 구현 시작 전 실제 참조 관계(어느 서비스에서 얼마나 쓰는지) 재확인 필요 |
| `auth`/`notification` 리팩터를 이번 PR에 포함할지 | [미정] | 위 표 기준으로는 효과가 작아 보류 권장. 실제 시작 시 사용자에게 재확인 |
| 서브패키지 이름(`membership` vs `member`, `schedule` vs `schedule-snapshot`) | [미정] | 구현 착수 시 확정 |
| `TripServiceSupport`를 루트에 남기는 것에 사용자 동의하는지 | [미정] | 대안 A의 ⚠ 표시 배치 — 구현 착수 전 확인 |
| 대안 B(`RecommendationEngine` 포트 추출)를 별도 스펙으로 진행할지 | [미정] | 사용자가 원하면 이번 리팩터와 분리해 착수 |
| 대안 C(애그리거트 DDD/헥사고날)를 언제 재검토할지 | [미정] | §"재검토된 권장" 트리거 참고 — 지금은 미착수 |

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] `trip` 패키지 내 어떤 레이어 폴더도 파일 10개 이상 flat하게 있지 않음
- [ ] API 응답/엔드포인트 diff 없음 (`git diff`로 프로덕션 코드가 이동/import 변경 외에 로직 변경이 없는지 확인)
- [ ] 구 패키지 경로에 잔존 파일 없음
- [ ] `docs/architecture.md` Package Layout의 trip 예시가 새 구조와 일치

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-30 | 초안 — 다음 세션에서 구현 착수 예정, 이번 세션에서는 구현하지 않음 |
| 2026-07-31 | 현재 코드베이스로 재측정(스펙 초안 대비 수치 stale 확인), 구조적 문제 4가지 비판 추가, 대안 1(원안 보강)·대안 2(포트 추출) 두 갈래로 재구성, DDD/헥사고날 전환 검토 추가(decision 003과 충돌 명시, 지금은 비채택 권장). 구현은 아직 하지 않음 |
| 2026-07-31 (재검토) | 사용자 요청으로 decision 003의 제약을 전제하지 않고 대안을 다시 비교 — 대안 A(feature 서브패키지)·B(포트/어댑터, 모듈러 모노리스)·C(애그리거트 DDD/헥사고날 전면 전환) 3갈래로 재구성. C를 "검토 후 기각"이 아니라 실제 마이그레이션 형태(포트/어댑터/애그리거트 패키지 구조)·이 코드베이스 고유의 제약(Trip에 `@OneToMany` 컬렉션 없음, TripMember/Recommendation repository를 여러 서비스가 직접 주입)까지 근거로 들어 비교. 003과의 관계는 배제 근거가 아닌 "채택 시 별도 amend 필요"라는 절차 메모로만 남김. 구현은 아직 하지 않음 |
