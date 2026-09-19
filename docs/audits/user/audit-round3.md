# User Architecture Audit — Round 3 (2026-08-26, SOLID/OOP 중심)

> **선행 문서 안내:** `docs/audits/user/audit.md`(1차, 2026-08-05)와 `audit-round2.md`(2차, 2026-08-05)가 이미 존재하며, 두 라운드의 A(3개)·B(7개) 전부 사용자 승인 후 구현·검증까지 끝났다(`refactor-log.md`). 이번 3차는 auth 도메인 3차 감사(`docs/audits/auth/audit-round3.md`)와 동일하게 **새로 요청받은 SOLID/OOP 관점**으로 현재 코드를 다시 전수 검토한 결과이며, 1·2차가 이미 다룬 항목(트랜잭션 경계 재구성, 불필요한 `save()`, dead code, 구조화 로깅, 스케줄러 테스트 부재 등)은 재검토만 하고 새 판단이 없으면 반복 서술하지 않는다. 1·2차 이후 코드베이스 변화(Resilience4j·커스텀 `TaskScheduler`·`@Async` 확산 여부)도 재확인했으나 실질적 변화는 없었다(§15 참고).
>
> 이번 세션에서 이미 저장소 전역에 반영된 두 가지 공통 변경 — 모든 Service `@RequiredArgsConstructor` 사용, 모든 Entity **클래스 레벨** `@Setter` 제거(도메인 메서드로 상태 전이) — 은 전제로 두고 재발견하지 않았다. `User`(및 `Trip`·`TripMember`·`RegularSchedule`·`PersonalSchedule`)의 `id` 필드에 남아 있는 **필드 레벨** `@Setter`는 `@GeneratedValue` PK를 테스트 픽스처에서 세팅하기 위한 저장소 전역 공통 패턴(user 도메인 전용 이슈 아님)이라 이번 라운드 범위 밖으로 판단해 다루지 않았다.

## 범위

- 패키지: `com.tripfit.tripfit.user` (`client`, `controller`, `domain`, `dto`, `exception`, `repository`, `service`) + `com.tripfit.tripfit.user.googlecalendar` (`client`, `controller`, `domain`, `dto`, `exception`, `repository`, `scheduler`, `service`)
- 제외: `user/schedule/**` (별도 "user-schedule" 도메인, 별도 감사)
- 감사자: 이번 세션(신선한 컨텍스트, 이번 대화에서 `user` 도메인 코드를 아직 수정한 적 없음), 읽기 전용
- 기준: `audit-checklist.md` 1~15항목 + 사용자 지정 우선 렌즈(SRP·OCP·LSP·ISP·DIP·캡슐화·God class/method·feature envy·inappropriate intimacy), `harness-workflow.md` ⛔ STOP
- main 30개 파일(`user` 10 + `user/googlecalendar` 20) 전수 재검토 + 교차 확인: `trip/service/TripServiceSupport`(`UserDirectoryService` 소비처), `auth/service/AuthService`·`notification/service/DeviceTokenService`(`UserLookupService` 소비처), 관련 테스트 16개 파일

## ✅ A. 반드시 수정해야 하는 사항

### A-1. `UserWithdrawalService` — Google Calendar revoke 프로토콜(조회→복호화→revoke)을 `GoogleCalendarService`를 거치지 않고 그 내부 협력자 3개에 직접 재구현함(inappropriate intimacy)

