# User Architecture Audit — 2026-08-05

`com.tripfit.tripfit.user` 및 `com.tripfit.tripfit.user.googlecalendar` 패키지를 대상으로 진행한 1차 아키텍처 감사 문서다. 반드시 수정해야 하는 A 항목 2건과 유지보수성 리팩토링 B 항목 6건(참고 C 4건, 비권장 D 4건)을 도출했다. 도출된 A/B 8개 항목은 코드에 반영 완료되었다(상세 내역은 `refactor-log.md` 참고).

## 범위

- 패키지: `com.tripfit.tripfit.user` (`client`, `controller`, `domain`, `dto`, `exception`, `repository`, `service`) + `com.tripfit.tripfit.user.googlecalendar` (`client`, `controller`, `domain`, `dto`, `exception`, `repository`, `scheduler`, `service`)
- 제외: `user/schedule/**` (별도 "user-schedule" 도메인, 별도 감사)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `.claude/skills/refactor-audit/references/audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP, `spring-boot-java.md`, `testing.md`
- main 21개 파일, test 10개 파일 전수 검토

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `GoogleCalendarService.connect()`/`syncUser()` — DB 트랜잭션이 Google 외부 API 다중(최대 ~9회 청크) 호출을 감싸고 있음

- **Priority**: Critical
- **Category**: Architecture / Performance
- **문제**: `GoogleCalendarService.connect()`(`GoogleCalendarService.java:83-150`)와 `syncUser()`(`:172-190`)는 모두 `@Transactional`이며, 그 안에서 `syncUserInternal()`(`:236-271`)을 호출한다. `syncUserInternal()`은 `resolveAccessToken()`(외부 HTTP — 필요 시 `refreshAccessToken`)에 이어 `googleCalendarOAuthClient.queryFreeBusy(...)`를 호출하는데, 이 메서드(`GoogleCalendarOAuthClient.java:110-126`)는 `FREE_BUSY_CHUNK_DAYS = 90`(`:52`) 단위로 최대 today+2년 윈도우를 잘라 **순차적으로 여러 번** Google `freeBusy` API를 호출한다(2년 윈도우 기준 최대 ~9회). 즉 `connect()` 한 번 호출로 DB 커넥션을 쥔 채 최대 9회의 순차 외부 HTTP 왕복을 기다릴 수 있다.
- **왜 문제인가**: `GoogleCalendarSyncScheduler.syncConnectedUsers()`(`:41-62`)는 **30분마다** `userRepository.findByIsGoogleCalendarConnectedTrue()`로 연동된 전체 유저를 조회해 각 유저마다 `syncUser()`를 순차 호출한다 — 유저 수가 늘어날수록 이 스케줄러가 반복적으로 DB 커넥션을 쥔 채 Google API를 여러 번 왕복하는 상황이 30분마다 재발한다. `docs/audits/auth/audit.md` A-1과 동일한 패턴(HikariCP 등 유한한 커넥션 풀을 외부 provider 응답 대기에 붙잡아 둠)이며, 청크 반복 호출 특성상 노출 시간이 auth의 단일 호출보다 길다. Google API 지연·장애 시 이 도메인과 무관한 다른 API(trip, notification 등)까지 커넥션 고갈로 전파될 수 있다.
- **개선 방법**: 외부 HTTP 호출(`exchangeAuthorizationCode`, `fetchGoogleAccountEmail`, `queryFreeBusy`, `refreshAccessToken`)을 트랜잭션 **밖**에서 먼저 수행하고, DB 쓰기(`credentialRepository.save`, `busyDayRepository` upsert/delete)만 짧은 `@Transactional`로 묶는다. auth 도메인의 `AuthService`/`AuthLoginPersistenceService` 분리 패턴(이미 이 코드베이스에 적용된 검증된 패턴)을 그대로 이식할 수 있다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/service/GoogleCalendarService.java` (신규 persistence 전용 헬퍼 빈 필요 가능)
- **예상 변경 라인 수**: 60~90줄
- **위험도**: Medium~High — 트랜잭션 경계 재구성. `GoogleCalendarServiceTest`는 Mockito mock 기반이라 실제 트랜잭션 범위를 검증 못 함.
- **테스트 영향도**: 기존 단위 테스트는 대부분 통과 예상되나(연동 검증은 mock 기반), 통합 테스트로 sync 흐름 재확인 필요.
- **예상 효과**: Google API 지연·장애가 DB 커넥션 풀 고갈로 번지는 리스크 제거 — 30분 주기 스케줄러가 반복 실행된다는 점에서 auth의 로그인 경로보다 상시 노출 빈도가 높음.
- **구현 상태**: ✅ 완료(2026-08-05) — `connect()`/`syncUser()`가 더 이상 `@Transactional`이 아니다. Google 서버 통신(코드 교환·access token 갱신·freeBusy 조회)을 먼저 끝내고, DB 쓰기만 신규 `GoogleCalendarSyncPersistenceService`(`@Transactional`)로 분리했다 (`user/googlecalendar/service/GoogleCalendarService.java`, `GoogleCalendarSyncPersistenceService.java`). B-2(불필요한 `save()` 제거)도 이 재구성으로 함께 해결됨. Mockito 단위 테스트로는 못 잡는 실제 트랜잭션 프록시·dirty checking 동작은 `GoogleCalendarSyncPersistenceIntegrationTest`(실제 MySQL)로 재확인함. 상세: [`refactor-log.md`](refactor-log.md).

