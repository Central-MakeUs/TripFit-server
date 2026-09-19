# notification Refactor Log

## 2026-08-08 — 스펙-코드 drift 해소: `notification_history` 인덱스 누락 반영

`docs/specs/notification/notification.md`(D9)·`docs/architecture/erd.md`가 이미 `(user_id, sent_at)` 인덱스를 명시하고 있었는데, 실제 `NotificationHistory` 엔티티에는 반영돼 있지 않았던 drift를 발견해 반영. 백엔드 성능 개선 검토 중 발견(신규 결정 아님 — 이미 승인된 스펙을 코드가 놓친 케이스).

### 쉽게 설명하면 (`plain-language-reporting.md`)

알림센터 목록 조회(`GET /api/v1/notifications`)는 "이 사용자의 최근 7일 알림을 최신순으로" 가져오는데, 이때 DB가 빠르게 찾을 수 있도록 미리 준비해두는 색인(인덱스)이 있어야 합니다. 설계 문서에는 이 인덱스가 있어야 한다고 이미 적혀 있었는데, 실제 코드에는 빠져 있었어요 — 지금 데이터량에서는 체감되는 지연은 아니지만, 사용자·알림이 늘어날수록 매번 전체를 훑어야 해서 느려질 수 있는 구조였습니다. 문서에 이미 있던 내용을 코드에 채워 넣기만 한 것이라 새로운 결정은 없었습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| 1 | `NotificationHistory`에 `@Table(indexes = @Index(columnList = "user_id, sent_at"))` 추가 | `NotificationHistory.java` |

### 검증 결과

- `./gradlew compileJava` — 통과
- `./gradlew test --tests "com.tripfit.tripfit.notification.*" --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"` — 통과
- API 계약 변경 없음(인덱스는 OpenAPI 스키마에 노출되지 않음) — oasdiff 재실행 불필요

## 2026-08-05 — 2차 라운드 A-1·A-2·B-1 반영

2차 감사([`audit-round2.md`](audit-round2.md)) 기준 A(반드시 수정) 2건, B(유지보수성) 1건 전부 반영. 사용자 승인: "A-1·A-2·B-1 전부".

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **A-1**: 알림센터 목록을 불러올 때, 알림 하나하나마다 "이 알림이 어느 여행방 알림인지" 이름을 확인하는 과정에서 매번 DB에 별도로 물어보고 있었어요. 최근 7일치 알림에 서로 다른 여행방이 여러 개 섞여 있으면, 그 방 개수만큼 DB에 왕복 질문을 더 하게 되는 구조였습니다. 이번에 "알림 목록 + 관련 여행방 이름"을 한 번의 질문으로 같이 받아오도록 바꿔서, 여러 방에 속한 사용자일수록 체감하던 지연을 줄였어요.
- **A-2**: 앱이 알림 받을 기기 정보(FCM 토큰)를 서버에 등록할 때, 네트워크가 불안정해서 클라이언트가 같은 요청을 짧은 시간에 두 번 보내는 경우가 있을 수 있어요. 이런 경우 드물게 서버가 "이미 있는 값인데 또 넣으려 했다"는 내부 오류로 500 에러를 돌려줄 수 있는 여지가 있었습니다(흔치는 않지만 재시도할 때마다 매번 성공을 보장하지 못했던 셈이에요). 이제 그런 충돌이 나면 서버가 스스로 "아, 이미 다른 요청이 방금 넣었구나"라고 판단해서 그 기존 값에 정상적으로 맞춰 넣도록(재할당) 고쳐서, 재시도할 때도 항상 성공하도록 만들었어요.
- **B-1**: 기능 변화는 없고 코드 정리예요 — 토큰 값이 비어 있는지 확인하는 코드가 등록·해제 두 메서드에 거의 똑같이 복사돼 있던 걸 한 곳으로 모았습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `NotificationHistoryRepository.findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc`를 파생 쿼리에서 `@Query` + `LEFT JOIN FETCH h.trip`으로 변경 — 알림센터 목록 조회 시 여행방 이름(roomName) 접근으로 인한 N+1 제거. 메서드 시그니처·반환 타입·정렬·호출부는 동일 유지 | `NotificationHistoryRepository.java` |
| A-2 | `DeviceTokenService.registerToken()`의 신규 토큰 저장 분기를 `save()` → `saveAndFlush()`로 바꿔 즉시 INSERT를 실행시키고, `DataIntegrityViolationException`(동시 등록 레이스로 인한 UNIQUE 위반) 발생 시 `findByToken` 재조회 후 `reassign()`으로 수렴하도록 `saveNewToken()` 헬퍼 신설 | `DeviceTokenService.java`, `DeviceTokenServiceTest.java`(레이스 폴백 테스트 추가) |
| B-1 | `registerToken()`/`unregisterToken()`에 중복돼 있던 토큰 blank 검증을 `requireNonBlankToken(String)` private 헬퍼로 통합 | `DeviceTokenService.java` |

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test` (전체) — 통과, 실패 0건
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (diff 0건)

**결론: notification 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목

`audit-round2.md`의 C 2개(`DeviceTokenController.unregister()`의 FCM 토큰 쿼리 파라미터 노출 — 엔드포인트 계약 변경이라 별도 스펙 승인 필요, 1차 C 3건 재확인), D 3개(1차 D 5건 재확인, `FcmProperties` Bean 위치 재확인) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round2.md` 해당 절 참고.

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