- **Priority**: Medium
- **Category**: Architecture (캡슐화 / feature envy)
- **문제**: `UserWithdrawalService.revokeGoogleCalendarIfConnected()`(`UserWithdrawalService.java:63-76`)는 `googleCalendarCredentialRepository.findByUser_Id(userId)` → `tokenCrypto.decrypt(credential.getRefreshTokenCiphertext())` → `googleCalendarOAuthClient.revokeRefreshToken(userId, refreshToken)` 3단계를 직접 수행한다. 그런데 이 정확히 같은 3단계는 `GoogleCalendarService.disconnect()`(`GoogleCalendarService.java:132-138`)에 이미 존재한다 — Google Calendar 연동 해제(revoke)라는 동작의 소유자는 명백히 `GoogleCalendarService`(`GoogleCalendarCredentialRepository`·`GoogleCalendarOAuthClient`·`tokenCrypto`를 필드로 갖고 있는 클래스)인데, `UserWithdrawalService`가 이 세 협력자를 자신의 생성자 의존성으로 **직접** 주입받아 같은 로직을 한 번 더 구현하고 있다.
- **왜 문제인가**: 같은 탈퇴 유스케이스 안에서 다른 3개 provider(Google 로그인·Kakao·Apple)는 전부 각자 소유 서비스(`googleLoginCredentialService.revokeAndDeleteIfPresent`·`kakaoUnlinkClient.unlink`·`appleCredentialService.revokeAndDeleteIfPresent`)에 위임하는데, Google Calendar만 소유 서비스(`GoogleCalendarService`)를 건너뛰고 그 내부 구현 디테일(repository·client·crypto)에 직접 접근한다 — 4개 provider 처리 방식이 비대칭이라는 것 자체가 캡슐화 결함의 신호다. 실제로 이 비대칭 때문에 "revoke 프로토콜"이 두 클래스에 나뉘어 존재하게 됐고, 향후 이 프로토콜에 변경이 생기면(예: revoke 실패 시 재시도 정책 추가, 로깅 필드 추가) 두 파일을 동시에 고쳐야 하며 하나를 놓치면 조용히 동작이 갈라진다. `UserWithdrawalService`가 `googlecalendar` 패키지의 내부 구현 타입(`GoogleCalendarCredentialRepository`, `GoogleCalendarOAuthClient`, `GoogleCalendarCredential`)을 3개나 직접 import하는 것 자체가 도메인 경계를 넘어선 과도한 결합(inappropriate intimacy)이다.
- **개선 방법**: `GoogleCalendarService`에 `public void revokeIfConnected(UUID userId)`를 추가한다 — 현재 `disconnect()`의 revoke 블록(`credentialRepository.findByUser_Id(userId).ifPresent(credential -> {...})`)을 그대로 옮긴 메서드다. `disconnect()`는 이 메서드를 호출하도록 교체한다(동작 동일 — 예외를 그대로 전파). `UserWithdrawalService.revokeGoogleCalendarIfConnected()`는 `googleCalendarService.revokeIfConnected(userId)` 호출 하나로 축소하고, 기존 try-catch(best-effort 흡수)는 호출부에 그대로 유지한다. `UserWithdrawalService`에서 `GoogleCalendarCredentialRepository`·`GoogleCalendarOAuthClient`·`SocialTokenCrypto`(이 클래스에서 다른 용도로 쓰이지 않음, 전체 사용처가 이 메서드뿐) 3개 의존성·import를 제거하고 `GoogleCalendarService` 의존성 1개로 교체한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/service/GoogleCalendarService.java`, `user/service/UserWithdrawalService.java`
- **예상 변경 라인 수**: ~25줄
- **위험도**: Low — `revokeRefreshToken()`은 이미 내부에서 모든 예외를 삼키는 best-effort 설계이고(`GoogleCalendarOAuthClient.java:254-271`), `disconnect()`가 이 블록을 예외 전파(non-catch)로 호출하는 현재 동작과 `withdraw()`가 try-catch로 흡수하는 현재 동작을 각각 호출부에 그대로 유지하므로 두 경로 모두 동작 변화가 없다.
- **테스트 영향도**: `UserWithdrawalServiceTest`가 `GoogleCalendarCredentialRepository`·`GoogleCalendarOAuthClient`·`SocialTokenCrypto` 3개 mock을 `GoogleCalendarService` mock 1개로 교체해야 한다(`withdraw_whenGoogleCalendarConnected_revokesRefreshTokenBeforeFinalizing` 등 5개 테스트 메서드가 `verify(googleCalendarOAuthClient)...` 대신 `verify(googleCalendarService).revokeIfConnected(USER_ID)`로 변경). `GoogleCalendarServiceTest`의 `disconnect_*` 케이스는 내부 구현이 메서드 추출만 됐을 뿐이라 그대로 통과.
- **예상 효과**: 4개 provider 모두 "소유 서비스에 위임"이라는 동일한 패턴으로 통일되고, Google Calendar revoke 프로토콜의 SSOT가 `GoogleCalendarService` 하나로 좁혀진다. `UserWithdrawalService`의 생성자 의존성도 3개 줄어 유스케이스 오케스트레이터로서의 책임이 더 명확해진다.

## ✅ B. 유지보수성 향상을 위한 리팩토링

### B-1. `UserDirectoryService` — 주입만 되고 한 번도 호출되지 않는 `UserSummaryService` 의존성(dead code)

- **Priority**: Low
- **Category**: Dead Code
- **문제**: `UserDirectoryService`(`user/service/UserDirectoryService.java`)는 생성자에서 `UserSummaryService userSummaryService`를 주입받아 필드에 저장하지만(`:23`, `:29`, `:33`), 이 클래스의 3개 public 메서드(`requireUser`·`findAllById`·`requireProfileNameComplete`) 어디에서도 `userSummaryService`를 호출하지 않는다(`grep "userSummaryService\."` 결과 대입문 1건뿐, 호출 0건).
- **왜 문제인가**: `UserDirectoryService`는 이 파일 자신의 클래스 주석이 밝히듯 "trip이 필요로 하는 User 조회·프로필 검증을 기존 서비스에 위임"하는 라우팅 전용 파사드다(`:9-13`). 실제로 라우팅하지 않는 의존성이 남아 있으면, 이 클래스를 처음 읽는 개발자가 "trip 쪽 어딘가 요약 응답도 라우팅되나?" 하고 존재하지 않는 경로를 찾게 되고, DI 컨테이너 관점에서도 불필요한 빈 참조를 유지하는 셈이다.
- **개선 방법**: `UserSummaryService` 필드·생성자 파라미터·import를 제거한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/service/UserDirectoryService.java`
- **예상 변경 라인 수**: ~5줄
- **위험도**: Low
- **테스트 영향도**: 없음 — `UserDirectoryService` 전용 테스트 파일이 없고(간접적으로 `TripServiceSupport` 경유 테스트만 존재), 호출되지 않던 필드라 제거해도 기존 테스트에 영향 없음.
- **예상 효과**: 파사드 클래스가 실제로 라우팅하는 대상과 선언된 의존성이 정확히 일치하게 됨.

