# 패키지 구조 리팩터 — 도메인 내 기능 서브패키지 정리

> 상태: Draft
> 유형: 구조 리팩터 (API/DB 계약 변경 없음 — 표준 스펙 템플릿의 API/데이터모델 섹션 N/A)
> 관련 BR: N/A

## 목표

`trip`, `auth`, `notification` 등 일부 도메인 패키지에 파일이 flat하게(`dto/`, `service/`, `domain/` 등 레이어 폴더 하나에 10개 이상) 쌓여 있어 코드를 찾는 데 시간이 걸린다. `user/schedule`, `user/googlecalendar`가 이미 쓰고 있는 `{domain}/{feature}/{layer}` 패턴을 다른 도메인에도 일관되게 적용해서 탐색성을 개선한다.

## 배경

- 컨벤션 근거: `AGENTS.md` Conventions — `{domain}/controller|dto|service|domain|repository|client`, **필요 시** `{domain}/{feature}/…`
- 이미 이 패턴을 따르는 예시: `user/schedule/*`, `user/googlecalendar/*` (각각 6~9개 파일, feature 경계가 명확)
- 아직 안 따르는 곳: `trip/*` (dto 13개, service 11개, domain 11개가 레이어 폴더 하나에 flat), `auth/*`, `notification/*`
- 순수 패키지 이동 + import 정리이므로 API 계약·DB 스키마·비즈니스 로직 변경 **없음** (STOP §5 Breaking-Change-Reason 대상 아님)

## 범위 (이번 리팩터가 다루는 것)

### Must Have

- [ ] `trip` 도메인을 기능 서브패키지로 분리 (아래 "목표 구조" 참조)
- [ ] 이동한 클래스를 참조하는 모든 import 경로 갱신
- [ ] `./gradlew build`, `./gradlew test` 그린 유지
- [ ] Swagger(`@Schema`, controller `@Tag` 등) 패키지 스캔에 영향 없는지 확인 (`common/config/OpenApiConfig.java`, `WebConfig.java`의 base-package 설정 점검)

### Nice to Have

- [ ] `auth` 도메인 서브패키지 분리 (`oauth`/`jwt`/`dev`는 이미 기능 단위로 분리돼 있어 우선순위 낮음 — 아래 참고)
- [ ] `notification` 도메인 서브패키지 분리 (dto 2개·domain 5개로 아직 flat이 심하지 않아 우선순위 낮음)

### Out of Scope (이번 리팩터에서 하지 않음)

- API 응답 필드·엔드포인트 경로 변경
- Entity/컬럼 변경 (ERD 불변)
- `common/`, `user/repository`, `user/dto` 등 이미 파일 수가 적은(3개 이하) 폴더 재편
- 클래스명 변경 (경로만 이동 — import만 바뀌고 참조 클래스명은 그대로 유지하는 것을 원칙으로 함. 클래스명까지 바꾸고 싶다면 이 스펙과 별도로 논의)

## 현재 구조 (문제 지점)

```
trip/
├── config/            (1)
├── controller/         TripController, TripMemberController (2)
├── domain/            Recommendation, RecommendationMode, ScheduleStatus, SlotStatuses,
│                       TimeSlot, Trip, TripMember, TripMemberRole,
│                       TripMemberScheduleSnapshot, TripMemberStatus, TripStatus (11)
├── dto/               CreateTripRequest/Response, JoinTripRequest, MemberPreviewResponse,
│                       MemberScheduleCalendarResponse, PatchTripRequest, TripDetailResponse,
│                       TripHomeCardResponse, TripListQuery/Response/Scope,
│                       TripMembersResponse, UpdateTripPinRequest (13)
├── exception/         (1)
├── repository/        RecommendationRepository, TripMemberRepository,
│                       TripMemberScheduleSnapshotRepository, TripRepository, projection/ (4+)
├── scheduler/          (1)
└── service/           InviteCodeGenerator, TripCommandService, TripDisplayNameHelper,
                        TripHomeMaintenanceService, TripJoinService, TripMemberQueryService,
                        TripQueryService, TripRecommendationService, TripScheduleSnapshotService,
                        TripService, TripServiceSupport (11)
```

레이어 폴더 하나에 서로 다른 기능(방 CRUD, 멤버 관리, 추천, 스케줄 스냅샷, 홈 유지보수)이 섞여 있어서 "추천 관련 코드 다 보여줘" 같은 탐색이 `dto/`, `service/`, `domain/`, `repository/`를 각각 열어서 이름으로 골라내야 한다.

## 목표 구조 (제안)

`trip` 도메인을 4개 기능 서브패키지 + 공용(`trip/` 루트에 남는 것)으로 분리한다.