### A-2. `UserWithdrawalService.withdraw()` — 단일 트랜잭션 안에서 최대 4개 외부 provider revoke HTTP 호출을 순차 실행

- **Priority**: High
- **Category**: Architecture / Performance
- **문제**: `withdraw()`(`UserWithdrawalService.java:83-116`)는 `@Transactional`이며, 그 안에서 `revokeGoogleCalendarIfConnected(userId)`(`:96`, Google Calendar revoke HTTP), `googleLoginCredentialService.revokeAndDeleteIfPresent(userId)`(`:97`, Google 로그인 credential revoke HTTP), `unlinkKakaoIfProvider(user)`(`:98`, Kakao unlink HTTP), `appleCredentialService.revokeAndDeleteIfPresent(userId)`(`:99`, Apple revoke HTTP)를 순서대로 호출한다. 각각 best-effort(예외를 삼킴)이지만 모두 같은 열린 DB 트랜잭션 안에서 순차 실행된다.
- **왜 문제인가**: 4개 provider 호출이 각각 타임아웃(공용 `RestClient` — connect 3초/read 5초, auth A-3에서 이미 적용됨)까지 걸릴 경우 탈퇴 API 하나가 DB 커넥션을 최대 ~20초 이상 붙잡아 둘 수 있다. 탈퇴는 사용자가 실행하는 일반 API 경로이므로, provider 장애 시 다른 탈퇴 요청·전체 API 커넥션 풀에 영향을 줄 수 있다.
- **개선 방법**: A-1과 동일한 방향 — provider revoke 호출들을 먼저 트랜잭션 밖(또는 `@Async`)에서 best-effort로 실행한 뒤, DB 쓰기(soft delete·hard delete)만 별도 짧은 트랜잭션으로 처리한다. best-effort 시맨틱은 이미 try-catch로 구현돼 있어 트랜잭션 롤백에 의존하지 않으므로 순서 재배치의 리스크가 낮다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/service/UserWithdrawalService.java`
- **예상 변경 라인 수**: 40~60줄
- **위험도**: Medium — 트랜잭션 경계 재구성, cascade 순서(방 나가기/삭제 → revoke → hard delete → soft delete) 유지 필요.
- **테스트 영향도**: `UserWithdrawalServiceTest`는 Mockito mock이라 호출 순서·존재 여부(`verify`)는 그대로 통과 예상. idempotent 재탈퇴 테스트(`withdraw_whenAlreadyWithdrawn_isIdempotentNoOp`)도 함께 재확인 필요.
- **예상 효과**: 탈퇴 API의 DB 커넥션 점유 시간 단축, provider 장애 시 전체 API 영향 축소.
- **구현 상태**: ✅ 완료(2026-08-05) — `withdraw()`가 provider revoke 4종을 먼저 끝낸 뒤, cascade·hard delete·soft delete를 신규 `UserWithdrawalPersistenceService`(`@Transactional`)로 분리했다 (`user/service/UserWithdrawalService.java`, `UserWithdrawalPersistenceService.java`). 실제 트랜잭션 동작은 `UserWithdrawalPersistenceIntegrationTest`(실제 MySQL)로 재확인함. 상세: [`refactor-log.md`](refactor-log.md).

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `UserSummaryService.markAllFreeIfNoSchedules`/`markAllFreeIfSchedulesCleared` — 완전히 동일한 구현의 중복 메서드

- **Priority**: Medium
- **Category**: Cleanup
- **문제**: `markAllFreeIfNoSchedules(User)`(`UserSummaryService.java:75-79`)와 `markAllFreeIfSchedulesCleared(User)`(`:89-93`)는 본문이 `if (!hasPreSchedule(user.getId())) { user.setAllFree(true); }`로 완전히 동일하다. 전자는 `TripJoinService`/`TripCommandService`(Skip+0행 시), 후자는 `ScheduleService`(일정 CLEAR 후)에서 각각 호출된다.
- **왜 문제인가**: 같은 로직을 뜻이 다른 이름으로 두 번 유지해야 해서, 정책이 바뀌면(예: "전부 free" 판정 조건 변경) 두 메서드를 동시에 고쳐야 하는데 물리적으로 떨어진 3개 호출부(trip 2곳, schedule 1곳)에서 어느 메서드가 어느 이벤트에 대응하는지 추적하기 번거롭다.
- **개선 방법**: 하나의 메서드(예: `markAllFreeIfNoSchedules`)로 통합하고 나머지 호출부(`ScheduleService.java:136`)를 그 메서드로 교체한다. 두 이름이 서로 다른 트리거를 문서화하는 역할을 했다면 통합 메서드의 `//` 주석에 두 트리거를 모두 적는다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/service/UserSummaryService.java`, `user/schedule/service/ScheduleService.java`
- **예상 변경 라인 수**: ~15줄
- **위험도**: Low
- **테스트 영향도**: `UserSummaryServiceTest`의 `markAllFreeIfNoSchedules_*` 2개는 그대로 유지, `ScheduleServiceTest`의 관련 호출부만 메서드명 변경.
- **예상 효과**: 중복 제거, 정책 변경 시 단일 지점 수정.
- **구현 상태**: ✅ 완료(2026-08-05) — `markAllFreeIfNoSchedules`로 통합, `ScheduleService.deleteRegular()` 호출부를 갱신했다.

