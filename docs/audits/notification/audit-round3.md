# Notification Architecture Audit — Round 3 (2026-08-27, SOLID/OOP 중심)

> **선행 문서 안내**: `docs/audits/notification/audit.md`(1차, 2026-08-05)와 `audit-round2.md`(2차, 2026-08-05)가 이미 존재하며, 두 라운드가 찾은 항목(1차 A-1 `deleteByTokenAndUser_Id` 벌크화, B-1~B-3 `@Schema` 스펙 ID 제거·주석 정정·예외 메시지 보강 / 2차 A-1 `trip` fetch join, A-2 등록 동시성 레이스, B-1 blank 검증 통합)은 전부 반영·검증까지 끝났다(`refactor-log.md`). `auth`/`user`/`user-schedule`/`trip` 3차 감사(`docs/audits/{auth,user,user-schedule,trip}/audit-round3.md`)와 동일하게, 이번 3차는 **새로 요청받은 SOLID/OOP 관점**으로 `notification` 도메인 전체(main 19개 파일)를 다시 전수 검토한 결과다. 1·2차가 이미 다룬 항목은 재검토만 하고 새 판단이 없으면 반복 서술하지 않는다.
>
> **2차 이후 가장 크게 바뀐 지점 — `DeviceTokenService.registerToken()`이 실제 운영 500 사고를 계기로 2차 A-2와 다른 방식으로 재구현됐다(반영 확인).** 2차 감사는 "새 토큰 저장 분기에서 `save()` → `saveAndFlush()` + `DataIntegrityViolationException` catch 후 `findToken` 재조회·`reassign()`" 방식을 제안했었다. 그런데 실제 반영은 이 방식이 아니라 `UserDeviceTokenRepository.upsertToken()`이라는 `ON DUPLICATE KEY UPDATE` 네이티브 쿼리로 원자적 upsert를 구현하는 방식으로 바뀌어 있다(`UserDeviceTokenRepository.java:19-31`) — 코드 주석에 "select-then-write 방식은 UNIQUE 제약 위반 시 실패한 flush로 세션이 오염돼 복구용 재조회마저 같은 제약 위반을 던지는 문제가 있어(운영 500 사고) upsert로 대체"라고 명시돼 있고, `DeviceTokenServiceIntegrationTest`(`registerToken_sameTokenAlreadyOwnedByAnotherUser_reassignsWithoutError` 등)가 이 사고의 재현·회귀 테스트로 존재한다. 즉 2차가 제안한 방식보다 더 강한 조치(엔티티 레이어의 select-then-write 자체를 없애고 DB 원자성에 위임)가 실제 프로덕션 사고 이후 적용됐다 — 별도 조치 불필요, 기록만 갱신. `UserDeviceToken`에 2차가 언급한 `reassign()` 도메인 메서드는 이 방식 전환으로 더 이상 필요 없어져 실제로 존재하지 않는다(직접 확인 — dead code 아님, 애초에 이 접근에서 불필요).
>
> 이번 세션에서 이미 저장소 전역에 반영된 두 가지 공통 변경 — 모든 Service `@RequiredArgsConstructor` 사용, 모든 Entity **클래스 레벨** `@Setter` 제거(도메인 메서드로 상태 전이) — 은 전제로 두고 재발견하지 않았다. 확인 결과 `notification` 도메인의 `@Service` 클래스(`DeviceTokenService`·`NotificationQueryService`)는 이미 `@RequiredArgsConstructor`를 쓰고 있었다. 유일한 예외는 `FcmService`인데, 이는 저장소 전역 컨벤션(`spring-boot-java.md` "예외 — 수동 생성자 유지")이 명시적으로 인정하는 케이스다 — `@Lazy FirebaseMessaging`은 생성자 파라미터에 붙어야 지연 프록시로 주입되고 필드 애너테이션은 Lombok이 복사하지 않아, 수동 생성자 유지 사유가 코드 주석(`FcmService.java:33-34`)에 그대로 남아 있다(이 사고 자체가 2026-08-14 `FcmService`에서 난 사고로 컨벤션 문서에 예시로 박제돼 있음). `NotificationHistory`·`UserDeviceToken` 모두 클래스 레벨 `@Setter` 없이 `markRead()`/생성자만으로 상태를 바꾼다 — 두 엔티티 다 개별 필드 `id`용 setter조차 없어(다른 도메인의 `Trip`/`TripMember` PK 테스트 픽스처 setter 패턴과 달리) 오히려 더 엄격하게 캡슐화돼 있음을 확인했다.

