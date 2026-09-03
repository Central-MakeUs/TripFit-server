# User Architecture Audit — Round 2 (2026-08-05)

`com.tripfit.tripfit.user` 및 `com.tripfit.tripfit.user.googlecalendar` 패키지를 대상으로 1차 리팩토링 반영 이후 진행한 2차 아키텍처 감사 문서다. 반드시 수정해야 하는 A 항목 1건과 유지보수성 리팩토링 B 항목 1건(참고 C 3건, 비권장 D 4건)을 도출했다. 도출된 항목은 승인 대기 상태로 정리되었으며, 이후 반영 내역은 `refactor-log.md`에 기록되었다.

## 범위

- 패키지: `com.tripfit.tripfit.user` (`client`, `controller`, `domain`, `dto`, `exception`, `repository`, `service`) + `com.tripfit.tripfit.user.googlecalendar` (`client`, `controller`, `domain`, `dto`, `exception`, `repository`, `scheduler`, `service`)
- 제외: `user/schedule/**` (별도 "user-schedule" 도메인, 별도 감사 — 이미 2차 라운드까지 완료됨)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용) — 1차 반영(커밋 `e7ce06c` Refactor) 이후 재검사
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP, `spring-boot-java.md`
- main 26개 파일(`user` 15 + `user/googlecalendar` 11) 전수 재검토 + 관련 호출부 교차 확인: `auth/service/AuthService`·`auth/dev/service/DevAuthService`가 `UserLookupService`/`UserSummaryService` 사용하는 지점, `trip` 도메인이 `UserSummaryService`/`UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED`/`GoogleCalendarService`를 사용하는 지점, `user/service/UserWithdrawalService`가 `auth/service/AppleCredentialService`·`GoogleLoginCredentialService`를 호출하는 지점
- `docs/audits/user/audit.md`(1차 A 2·B 6·C 3·D 4)와 `refactor-log.md`(반영 diff) 원문 대조, `docs/audits/user-schedule/audit-round2.md`·`docs/audits/auth/audit-round2.md`(같은 세션 직전 라운드) 선례 대조

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `GoogleCalendarService.disconnect()` — 여전히 `@Transactional` 안에서 Google revoke HTTP 호출을 수행 (1차 A-1이 `connect()`/`syncUser()`에는 적용됐지만 같은 클래스의 `disconnect()`는 감사 대상에서 빠짐)