### B-2. `GoogleCalendarService` — JPA 관리 엔티티에 대한 불필요한 명시적 `save()` 반복

- **Priority**: Low
- **Category**: JPA / Cleanup
- **문제**: `syncUserInternal()`의 성공 분기(`credential.markSynced(); credentialRepository.save(credential);` — `:249-250`)와 실패 분기(`credential.markSyncError(...); credentialRepository.save(credential);` — `:268-269`), 그리고 `resolveAccessToken()`(`credential.updateAccessTokenCache(...); ... credentialRepository.save(credential);` — `:292-297`) 모두 이미 같은 `@Transactional` 범위 안에서 조회돼 관리 중(managed)인 `credential` 엔티티에 대해 명시적 `save()`를 다시 호출한다. `connect()`에서의 최초 `save()`(신규 엔티티 영속화에 필요)와 달리, 이후 호출들은 dirty checking으로 이미 커밋 시점에 자동 반영된다.
- **왜 문제인가**: 매번 `SimpleJpaRepository.save()`는 이미 영속 상태인 엔티티에 대해서도 내부적으로 신규 여부 판별 로직을 거치므로 불필요한 오버헤드다. 또한 "왜 여기서도 save()를 또 부르는지" 신규 개발자가 혼동할 수 있다.
- **개선 방법**: 관리 상태가 확실한 지점(`syncUserInternal` 성공/실패 분기, `resolveAccessToken`)의 명시적 `save()` 호출을 제거하고 dirty checking에 위임한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/service/GoogleCalendarService.java`
- **예상 변경 라인 수**: ~6줄
- **위험도**: Low — `GoogleCalendarServiceTest`의 `connect_setsFlagAndSyncs`는 `verify(credentialRepository, atLeastOnce()).save(...)`로 `atLeastOnce()`를 쓰므로 최초 신규 저장 1회만 남아도 통과.
- **테스트 영향도**: 기존 테스트 대부분 영향 없음(정확한 호출 횟수를 검증하는 테스트는 없음).
- **예상 효과**: 미세한 성능 개선, 코드 의도 명확화.
- **구현 상태**: ✅ 완료(2026-08-05) — A-1 트랜잭션 재구성 과정에서 자연히 해결됨(성공/실패 분기가 managed 엔티티에 대해 dirty checking에 위임하도록 재작성됨).

### B-3. `GoogleCalendarBusyMapper.applyInterval()` — 도달 불가능한 죽은 분기

- **Priority**: Low
- **Category**: Dead Code
- **문제**: `applyInterval()`(`GoogleCalendarBusyMapper.java:44-72`)에서 `end`가 자정이면 `endDate = endDate.minusDays(1)`로 미리 보정한다(`:55-57`). 그런데 반복문 안의 `if (date.equals(end.toLocalDate()) && dayEnd.equals(LocalTime.MIDNIGHT)) { dayEnd = LocalTime.of(23,59,59,999999999); }`(`:65-66`)은 `end.toLocalDate()`가 **보정 전 원본** 날짜를 가리키는 반면, 반복문의 `date`는 보정된(하루 줄어든) `endDate`까지만 순회하므로 이 조건이 참이 되는 경우가 없다.
- **왜 문제인가**: 실행에 영향은 없지만(같은 결과가 바로 위 삼항연산자의 `else` 분기로 이미 처리됨 — `GoogleCalendarBusyMapperTest.mapIntervalsToDays_allDayBusy_marksAllSlots`로 확인), 죽은 분기가 남아있으면 이 메서드를 다음에 수정하는 개발자가 "자정 보정이 두 군데에서 일어난다"고 오해하기 쉽다.
- **개선 방법**: 해당 `if` 블록(`:65-66`)을 삭제한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/service/GoogleCalendarBusyMapper.java`
- **예상 변경 라인 수**: ~3줄
- **위험도**: Low
- **테스트 영향도**: 없음 — 기존 `GoogleCalendarBusyMapperTest` 2개 테스트로 회귀 없음 확인 가능.
- **예상 효과**: 가독성 개선, 혼동 제거.
- **구현 상태**: ✅ 완료(2026-08-05) — 해당 죽은 `if` 블록 삭제.