### B-2. `GoogleCalendarService.indexBusyDays` — Service에 놓인 순수 변환 유틸리티가 다른 Service에서 직접 호출됨

- **Priority**: Low
- **Category**: Architecture (SRP) / Cleanup
- **문제**: `indexBusyDays(List<GoogleCalendarBusyDay>)`(`GoogleCalendarService.java:194-201`, `public static`)는 DB·외부 호출이 전혀 없는 순수 리스트→맵 변환 함수인데, `GoogleCalendarService` 자신의 `findBusyDaysByUserId()`뿐 아니라 다른 클래스인 `GoogleCalendarSyncPersistenceService.replaceBusyDays()`(`GoogleCalendarSyncPersistenceService.java:131`)에서도 `GoogleCalendarService.indexBusyDays(existing)`로 직접 호출한다. 같은 패키지에는 이미 이런 종류의 의존성 없는 순수 변환 로직만 모아두는 전용 클래스 `GoogleCalendarBusyMapper`(`mapIntervalsToDays`·`toEntity`, `private` 생성자 + `static` 메서드만)가 있다.
- **왜 문제인가**: `GoogleCalendarSyncPersistenceService`(DB 쓰기 전용 계층)가 순수 변환 함수 하나 때문에 `GoogleCalendarService`(Google API 호출·트랜잭션 조율 계층)를 의존성으로 참조하게 되는데, 이는 논리적으로 "쓰기 계층이 조회·조율 계층의 정적 유틸을 빌려 쓰는" 역방향 결합이다. `GoogleCalendarBusyMapper`가 이미 "이 패키지의 의존성 없는 변환 로직은 여기 모은다"는 역할을 하고 있으므로(1차 감사 D 판단으로 확정된 위치), `indexBusyDays`만 다른 곳에 남아 있는 것은 SRP 경계가 일관되지 않은 것이다.
- **개선 방법**: `indexBusyDays`를 `GoogleCalendarBusyMapper`로 이동하고, `GoogleCalendarService.findBusyDaysByUserId()`·`GoogleCalendarSyncPersistenceService.replaceBusyDays()` 양쪽 호출부를 `GoogleCalendarBusyMapper.indexBusyDays(...)`로 교체한다.
- **API 영향**: No Impact
- **예상 변경 파일**: `user/googlecalendar/service/GoogleCalendarService.java`, `user/googlecalendar/service/GoogleCalendarSyncPersistenceService.java`, `user/googlecalendar/service/GoogleCalendarBusyMapper.java`
- **예상 변경 라인 수**: ~15줄
- **위험도**: Low — 메서드 본문 변경 없이 위치만 이동
- **테스트 영향도**: `GoogleCalendarBusyMapperTest`에 케이스 추가 권장(선택). 기존 `GoogleCalendarServiceTest`/`GoogleCalendarSyncPersistenceServiceTest`는 `indexBusyDays`를 직접 테스트하지 않고 결과 동작만 검증하므로 영향 없음.
- **예상 효과**: `GoogleCalendarBusyMapper`가 "이 패키지의 순수 변환 로직 SSOT"라는 역할을 완전하게 갖추게 되고, `GoogleCalendarSyncPersistenceService`가 조회·조율 계층(`GoogleCalendarService`)에 갖던 불필요한 의존을 끊는다.