## 2026-08-27 — Round 3 (SOLID/OOP 중심) A/B 없음, 반영 사항 없음

감사([`audit-round3.md`](audit-round3.md)) 기준 **A 항목 없음, B 항목 없음** — 구조적으로 반영할 대상을 찾지 못했다. 코드 변경 없음.

### 쉽게 설명하면 (`plain-language-reporting.md`)

이번엔 다른 도메인 3차 감사에서 자주 나온 문제 유형(안 쓰는데 남아 있는 기능, 어디서도 안 불리는 죽은 코드)을 이 도메인에서도 똑같이 찾아봤는데, 이미 다 깨끗한 상태였어요. 특히 2차 감사 때 제안했던 "토큰 등록이 동시에 겹치면 에러 날 수 있다"는 문제는, 그 뒤 실제 운영에서 비슷한 에러가 한 번 발생한 걸 계기로 이미 더 확실한 방식(DB 자체가 "있으면 갱신, 없으면 새로 만들기"를 한 번에 처리하도록)으로 고쳐져 있었고, 그 사고를 재현하는 테스트도 이미 마련돼 있었어요.

### 확인한 것 (구현 없음)

- 2차 A-2 제안(저장 실패 시 재조회·재할당 방식)은 실제로는 더 강한 방식(`UserDeviceTokenRepository.upsertToken()` 네이티브 원자적 upsert)으로 대체돼 반영됨 — 별도 조치 불필요, 기록만 갱신.
- `NotificationHistory`/`UserDeviceToken` 엔티티에 미사용 setter 없음, Repository 메서드 전부 실사용처 있음 — 삭제 대상 없음.
- SOLID/OOP 렌즈로 짚어본 3개 지점(이벤트 리스너의 6종 이벤트 구독, `FcmService` 인터페이스 부재, `NotificationResponse.from()`의 연관관계 탐색) 모두 근거 있는 현재 구조 — `audit-round3.md` C 참고.

### 검증

- 코드 변경이 없어 `./gradlew test`·`oasdiff` 재실행 불필요(직전 커밋 상태와 동일).

### 남겨둔 C/D 항목 (Round 3)

`audit-round3.md`의 C 3개(1·2차 재검증 + 신규 SOLID 렌즈 재확인 2건), D 3개(1·2차 재확인 + `NotificationEventListener`의 crossdomain Repository 직접 주입 유지) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round3.md` 해당 절 참고.

### Later 후속 제안 (audit-round3.md §15, 상태 변화 없음)

1·2차 §15의 6개 제안(FCM 재시도·실패율 지표·메시지 브로커·ETag·토큰 URL 노출·Idempotency-Key) 재확인 결과 상태 변화 없음 — 판단 그대로 유지.