### B-4. `GoogleCalendarOAuthClient.queryFreeBusyChunk()` — `ZoneId.of("Asia/Seoul")` 인라인 중복 생성 + import 누락

- **Priority**: Low
- **Category**: Cleanup / Performance
- **문제**: `queryFreeBusyChunk()`(`GoogleCalendarOAuthClient.java:147-148`)는 같은 문장 안에서 `java.time.ZoneId.of("Asia/Seoul")`을 fully-qualified 이름으로 **두 번** 생성한다. 같은 패키지의 `GoogleCalendarService`(`:43`)와 `GoogleCalendarBusyMapper`(`:17`)는 이미 `private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");` 상수를 두고 재사용한다.
- **왜 문제인가**: 매 청크 호출(최대 9회/sync)마다 `ZoneId.of()` 조회를 반복하는 낭비이자, 같은 기능 패키지 안에서 스타일이 일관되지 않다(다른 두 클래스는 상수화, 이 클래스만 인라인+fully-qualified).
- **개선 방법**: `import java.time.ZoneId;` 추가 후 `private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");` 필드로 추출해 재사용.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/client/GoogleCalendarOAuthClient.java`
- **예상 변경 라인 수**: ~5줄
- **위험도**: Low
- **테스트 영향도**: 없음 — `GoogleCalendarOAuthClientTest`는 시각 계산 로직과 무관.
- **예상 효과**: 미세한 성능·가독성·일관성 개선.
- **구현 상태**: ✅ 완료(2026-08-05) — `SEOUL` 상수로 추출해 재사용하도록 변경.

### B-5. `KakaoUnlinkClient` — 구조화 로깅 패턴 미적용(같은 도메인 내 관례 불일치)

- **Priority**: Medium
- **Category**: Cleanup / Security(observability)
- **문제**: `KakaoUnlinkClient.unlink()`의 실패 로그(`KakaoUnlinkClient.java:45`)는 `log.warn("Kakao unlink failed for socialId={}", socialId, exception)`로 평문 로그를 남긴다. 반면 같은 "소셜 연동 실패" 성격의 `GoogleCalendarOAuthClient`·`GoogleCalendarService`·`GoogleCalendarSyncScheduler`는 전부 `SocialIntegrationLog`/`SocialLogContext`로 구조화 로그를 남기며, `logback-spring.xml`(`:11-19`)은 `auth.oauth`·`auth.service`·`user.googlecalendar` 3개 패키지만 `STRUCTURED_JSON` appender를 적용한다 — `user.client`(KakaoUnlinkClient 패키지)는 대상이 아니다.
- **왜 문제인가**: 탈퇴 시 Kakao unlink 실패는 다른 provider revoke 실패와 동일하게 "소셜 연동 실패" 범주인데도 Loki에서 `provider`/`action` 필드로 필터링해 조회할 수 없다 — 장애 원인 추적 시 이 경로만 사각지대가 된다.
- **개선 방법**: `KakaoUnlinkClient`도 `SocialIntegrationLog.warn(log, SocialLogContext.of(SocialProvider.KAKAO, ...).withUserId(...), ...)` 패턴으로 통일하고, `logback-spring.xml`의 `STRUCTURED_JSON` 대상 로거 목록에 `com.tripfit.tripfit.user.client`를 추가한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/client/KakaoUnlinkClient.java`, `src/main/resources/logback-spring.xml`, (필요 시) `common/logging/SocialIntegrationAction.java`에 unlink 액션 상수 추가
- **예상 변경 라인 수**: ~15줄
- **위험도**: Low
- **테스트 영향도**: `KakaoUnlinkClientTest`의 `unlink_providerError_doesNotThrow`/`unlink_providerUnreachable_doesNotThrow`는 로그 포맷과 무관하게 통과.
- **예상 효과**: 소셜 연동 장애 관측 가능성(observability) 일관성 확보.
- **구현 상태**: ✅ 완료(2026-08-05) — `SocialIntegrationLog`/`SocialLogContext` 패턴 적용, `logback-spring.xml`에 `user.client` 패키지 추가.

