# Notification Architecture Audit — 2026-08-05

## 범위

- 패키지: `com.tripfit.tripfit.notification` (`config`, `controller`, `domain`, `dto`, `event`, `exception`, `repository`, `scheduler`, `service`)
- 테스트: `src/test/java/com/tripfit/tripfit/notification/**` (controller 3개, scheduler 1개, service 4개)
- 교차 참조(감사만, 수정 대상 아님): `trip/service/TripCommandService`·`TripRecommendationService`(이벤트 발행부), `trip/repository/TripMemberRepository`(`findByTripIdAndDeletedAtIsNull` — 이미 `JOIN FETCH tm.user`로 N+1 없음 확인), `user/domain/User`(`notificationEnabled`, `displayName()`), `user/repository/UserRepository`(`findIdsForScheduleReminder`), `auth/security/AppConfig`(`FcmProperties` Bean 등록)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP
- main 24개 파일, test 8개 파일 전수 검토 (`FcmProperties`, `FirebaseConfig`, `DeviceTokenController`, `NotificationController`, `DeviceType`, `LandingType`, `NotificationHistory`, `NotificationType`, `UserDeviceToken`, `DeviceTokenRegisterRequest`, `NotificationResponse`, `AllMembersSubmittedEvent`, `ScheduleReminderEvent`, `TripConfirmCanceledEvent`, `TripConfirmedEvent`, `TripInfoChangedEvent`, `TripJoinCompletedEvent`, `NotificationErrorCode`, `NotificationHistoryRepository`, `UserDeviceTokenRepository`, `ScheduleReminderBatch`, `DeviceTokenService`, `FcmService`, `NotificationEventListener`, `NotificationQueryService`)

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `DeviceTokenService.unregisterToken()` — 동일 클래스에서 이미 한 번 겪은 `ObjectOptimisticLockingFailureException` 레이스가 여기엔 방치돼 있고, 불필요한 쿼리도 1번 더 실행됨