## 💡 C. 참고 사항 (권장하지만 이번엔 수정하지 않음)

- **`UserWithdrawalService.withdraw()`의 provider별 처리(Google Calendar·Google 로그인·Kakao·Apple) — Strategy로 승격하지 않음.** OCP 관점에서 보면 새 provider가 추가될 때마다 `withdraw()` 본문에 호출 한 줄을 추가해야 하는 구조이지만, `auth/audit-round3.md` C 섹션이 `AuthService.login()`의 동일한 provider 분기에 대해 이미 내린 판단과 같은 이유로 보류한다 — provider 3~4개뿐이고 각 provider의 정리 방식이 서로 다르다(Kakao는 unlink만, 나머지는 revoke+delete, Google Calendar는 A-1 적용 후에도 credential 삭제는 `UserWithdrawalPersistenceService`가 별도로 수행). 공통 인터페이스로 묶으려면 타입 소거된 컨텍스트가 필요해 지금의 명시적 호출 나열보다 오히려 후퇴한다.
- **`UserErrorCode.SCHEDULE_ACTIVATION_REQUIRED`가 `trip/service/TripServiceSupport`에서 throw됨** — 1·2차에서 이미 다룬 항목, 재확인 결과 코드 변화 없음. ErrorCode 변경은 이 감사 절대 원칙(API 계약 100% 유지) 범위 밖이라 이번에도 다루지 않는다.
- **`GoogleCalendarOAuthClient`(385줄) — God class 아님, SOLID 렌즈로 재확인.** 줄 수만 보면 이 도메인에서 가장 큰 파일이지만, 5개 public 메서드(`exchangeAuthorizationCode`·`refreshAccessToken`·`queryFreeBusy`·`fetchGoogleAccountEmail`·`revokeRefreshToken`)가 Google이 노출하는 5개의 서로 다른 REST 엔드포인트(token 교환·token 갱신·freeBusy·userinfo/primary calendar·revoke)에 정확히 1:1로 대응하고, 각 메서드는 자신의 엔드포인트 호출·에러 매핑만 담당한다. 책임이 여러 개가 아니라 "Google Calendar 관련 외부 API 클라이언트"라는 단일 책임 안에 엔드포인트 수만큼의 메서드가 있는 것이라 God class로 보지 않는다.
- **1·2차가 이미 다룬 트랜잭션 경계·`save()` 중복·구조화 로깅·스케줄러 테스트 항목 — 재검토 결과 코드 변화 없음, 새 판단 없음.** `connect()`/`syncUser()`/`disconnect()` 전부 여전히 `@Transactional`이 아니고 DB 쓰기만 `*PersistenceService`에 위임하는 구조가 유지되고 있음을 확인했다.

