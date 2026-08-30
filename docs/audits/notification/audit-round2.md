# Notification Architecture Audit — 2차 라운드 (2026-08-05)

## 범위

- 패키지: `com.tripfit.tripfit.notification` — `config`, `controller`, `domain`, `dto`, `event`, `exception`, `repository`, `scheduler`, `service` 전체 (main 24개 파일)
- 테스트: `src/test/java/com/tripfit/tripfit/notification/**` (controller 3개, scheduler 1개, service 4개) 전수 재검토
- 스펙: `docs/specs/notification/notification.md` (D1~D12, 변경 이력 전체 재확인)
- 교차 참조(수정 대상 아님): `trip/service/TripCommandService`·`TripRecommendationService`(이벤트 발행부 — 발행 지점 5곳 재확인, 변경 없음), `common/exception/GlobalExceptionHandler`(도메인 예외 매핑 확인)
- 감사자: 서브에이전트 (`Agent` 툴, 읽기 전용)
- 기준: `audit-checklist.md` 1~15항목, `harness-workflow.md` ⛔ STOP

### 1차 감사 대비

1차(`docs/audits/notification/audit.md`, `refactor-log.md`)는 A-1(`deleteByTokenAndUser_Id` 벌크 쿼리化)·B-1(`@Schema` 내부 스펙 ID 제거)·B-2(stale 주석 정정)·B-3(`orElseThrow()` 메시지)를 전부 반영 완료했고, 코드에서 실제로 확인했다(`UserDeviceTokenRepository.java`, `DeviceTokenService.java`, `NotificationHistory.java`, `UserDeviceToken.java`, `NotificationType.java`, `TripConfirmCanceledEvent.java`, `NotificationEventListener.java` 전부 반영된 상태 확인). 1차 C/D(테스트 커버리지 공백 3건, 3종 어노테이션 반복, `FcmProperties` Bean 위치, `sendBatch`의 광범위 catch, `dispatch`의 게이트 재필터링, `BATCH_SIZE` 미통합, `@Lazy` 초기화)는 근거를 재검토한 결과 상황 변화가 없어 그대로 유지가 타당하다 — 아래 D 섹션에 재확인 결과만 간단히 남긴다.

이번 라운드는 1차가 다루지 않은 **읽기 경로(`GET /api/v1/notifications`)의 JPA 페치 전략**과 **등록 경로(`registerToken`)의 동시성**이라는 새 시각에서 감사했다. 둘 다 1차 audit.md에 언급이 없다.

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `GET /api/v1/notifications` — `NotificationHistory.trip` LAZY 연관관계가 목록 조회마다 N+1을 유발

- **Priority**: High
- **Category**: Performance / JPA
- **문제**: `NotificationHistoryRepository.findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc`(`NotificationHistoryRepository.java:15-17`)는 파생 쿼리로, `trip` 연관관계에 대한 fetch join이 없다. `NotificationHistory.trip`(`NotificationHistory.java:47-49`)은 `FetchType.LAZY`다. `NotificationResponse.from()`(`NotificationResponse.java:37-48`)의 `history.getTrip() != null ? history.getTrip().getId() : null`(tripId)까지는 프록시의 식별자만 읽어 추가 쿼리가 없지만, 바로 다음 줄 `history.getTrip().getName()`(roomName, `:45`)은 프록시를 실제로 초기화시켜 **여행방 하나당 별도 SELECT**를 발생시킨다. 6개 알림 타입 중 5개(`JOIN_COMPLETED`·`ALL_MEMBERS_SUBMITTED`·`TRIP_INFO_CHANGED`·`TRIP_CONFIRMED`·`TRIP_CONFIRM_CANCELED`)가 `trip`을 갖고 있어(`SCHEDULE_REMINDER`만 null), 실사용 목록 대부분이 이 경로를 탄다.
- **왜 문제인가**: `NotificationQueryService.listRecent()`는 목록 API(호출 빈도가 높은 GET, 페이지네이션 없이 7일치 전체 반환)인데, 조회 결과에 서로 다른 여행방이 N개 섞여 있으면 N번의 추가 왕복이 발생한다. 트립당 최대 10명이라는 현재 규모에서도 사용자가 최근 7일 안에 여러 방에 속해 있으면 바로 체감되고, 서비스 성장(사용자당 동시 참여 방 수 증가)에 비례해 그대로 악화된다. `NotificationQueryServiceTest`(mock 기반)·`NotificationSwaggerSchemaTest`(스키마만 검증) 어느 쪽도 실제 Hibernate 프록시 초기화 경로를 타지 않아, 지금까지 어떤 테스트도 이 문제를 드러내지 못했다.
- **개선 방법**: 파생 쿼리를 동일 시그니처의 `@Query`로 바꾸고 `LEFT JOIN FETCH h.trip`을 추가한다 — `ManyToOne` 단일 연관관계라 카티전 곱 걱정 없이 그대로 적용 가능.
  ```java
  @Query("SELECT h FROM NotificationHistory h LEFT JOIN FETCH h.trip "
      + "WHERE h.user.id = :userId AND h.sentAt >= :since ORDER BY h.sentAt DESC")
  List<NotificationHistory> findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
      @Param("userId") UUID userId, @Param("since") LocalDateTime since);
  ```
  메서드명·파라미터·반환 타입·정렬은 그대로 유지해 호출부(`NotificationQueryService`) 변경이 필요 없다.