- **Priority**: High
- **Category**: Architecture / Performance
- **문제**: `unregisterToken()`(`DeviceTokenService.java:44-53`)은 `existsByTokenAndUser_Id(token, userId)`(`:49`)로 존재를 확인한 뒤 `deleteByTokenAndUser_Id(token, userId)`(`:52`)를 호출한다. 이 `deleteByTokenAndUser_Id`는 `UserDeviceTokenRepository.java:19`에 `@Modifying`/`@Query` 없이 순수 파생 삭제 메서드로 선언돼 있다 — Spring Data JPA는 이런 `deleteBy...` 파생 메서드를 "먼저 SELECT로 대상을 찾고, 각 엔티티에 `entityManager.remove()`를 호출"하는 방식으로 구현한다. 반면 같은 파일의 `deleteByTokenIn`(`:25-27`)은 정확히 이 문제 때문에 `@Modifying @Query("DELETE ...")` 벌크 쿼리로 이미 바뀌어 있고, 그 이유가 바로 위 주석(`:21-24`)에 남아 있다: "엔티티 단위 delete(파생 쿼리 기본 동작)는 삭제 직전 row 존재를 전제해 이미 다른 트랜잭션이 지운 토큰이면 `ObjectOptimisticLockingFailureException`을 던져 호출자 트랜잭션 전체를 롤백시켰다." `deleteByTokenAndUser_Id`는 이 수정이 적용되지 않은 채 동일한 파생 삭제 패턴으로 남아 있다.
- **왜 문제인가**: 로그아웃(`unregisterToken`)과 `FcmService.deleteInvalidTokens()`(무효 토큰 자동 정리, `FcmService.java:98-110` → `deleteByTokenIn`)가 같은 토큰을 거의 동시에 건드릴 수 있는 구조다(로그아웃 직후에도 이미 발송 중이던 알림의 무효 토큰 정리가 뒤늦게 도착할 수 있음). `existsByTokenAndUser_Id`가 true를 반환한 직후, 다른 트랜잭션이 같은 row를 먼저 지우고 커밋하면 `deleteByTokenAndUser_Id`의 내부 `remove()`가 이미 사라진 row에 대해 실행돼 `ObjectOptimisticLockingFailureException`이 던져진다. 이 예외는 `NotificationErrorCode`로 매핑되지 않으므로 `GlobalExceptionHandler`가 처리하지 못하고 500으로 새어나간다. 또한 정상 경로에서도 `exists`(SELECT) + 파생 delete 내부 SELECT + DELETE로 최소 2~3회 DB 왕복이 필요해, 같은 목적을 1회 쿼리로 처리할 수 있는데 중복 조회하고 있다(checklist 6·7·8).
- **개선 방법**: `deleteByTokenAndUser_Id`를 `deleteByTokenIn`과 동일한 패턴으로 `@Modifying @Query("DELETE FROM UserDeviceToken t WHERE t.token = :token AND t.user.id = :userId")`로 바꾸고 반환 타입을 `long`(삭제된 행 수)으로 변경한다. `DeviceTokenService.unregisterToken()`은 `existsByTokenAndUser_Id` 호출을 제거하고, `deleteByTokenAndUser_Id`의 반환값이 0이면 `NOTIFICATION_TOKEN_NOT_FOUND`를 던지도록 바꾼다. 벌크 쿼리는 대상이 0건이어도 조용히 통과하므로(기존 `deleteByTokenIn` 주석과 동일 근거) 레이스 자체가 사라지고, 쿼리 횟수도 2~3회에서 1회로 줄어든다.
- **API 영향**: No Impact — 204/404 HTTP 응답, `ErrorCode`, 요청/응답 바디 전부 동일. 레이스가 아주 드물게 발생했을 때만 500 대신 정상 404가 나가게 되는 차이뿐(더 정확한 계약 준수).
- **예상 변경 파일**: `notification/repository/UserDeviceTokenRepository.java`, `notification/service/DeviceTokenService.java`, `src/test/java/com/tripfit/tripfit/notification/service/DeviceTokenServiceTest.java`
- **예상 변경 라인 수**: ~20줄
- **위험도**: Low — 이미 같은 파일에 존재하는 `deleteByTokenIn` 패턴을 그대로 복제하는 수준의 변경.
- **테스트 영향도**: `DeviceTokenServiceTest.unregisterToken_notOwned_throwsNotFound`/`unregisterToken_owned_deletesToken`(둘 다 `existsByTokenAndUser_Id` 스텁 중심) 재작성 필요 — `deleteByTokenAndUser_Id` 반환값(0 vs 1)으로 스텁 전환. `DeviceTokenControllerTest`는 서비스 목을 쓰므로 영향 없음.
- **예상 효과**: 동일 클래스 안에서 이미 한 번 수정한 버그 패턴의 재발 방지, `unregisterToken` 호출당 DB 왕복 1회 감소.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `@Schema(description)`에 `(D5)`/`(D7)`/`(BR-NOTI-XXX)` 스펙 ID가 그대로 노출됨 — `spring-boot-java.md` "전 어노테이션 공통 금지" 규칙 위반

