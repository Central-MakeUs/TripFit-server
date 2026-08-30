# Trip Architecture Audit — 2차 라운드 (2026-08-05)

## 범위

- 패키지: `com.tripfit.tripfit.trip` — `config`, `controller`, `domain`, `dto`, `exception`, `repository`, `repository.projection`, `scheduler`, `service` (추천/recommendation은 별도 패키지 없이 `service/`에 flat 포함)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP
- 전수 검토: main 71개 파일, test 17개 파일(1차 라운드 이후 `TripServiceSupportTest` 신설분 포함), 관련 스펙(`docs/specs/trip/*`), `docs/architecture/erd.md`, `docs/product/business-rules/trip.md`
- **1차 감사 대비**: 1차(`docs/audits/trip/audit.md`, `refactor-log.md`)는 N+1(정기·개인 일정 배치 조회), 방장 검증 헬퍼 통합, `TripQueryService.toDetail` 패스스루 제거, 추천 삭제 책임 이관(A-1·A-2·B-1~B-3)을 이미 반영 완료. 이번 라운드는 그 결과물을 전제로 **① 도메인/임베더블의 미사용 API 표면(dead code), ② 빈(bean) 가시성/캡슐화, ③ ERD·감사 관점에서 "겹쳐 보이지만 실제로는 다른" 로직 구분, ④ JPA 엔티티 identity 설계, ⑤ 스키마 문서와의 정합성**이라는, 1차가 다루지 않은 시각으로 재검토했다. 1차 C/D 항목(초대코드 alphabet 명칭, `deleteTrip` cascade 개별 UPDATE, `requireValidFeedback`/`requireValidUnconfirmReason` 중복, `TripServiceSupport` 다책임, `TripHomeMaintenanceService` 단일 트랜잭션, interceptor EXISTS 2회, DTO 필드 중복, `TripServiceTest` 수동 조립, `requireOwner` 재검증, anemic 모델, `TripJoinService` 분리, `RecommendationFeedback` FK 미설정)는 보류 사유가 여전히 유효함을 확인했고, 별도 근거 없이 재상정하지 않았다.

## ✅ A. 반드시 수정해야 하는 사항

이번 라운드에서 **A 없음**.

- 이유: 성능(N+1)·보안·명백한 버그 관점에서 전 서비스 계층(`TripServiceSupport`, `TripCommandService`, `TripQueryService`, `TripMemberQueryService`, `TripRecommendationService`, `TripScheduleSnapshotService`, `RecommendationEngine`, `TripHomeMaintenanceService`)과 컨트롤러·인터셉터·리포지토리를 재검토했으나, 1차에서 이미 배치 조회로 정리된 이후 추가로 발견되는 N+1·불필요 반복 조회·SQL Injection·민감정보 로그·검증 누락이 없었다. 로그 출력 자체가 이 패키지에 없어(grep 결과 0건) 민감정보 로그 리스크도 없다. `TripAuthorizationInterceptor`의 UUID 파싱·NOT_FOUND 통일 처리도 정보 누수 방지가 이미 적용돼 있다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `SlotStatuses.get(TimeSlot)` / `set(TimeSlot, ScheduleStatus)` — 어디서도 호출되지 않는 Dead Code

- **Priority**: Low
- **Category**: Dead Code
- **문제**: `trip/domain/SlotStatuses.java:54-68`의 제네릭 접근자 `get(TimeSlot slot)`/`set(TimeSlot slot, ScheduleStatus status)`는 `main`·`test` 전체(`grep -rn "\.get(TimeSlot\.\|\.set(TimeSlot\." src/`)에서 호출부가 0건이다. 실제 호출부(`RecommendationEngine`, `TripMemberQueryService`, `ScheduleCalendarResolver`, `TripMemberScheduleSnapshot` 등)는 모두 개별 필드 접근자(`getMorningStatus()`/`getAfternoonStatus()`/`getEveningStatus()`, 그리고 생성자)만 사용한다. `TimeSlotTest`도 `TimeSlot.statusForRange`/`overlaps`만 검증하고 이 두 메서드는 다루지 않는다.
- **왜 문제인가**: 같은 정보를 얻는 경로가 2가지(개별 getter/setter vs `TimeSlot` 파라미터화 접근자)로 남아 있어, 신규 개발자가 "언제 어떤 경로를 써야 하는지" 헷갈릴 수 있고, 실제로 아무도 안 쓰는 만큼 유지보수 대상만 늘어난다.
- **개선 방법**: `get(TimeSlot)`/`set(TimeSlot, ScheduleStatus)` 두 메서드를 삭제. 개별 getter/setter(`getMorningStatus()` 등)는 그대로 유지.
- **API 영향**: No Impact — `SlotStatuses`는 API DTO가 아닌 내부 `@Embeddable`이고, 삭제 대상 메서드는 어떤 Controller·DTO 변환에도 관여하지 않는다.
- **예상 변경 파일**: `trip/domain/SlotStatuses.java`
- **예상 변경 라인 수**: 15줄 삭제
- **위험도**: Low — 호출부 자체가 없어 컴파일 영향 없음.
- **테스트 영향도**: 없음(해당 메서드를 검증하는 테스트가 없어 삭제해도 회귀 없음).
- **예상 효과**: 불필요한 공개 API 표면 축소, `SlotStatuses` 사용 경로 단일화(개별 getter/setter만 남김).