- **API 영향**: No Impact — 응답 필드·순서·타입 전부 동일, 쿼리 결과 자체도 동일(단지 N+1 대신 1회 JOIN으로 가져옴).
- **예상 변경 파일**: `notification/repository/NotificationHistoryRepository.java`
- **예상 변경 라인 수**: ~5줄
- **위험도**: Low — 단일 `ManyToOne` fetch join으로 결과 row 중복 위험 없음.
- **테스트 영향도**: 기존 `NotificationQueryServiceTest`는 repository를 mock하므로 변경 없이 통과. 이 리포지토리에 대한 `@DataJpaTest`/DB 레벨 테스트가 현재 전무해 이 수정이 실제로 쿼리 수를 줄였는지 기계적으로 검증할 수단이 없다는 점은 남는 공백이다(아래 B-1 참고 — 별도 항목으로 분리, 이번 A 항목 자체는 코드 한 줄 수정에 그친다).
- **예상 효과**: 목록 조회당 DB 왕복이 (1 + 서로 다른 트립 수)에서 1회로 감소, 사용자 체감 지연 감소.

### A-2. `DeviceTokenService.registerToken()` — 동시 신규 토큰 등록 시 처리되지 않은 `DataIntegrityViolationException`으로 500 노출 가능

- **Priority**: Medium
- **Category**: Concurrency / Exception 구조
- **문제**: `registerToken()`(`DeviceTokenService.java:29-41`)은 `findByToken(...).ifPresentOrElse(reassign, save-new)` 패턴이다. `UserDeviceToken.token`은 `unique = true`(`UserDeviceToken.java:44-46`)이고, PK가 `@UuidGenerator`(메모리에서 즉시 값 생성, `IDENTITY` 아님)라 `save()`는 새 엔티티를 영속성 컨텍스트에 `persist()`할 뿐, 실제 INSERT는 트랜잭션 커밋(=`@Transactional` 프록시가 `registerToken()` 리턴 후 커밋하는 시점)까지 지연된다. 즉 같은 신규 토큰 값으로 거의 동시에 두 번 `registerToken`이 호출되면(모바일 네트워크 타임아웃 후 클라이언트 재전송 등 흔한 시나리오) 둘 다 `findByToken`에서 empty를 보고 둘 다 `save()`를 호출하고, 커밋 시점에 한쪽만 성공하고 다른 한쪽은 UNIQUE 제약 위반으로 `DataIntegrityViolationException`을 던진다. 이 예외는 `TripFitException`이 아니므로 `GlobalExceptionHandler.handleTripFitException`이 처리하지 못하고, `GlobalExceptionHandler.handleUnexpectedException`(범용 500)으로 떨어진다.
- **왜 문제인가**: `POST /device-tokens`는 "이미 등록된 토큰이면 갱신"이라는 사실상 멱등적 계약(스펙 API 표: "기존 토큰이면 소유자·`deviceType`·`updatedAt` 갱신")인데, 타이밍이 나쁘면 클라이언트 재시도 요청 하나가 예측 불가능하게 500을 받을 수 있다. `registerToken()` 내부에서 `save()` 호출 직후 try-catch를 걸어도, 위에서 설명한 지연 flush 특성상 그 지점에서는 예외가 아직 발생하지 않으므로(커밋은 메서드 리턴 이후) 잡히지 않는다 — 이 타이밍 함정 자체가 놓치기 쉬운 지점이다.
- **개선 방법**: 새 토큰 저장 분기에서 `save()` 대신 `saveAndFlush()`를 호출해 메서드 본문 안에서 즉시 INSERT를 실행시키고, 그 자리에서 `DataIntegrityViolationException`을 잡아 "동시에 다른 요청이 먼저 넣었다"로 간주해 `findByToken(...).orElseThrow(...)`로 재조회 후 `reassign(user, deviceType())`으로 수렴시킨다.
  ```java
  () -> {
    try {
      userDeviceTokenRepository.saveAndFlush(
          new UserDeviceToken(user, request.token(), request.deviceType()));
    } catch (DataIntegrityViolationException exception) {
      userDeviceTokenRepository.findByToken(request.token())
          .orElseThrow(() -> exception)
          .reassign(user, request.deviceType());
    }
  }
  ```