- **Priority**: Medium
- **Category**: Readability / Convention
- **문제**: `spring-boot-java.md`의 "OpenAPI 설명 어노테이션 (전부)" 절은 `@Schema(description)`을 포함한 모든 설명 어노테이션에 "GitHub 이슈 번호·BR/스펙 ID(`BR-USER-007`, `D5` 등) 금지"를 명시한다. 그런데 `NotificationHistory.java:30`(`...알림센터 조회·읽음 상태를 포함한다(D5)`), `UserDeviceToken.java:28`(`...재할당될 수 있다(D7)`)·`:39`(`...재등록 시 재할당(D7)`), `NotificationType.java:5`(`(BR-NOTI-001~005·009)`)와 6개 enum 상수 전부(`:7,10,13,16,19,22`, 각각 `(BR-NOTI-00X)`)에 이 스펙 ID들이 그대로 남아 있다. 이 문자열은 실제로 `/v3/api-docs`·Swagger UI에 노출된다(`NotificationSwaggerSchemaTest`가 검증하는 바로 그 스키마).
- **왜 문제인가**: 이 도메인은 이미 `@Schema`를 잘못 써서 사고가 난 전례(raw `SuccessResponse` 타입 지정으로 스키마가 통째로 사라진 사고, `harness-workflow.md` STOP §1-6에 "NotificationController 사고 사례"로 명시)가 있는 도메인이다. Swagger 문서 독자(프론트·신규 개발자)는 `D7`·`BR-NOTI-002` 같은 내부 스펙 식별자의 의미를 알 수 없고, 스펙 문서 쪽에서 D-번호가 바뀌거나 BR 번호가 재편되면(예: `docs/decisions/` 개편) 코드 쪽 문자열은 조용히 stale해진다.
- **개선 방법**: 각 `@Schema(description)`에서 `(D5)`/`(D7)`/`(BR-NOTI-XXX)` 괄호 부분만 제거하고 나머지 설명(도메인 의미)은 그대로 유지한다. 예: `"사용자 기기별 FCM 디바이스 토큰. 재로그인 시 소유자가 재할당될 수 있다(D7)"` → `"사용자 기기별 FCM 디바이스 토큰. 재로그인 시 소유자가 재할당될 수 있다"`. 스펙 근거가 필요하면 `docs/specs/notification/notification.md`에 이미 D1~D12 표로 정리돼 있으므로 중복 불필요.
- **API 영향**: No Impact — `description` 문자열만 편집, 필드·타입·enum 값·에러코드·경로는 변경 없음. `Breaking-Change-Reason` 트레일러 대상(필드/enum 값/ErrorCode/경로 변경)에 해당하지 않음.
- **예상 변경 파일**: `notification/domain/NotificationHistory.java`, `notification/domain/UserDeviceToken.java`, `notification/domain/NotificationType.java`
- **예상 변경 라인 수**: ~10줄(문자열 편집)
- **위험도**: Low
- **테스트 영향도**: `NotificationSwaggerSchemaTest`는 enum 값 목록(`containsExactlyInAnyOrder`)만 검증하고 description 문자열은 검증하지 않으므로 영향 없음.
- **예상 효과**: 실제 배포되는 Swagger 문서에서 내부 스펙 식별자 노출 제거, 코드-스펙 재편 시 stale 문자열 방지, 프로젝트 규칙 준수.

### B-2. `TripConfirmCanceledEvent.java` 클래스 주석이 실제 구현과 반대로 "미발행"이라 적혀 있음

- **Priority**: Low
- **Category**: Cleanup / Legacy
- **문제**: `TripConfirmCanceledEvent.java:5`의 주석은 `"// BR-NOTI-009 — 방장이 확정 취소 시 참여자(방장 제외)에게 발송. #13 취소 API 구현 후 해당 서비스에서 발행 예정(현재 미발행)"`이라고 돼 있다. 하지만 실제로는 `TripRecommendationService.unconfirm()`(`trip/service/TripRecommendationService.java:260`)에서 이미 `applicationEventPublisher.publishEvent(new TripConfirmCanceledEvent(tripId))`를 호출하고 있고, `NotificationEventListener.onTripConfirmCanceled()`도 구현·테스트(`NotificationEventListenerTest.onTripConfirmCanceled_notifiesMembersExcludingOwner`)까지 존재한다. `docs/specs/notification/notification.md` 변경 이력(2026-07-31)에도 "`#13`이 이미 Closed·구현 완료 상태인데 본 문서가 미구현으로 stale하게 남아 있었음"을 정정한 기록이 있다 — 정작 이벤트 record 파일의 주석만 그 정정이 반영되지 않았다.
- **왜 문제인가**: 이 주석만 보고 "BR-NOTI-009는 아직 미발행"이라고 오판하면 harness-workflow.md STOP §1-5("구현 상태 보고 전 코드 우선 확인")가 경계하는 바로 그 실수(스펙/주석 문구만 보고 미구현이라 단정)로 이어질 수 있다.
- **개선 방법**: 주석을 실제 발행 지점에 맞게 수정한다. 예: `"// BR-NOTI-009 — 방장이 확정 취소 시 참여자(방장 제외)에게 발송. TripRecommendationService.unconfirm에서 커밋 후 발행"` — 다른 5개 이벤트 파일(`TripConfirmedEvent` 등)과 동일한 문체로 통일.
- **API 영향**: No Impact — 주석만 수정, 코드 동작 변경 없음.
- **예상 변경 파일**: `notification/event/TripConfirmCanceledEvent.java`
- **예상 변경 라인 수**: 1줄
- **위험도**: Low
- **테스트 영향도**: 없음.
- **예상 효과**: 코드-구현 상태 정합성 회복, 향후 이 파일만 보고 오판하는 것 방지.