## 범위

- 패키지: `com.tripfit.tripfit.notification` — `config`, `controller`, `domain`, `dto`, `event`, `exception`, `repository`, `scheduler`, `service` (main 19개 파일 전수 재검토)
- 테스트: `src/test/java/com/tripfit/tripfit/notification/**` 9개 파일 전수 확인(controller 3·scheduler 1·service 5) — 사용처 검증(dead code 확인)·회귀 테스트 성격 파악에 활용
- 교차 확인: `trip.repository.TripMemberRepository.findByTripIdAndDeletedAtIsNull`(`JOIN FETCH tm.user`로 N+1 없음 재확인), `trip.event.*`(5종 이벤트 record — 발행 지점 재확인), `user.repository.UserRepository.findIdsForScheduleReminder`, `docs/specs/notification/notification.md` D4(FCM 단일 채널 결정)
- 감사자: 현재 세션(신선한 컨텍스트, 이번 대화에서 `notification` 도메인 코드를 수정한 적 없음), 읽기 전용
- 기준: `audit-checklist.md` 1~15항목 + 사용자 지정 우선 렌즈(SRP·OCP·LSP·ISP·DIP·캡슐화·God class/method·feature envy·inappropriate intimacy), `core-guardrails.md` ⛔ STOP

## ✅ A. 반드시 수정해야 하는 사항

이번 라운드에서 A 항목 없음 — Critical/High급 구조적 결함(버그·성능 회귀·보안 문제·명백한 SOLID 위반)을 찾지 못했다. 1·2차가 찾은 N+1(`trip` fetch join)·동시성 레이스(`upsertToken` 원자적 upsert로 대체)는 이미 해결됐고, 이번 SOLID/OOP 렌즈로 다시 훑어도 `DeviceTokenService`(등록·해제)/`FcmService`(발송 어댑터)/`NotificationQueryService`(알림센터 조회)/`NotificationEventListener`(이벤트→발송 조율)의 책임 경계가 서로 겹치지 않고, 각 클래스가 단일 책임을 유지하고 있음을 확인했다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

이번 라운드에서 B 항목 없음 — 개별 필드 setter, 미사용 Repository 메서드, 중복 검증·매핑 코드 등 이전 라운드(특히 `trip` 3차 감사)에서 실제로 발견됐던 패턴을 동일한 방법(전체 메서드·필드 grep 기반 사용처 추적)으로 찾아봤으나, `notification` 도메인에는 해당 사례가 없었다:

- `NotificationHistory`·`UserDeviceToken`은 필드 레벨 setter조차 없다(PK조차 별도 setter 없음) — 우회 가능한 캡슐화 구멍 자체가 없음.
- `NotificationHistoryRepository`·`UserDeviceTokenRepository`의 모든 메서드(`findByToken`·`upsertToken`·`deleteByTokenAndUser_Id`·`deleteByTokenIn`·`findUserIdAndTokenByUserIdIn`·`findByIdAndUser_Id`·`findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc`)를 개별 grep한 결과 전부 최소 1곳 이상의 실사용처(main 또는 test)가 있다 — 미사용 메서드 없음.
- `DeviceTokenService`의 blank 토큰 검증은 이미 2차 B-1에서 `requireNonBlankToken` 헬퍼로 통합된 상태 그대로 유지되고 있다 — 재발 없음.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **1·2차 `audit.md`/`audit-round2.md`의 C 항목 재검증 결과 — 전부 여전히 유효, 변경 없음.** `FcmServiceTest`가 실패(예외 흡수) 경로 1개만 검증하고 성공 경로(멀티캐스트 payload 구성·무효 토큰 자동 삭제·500건 배치 분할)는 미검증(직접 확인 — `FcmServiceTest.java`는 여전히 테스트 1개), `NotificationEventListenerTest`에 `onScheduleReminder()` 테스트 부재(직접 확인 — 6개 리스너 메서드 중 5개만 테스트됨), 6개 리스너 메서드의 `@Async`+`@TransactionalEventListener`+`@Transactional` 3종 어노테이션 스택 반복(재사용처가 이 클래스 하나뿐이라 메타 어노테이션 추출 시 오히려 가독성 저하), `FcmProperties` Bean 등록이 `notification` 패키지가 아닌 `auth/security/AppConfig`(`JwtProperties`·`OAuthProperties`·`SocialTokenCryptoProperties`와 동일 컨벤션), `DeviceTokenController.unregister()`의 FCM 토큰이 쿼리 파라미터로 전달(엔드포인트 계약 변경이라 스펙 승인 없이 손댈 수 없음) — 모두 코드를 다시 읽어 상황 변화가 없음을 확인했다.
- **`NotificationEventListener`가 6개 이벤트 타입을 구독하는 구조(OCP)** — 새 알림 이벤트가 추가될 때마다 이 클래스에 `on...()` 메서드 하나가 늘어나는 형태라, 엄밀히는 "확장할 때마다 기존 클래스를 수정"하는 모양새다. 하지만 Spring의 `@TransactionalEventListener`는 이벤트 타입별로 메서드를 등록하는 방식이 표준 관용구이고, 이걸 피하려면 `NotificationHandler` 인터페이스 + 이벤트 타입→핸들러 매핑 레지스트리를 새로 도입해야 하는데, 지금 6개 핸들러 각각이 서로 다른 조합의 의존성(예: `onAllMembersSubmitted`는 `tripRepository`만, `onScheduleReminder`는 `userRepository`만 필요)을 쓰고 있어 레지스트리로 묶으면 오히려 "각 핸들러가 이 클래스의 6개 의존성 전부를 잠재적으로 필요로 하는 것처럼" 보이는 인터페이스가 생긴다. 현재도 `dispatch()` 공통 로직은 이미 private 메서드로 추출돼 있어 실질적 중복은 없다 — 클래스 크기(207줄, 핸들러 6개)가 아직 God Class로 볼 정도가 아니라 지금 시점에 인터페이스를 도입하는 것은 YAGNI로 판단, 핸들러가 10개 이상으로 늘거나 핸들러별 로직이 각각 복잡해질 때 재검토할 사안으로 남긴다.
- **`FcmService`가 인터페이스 없이 concrete 클래스로 직접 주입됨(DIP)** — 얼핏 "발송 채널 추상화가 없다"고 볼 수 있지만, `docs/specs/notification/notification.md` D4가 "FCM 단일 채널 vs APNs 병행" 결정에서 **FCM 단일 채널을 명시적으로 확정**했다(APNs 등 추가 채널 계획이 스펙에 없음). 즉 이 도메인은 애초에 다중 채널을 상정하지 않기로 결정된 상태라, `NotificationSender` 같은 인터페이스를 지금 도입하면 구현체가 영원히 1개뿐인 추상화가 된다 — YAGNI 위반. 스펙 D4가 바뀌어 다른 채널이 추가될 때 재검토.
- **`NotificationResponse.from()`이 `history.getTrip().getId()`/`.getTrip().getName()`으로 연관관계를 2단계 타고 들어감(Law of Demeter 관점 feature envy 후보)** — 검토 결과 `spring-boot-java.md`가 "풀 DDD 미적용 — JPA 연관관계·객체 그래프 탐색 허용"을 프로젝트 전역 컨벤션으로 명시하고 있고, 이 탐색은 이미 `NotificationHistoryRepository`가 `LEFT JOIN FETCH h.trip`으로 즉시 로딩해두는 지점이라 추가 쿼리도 없다(2차 A-1이 이미 이 경로를 fetch join으로 최적화 완료). DTO 정적 팩터리가 연관 엔티티 필드 1~2개를 읽어 평탄화하는 것은 이 프로젝트에서 반복되는 정상 패턴이라 판단해 손대지 않는다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **1·2차 D 항목 전부 재확인 — 상황 변화 없음, 그대로 유지.** `FcmService.sendBatch()`의 광범위한 `catch (Exception)`(REQUIRES_NEW 트랜잭션·이력 저장 보존 목적, `FcmServiceTest`로 의도 검증됨), `dispatch()`의 `notification_enabled` 재필터링(BR-USER-005 게이트 단일 지점 유지, 의도적 중복), `ScheduleReminderBatch`/`FcmService`의 동일 값 `BATCH_SIZE=500`을 통합하지 않음(이벤트 발행 배치 크기 vs Firebase 하드 리밋 — 출처가 다른 값), `FirebaseConfig`의 `@Lazy` 초기화(로컬 개발 환경에서 FCM 키 없이도 부팅 보장), `FcmProperties`의 Bean 등록 위치(`AppConfig` — 다른 Properties와 동일 컨벤션). 코드·근거 모두 이전 라운드 시점과 동일함을 이번 라운드에서 직접 재확인했다.
- **`NotificationEventListener`가 `TripRepository`·`TripMemberRepository`·`UserRepository`(모두 다른 도메인 소유)를 포트/인터페이스 없이 직접 주입받는 구조 — 분리하지 않는다.** `trip` 3차 감사가 이미 `trip/port/out` 포트/어댑터 자체를 폐기하고(`docs/decisions/003-architecture-guide.md` 결정 11) concrete 클래스 직접 주입을 저장소 전역 표준으로 확정했다 — `notification`이 `trip`/`user`의 Repository를 직접 참조하는 것도 같은 방향이다. 다만 `trip`이 concrete **Service**(`ScheduleAvailabilityService`·`UserDirectoryService`)를 주입받는 것과 달리 `notification`은 concrete **Repository**를 직접 주입받는다는 차이가 있는데, 이는 `NotificationEventListener`가 필요로 하는 게 `trip`/`user`의 유스케이스가 아니라 단순 엔티티 조회(존재 확인·멤버 목록·유저 목록)뿐이라 Repository 직접 접근이 오히려 더 얇고 적절한 경계다 — Service 레이어를 하나 더 얹으면 위임만 하는 얇은 메서드가 늘어날 뿐 실익이 없다.
- **`ScheduleReminderBatch.run()`이 `@Transactional(readOnly = true)`인 배치 메서드 안에서 조회 결과를 바로 이벤트로 쪼개 발행 — Command/Query 분리를 강제하지 않는다.** 형식적으로는 "조회 겸 발행"이 SRP를 살짝 넘나드는 것처럼 보이지만, 이 메서드의 유일한 책임은 "리마인드 대상자를 조회해 배치 단위로 이벤트를 쪼개 발행"하는 것 하나이고, 실제 알림 생성·발송은 전부 `NotificationEventListener.onScheduleReminder()`로 위임돼 있어 책임이 이미 분리돼 있다. 별도 QueryService를 두면 이 배치 스케줄러 하나만을 위한 1회용 클래스가 늘어난다.