```
trip/
├── config/                          (그대로 유지 — 파일 1개)
├── domain/                           Trip, TripStatus                       (공용 — room 자체)
├── controller/                       TripController                         (방 CRUD/조회)
├── dto/                               CreateTripRequest/Response, PatchTripRequest,
│                                       TripDetailResponse, TripHomeCardResponse,
│                                       TripListQuery/Response/Scope, UpdateTripPinRequest
├── service/                           TripCommandService, TripQueryService, TripService,
│                                       TripServiceSupport, TripDisplayNameHelper,
│                                       TripHomeMaintenanceService
├── repository/                        TripRepository
├── scheduler/                         (그대로 유지)
├── exception/                         (그대로 유지 — 공용 TripErrorCode 등)
│
├── membership/                        # 참여·멤버 관리
│   ├── domain/                        TripMember, TripMemberRole, TripMemberStatus
│   ├── controller/                    TripMemberController
│   ├── dto/                           JoinTripRequest, MemberPreviewResponse, TripMembersResponse
│   ├── service/                       TripJoinService, TripMemberQueryService, InviteCodeGenerator
│   └── repository/                    TripMemberRepository
│
├── recommendation/                    # 추천
│   ├── domain/                        Recommendation, RecommendationMode
│   ├── service/                       TripRecommendationService
│   └── repository/                    RecommendationRepository
│
└── schedule/                          # 여행방 내 스케줄 합산/스냅샷
    ├── domain/                        ScheduleStatus, SlotStatuses, TimeSlot,
    │                                   TripMemberScheduleSnapshot
    ├── dto/                           MemberScheduleCalendarResponse
    ├── service/                       TripScheduleSnapshotService
    └── repository/                    TripMemberScheduleSnapshotRepository, projection/
```

**분류 기준 메모 (구현자가 재확인할 것— Draft이므로 확정 아님):**

- `UpdateTripPinRequest`는 room 자체 속성(pin)이라 루트 `dto/`에 유지 — membership이 아님
- `TripMemberStatus`가 room 진행 상태와 얽혀 있는지(`#54` 스펙 — status는 `respondedAt` 파생) 확인 후 `membership/domain`으로 이동해도 room 쪽 로직에서 참조가 깨지지 않는지 점검
- `TripController` vs `TripMemberController`의 책임 경계가 이미 파일명으로 나뉘어 있으므로 컨트롤러 이동은 기계적

## 다른 도메인 (Nice — 우선순위 낮음)

| 도메인 | 현재 상태 | 제안 |
|--------|-----------|------|
| `auth` | `jwt/`, `oauth/`, `dev/`, `security/`로 이미 기능 분리돼 있음. `dto/`(6)·`service/`(2)·`repository/`(1)만 flat | 그대로 둬도 무방. 굳이 하면 `dto/`를 로그인(`LoginRequest/Response`, `RefreshRequest/Response`)과 관리용(`DevLoginRequest`, `LogoutRequest`)으로 나눌 수 있으나 효과 작음 |
| `notification` | `dto/`(2), `domain/`(5), `event/`(6), `service/`(4)로 이미 파일 수가 적당 | 리팩터 불필요. `event/`가 6개로 늘면 `event/trip/`, `event/schedule/`로 나누는 것 검토 |
| `user` (schedule/googlecalendar 제외 루트) | `controller/`, `domain/`, `dto/`(3), `exception/`, `repository/`, `service/` — 각 폴더 1~3개 | 리팩터 불필요 (이미 작음) |

## 구현 가이드 (다음 세션에서 따라 할 순서)

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
6. **완료 후** — `docs/architecture/erd.md`는 패키지 구조와 무관하므로 갱신 불필요. 이 스펙 문서 상태를 `Implemented`로 갱신

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `TripMemberStatus`를 `membership/`으로 옮길지, room 상태와 결합돼 루트에 남길지 | [미정] | 구현 시작 전 실제 참조 관계(어느 서비스에서 얼마나 쓰는지) 재확인 필요 |
| `auth`/`notification` 리팩터를 이번 PR에 포함할지 | [미정] | 위 표 기준으로는 효과가 작아 보류 권장. 실제 시작 시 사용자에게 재확인 |
| 서브패키지 이름(`membership` vs `member`, `schedule` vs `schedule-snapshot`) | [미정] | 구현 착수 시 확정 |

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] `trip` 패키지 내 어떤 레이어 폴더도 파일 10개 이상 flat하게 있지 않음
- [ ] API 응답/엔드포인트 diff 없음 (`git diff`로 프로덕션 코드가 이동/import 변경 외에 로직 변경이 없는지 확인)
- [ ] 구 패키지 경로에 잔존 파일 없음

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-30 | 초안 — 다음 세션에서 구현 착수 예정, 이번 세션에서는 구현하지 않음 |