## 🚫 D. 수정하지 않는 것이 더 좋은 사항

- **`UserLookupService`/`UserProfileService`/`UserSummaryService`/`UserWithdrawalService`(+`UserDirectoryService`)를 하나로 합치지 않음 — 1·2차 판단 재확인, 여전히 유효.** 각각 조회 SSOT, 프로필 수정+검증, DTO 조립, 탈퇴 오케스트레이션, trip 도메인 대상 라우팅 파사드라는 명확히 다른 책임을 갖는다. `UserDirectoryService`가 `UserLookupService`·`UserProfileService`를 감싸는 게 "불필요한 중간 계층(과도한 추상화)"으로 보일 수 있으나, 실제로 `trip/service/TripServiceSupport`가 `user` 도메인의 여러 서비스를 직접 import하지 않고 이 파사드 하나만 의존하게 해 도메인 경계를 지키는 역할을 한다(B-1로 미사용 의존성만 제거하면 이 역할이 더 명확해진다) — YAGNI 위반이 아니라 실사용 중인 도메인 간 경계다.
- **`User` 엔티티가 `equals()`/`hashCode()`를 오버라이드하지 않음** — 1·2차 판단 재확인. UUID PK 엔티티에서 기본 identity 비교를 쓰는 편이 여전히 JPA 모범사례에 가깝고, `Set`/`Map` 키로 쓰는 코드도 없다.
- **`GoogleCalendarBusyMapper`를 Spring 빈으로 바꾸지 않음** — 1차 판단 재확인, B-2로 `indexBusyDays`가 추가돼도 여전히 의존성 없는 `private` 생성자 + `static` 메서드 모음이라 판단이 바뀌지 않는다.
- **`GoogleCalendarService`의 생성자 의존성 8개 — God Service 아니라 정당한 오케스트레이터.** `connect()`/`disconnect()`/`syncUser()`/`findBusyDaysByUserId()`/`findBusyDaysByUserIds()` 각각은 자신이 쓰는 협력자 서브셋만 짧게 호출하고 실제 작업(암호화·HTTP·영속화·매핑)은 전부 위임한다(`AuthService` God Service 아님 판단, `auth/audit-round3.md` D와 동일 논리). A-1로 `revokeIfConnected()`가 하나 더 추가돼도 기존 협력자만 재사용하므로 의존성 개수는 늘지 않는다.

## 15. 백엔드 아키텍처 개선 제안

1·2차 §15의 3개 제안을 재확인한 결과 상태 변화 없음 — 새 SOLID/OOP 렌즈에서도 이 도메인에 새로 제안할 아키텍처 카테고리는 없다.

- **Resilience — Google Calendar 외부 호출 Circuit Breaker**: `grep` 재확인 결과 `resilience4j` 관련 의존성 여전히 없음. **Later** 유지.
- **Async — 탈퇴 시 provider revoke 4종 `@Async`화**: `@Async` 사용처는 여전히 `notification/service/NotificationEventListener` 6곳뿐, `user`/`user/googlecalendar`에는 없음. **Later** 유지.
- **Concurrency — `@Scheduled` 기본 단일 스레드 풀을 3개 도메인 스케줄러가 공유**: `application.yml`에 scheduling pool 관련 설정 여전히 없음(`maximum-pool-size: 10`은 HikariCP DB 커넥션 풀 설정으로 별개). **Later** 유지, `cross-cutting` 도메인 감사 시 논의 권장(2차와 동일).

## 승인 대기

사용자 승인 후 A/B 항목만 우선순위 순으로 구현합니다(A-1: `GoogleCalendarService.revokeIfConnected()` 추출 + `UserWithdrawalService` 위임 전환, B-1: `UserDirectoryService` 미사용 의존성 제거, B-2: `indexBusyDays`를 `GoogleCalendarBusyMapper`로 이동). C/D는 이번 라운드에서 수정하지 않습니다.