### B-6. `GoogleCalendarController` — 컨트롤러 레벨 테스트 부재

- **Priority**: Medium
- **Category**: Test Coverage
- **문제**: `user/controller/UserController`는 `UserControllerTest`(MockMvc standalone) + `UserSecurityIntegrationTest`(JWT 통합) 2종 테스트가 있지만, `user/googlecalendar/controller/GoogleCalendarController`는 대응하는 `GoogleCalendarControllerTest`가 존재하지 않는다(`connect`/`disconnect` 2개 엔드포인트, `@Valid` 검증, 502/409 에러 응답 모두 미검증).
- **왜 문제인가**: `ConnectGoogleCalendarRequest`의 `@NotBlank authorizationCode` 검증 실패 시 400 응답, `GoogleCalendarErrorCode` 매핑(502/409)이 실제로 Controller 레이어에서 올바르게 envelope 변환되는지 서비스 단위 테스트만으로는 보장되지 않는다.
- **개선 방법**: `UserControllerTest`와 동일한 MockMvc standalone 패턴으로 `GoogleCalendarControllerTest`를 신규 작성 — `connect` 성공/검증실패, `disconnect` 성공/409 케이스.
- **API 영향**: No Impact (테스트만 추가)
- **예상 변경 파일**: (신규) `src/test/java/com/tripfit/tripfit/user/googlecalendar/controller/GoogleCalendarControllerTest.java`
- **예상 변경 라인 수**: ~120줄(신규)
- **위험도**: Low
- **테스트 영향도**: 커버리지 공백 해소.
- **예상 효과**: Controller-Service-ErrorCode 연결 지점의 회귀 방지.
- **구현 상태**: ✅ 완료(2026-08-05) — `GoogleCalendarControllerTest` 신규 작성 (connect 성공·검증실패 400·502, disconnect 성공·409).

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`GoogleCalendarSyncScheduler.syncConnectedUsers()`가 매 30분 전체 연동 유저를 `List<User>`로 메모리에 적재** — 현재 유저 규모(MVP)에서는 문제없으나, 유저 수가 크게 늘면 페이징·스트리밍 조회로 바꿔야 할 수 있다. 지금 바꾸면 YAGNI 위반이자 검증되지 않은 조기 최적화라 보류.
- **`GoogleCalendarOAuthClient.revokeRefreshToken()`의 URL 문자열 결합**(`REVOKE_URL + "?token=" + refreshToken`) — `auth/oauth/GoogleOAuthClient`에서 동일 패턴이 이미 auth 감사에서 "opaque provider 토큰이라 인코딩 위험 낮음, 변경 가치 낮음"으로 보류된 전례가 있다. 이 도메인에서도 동일 판단 적용, 일관성 유지 차원에서 지금 손대지 않는다.
- **`UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED`가 `trip/service/TripServiceSupport`에서 throw됨** — `user` 도메인 소유 ErrorCode를 다른 도메인이 던지는 구조라 `spring-boot-java.md`의 "도메인별 ErrorCode" 원칙과 약간 어긋나 보이지만, 이는 감사 절대 제약 #5(ErrorCode 변경은 A/B 범위 밖)에 해당해 이번 라운드 대상이 아니다 — 별도 참고로만 남긴다.
- **`build.gradle` 의존성 감사(카테고리 12) 미실시** — `user` 도메인만으로는 특정 라이브러리(예: `spring-boot-starter-web`, jackson 등)의 실사용 여부를 프로젝트 전체 관점에서 판단하기 어려워 이번 감사 범위에서 제외했다. 별도 전체 의존성 감사가 필요하면 도메인 단위가 아닌 프로젝트 전체 스캔으로 진행해야 한다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`User` 엔티티가 `equals()`/`hashCode()`를 오버라이드하지 않음** — UUID `@GeneratedValue` 엔티티에서 식별자 기반 `equals`를 직접 구현하면 신규(미영속) 엔티티 간 비교나 프록시 비교에서 함정이 생기기 쉽다. 현재처럼 기본 `Object` identity에 의존하는 편이 JPA 모범사례에 가깝고, 실제로 이 엔티티를 `Set`/`Map` 키로 쓰는 코드도 없어 바꿀 이유가 없다.
- **`UserLookupService`를 단일 메서드짜리 "과도한 추상화"로 보고 인라인하지 않음** — `spring-boot-java.md`가 이를 "User 조회 SSOT"로 명시했고, `auth`·`user`·`user/schedule`·`user/googlecalendar` 등 여러 도메인이 실제로 재사용 중이다(이번 감사에서만도 `UserProfileService`·`UserSummaryService`·`GoogleCalendarService`·`UserWithdrawalService` 4곳이 호출). 이미 가치를 증명한 패턴이라 YAGNI 위반이 아니다.
- **`GoogleCalendarBusyMapper`를 Spring 빈으로 바꾸지 않음** — 의존성 없는 순수 변환 로직(`private` 생성자 + `static` 메서드)이라 DI로 관리할 이유가 없다. 빈으로 승격하면 불필요한 컨테이너 관리 오버헤드만 추가된다.
- **`UserProfileService`/`UserSummaryService`/`UserLookupService`/`UserWithdrawalService`를 하나의 `UserService`로 합치지 않음** — 각각 프로필 수정, 요약/방입장 파생, ID 조회, 탈퇴 오케스트레이션이라는 명확히 다른 책임을 갖고 있다. 합치면 `auth` 감사에서 이미 지적된 God Service 패턴(감사 체크리스트 9번)이 재현된다.