### B-2. `TripScheduleSnapshotService` — 패키지 내부에서만 쓰이는데 불필요하게 `public`

- **Priority**: Low
- **Category**: Spring Best Practice (캡슐화) / 패키지 구조
- **문제**: `trip/service/TripScheduleSnapshotService.java:23`는 `public class`이지만, 실제 참조는 `TripRecommendationService`(`confirmSchedule` 내부)와 `TripHomeMaintenanceService`(`runForDate`) 둘 다 같은 `trip.service` 패키지 안에 있다(`grep -rn "TripScheduleSnapshotService" src/main/java`로 패키지 외부 import 0건 확인). 반면 같은 계층의 형제 클래스인 `TripCommandService`, `TripJoinService`, `TripQueryService`, `TripMemberQueryService`, `TripRecommendationService`, `RecommendationEngine`은 모두 package-private(`class Xxx`)로 일관돼 있다.
- **왜 문제인가**: 이 클래스만 `public`으로 남아 있어 "이 서비스는 다른 도메인·패키지에서 직접 써도 되는 진입점"이라는 잘못된 신호를 준다. 실제로는 `TripHomeMaintenanceService`(스케줄러 진입점, 패키지 외부에서 쓰여 `public`이 맞음)나 `TripServiceSupport`(`config` 패키지의 인터셉터가 참조해 `public`이 맞음)와 달리 캡슐화 경계를 넘는 실사용이 없다.
- **개선 방법**: `public class TripScheduleSnapshotService` → `class TripScheduleSnapshotService`(package-private)로 변경. 생성자도 `public` → 기본(package-private)으로 함께 낮춘다.
- **API 영향**: No Impact — HTTP 계층과 무관한 내부 서비스 가시성 변경.
- **예상 변경 파일**: `trip/service/TripScheduleSnapshotService.java`
- **예상 변경 라인 수**: 2줄(class·생성자 선언)
- **위험도**: Low — 호출부(`TripRecommendationServiceTest`, `TripHomeMaintenanceServiceTest`, `TripScheduleSnapshotServiceTest`, `TripScheduleSnapshotServiceIntegrationTest`, `TripServiceTest`) 전부 `com.tripfit.tripfit.trip.service` 동일 패키지에 있음을 확인, Spring 빈 등록·DI(생성자 주입, `@Autowired` 불필요)도 가시성과 무관하게 정상 동작.
- **테스트 영향도**: 없음 — 전부 같은 패키지에서 참조.
- **예상 효과**: 서비스 계층 가시성 컨벤션 일관화, 실수로 다른 패키지에서 직접 의존하는 것을 컴파일 타임에 방지.

## 💡 C. 참고 사항 (이번엔 수정 안 함, 이유 필수)

- **`RecommendationEngine.loadContext`와 `TripServiceSupport.resolveMergedSchedules`가 "정기+개인+구글 일정을 배치 조회해 `groupingBy`로 나눈다"는 조회 패턴이 구조적으로 유사**하다(둘 다 `regularScheduleRepository.findByUserIdIn`/`personalScheduleRepository.findByUserIdInAndScheduleDateBetween`/`googleCalendarService.findBusyDaysByUserIds`를 호출). 다만 반환 형태와 후속 용도가 다르다 — `resolveMergedSchedules`는 `Map<UUID, List<CalendarDayResponse>>`(달력 렌더링용)만 반환하는 반면, `loadContext`는 그 결과를 `Map<UUID, Map<LocalDate, CalendarDayResponse>>`(날짜 O(1) 조회용, 후보 윈도우마다 재사용)로 인덱싱하고 **`regularsByUser`(원본 `RegularSchedule` 리스트, 연차 계산에 필요)까지 별도로 보존**한다. 공통 헬퍼로 합치면 `resolveMergedSchedules` 호출부(달력 조회·스냅샷 freeze)에 불필요한 날짜-Map 변환과 원본 regulars 보존 책임이 새로 생겨 오히려 계층이 하나 늘어난다 — 이번 라운드에서는 반환 형태 차이를 이유로 통합하지 않는다.
- **`CreateTripRequest`/`PatchTripRequest`가 `name`/`durationNights`/`durationDays`/`memberCount`/`destination` 필드를 그대로 중복**한다. 다만 `CreateTripRequest`에만 `startRange`/`endRange`(방 생성 시 1회만 정함, 이후 불변)가 있어 계약이 다르고, 1차 감사의 `TripDetailResponse`/`TripHomeCardResponse` 판단과 동일한 논리(계약이 다른 DTO는 공통화하지 않음)가 그대로 적용돼 넘어간다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항 (이유 필수)