- **API 영향**: No Impact — 성공 시 여전히 204, 신규 `ErrorCode` 없음. 극히 드문 레이스가 500 대신 정상 204로 수렴하는 차이뿐(계약을 더 정확히 지킴).
- **예상 변경 파일**: `notification/service/DeviceTokenService.java`, `src/test/java/com/tripfit/tripfit/notification/service/DeviceTokenServiceTest.java`
- **예상 변경 라인 수**: ~15줄
- **위험도**: Medium — `saveAndFlush`로 flush 시점을 앞당기므로, 신규 토큰 저장 경로에 한해 쿼리 타이밍이 미세하게 바뀐다(그 외 로직·트랜잭션 경계는 동일).
- **테스트 영향도**: `DeviceTokenServiceTest.registerToken_newToken_savesToken`의 `verify(...).save(...)`를 `saveAndFlush(...)`로 교체 필요. 레이스 폴백 경로(mock에서 `saveAndFlush`가 `DataIntegrityViolationException`을 던지도록 스텁 후 `findByToken` 재호출·`reassign` 검증)에 대한 신규 테스트 케이스 추가를 권장.
- **예상 효과**: 클라이언트 재시도로 인한 드문 500 제거, 등록 API의 실질적 멱등성 강화.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `DeviceTokenService`의 토큰 blank 검증이 두 메서드에 동일하게 중복

- **Priority**: Low
- **Category**: Cleanup / Readability
- **문제**: `registerToken()`(`:31-33`)과 `unregisterToken()`(`:46-48`)에 `if (token == null || token.isBlank()) throw new TripFitException(NotificationErrorCode.NOTIFICATION_TOKEN_REQUIRED);`가 토큰 변수명만 다르고 그대로 반복된다.
- **왜 문제인가**: 같은 클래스 안의 동일 검증 로직이 두 곳에 있어, 검증 규칙이 바뀌면(예: trim 처리 추가) 한쪽만 고치고 다른 쪽을 놓치기 쉽다.
- **개선 방법**: `private static void requireNonBlankToken(String token)` 헬퍼로 추출해 두 메서드에서 호출.
- **API 영향**: No Impact — 예외 타입·코드·메시지 동일.
- **예상 변경 파일**: `notification/service/DeviceTokenService.java`
- **예상 변경 라인 수**: ~8줄
- **위험도**: Low
- **테스트 영향도**: 없음 — 기존 `registerToken_blankToken_throwsTokenRequired` 테스트만으로 커버됨.
- **예상 효과**: 검증 로직 단일 지점화, 코드량 소폭 감소.

## 💡 C. 참고 사항 (이번엔 수정 안 함, 이유 필수)

