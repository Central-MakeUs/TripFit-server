# notification Refactor Log

## 2026-08-05 — A-1, B-1~B-3 반영

`audit.md`의 A(반드시 수정) 1건, B(유지보수성) 3건을 전부 반영. C/D는 이번 라운드에서 보류.

### 반영 항목

- **A-1 (High, Architecture/Performance)**: `DeviceTokenService.unregisterToken()`이 `existsByTokenAndUser_Id`(SELECT) 확인 후 파생 `deleteByTokenAndUser_Id`(엔티티 단위 delete)를 호출하던 것을 제거. 같은 리포지토리의 `deleteByTokenIn`이 정확히 같은 이유(로그아웃과 FCM 무효 토큰 정리가 같은 토큰을 거의 동시에 지울 수 있어, 엔티티 단위 delete는 이미 지워진 row에 대해 `ObjectOptimisticLockingFailureException`을 던져 트랜잭션 전체를 롤백시킴)로 이미 벌크 쿼리로 바뀌어 있던 패턴을 `deleteByTokenAndUser_Id`에도 동일 적용 — `@Modifying @Query("DELETE ...")`로 바꾸고 반환 타입을 `long`(삭제된 행 수)으로 변경. `existsByTokenAndUser_Id`는 이 호출부가 유일한 사용처였으므로 함께 삭제(레거시 즉시 삭제 원칙). `DeviceTokenService.unregisterToken()`은 존재 확인 없이 바로 삭제를 시도하고 반환값이 0이면 `NOTIFICATION_TOKEN_NOT_FOUND`를 던지도록 변경 — DB 왕복이 최소 2~3회에서 1회로 감소.
- **B-1 (Medium, Convention)**: `NotificationHistory`·`UserDeviceToken`·`NotificationType`의 `@Schema(description)` 9곳에 남아 있던 내부 스펙 식별자(`(D5)`, `(D7)`, `(BR-NOTI-001~005·009)`)를 제거 — `spring-boot-java.md` "OpenAPI 설명 어노테이션 공통 금지" 규칙(이슈 번호·BR/스펙 ID 금지) 준수. 도메인 의미를 담은 나머지 설명 문장은 그대로 유지.
- **B-2 (Low, Cleanup/Legacy)**: `TripConfirmCanceledEvent.java`의 클래스 주석이 "#13 취소 API 구현 후 발행 예정(현재 미발행)"이라고 stale하게 남아 있던 것을, 실제 발행 지점(`TripRecommendationService.unconfirm()`에서 커밋 후 발행)을 가리키도록 수정.
- **B-3 (Low, Exception 구조)**: `NotificationEventListener.requireTrip()`의 인자 없는 `orElseThrow()`(메시지 없는 `NoSuchElementException`)에 `tripId`를 포함한 메시지를 추가 — `@Async` 컨텍스트에서 실패해도 로그만으로 원인 파악 가능하도록.

### 변경 파일

```
 src/main/java/.../notification/repository/UserDeviceTokenRepository.java |  9 ++++----
 src/main/java/.../notification/service/DeviceTokenService.java           |  6 ++---
 src/main/java/.../notification/domain/NotificationHistory.java           |  2 +-
 src/main/java/.../notification/domain/UserDeviceToken.java               |  4 +--
 src/main/java/.../notification/domain/NotificationType.java              | 12 +++++-----
 src/main/java/.../notification/event/TripConfirmCanceledEvent.java       |  2 +-
 src/main/java/.../notification/service/NotificationEventListener.java    |  5 +++-
 src/test/java/.../notification/service/DeviceTokenServiceTest.java       |  4 +--
 8 files changed
```

### 검증

- `./gradlew test --tests "com.tripfit.tripfit.notification.*"` — 통과
- `./gradlew test --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"` → `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` — **"No breaking changes to report"**
- `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` — notification 변경으로 인한 diff 없음(엔티티·enum 상수 레벨 `@Schema` 설명은 애초에 생성된 OpenAPI 문서에 별도로 노출되지 않음). 유일한 diff는 trip 도메인 A-2(`PUT`→`PATCH` 문구 수정, 별도 라운드)뿐
- `./gradlew test` (전체, ArchitectureTest 포함) — 통과

### 남겨둔 항목 (C/D — 이번 라운드 보류)

- **C**: `FcmServiceTest`·`NotificationEventListenerTest.onScheduleReminder`의 얕은 테스트 커버리지(신규 테스트 작성은 구조 리팩터 범위 밖), 리스너 6개 메서드의 3종 어노테이션 반복(재사용처 1곳뿐이라 메타 어노테이션 추출 시 오히려 가독성 저하), `FcmProperties` Bean 등록이 `notification` 패키지가 아니라 `auth/security/AppConfig`(다른 Properties와 동일 컨벤션), `@TransactionalEventListener(AFTER_COMMIT)` 타이밍 자체를 검증하는 통합 테스트 부재(프레임워크 신뢰 영역) — `audit.md` C 참고
- **D**: `FcmService.sendBatch()`의 광범위한 `catch (Exception)`(REQUIRES_NEW 트랜잭션·이력 저장 보존 목적, 의도적), `dispatch()`의 `notification_enabled` 재필터링(BR-USER-005 게이트 단일 지점 유지 목적, 의도적 중복), `ScheduleReminderBatch`/`FcmService`의 동일한 `BATCH_SIZE=500` 상수를 통합하지 않음(출처가 다른 값이라 결합 방지), `FirebaseConfig`의 `@Lazy` 초기화(로컬 개발 환경 부팅 보장) — `audit.md` D 참고