- **Priority**: High
- **Category**: Architecture / Performance
- **문제**: `disconnect()`(`GoogleCalendarService.java:143-159`)는 여전히 `@Transactional`이며, 그 안에서 `credentialRepository.findByUser_Id(userId).ifPresent(credential -> { String refreshToken = tokenCrypto.decrypt(...); googleCalendarOAuthClient.revokeRefreshToken(userId, refreshToken); })`로 Google revoke 엔드포인트(`https://oauth2.googleapis.com/revoke`) HTTP POST를 호출한 뒤, 같은 트랜잭션 안에서 `clearGoogleLayer(userId)`(`credentialRepository.deleteByUser_Id`+`busyDayRepository.deleteByUser_Id`)와 `user.setGoogleCalendarConnected(false)`를 실행한다. 1차 A-1은 정확히 같은 파일의 `connect()`/`syncUser()`에서 "DB 트랜잭션이 외부 HTTP 호출을 감싸는" 패턴을 Critical로 지적해 고쳤다 — 지금 `connect()`는 전혀 `@Transactional`이 아니고, `syncUserInternal()`(사실상 `syncUser()`의 본체)도 트랜잭션 없이 실행되며 DB 쓰기만 `GoogleCalendarSyncPersistenceService`의 짧은 `@Transactional`에 위임한다. 하지만 같은 클래스의 세 번째 public 메서드인 `disconnect()`는 1차 A-1의 "문제"·"개선 방법" 어디에도 언급되지 않았고, 실제로 리팩토링되지 않은 채 원래 패턴 그대로 남아 있다. `grep -n "@Transactional" user/googlecalendar user/service`(단, `readOnly=true`·이미 분리된 persistence 서비스 제외) 결과, 이 도메인 전체에서 "외부 HTTP를 감싸는 쓰기 트랜잭션"은 `GoogleCalendarService.java:143`(`disconnect()`) 단 한 곳만 남아 있다.
- **왜 문제인가**: `docs/audits/auth/audit-round2.md` A-1이 지적한 것과 완전히 같은 패턴이다 — "1차에서 Critical로 고쳤다고 검증한 트랜잭션 경계 문제가, 같은 기능의 다른 진입점(다음 단계·자매 메서드)에 형태만 바꿔 재도입돼 있다." `revokeRefreshToken()`(`GoogleCalendarOAuthClient.java:240-256`)은 공용 `RestClient`(connect 3초·read 5초 타임아웃, auth A-3에서 적용된 공용 설정)를 쓰므로 Google 서버가 느려지면 최대 8초 가까이 DB 커넥션을 붙잡아 둘 수 있다. `disconnect()`는 사용자가 명시적으로 호출하는 API라 `connect()`/`syncUser()`(30분 폴링으로 반복 실행)만큼 상시 재발하지는 않지만, 여전히 커넥션 풀이 유한하다는 전제는 동일하고, Google 장애 시 해제 요청 하나가 다른 API(trip 등)에까지 영향을 줄 수 있는 리스크 클래스는 1차 A-1이 "고쳤다"고 결론 낸 것과 동일하다.
- **개선 방법**: `connect()`와 동일한 순서로 재구성한다 — (1) `credentialRepository.findByUser_Id(userId)` 조회 → 복호화 → `revokeRefreshToken()` 호출을 트랜잭션 **밖**에서 먼저 끝내고, (2) DB 쓰기(credential·busy_day 삭제, flag=false)는 짧은 `@Transactional`에 위임한다. 마침 `GoogleCalendarSyncPersistenceService.applyPermanentAuthFailure(userId)`가 이미 "credential·busy_day 삭제 + flag=false"를 정확히 같은 순서로 수행하는 짧은 `@Transactional` 메서드로 존재하고(권한 영구 실패 시 재사용 중, 단위·통합 테스트로 이미 검증됨) — `disconnect()`도 이 메서드를 그대로 재사용하면 새 트랜잭션 로직을 새로 작성하지 않고도 고칠 수 있다(재사용 시 이름이 "권한 영구 실패"라는 의미와 어긋나면, 같은 골격의 private 헬퍼로 감싸 `disconnect()`용 진입점만 하나 추가하는 것도 방법). `disconnect()`가 그대로 담당해야 하는 것: (a) 연동 여부 확인 후 409(`GOOGLE_CALENDAR_NOT_CONNECTED`) — 이 검증 자체는 HTTP 호출 전이므로 순서 유지, (b) credential 조회·복호화·`revokeRefreshToken()`(HTTP, 트랜잭션 밖), (c) persistenceService 호출(짧은 트랜잭션), (d) 최신 `User`를 다시 읽어 `userSummaryService.toSummary()` 반환.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/service/GoogleCalendarService.java` (+ 필요 시 `GoogleCalendarSyncPersistenceService.java`에 재사용 메서드 정리), `test/.../user/googlecalendar/service/GoogleCalendarServiceTest.java`
- **예상 변경 라인 수**: 20~30줄
- **위험도**: Low~Medium — `applyPermanentAuthFailure`가 이미 같은 DB 쓰기를 수행하는 검증된 메서드라 트랜잭션 로직을 새로 작성할 필요가 없다. `revokeRefreshToken()`은 내부에서 이미 모든 예외를 삼키는 best-effort 설계라(`GoogleCalendarOAuthClient.java:247-255`) 호출 위치를 트랜잭션 밖으로 옮겨도 예외 전파 시맨틱은 변하지 않는다.
- **테스트 영향도**: `GoogleCalendarServiceTest`의 `disconnect_keepsPersonalSchedules`/`disconnect_whenNotConnected_throws409` 2개는 Mockito mock 기반이라 대부분 통과 예상되나, `credentialRepository`/`busyDayRepository`에 대한 직접 `verify(...).deleteByUser_Id(...)` 어서션이 `persistenceService`에 대한 `verify()`로 바뀌어야 한다(1차 A-1이 `connect()`/`syncUser()` 테스트를 갱신한 것과 동일 패턴). 실제 트랜잭션 경계는 `GoogleCalendarSyncPersistenceIntegrationTest`의 `applyPermanentAuthFailure_deletesGoogleLayerAndClearsFlagInRealDb`가 이미 실제 MySQL로 커버 중이라(재사용 시) 추가 통합 테스트 없이도 신뢰 가능.
- **예상 효과**: 1차 A-1이 목표했던 "Google 서버 지연·장애가 DB 커넥션 풀 고갈로 번지지 않게 한다"는 목표를 `GoogleCalendarService`의 public 메서드 3개(`connect`/`syncUser`/`disconnect`) 전체에 대해 완결시킨다. 기존 검증된 메서드 재사용으로 중복 코드도 함께 줄어든다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `GoogleCalendarSyncScheduler` — 전용 테스트 부재

- **Priority**: Medium
- **Category**: Test Coverage
- **문제**: 1차 B-6은 `GoogleCalendarController`에 테스트가 없던 공백을 메웠지만, 같은 `googlecalendar` 패키지의 `GoogleCalendarSyncScheduler`(30분마다 연동 유저 전체를 조회해 유저별 hash 지터로 6개 슬롯 중 하나만 처리하며 `syncUser()` 호출)는 이번 재검토에서도 대응 테스트 파일이 존재하지 않는다(`find src/test -iname "*GoogleCalendarSyncScheduler*"` 결과 0건). `shouldSkipThisCycle()`의 해시 지터 분산 로직, "한 유저의 `syncUser()` 예외가 다음 유저 처리를 막지 않는다"는 for 루프 안 try-catch 안전장치 모두 커버되지 않는다.
- **왜 문제인가**: 지터 로직(`Math.floorMod(userId.hashCode(), JITTER_SLOT_COUNT)` 기반 슬롯 분산)은 외부 의존성 없는 순수 계산이라 단위 테스트로 검증하기 쉬운데도 비어 있어, 향후 `JITTER_SLOT_COUNT`나 `SYNC_INTERVAL_MS`를 바꿀 때 회귀를 잡을 안전망이 없다. 이 스케줄러의 핵심 장애 격리 장치(한 유저 예외가 다음 유저를 막지 않음)도 테스트로 고정돼 있지 않아, 누군가 실수로 try-catch를 for 루프 밖으로 옮기는 회귀가 생겨도 잡히지 않는다.
- **개선 방법**: `GoogleCalendarSyncSchedulerTest`를 신규 작성 — `GoogleCalendarService`를 mock으로 주입해 (1) 연동 유저 목록에 대해 지터 슬롯에 해당하는 유저만 `syncUser()`가 호출되는지(결정적 `cycle` 값과 UUID를 골라 검증), (2) 한 유저의 `syncUser()`가 예외를 던져도 이후 유저의 `syncUser()`가 계속 호출되는지 검증.
- **API 영향**: No Impact (테스트만 추가)
- **예상 변경 파일**: (신규) `src/test/java/com/tripfit/tripfit/user/googlecalendar/scheduler/GoogleCalendarSyncSchedulerTest.java`
- **예상 변경 라인 수**: ~80줄(신규)
- **위험도**: Low
- **테스트 영향도**: 커버리지 공백 해소, 기존 코드 변경 없음
- **예상 효과**: 스케줄러의 부하 분산·장애 격리 로직에 대한 회귀 방지. `GoogleCalendarControllerTest`(1차 B-6)로 이미 채운 Controller 계층 공백에 이어, `googlecalendar` 패키지의 남은 테스트 사각지대를 해소.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`GoogleCalendarSyncScheduler.syncConnectedUsers()`의 유저별 순차 처리 + Spring 기본 단일 스레드 `@Scheduled` 풀** — 이번 라운드에서 신규로 확인한 사실: 저장소 전체에 커스텀 `TaskScheduler`/`spring.task.scheduling.pool.size` 설정이 없다(`common/config/SchedulingConfig.java`는 `@EnableScheduling`/`@EnableAsync`만 선언, `application.yml`에도 scheduling pool 설정 없음). Spring Boot가 이 경우 기본 제공하는 `@Scheduled` 실행 스레드 풀 크기는 1이다. 이 저장소의 `@Scheduled` 메서드는 `GoogleCalendarSyncScheduler`(본 도메인)·`ScheduleReminderBatch`(notification)·`TripHomeScheduler`(trip) 3개뿐인데, 전부 같은 단일 스레드를 공유한다. `GoogleCalendarSyncScheduler`는 for 루프 안에서 유저마다 `syncUser()`(내부적으로 최대 9청크 순차 HTTP, 청크당 최대 8초 타임아웃)를 동기 호출하므로, 이 스케줄러 실행 중에는 다른 두 스케줄러(알림 리마인드·trip 홈 유지보수)가 지연될 수 있다는 구조적 리스크가 실제로 존재한다. **다만** 이 문제의 해결책(전용 `TaskScheduler` 빈 또는 pool size 상향)은 `common/config/SchedulingConfig.java`(공용 인프라, 3개 도메인이 얽힘)에 있어 "user 도메인 무손실 리팩토링" 라운드의 파일 범위 밖이다. 현재 MVP 트래픽 규모(연동 유저 소수, jitter로 사이클당 1/6만 처리)에서는 실질적 지연이 감지되지 않아 지금 당장 고칠 정도는 아니라고 판단해 이번 라운드에서는 다루지 않는다 — 아래 15번에 Later 제안으로 남긴다.
- **`GoogleCalendarBusyDayRepository.deleteByUser_Id`/`GoogleCalendarCredentialRepository.deleteByUser_Id` — 파생 delete가 벌크 DELETE가 아니라 "조회 후 개별 삭제"로 동작함** — `auth/audit-round2.md` C가 `RefreshTokenRepository`/`AppleCredentialRepository`/`GoogleLoginCredentialRepository`에 대해 이미 같은 판단(대상 행 수가 적어 `@Modifying` 벌크 삭제로 바꿔도 실익이 낮음)을 내렸다. `GoogleCalendarBusyDay`도 user당 sparse(busy 있는 날만) 최대 며칠치 행이고 `GoogleCalendarCredential`은 `@OneToOne`이라 최대 1행이라, 같은 이유로 지금 손대지 않는다 — 저장소 전체 일관성 유지 차원에서도 그대로 둔다.
- **1차 `audit.md`의 C 3개 재검증 결과 — 전부 여전히 유효, 변경 없음**:
  - `GoogleCalendarSyncScheduler.syncConnectedUsers()`가 매 사이클 전체 연동 유저를 `List<User>`로 메모리 적재 — 코드 변경 없이 확인, MVP 규모에서 여전히 문제없음. 지금 페이징으로 바꾸면 YAGNI 위반.
  - `GoogleCalendarOAuthClient.revokeRefreshToken()`의 `REVOKE_URL + "?token=" + refreshToken` 문자열 결합 — 여전히 동일 코드. `auth` 도메인의 동일 패턴(`GoogleOAuthClient.revokeRefreshToken()`)이 `auth/audit-round2.md`에서도 재검증을 통과했고(opaque 토큰이라 인코딩 위험 낮음), 같은 논리가 이 도메인에도 그대로 적용된다.
  - `UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED`가 `trip/service/TripServiceSupport.requireActive()`에서 여전히 그대로 throw됨(`:245-251`) — `user` 도메인 소유 ErrorCode를 다른 도메인이 던지는 구조는 1차와 동일하게 남아 있으나, ErrorCode 변경은 감사 절대 제약(API 계약 100% 유지) 범위 밖이라 이번에도 다루지 않는다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **1차 `audit.md`의 D 4개 재검증 결과 — 전부 여전히 유효, 오히려 근거가 더 강해짐**:
  - `User` 엔티티가 `equals()`/`hashCode()`를 오버라이드하지 않음 — 코드 변경 없음, UUID PK 엔티티에서 기본 identity 비교를 쓰는 편이 여전히 JPA 모범사례에 가깝다.
  - `UserLookupService`를 인라인하지 않음 — 1차 감사 때보다 재사용처가 오히려 더 늘었다. 이번 라운드에서 확인한 신규 소비처만도 `GoogleCalendarSyncPersistenceService`·`UserWithdrawalPersistenceService`(1차 A-1/A-2로 신설된 persistence 빈 2곳) 2곳이 추가로 이 서비스를 호출한다 — "User 조회 SSOT"라는 가치가 1차 때보다 더 명확히 증명됐다.
  - `GoogleCalendarBusyMapper`를 Spring 빈으로 바꾸지 않음 — 여전히 `private` 생성자 + `static` 메서드만 있는 의존성 없는 순수 변환 로직. 코드 변경 없음, 판단 유지.
  - `UserProfileService`/`UserSummaryService`/`UserLookupService`/`UserWithdrawalService`를 하나의 `UserService`로 합치지 않음 — 1차 이후 오히려 `GoogleCalendarSyncPersistenceService`·`UserWithdrawalPersistenceService`라는 책임이 명확한 신규 서비스가 2개 더 늘어 "조회/저장/조율을 분리한다"는 이 도메인의 기존 설계 방향이 더 강화됐다. 지금 합치면 A-1/A-2가 어렵게 분리해 둔 트랜잭션 경계를 다시 흐릴 위험이 있다.

## 15. 백엔드 아키텍처 개선 제안

- **Resilience — Google Calendar 외부 호출 Circuit Breaker**: `grep` 재확인 결과 `resilience4j`·Circuit Breaker 관련 의존성·구현 전무 — 1차와 동일하게 **Later** 유지. A-1(round2)까지 적용되면 `GoogleCalendarService`의 모든 외부 HTTP 호출이 트랜잭션 밖에서 실행돼, 이후 Circuit Breaker 도입 시 대상 지점이 더 단순해진다(참고, Now로 격상할 사유는 아님).
- **Async Processing — 탈퇴 시 provider revoke 4종 `@Async`화**: 1차와 동일하게 **Later** 유지(YAGNI, 명확한 트래픽 근거 없음). 다만 이번 라운드에서 확인한 갱신 사항: `common/config/SchedulingConfig.java`에 `@EnableAsync`가 이미 활성화돼 있고, `notification/service/NotificationEventListener`가 이미 `@Async` 메서드 6개를 실사용 중이다 — 즉 인프라 준비 비용은 1차 예상보다 낮다는 점만 참고로 추가한다. 판단(Later) 자체는 바뀌지 않는다.
- **Concurrency — `GoogleCalendarSyncScheduler` 다중 인스턴스 중복 실행 방지(분산 락)**: 배포 구조가 여전히 단일 EC2 인스턴스임을 재확인 — 1차와 동일하게 **Later** 유지.
- **신규 — Concurrency: `@Scheduled` 기본 단일 스레드 풀을 3개 도메인 스케줄러가 공유**: 위 C에서 지적한 대로, `GoogleCalendarSyncScheduler`·`ScheduleReminderBatch`(notification)·`TripHomeScheduler`(trip)가 커스텀 `TaskScheduler` 없이 Spring Boot 기본 풀(크기 1)을 공유한다. `GoogleCalendarSyncScheduler`의 유저별 순차 동기 HTTP 호출이 길어지면 다른 두 스케줄러의 실행이 밀릴 수 있다. **장단점**: 장점 — 전용 `TaskScheduler` 빈(`spring.task.scheduling.pool.size` 상향 또는 `ThreadPoolTaskScheduler` 빈 등록)으로 스케줄러 간 상호 차단을 제거. 단점 — 여러 스케줄러가 동시에 도는 걸 전제하므로 각 스케줄러의 idempotency·동시성 안전성을 재검토해야 함(현재 `GoogleCalendarSyncScheduler`는 유저별 독립 처리라 안전해 보이지만 검증 필요). **구현 난이도**: 낮음(설정 추가 수준). **Now/Later/Never**: **Later** — 현재 MVP 트래픽에서 실질적 지연이 관측되지 않았고, 수정 파일이 `common/config/SchedulingConfig.java`라 `user` 도메인 단독 결정 사안이 아니다. `common`(cross-cutting) 도메인 감사 시 3개 스케줄러 소유 도메인과 함께 논의해 도입 권장.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