## 15. 백엔드 아키텍처 개선 제안

### Resilience — Google Calendar 외부 호출 Circuit Breaker

- **왜 필요한지**: A-1에서 지적한 트랜잭션 재구성만으로는 Google API 자체 장애 시 반복 실패 호출을 막지 못한다. Resilience4j Circuit Breaker를 붙이면 Google 장애 시 스케줄러가 빠르게 실패 처리해 스레드 소모를 줄일 수 있다.
- **장단점**: 장점 — 장애 전파 최소화, 30분 주기 스케줄러가 장애 provider에 계속 재시도하며 자원을 낭비하는 것을 방지. 단점 — 새 의존성, half-open 임계값 튜닝 필요.
- **구현 난이도**: 중간
- **Now / Later / Never**: **Later** — A-1(트랜잭션 재구성)이 선행되면 실질 영향 범위가 크게 줄어 우선순위가 낮아진다. `auth` 감사에서도 동일 제안이 Later로 판단됐다.

### Async Processing — 탈퇴 시 provider revoke 4종 호출 비동기화

- **왜 필요한지**: A-2에서 지적한 대로 `UserWithdrawalService.withdraw()`가 순차 실행하는 Google Calendar/Google Login/Kakao/Apple revoke 4개는 모두 best-effort(실패해도 무시)라 `@Async`로 분리해도 시맨틱이 동일하다.
- **장단점**: 장점 — 탈퇴 API 응답 지연 감소. 단점 — `@Async` 도입 시 트랜잭션 경계·예외 처리 설계가 A-2와 맞물려야 함.
- **구현 난이도**: 낮음~중간(A-2와 함께 설계 시)
- **Now / Later / Never**: **Later** — A-2 트랜잭션 재구성이 먼저 이뤄져야 중복 설계를 피할 수 있다.