## 15. 백엔드 아키텍처 개선 제안

1·2차 §15의 제안들을 재확인한 결과 상태 변화 없음 — 새 SOLID/OOP 렌즈에서도 이 도메인에 새로 제안할 아키텍처 카테고리는 없다.

- **Resilience — FCM 발송 재시도**: 1차 판단(발송 규모가 크지 않고 `Resilience4j` 의존성 없음) 유지. **Later**.
- **Monitoring — FCM 실패율 지표**: 1차 판단(Micrometer 레지스트리 프로젝트 전체에 없음, 도메인 하나만 먼저 넣으면 관측 스택 파편화) 유지. **Later**.
- **Event Architecture / Async — 외부 메시지 브로커 도입**: 1차 판단(현재 단일 인스턴스 배포·트래픽 규모에서 in-process `@Async`로 충분) 유지. **Never**.
- **API — 알림센터 목록 조건부 GET(ETag)**: 2차 판단(현재 응답이 이미 7일 윈도우로 작고 폴링 패턴 데이터 없음) 유지. **Later**.
- **Security — FCM 토큰 URL 노출(쿼리 파라미터) 근본 해결**: 2차 판단(코드 리팩터만으론 불가, 스펙 amend 필요) 유지. **Later**.
- **Concurrency — 프로젝트 전반 Idempotency-Key 도입**: 2차 판단(이 도메인 하나의 레이스는 이미 upsert로 해소, 범용 인프라 도입은 과함) 유지. **Never**.

## 승인 대기

이번 라운드는 A/B 항목이 없어 구현할 것이 없습니다. C/D는 이번 라운드에서도 수정하지 않습니다.