- **`Trip`·`TripMember` 등 trip 도메인 JPA 엔티티에 `equals()`/`hashCode()` 오버라이드가 없고 기본(Object identity) 비교에 의존**한다. 언뜻 JPA 점검 항목(#7)의 결함처럼 보이지만, 실제로 이 패키지 전체에서 `Set<Trip>`/`Set<TripMember>`/`Map<TripMember, ...>`처럼 엔티티 자체를 키·Set 원소로 쓰는 코드가 전혀 없다(grep 확인 — 전부 `List`나 `id`를 키로 쓰는 `Map<UUID, ...>`). JPA 엔티티에 `equals`/`hashCode`를 잘못 구현하면(지연 로딩 프록시·detached 인스턴스·복합 컬렉션 등과 얽혀) 오히려 새로운 버그의 원인이 되는 사례가 흔하고, 지금 이 도메인엔 그걸 요구하는 실제 사용처가 없어 YAGNI 원칙상 추가하지 않는다.
- **`TripMemberScheduleSnapshot.frozenAt`이 상속받은 `BaseTimeEntity.createdAt`과 같은 순간을 가리켜 보여도 별도 컬럼으로 유지**한다. `frozenAt`은 `freezeTrip()` 안에서 `LocalDateTime.now()`를 **한 번만** 호출해 그 배치(freeze 1회 실행)의 모든 행에 동일하게 부여하는 반면, `createdAt`은 JPA Auditing이 `saveAll()`로 저장되는 각 엔티티마다 개별적으로 채우는 값이라 여러 행 사이에 미세하게 달라질 수 있다 — 즉 "이 스냅샷들이 같은 freeze 배치에서 나왔다"는 보장은 `frozenAt`만 줄 수 있다. 또한 `docs/architecture/erd.md:174-186`가 `frozen_at`을 `created_at`/`updated_at`과 나란히 이미 명시적으로 문서화한 SSOT 컬럼이라, 지금 응답 DTO에서 안 읽힌다는 이유만으로 제거하면 ERD 동기화가 별도로 필요해지고, 이 프로젝트는 Flyway/Liquibase 없이 `ddl-auto: update`로만 스키마를 관리해(`application-dev.yml`) 컬럼을 지워도 기존 DB에는 orphan 컬럼으로 계속 남는 스키마 drift 위험까지 있다 — 실익 대비 비용이 커서 유지한다.

## 15. 백엔드 아키텍처 개선 제안

- **API — `GET /trips` Cursor Pagination 부재**: `TripQueryService.listMyTrips`는 scope·status·ownerOnly로 필터링만 하고 페이지네이션 없이 사용자의 전체 멤버십을 한 번에 반환한다. 현재 MVP 단계(`memberCount` 상한 10, 여행방 자체는 사용자당 상한 없음)에서는 한 사용자가 짧은 기간에 만들 수 있는 방 수가 실질적으로 적어 문제가 되지 않지만, 서비스가 오래 운영되며 한 사용자의 누적 여행방 수(과거 EXPIRED 포함, `scope=all`)가 수백 건으로 늘어나면 응답 페이로드·정렬 비용이 커진다. **Now/Later**: **Later** — 지금 도입하면 `TripListQuery`·`TripListResponse` 계약 변경(cursor 필드 추가)이 필요해 API 계약 변경 비용이 실제 트래픽 이득보다 크다. 사용자당 누적 여행방 수가 실제로 체감될 만큼 늘어나는 시점에 재검토.
- **Security/API — `POST /trips` Idempotency Key 부재**: 여행방 생성은 멱등이 아니어서(매번 새 UUID·초대코드 발급), 모바일 클라이언트의 네트워크 재시도·더블탭이 중복 여행방 생성으로 이어질 수 있다. 현재 이런 중복 생성이 실제 문제로 보고된 바 없고, 도입하려면 별도 Idempotency-Key 저장소(TTL 포함)가 필요해 구현 난이도 대비 지금 시점 이득이 불확실하다. **Now/Later**: **Later** — 실제 중복 생성 사례가 관측되거나 클라이언트가 자동 재시도 로직을 도입하는 시점에 재검토.
- **Concurrency/Redis/Async — 이 도메인에 적용할 가치 있는 새 항목 없음**: 1차 감사의 결론(추천 계산·홈 배치는 CPU 바운드, 정원 상한 10명 규모에서 큐잉·캐싱·분산 락 도입 근거 없음)에서 상황 변화가 없다. **Now/Later/Never**: **Never**(현재 규모 기준) — 1차와 동일 판단 유지.

## 승인 대기

사용자 승인 후 B-1·B-2만(A 없음) 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