### B-3. `NotificationEventListener.requireTrip()` — 근거 없는 `NoSuchElementException`

- **Priority**: Low
- **Category**: Exception 구조
- **문제**: `requireTrip()`(`NotificationEventListener.java:156-158`)은 `tripRepository.findById(tripId).orElseThrow()`로, 인자 없는 `orElseThrow()`는 메시지가 없는 `NoSuchElementException`을 던진다. 이 메서드는 6개 리스너 메서드 중 5개(`onTripJoinCompleted`·`onAllMembersSubmitted`·`onTripInfoChanged`·`onTripConfirmed`·`onTripConfirmCanceled`)에서 호출된다.
- **왜 문제인가**: 이 호출은 `@Async` 컨텍스트 안에서 실행되므로 예외가 발생해도 호출자(이벤트 발행 트랜잭션)에는 전파되지 않고 Spring 기본 `AsyncUncaughtExceptionHandler`가 로그만 남긴다 — 즉 유일한 관측 수단이 로그인데, 메시지 없는 `NoSuchElementException`은 어떤 `tripId`에서 실패했는지 스택트레이스만으로는 바로 드러나지 않는다.
- **개선 방법**: `orElseThrow(() -> new IllegalStateException("Trip not found for notification dispatch: " + tripId))`처럼 `tripId`를 포함한 메시지로 바꾼다. HTTP로 노출되는 경로가 아니므로 `TripFitException`/`ErrorCode`까지 갈 필요는 없음(API 영향 없음 원칙에도 더 부합).
- **API 영향**: No Impact — 비동기 내부 예외 메시지만 변경, 어떤 API 응답도 거치지 않음.
- **예상 변경 파일**: `notification/service/NotificationEventListener.java`
- **예상 변경 라인 수**: ~3줄
- **위험도**: Low
- **테스트 영향도**: 없음 — 현재 이 실패 경로를 검증하는 테스트가 없고(Trip은 이벤트 발행 시점 이미 커밋된 상태라 실제로 없어질 가능성이 낮음), 메시지 추가는 기존 성공 경로 테스트에 영향 없음.
- **예상 효과**: 이 경로가 실제로 실패했을 때(예: 데이터 정합성 문제) 로그만으로 원인 파악이 가능해짐.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`FcmServiceTest`가 실패(예외 흡수) 경로 1개만 검증하고, 성공 경로(멀티캐스트 payload 구성 — `id`/`landingType`/`tripId` data 필드, 무효 토큰 자동 삭제 `deleteInvalidTokens`, 500건 배치 분할)는 전혀 테스트되지 않음** — `FcmService`는 이 도메인에서 가장 로직이 복잡한 클래스인데 커버리지가 얕다. 다만 이번 라운드는 구조 리팩터링 감사이고 신규 테스트 작성은 별도 작업 성격이 강해, 사용자 승인 후 별도 이슈로 다루는 게 적절해 보여 이번엔 추가하지 않는다.
- **`NotificationEventListenerTest`에 `onScheduleReminder()` 테스트가 없음** — 나머지 5개 핸들러는 전부 테스트가 있는데 이 메서드만 빠져 있다. 위와 같은 이유(신규 테스트 추가는 이번 구조 리팩터 범위 밖)로 이번엔 보류.
- **6개 리스너 메서드가 `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)` 3종 어노테이션 스택을 그대로 반복** — 커스텀 메타 어노테이션(예: `@AfterCommitAsync`)으로 묶으면 줄 수는 줄어들지만, 이 조합을 쓰는 곳이 `NotificationEventListener` 한 클래스뿐이라 재사용 이득이 없고, 오히려 "이 메서드가 정확히 어떤 시점에 어떤 트랜잭션으로 실행되는지"를 알려면 커스텀 어노테이션 정의까지 따라가야 해서 표준 Spring 어노테이션을 그대로 쓰는 지금보다 가독성이 떨어진다 — YAGNI로 보류.
- **`FcmProperties`(`notification/config/`)의 실제 Spring Bean 등록(`@EnableConfigurationProperties`)이 `notification` 패키지가 아니라 `auth/security/AppConfig.java`에서 이뤄짐** — 얼핏 도메인 경계가 흐려 보이지만, `JwtProperties`·`OAuthProperties`·`SocialTokenCryptoProperties`도 전부 같은 `AppConfig`에 함께 등록되는 프로젝트 전역 컨벤션이다. `notification`만 분리하면 오히려 나머지 3개 도메인과 컨벤션이 어긋나므로, `AppConfig` 자체를 도메인별로 쪼갤지는 이 도메인 단독이 아니라 프로젝트 전체 논의가 선행돼야 할 사안 — 이번 라운드에서는 보류.
- **`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`의 실제 "커밋 후에만 실행되는지" 타이밍 자체를 검증하는 통합 테스트가 없음** — 현재 단위 테스트는 리스너 메서드를 직접 호출해 비즈니스 로직(수신자 필터링·메시지 문구·게이트 적용)만 검증하고, 프록시가 실제로 커밋 이후에만 호출을 위임하는지는 프레임워크 신뢰 영역으로 남겨둔 것으로 보인다. `@SpringBootTest`로 이 타이밍까지 검증하려면 비용이 크고, 6개 메서드 모두 동일한 표준 조합을 쓰므로 도메인마다 프레임워크 동작 자체를 재검증할 실익이 낮다 — 보류.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`FcmService.sendBatch()`의 광범위한 `catch (Exception exception)`(`FcmService.java:89`)** — `FirebaseMessagingException`뿐 아니라 `@Lazy` 빈 초기화 실패(`BeanCreationException` 등 임의 `RuntimeException`)까지 전부 흡수해야 호출자의 `REQUIRES_NEW` 트랜잭션이 롤백되지 않고, 이미 저장한 `NotificationHistory`가 함께 사라지지 않는다 — 이미 상세 주석(`:90-93`)과 전용 테스트(`FcmServiceTest.sendMulticast_whenFirebaseMessagingThrowsRuntimeException_doesNotPropagate`)로 이 의도가 명시돼 있다. 예외 타입을 좁히면 오히려 이력 저장까지 함께 롤백되는 회귀가 생긴다.
- **`dispatch()`가 `ScheduleReminderEvent` 경로에서 이미 `notification_enabled=true`로 필터링된 유저 목록(`UserRepository.findIdsForScheduleReminder`)을 `User::isNotificationEnabled`(`NotificationEventListener.java:176`)로 다시 필터링** — 중복처럼 보이지만 `dispatch()`는 6개 이벤트 전부가 거쳐 가는 BR-USER-005 게이트의 단일 지점(D10: "예외 없이 전체 이벤트가 게이트를 따름")이다. 호출부마다 "이미 필터링했는지"를 개별 판단해 재필터링을 생략하면, 앞으로 새 알림 이벤트를 추가할 때 게이트 적용을 깜빡하는 버그가 생기기 쉽다 — 의도적 중복.
- **`ScheduleReminderBatch.BATCH_SIZE`(`:20`)와 `FcmService.BATCH_SIZE`(`:27`)가 둘 다 500이지만 하나로 묶지 않음** — 전자는 이벤트 발행 단위(수신자 배치로 트랜잭션 처리량 분산), 후자는 Firebase 멀티캐스트 API의 하드 리밋이라 값의 출처가 다르다. `FcmService`는 어떤 크기의 토큰 목록이 들어와도 내부에서 다시 500 단위로 재분할하므로 두 상수를 하나로 합쳐도 안전성엔 영향이 없지만, 우연히 같은 값을 쓰는 두 클래스를 공용 상수로 묶으면 "Firebase 리밋이 바뀌면 배치 크기도 같이 바뀌어야 한다"는 잘못된 결합을 암시하게 된다 — 분리 유지가 맞다.
- **`NotificationHistory`/`UserDeviceToken`이 무분별한 `@Setter` 없이 `markRead()`/`reassign()` 같은 의도된 도메인 메서드만 노출** — 이미 좋은 구조다(다른 도메인 감사에서 지적된 "클래스 레벨 `@Setter` 남용" 패턴과 대비됨). 바꿀 이유가 없다.
- **`FirebaseConfig.firebaseMessaging()`가 `@Lazy` 빈으로 지연 초기화** — 키 미설정 로컬·테스트 환경에서도 앱이 부팅되게 하는 의도된 설계(`SocialTokenCrypto`와 동일 패턴, 주석에 명시)다. 즉시 초기화로 바꾸면 로컬 개발자가 FCM 서비스 계정 키 없이는 앱을 아예 못 띄우게 된다 — 바꾸지 않음.