### Concurrency — `GoogleCalendarSyncScheduler` 다중 인스턴스 중복 실행 방지

- **왜 필요한지**: 현재 `@Scheduled(fixedRate)` + in-memory jitter(`shouldSkipThisCycle`)만으로 부하를 분산하는데, 이는 애플리케이션 인스턴스가 1개일 때만 유효하다. 배포 문서상 현재는 EC2 단일 인스턴스이지만, 향후 수평 확장 시 인스턴스마다 동일한 연동 유저 전체를 각자 sync하게 되어 Google API 호출이 인스턴스 수만큼 중복된다.
- **장단점**: 장점 — 분산 락(Shedlock 등)으로 중복 실행 방지. 단점 — Redis/DB 락 인프라 추가 필요.
- **구현 난이도**: 중간
- **Now / Later / Never**: **Later** — 현재 단일 인스턴스 배포 구조에서는 문제가 되지 않는다. 수평 확장이 실제로 결정될 때 도입.

## 승인 대기

~~사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.~~

**A/B (8개 항목)**: ✅ 2026-08-05 전부 반영 완료. 각 항목 **구현 상태** 줄 참고, 상세 변경 내역·검증 결과는 [`refactor-log.md`](refactor-log.md). C/D는 이번 라운드에서 수정하지 않았다(각 항목 사유 참고).