- **`DeviceTokenController.unregister()`의 FCM 토큰이 `@RequestParam`(쿼리 스트링)으로 전달됨**(`DeviceTokenController.java:99-104`) — 액세스 로그·프록시 로그에 토큰 값이 남을 수 있어 이상적으로는 DELETE 요청 바디나 별도 식별자로 옮기는 게 낫다. 하지만 이는 `DELETE /api/v1/notifications/device-tokens?token=...`이라는 승인된 엔드포인트 계약(스펙 API 표, D 결정 없음) 자체를 바꾸는 일이라 이번 라운드의 절대 원칙 1(API Contract 100% 동일)에 위배돼 손대지 않는다. FCM 토큰은 비밀값이 아니라 기기 식별 토큰이라 즉각적 보안 사고로 이어지진 않지만, 개선하려면 별도 스펙 amend 승인이 선행돼야 한다.
- **1차 C 항목 재확인 — 변경 사유 없음**: `FcmServiceTest`가 실패 경로 1개만 검증(성공 멀티캐스트 payload·배치 분할 미검증), `NotificationEventListenerTest`에 `onScheduleReminder()` 테스트 부재, 3종 어노테이션 스택(`@Async`+`@TransactionalEventListener`+`@Transactional`) 반복. 셋 다 코드가 그 사이 바뀌지 않았고, 신규 테스트 작성·메타 어노테이션 추출이 "이번 구조 리팩터 범위 밖"이라는 1차 판단 근거도 그대로 유효해 유지한다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **1차 D 항목 전부 재확인 — 상황 변화 없음, 그대로 유지**: `FcmService.sendBatch()`의 광범위한 `catch (Exception)`(REQUIRES_NEW 트랜잭션·이력 저장 보존 목적), `dispatch()`의 `notification_enabled` 재필터링(BR-USER-005 게이트 단일 지점 유지), `ScheduleReminderBatch`/`FcmService`의 동일 값 `BATCH_SIZE=500`을 통합하지 않음(출처가 다른 상수), `FirebaseConfig`의 `@Lazy` 초기화(로컬 개발 부팅 보장), `NotificationHistory`/`UserDeviceToken`의 무분별한 `@Setter` 미사용. 코드·근거 모두 1차 감사 시점과 동일함을 이번 라운드에서 직접 재확인했다 — 다시 A/B로 올릴 근거 없음.
- **`FcmProperties` Bean 등록이 `notification` 패키지가 아닌 `auth/security/AppConfig`에서 이뤄짐** — 1차와 동일하게, `JwtProperties`·`OAuthProperties`·`SocialTokenCryptoProperties`도 같은 파일에 모이는 프로젝트 전역 컨벤션이라 `notification`만 분리하면 오히려 어긋난다. 이 역시 상황 변화 없음.

## 15. 백엔드 아키텍처 개선 제안

- **1차 결론 유지**: Resilience(FCM 재시도) — Later, Monitoring — Later, Event Architecture/Async(Kafka 등) — Never, Database(인덱스) — 추가 제안 없음. 트래픽·인프라 상황이 바뀌지 않아 그대로 둔다.
- **API — 알림센터 목록에 조건부 GET(ETag/Last-Modified) 도입**: `GET /api/v1/notifications`는 페이지네이션 없이 7일치 전체를 매번 반환한다(D9). 클라이언트가 배지 갱신 등으로 자주 폴링한다면 ETag로 불필요한 payload 전송을 줄일 여지가 있다. 다만 현재 응답이 이미 7일 윈도우로 작고, 실제 폴링 주기·빈도에 대한 데이터가 없어 지금 투자할 근거는 약하다 — **Later**(클라이언트 폴링 패턴이 확인되고 payload 크기가 실제로 문제가 될 때 재검토).
- **Security — 위 C 항목(FCM 토큰 URL 노출)의 근본 해결(엔드포인트 형태 변경)**: 코드 리팩터만으로는 해결 불가하고 스펙 amend가 필요하다 — **Later**(별도 스펙 승인 트랙, 이번 코드 감사 범위 아님).
- **Concurrency — 프로젝트 전반 Idempotency-Key 도입**: A-2가 발견한 레이스는 이번 라운드에서 해당 메서드에 국소적으로 고쳤다. 모든 변경(mutating) 엔드포인트에 범용 Idempotency-Key 인프라를 도입하는 것은 이 도메인 하나의 좁은 레이스 하나를 근거로 정당화하기엔 과하다 — **Never**(현재는 YAGNI, 유사 레이스가 다른 도메인에서도 반복 발견되면 그때 범용 도입 논의).

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다(A-1 → A-2 → B-1). C/D는 이번 라운드에서 수정하지 않습니다.