## 15. 백엔드 아키텍처 개선 제안

- **Resilience (FCM 발송 재시도)**: `FcmService.sendBatch()`는 현재 예외를 흡수만 하고 재시도가 없어, 일시적 네트워크 장애 시 `NotificationHistory`는 저장되지만 실제 푸시는 유실된다. `build.gradle`에는 Resilience4j 등 재시도/서킷브레이커 라이브러리가 없고, 이 도메인의 발송 규모(여행방당 최대 10명, 정기 리마인드는 월 2회 배치)가 크지 않아 지금 도입 비용 대비 효과가 낮다 — **Later**(실제 푸시 유실 관련 사용자 불만이 접수되면 그때 Resilience4j `@Retry`를 `sendEach()` 주변에 도입 검토).
- **Monitoring**: FCM 실패가 `log.warn`으로만 남고 실패율 지표가 없다. `spring-boot-starter-actuator`는 이미 의존성에 있지만 Micrometer 레지스트리(Prometheus 등)는 프로젝트 전체에 아직 없다. notification 도메인 하나만 먼저 지표를 넣으면 관측 스택이 파편화된다 — **Later**(프로젝트 전체 Monitoring 도입 결정과 함께 진행).
- **Event Architecture / Async**: 이미 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` + `REQUIRES_NEW`로 "여행방 트랜잭션 커밋"과 "알림 발송"의 경계를 잘 분리하고 있다. Kafka/RabbitMQ 같은 외부 메시지 브로커 도입은 현재 단일 인스턴스 배포(`deploy/app`)와 이 트래픽 규모에서 운영 복잡도만 늘린다 — **Never**(현재 규모에서는 in-process `@Async`로 충분).
- **Database**: `notification_history` 조회(`GET /api/v1/notifications`)는 스펙(D9)에서 이미 `(user_id, sent_at)` 인덱스를 명시하고 있고, 7일 윈도우 제한으로 스캔 범위가 작다 — 추가 제안 없음.

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다. C/D는 이번 라운드에서 수정하지 않습니다.
