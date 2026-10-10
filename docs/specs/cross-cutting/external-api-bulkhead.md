# 외부 API 장애 격리 (벌크헤드)

> 상태: **Implemented** (2026-10-11, `#134` — PR 전)
> MVP: In scope (기존 알림·로그인 기능의 내부 동작 변경 — 새 기능 아님)
> 관련 BR: 해당 없음 (알림·로그인 규칙 자체는 바뀌지 않는다)

Firebase·Apple·Google·카카오가 느려지거나 멈췄을 때, 그 영향이 DB 커넥션 풀과 요청 처리 스레드를 거쳐 다른 API로 번지지 않게 막는 설계다. 벌크헤드는 배의 격벽처럼 자원을 칸으로 나눠, 한 칸의 장애가 다른 칸으로 번지지 않게 하는 방식을 말한다. 이 문서에서 어떤 호출을 어떤 자원으로 격리하는지, 그 근거가 된 실험과 라이브러리 기본값, 적용 전후 부하 테스트 결과를 확인할 수 있다.

이 문서에서 **실행기**는 한 가지 일만 맡는 스레드 묶음(Spring `ThreadPoolTaskExecutor`)이다. 스레드 수와 대기열 크기에 상한이 있고, 둘 다 차면 새 작업을 거절한다.

## 목표

외부 API 하나가 멈춰도 그 API를 쓰지 않는 요청의 응답 시간과 성공률은 그대로 유지한다.

## 배경

### 외부 호출별 격리 판단

외부 호출을 경로별로 모두 확인했다. 다른 API로 영향이 번질 수 있는 곳은 아래 표의 처음 세 줄이고, 나머지는 이미 격리돼 있거나 효과가 작아 범위에서 뺐다.

| 외부 호출 | 실행 위치 | 이번 범위 | 이유 |
|-----------|-----------|-----------|------|
| **FCM(Firebase Cloud Messaging) 발송** | 커밋 후 `@Async` 작업(기본 풀 8개) | 격리 | 타임아웃이 없고, DB 트랜잭션 안에서 호출해 기다리는 동안 DB 커넥션을 쥔다. 커넥션 10개 중 최대 8개가 묶인다 |
| **Apple·Google 인가 코드 교환** | 로그인 요청 스레드 | 격리 | 실패해도 로그인은 성공시키는 부가 단계인데, 로그인 응답이 최대 8초(연결 3초 + 응답 5초) 기다린다 |
| **카카오 `user/me`** | 로그인 요청 스레드 | 격리 | 로그인에 필수라 뺄 수 없다. 카카오가 5초씩 걸리면 초당 약 40건의 카카오 로그인만으로 요청 처리 스레드 200개가 모두 찬다 |
| Google·Apple 서명 키(JWKS, JSON Web Key Set) 조회 | 로그인 요청 스레드 | 제외 | 15분 캐시와 0.5초 타임아웃이 있다 |
| Google 캘린더 동기화 | 스케줄러 | 제외 | 백그라운드에서 한 명씩, 트랜잭션 밖에서 처리한다 |
| Google 캘린더 연결 | 요청 스레드 | 제외 | 사용자당 한 번이라 빈도가 너무 낮다 |
| 탈퇴 시 연결 해제(카카오·Google·Apple·캘린더) | 요청 스레드 | 제외 | 빈도가 너무 낮다 |
| 공휴일 API | 스케줄러 | 제외 | 하루 1번 돌고, 실패하면 캐시를 쓴다 |

외부 호출용 HTTP 클라이언트(`SimpleClientHttpRequestFactory`)는 공유 커넥션 풀이 없어 provider끼리 경쟁하지 않는다.

### 재현 실험 (2026-10-08)

실제 MySQL 8.0(Testcontainers)에서 `FirebaseMessaging.sendEach`가 응답하지 않도록 만들고 알림 이벤트 12건을 발행했다. 실험 코드는 저장소에 남기지 않았다.

| 측정 | 결과 |
|------|------|
| FCM 응답을 기다리는 알림 작업 | 8건 (기본 비동기 풀 크기). 나머지 4건은 대기열에서 멈춤 |
| 점유된 DB 커넥션 | 10개 중 **8개** |
| 일반 DB 작업 40건(각 50ms)에 걸린 시간 | 364ms → **1,095ms** (약 3배) |

실제 장애에서는 아래 B-1의 타임아웃이 없으므로 이 상태가 서버 재시작 전까지 풀리지 않는다.

### 함께 머지될 작업 — `#130` · `#131`

[`#130`](https://github.com/Central-MakeUs/TripFit-server/issues/130)은 여행방 참여 정원 보장을 비관적 락에서 낙관적 락(`@Version` + 버전 충돌 재시도)으로 바꾸는 작업이고, 그 PR [`#131`](https://github.com/Central-MakeUs/TripFit-server/pull/131)은 이 문서 작성 시점에 머지 전이다. 이 브랜치는 `main`에서 시작했지만, 최종 코드는 두 PR이 모두 머지된 상태다. 그래서 B-4·B-12는 두 상태 모두에서 맞도록 정했고, 동시성 실험도 두 상태에서 각각 돌렸다.

`#130` 스펙(`docs/specs/trip/trip-join-optimistic-lock.md`, `#131` 브랜치에만 있음)의 실험에서, 자식 행 INSERT가 외래키 때문에 부모 행(`trip`)에 공유 잠금을 건 뒤 같은 부모 행 UPDATE를 기다리면 동시 요청끼리 데드락이 났다(196건 중 183건). 아래 B-4는 이 패턴을 피하기 위한 결정이다.

### 근거 — 라이브러리 실물과 provider 문서

로컬 jar를 `javap`로 직접 확인한 값이다(`core-workflow.md` G1 ① 로컬 실물).

| 항목 | 확인한 값 | 출처 |
|------|-----------|------|
| Firebase 기본 타임아웃 | `FirebaseOptions.Builder`의 연결·읽기·쓰기 타임아웃 기본값 0. 기본 전송(`ApacheHttp2Transport`)이 이 값을 `Timeout.ofMilliseconds(0)`으로 넘기고, httpcore5에서 0은 `INFINITE` | firebase-admin 9.10.0 · httpcore5 5.4 |
| Firebase `sendEach` | 메시지마다 SDK 내부 풀(고정 100스레드, 무제한 대기열)에 제출하고, 호출 스레드는 `ApiFuture.get()`으로 상한 없이 기다린다 | firebase-admin 9.10.0 |
| Firebase 재시도 | 503 응답 시 최대 4회 재시도, 간격 최대 60초 | firebase-admin 9.10.0 `ApiClientUtils` |
| Spring Boot 기본 비동기 풀 | 코어 8, 대기열 무제한. `Executor` 빈을 직접 정의하면 기본 풀(`applicationTaskExecutor`)은 만들어지지 않는다 | spring-boot-autoconfigure 4.1.0 |
| Tomcat 요청 처리 스레드 | 최대 200 | spring-boot-tomcat 4.1.0 |
| `AFTER_COMMIT` 리스너 실행 시점 | `afterCompletion` 안에서 실행되고, 여기서 난 예외는 Spring이 잡아 로그만 남긴다(요청은 실패하지 않는다) | spring-tx 7.0.8 |
| `@ConcurrencyLimit` | `policy = REJECT`면 한도 초과 시 `InvocationRejectedException`(`RejectedExecutionException` 하위)을 던진다. 한도는 빈 인스턴스·메서드 단위 | spring-context 7.0.8 |
| Apple 인가 코드 | 1회용, 5분 유효 | [WWDC22 — Enhance your Sign in with Apple experience](https://developer.apple.com/videos/play/wwdc2022/10122/) |
| Google refresh token | `access_type=offline`이고 **처음 승인할 때만** 반환 | [Using OAuth 2.0 for Web Server Applications](https://developers.google.com/identity/protocols/oauth2/web-server) |

## 변경 범위

Approved 스펙 [`notification.md`](../notification/notification.md) · [`auth-social-login.md`](../auth/auth-social-login.md) · [`google-login-revoke.md`](../auth/google-login-revoke.md) · [`apple-oauth-multi-audience.md`](../auth/apple-oauth-multi-audience.md) · [`user-account-withdrawal.md`](../user/user-account-withdrawal.md)의 내부 동작을 바꾼다. API 계약은 바뀌지 않는다(B-14).

### ADDED

- Firebase 연결·읽기·쓰기 타임아웃과 배치 단위 대기 상한 (B-1, B-2)
- 실행기 빈 2개: `notificationPushExecutor` · `socialCredentialExchangeExecutor` (B-6, B-8)
- 알림 이력을 원래 트랜잭션의 커밋 직전에 저장하는 리스너와 발송 제출기 `NotificationPushDispatcher` (B-3~B-5)
- 인가 코드 교환 제출기 `LoginCredentialExchangeDispatcher` (B-8)
- 카카오 `user/me` 호출 전용 클라이언트 `KakaoUserInfoClient`와 동시 호출 상한 (B-11)
- `ResilienceConfig` — `@EnableResilientMethods`로 `@ConcurrencyLimit`·`@Retryable` 처리를 켠다. `#131`과 같은 파일 (B-12)
- 사용자 행 잠금 조회(`UserRepository.findByIdForUpdate`, `UserLookupService.findActiveUserForUpdate`) — 교환 결과 저장 시 탈퇴 여부 확인용 (B-9)
- 탈퇴 마지막 단계의 로그인 credential 재확인 (B-10)
- 탈퇴 DB 정리에서 여행방 변경을 사용자 행보다 먼저 flush (B-16)
- 외부 URL 설정 키(카카오 `user/me`, Google 토큰 엔드포인트) — 기본값은 지금 주소이고, 부하 테스트에서만 값을 바꾼다 (B-15). Apple 토큰 엔드포인트는 시나리오가 쓰지 않아 설정 키로 빼지 않았다
- `loadtest/` — k6·WireMock 시나리오와 실행 방법, 테스트 소스의 부하 테스트용 실행 설정 (B-15)

### MODIFIED

- 알림 이력 저장: (변경 전) 커밋 후 별도 작업이 새 트랜잭션에서 이력 저장과 FCM 발송을 함께 처리 → (변경 후) 원래 트랜잭션 커밋 직전에 이력 저장, FCM 발송은 커밋 후 실행기에서 트랜잭션 없이 실행
- 무효 FCM 토큰 삭제: (변경 전) 발송과 같은 트랜잭션 → (변경 후) 발송 후 `DeviceTokenService.deleteInvalidTokens`의 짧은 트랜잭션
- `FcmService` 발송: (변경 전) `sendEach` 무제한 대기 → (변경 후) `sendEachAsync` + 30초 상한
- `ScheduleReminderBatch`: (변경 전) 읽기 전용 트랜잭션 하나에서 이벤트 발행 → (변경 후) 500명 단위로 각자 쓰기 트랜잭션 (B-7)
- `AuthService.login`: (변경 전) Apple·Google 코드 교환을 응답 전에 동기 실행 → (변경 후) 응답 후 실행기에서 실행
- 카카오 토큰 검증: (변경 전) 동시 호출 제한 없음 → (변경 후) 20개 초과 시 즉시 503 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE`
- 문서: 위 Approved 스펙 5개 · [`how-it-works.md`](../../how-it-works.md)(토큰 저장 시점, STOP §6) · `spring-boot-java.md` ACID 절 · `package-layout.md` · `docs/specs/README.md`

### REMOVED

- `NotificationEventListener`의 `@Async` + `@Transactional(REQUIRES_NEW)` 리스너 6개 (B-3·B-5 구조로 대체)
- `@EnableAsync` — 비동기 작업을 모두 실행기에 직접 제출하므로 쓰는 곳이 없어진다 (B-13)
- `FcmService`가 발송 트랜잭션 안에서 토큰을 지우던 경로

## 요구사항

### Must Have

- [x] B-1 Firebase 타임아웃, B-2 배치 대기 상한
- [x] B-3·B-4 알림 이력을 원래 트랜잭션의 커밋 직전에 저장 (flush 먼저)
- [x] B-5·B-6 FCM 발송을 커밋 후 `notificationPushExecutor`에서 트랜잭션 없이 실행, 대기열이 가득 차면 푸시만 건너뜀
- [x] B-7 정기 리마인드 500명 단위 쓰기 트랜잭션
- [x] B-8 Apple·Google 코드 교환을 응답 후 `socialCredentialExchangeExecutor`에서 실행, 대기열이 가득 차면 건너뜀
- [x] B-9·B-10 교환 도중 탈퇴한 사용자의 credential이 남지 않음
- [x] B-11·B-12 카카오 동시 호출 상한
- [x] B-13 `@EnableAsync`와 기존 리스너 구조 삭제
- [x] B-16 방장 탈퇴의 잠금 순서를 여행방 → 사용자로 고정
- [x] B-15 부하 테스트 시나리오 3개 실행, 적용 전후 수치를 아래 "측정 결과"에 기록

### Nice to Have

- 없음

### Out of Scope (이번 스펙에서 하지 않음)

- 알림 실패 재시도 — `notification.md` `[미정]` 그대로 둔다. 서버 재시작 때 대기 중이던 푸시가 사라지는 것도 지금과 같다
- Firebase SDK 내부 풀(100스레드) 크기 조정
- 위 표에서 "제외"로 둔 호출의 격리
- 서킷 브레이커 — 이번 범위는 자원 격리(벌크헤드)와 타임아웃뿐이다

## API / 인터페이스

API 계약 변경 없음. 동작만 아래처럼 달라진다.

| Method | Path | 달라지는 점 |
|--------|------|-------------|
| POST | `/api/v1/auth/login` | Apple·Google 로그인이 토큰 서버 응답을 기다리지 않아 빨라진다. 카카오 동시 검증이 20개를 넘으면 기존 503 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE`을 돌려준다(Swagger 503 설명에 조건 추가) |
| — | 알림을 만드는 API 5종(참여 완료·전원 제출·정보 변경·확정·확정 취소) | 알림 이력 INSERT가 요청 트랜잭션에 포함된다. 응답 형태는 같다 |

## 데이터 모델

스키마 변경 없음. `users` 행 잠금 조회(`SELECT ... FOR UPDATE`) 메서드 1개만 추가한다(B-9).

## 비즈니스 규칙

해당 없음.

## 설계

### 결정 사항

결정 ID의 B는 Bulkhead의 머리글자다. 실행기 크기·한도 같은 수치는 이 표가 기준이다.

| ID | 결정 | 이유 |
|----|------|------|
| B-1 | Firebase 연결 3초 · 읽기 5초 · 쓰기 5초 | 기본값이 무제한이다. 다른 외부 호출(`RestClient` 3초/5초)과 맞춘다 |
| B-2 | 배치 하나의 대기는 `sendEachAsync(...).get(30초)`로 끊고, 넘으면 발송을 취소한다 | 503 재시도(최대 4회, 간격 최대 60초)는 타임아웃만으로 끝나지 않을 수 있다 |
| B-3 | 알림 이력은 이벤트를 발행한 트랜잭션의 커밋 직전(`BEFORE_COMMIT` 리스너)에 저장한다 | 원래 작업과 알림 이력이 함께 저장되거나 함께 취소된다. 푸시를 못 보내도 알림센터는 실제 상태와 맞다 |
| B-4 | `BEFORE_COMMIT` 리스너는 이력 INSERT 전에 **Repository의 `flush()`**를 호출한다 | `#130` 실험의 데드락 패턴을 피한다(아래 설명) |
| B-5 | FCM 발송은 커밋 후(`afterCommit`) `notificationPushExecutor`에 직접 제출하고 트랜잭션 없이 실행한다. 무효 토큰 삭제만 짧은 트랜잭션으로 연다 | 외부 대기 중에 DB 커넥션을 쥐지 않는다. 직접 제출해야 거절을 이 코드에서 처리할 수 있다 |
| B-6 | `notificationPushExecutor`: 스레드 4개, 대기열 200. 대기열이 가득 차면 푸시만 건너뛰고 WARN 로그(알림 종류·토큰 수). 종료 시 최대 30초 대기 | 이력은 B-3에서 이미 저장됐으므로 건너뛰어도 알림센터는 정확하다 |
| B-7 | 정기 리마인드는 500명마다 쓰기 트랜잭션을 따로 열어 그 안에서 이벤트를 발행한다. 한 묶음이 실패하면 그 묶음만 로그를 남기고 건너뛴다 | 지금은 읽기 전용 트랜잭션이라 B-3의 INSERT가 실패한다. 전체를 한 트랜잭션에 넣으면 수신자 행 잠금을 오래 쥔다. 예전에는 묶음마다 비동기 작업이 따로 돌아 실패가 격리됐으므로 그 성질을 유지한다 |
| B-8 | Apple·Google 코드 교환은 로그인 응답 후 `socialCredentialExchangeExecutor`(스레드 4개, 대기열 100)에서 실행한다. 대기열이 가득 차면 건너뛰고 WARN 로그 | 기존 "교환 실패 시 로그인은 성공, 저장만 생략" 정책과 같다. 대기열이 차는 건 토큰 서버가 느리거나 멈췄을 때뿐이다 |
| B-9 | 교환 결과 저장 트랜잭션은 `users` 행을 잠금 조회해 탈퇴 여부를 확인한다. 탈퇴했으면 저장하지 않고, 받은 토큰을 트랜잭션 밖에서 바로 revoke한다 | 로그인 직후 탈퇴하면, 탈퇴의 revoke 단계가 끝난 뒤에 교환이 끝날 수 있다 |
| B-10 | 탈퇴는 DB 정리(`finalizeWithdrawal`) 후 Apple·Google 로그인 credential을 한 번 더 확인해 revoke·삭제한다 | B-9의 잠금 조회가 탈퇴 커밋보다 먼저 끝난 경우를 잡는다. 보통은 DB 조회 1번으로 끝난다 |
| B-11 | 카카오 `user/me` 호출을 `KakaoUserInfoClient`로 빼고 `@ConcurrencyLimit(limit = 20, policy = REJECT)`를 붙인다. `KakaoTokenVerifier`가 거절을 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE`(503)로 바꾼다 | 같은 빈 안에서 호출하면 어노테이션이 적용되지 않는다. 20은 요청 처리 스레드 200개의 10%다 |
| B-12 | `ResilienceConfig`는 `#131`과 같은 파일을 같은 내용으로 추가한다 | 양쪽에서 똑같이 추가된 파일은 git이 충돌 없이 합쳐, 두 PR의 머지 순서가 자유로워진다 |
| B-13 | `@EnableAsync`를 삭제한다 | 실행기 빈을 정의하면 기본 비동기 풀이 사라진다(근거 표). 이름 없는 `@Async`가 남으면 스레드 수 제한이 없는 실행기로 조용히 넘어간다 |
| B-14 | 새 `ErrorCode` 없이 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE`(503)을 재사용하고, `Breaking-Change-Reason` 트레일러는 붙이지 않는다 | 프론트의 처리("잠시 후 다시 시도")가 같고, 필드·enum·코드·경로가 바뀌지 않아 STOP §5 대상이 아니다 |
| B-15 | 부하 테스트는 `bootTestRun`으로 띄운다. FCM과 Google ID 토큰 검증은 테스트 소스의 대역으로 바꾸고, 외부 URL은 설정값으로 WireMock을 가리킨다 | 운영 코드에 테스트용 대역을 넣지 않는다. FCM 주소는 SDK 안에 고정돼 있어 WireMock으로 돌릴 수 없다 |
| B-16 | 탈퇴 DB 정리(`finalizeWithdrawal`)는 여행방 정리 뒤, 사용자 행을 고치기 전에 Repository `flush()`를 호출한다 | 그대로 두면 맨 먼저 불러온 사용자 행 UPDATE가 여행방 UPDATE보다 먼저 나가, 알림 이력을 저장하는 요청(여행방 → 사용자 순서)과 반대 순서가 된다. `#131` 코드에서도 방장 탈퇴는 같은 순서였다 |

**B-3을 고른 이유 더.** 커밋 후 작업이 거절되거나 서버가 재시작돼도 알림센터가 빠지지 않는다. 푸시가 이력 커밋보다 먼저 도착해 읽음 처리 API가 이력을 못 찾던 경쟁도 없어진다. 대신 이력 INSERT가 실패하면 원래 작업도 실패한다(리스크 표).

**B-4의 잠금 순서.** 그대로 두면 커밋 시점의 flush가 이력 INSERT(외래키로 `trip` 행 공유 잠금)를 `trip` UPDATE(배타 잠금)보다 먼저 보낸다. 같은 방에 동시에 들어온 두 요청이 서로의 공유 잠금 때문에 UPDATE를 못 해 데드락이 된다. 먼저 flush하면 이 트랜잭션이 `trip` 행 배타 잠금을 쥔 뒤에 INSERT한다. EntityManager가 아니라 Repository로 flush해야 `#131`의 버전 충돌이 Spring 예외(`ObjectOptimisticLockingFailureException`)로 바뀌어 재시도에 걸린다.

**B-16을 더한 이유.** 리뷰에서 "방장 탈퇴와 멤버의 일정 확인 완료가 겹치면 잠금 순서가 반대"라는 지적이 나왔다. Hibernate가 실제로 보내는 SQL 순서를 기록해 보니, 방장 탈퇴는 `UPDATE users`를 `UPDATE trip`보다 먼저 보냈다(`UserWithdrawalLockOrderIntegrationTest`가 수정 전 실패). 두 요청이 겹치는 창은 1ms도 안 돼 동시 실행 반복으로는 잡히지 않으므로, 실행 순서 자체를 테스트로 고정했다.

**B-8의 대기열 크기.** 작업 하나는 최대 8초(연결 3초 + 응답 5초)라, 대기열 100개가 차도 마지막 작업이 100 ÷ 4 × 8초 = 200초 안에 시작된다. Apple 인가 코드 유효시간(5분)보다 짧다. 대기열이 차는 상황이면 로그인 요청에서 직접 교환해도 토큰을 얻지 못하므로, 건너뛰는 쪽이 로그인만 느려지는 쪽보다 낫다고 판단했다.

### 처리 순서 — 알림

이벤트를 발행한 요청 하나가 거치는 순서다.

1. 원래 작업(예: 여행 확정)이 엔티티를 고친다. `@TripActivity`도 같은 트랜잭션에서 실행된다.
2. `NotificationEventListener`가 커밋 직전에 Repository `flush()`로 쌓인 UPDATE를 먼저 보낸다(B-4).
3. 같은 리스너가 수신자를 조회해 알림 이력을 INSERT한다. 발송에 필요한 값은 `NotificationPush`(토큰별 이력 ID·제목·본문·이동 화면·여행방 ID)에 기본 타입으로 모은다. `open-in-view`가 꺼져 있어 엔티티를 트랜잭션 밖으로 넘기지 않는다.
4. 리스너가 `afterCommit` 콜백을 등록하고 커밋된다. 롤백되면 콜백이 실행되지 않아 이력과 푸시 모두 없다.
5. `afterCommit` 콜백이 `NotificationPushDispatcher`를 통해 발송 작업을 `notificationPushExecutor`에 제출한다. 거절되면 로그만 남긴다(B-6).
6. 발송 작업이 `FcmService`에서 트랜잭션 없이 FCM을 호출하고(B-1·B-2), 무효 토큰이 있으면 `DeviceTokenService.deleteInvalidTokens`의 짧은 트랜잭션으로 지운다.

### 처리 순서 — Apple·Google 로그인

1. 소셜 토큰 검증 → 사용자 저장(별도 트랜잭션) → JWT 발급까지는 지금과 같다.
2. 인가 코드가 있으면 `LoginCredentialExchangeDispatcher`가 교환 작업을 `socialCredentialExchangeExecutor`에 제출하고 바로 응답한다. 거절되면 로그만 남긴다.
3. 교환 작업(`AppleCredentialService`·`GoogleLoginCredentialService`)이 토큰 엔드포인트를 트랜잭션 밖에서 호출하고 결과를 암호화한다.
4. 저장 트랜잭션이 `users` 행을 잠금 조회한다. 탈퇴했으면 저장하지 않고 트랜잭션 밖에서 revoke하고, 아니면 저장한다(B-9).

## 부하 테스트

시나리오 조건, 실행 방법, 결과를 읽을 때 주의할 점은 [`loadtest/README.md`](../../../loadtest/README.md)에 있다. 이 절은 시나리오별 비교 지표와 측정 결과만 둔다.

| 시나리오 | 비교 지표 |
|----------|-----------|
| S1 FCM 정지 | 여행방 목록 조회 p95·p99, 사용 중·대기 DB 커넥션 수 |
| S2 카카오 지연 | 여행방 목록 조회 p95·p99, 보내지 못한 조회 수, 카카오 로그인 응답 분포 |
| S3 토큰 서버 지연 | Google 로그인 p50·p95, 저장된 credential 수 |

p50·p95·p99는 응답 시간 백분위다. 예를 들어 p95가 5ms면 요청의 95%가 5ms 안에 끝났다는 뜻이다.

### 측정 결과 (2026-10-11)

측정 환경은 Apple M4(10코어)·16GB, Docker 29.6.2(MySQL 8.0·Redis 7.4·WireMock 3.9.1·k6 0.54.0), JDK 21.0.11이다. 앱은 운영과 같게 Hikari(DB 커넥션 풀) 커넥션 10개, Tomcat 요청 처리 스레드 200개로 띄웠다. 시나리오마다 앱을 다시 띄워 DB를 새로 만들었다. 적용 전 수치는 이 브랜치에서 부하 테스트 장치만 들어간 상태(커밋 `97c73af`)로 쟀다. k6 요약 원본(`loadtest/results/`)은 커밋하지 않으므로 아래 표가 유일한 기록이다.

**S1 FCM 정지** — 여행방 목록 조회(초당 300건) 기준이다.

| 상태 | p50 | p95 | p99 | 사용 중 DB 커넥션(평균) | 커넥션 대기 요청(최대) |
|------|-----|-----|-----|------------------------|------------------------|
| 참고: FCM 정상, 적용 전 | 2.87ms | 6.29ms | 69.14ms | 3.4 | 1 |
| FCM 정지, 적용 전 | 3.06ms | 28.87ms | 129.42ms | **9.0** | 63 |
| FCM 정지, 적용 후 | 2.70ms | **4.64ms** | **30.59ms** | **1.0** | 28 |

적용 전에는 FCM을 기다리는 알림 작업이 커넥션 10개 중 8개를 계속 쥐었다. 적용 후에는 FCM을 기다리는 동안 커넥션을 쥐지 않아, FCM 정상일 때(적용 전)보다도 커넥션 사용이 적다. 알림 이력은 수정 1,201건 × 멤버 9명 = 10,809건이 모두 저장됐다. 푸시는 993건이 대기열 포화로 건너뛰어졌고 8건이 30초 상한에서 취소됐다.

**S2 카카오 지연** — 여행방 목록 조회(초당 300건, 60초간 18,000건 목표) 기준이다.

| 상태 | p50 | p95 | p99 | 보내지 못한 조회 | 카카오 로그인 |
|------|-----|-----|-----|------------------|---------------|
| 적용 전 | 2.92s | 3.57s | 4.80s | **9,848건** | 3,000건 전부 200, p50 5.08s |
| 적용 후 | 2.84ms | **7.64ms** | 169.56ms | **10건** | 200 299건, 503 4,625건, 연결 실패 541건 |

적용 전에는 카카오를 기다리는 로그인이 요청 처리 스레드를 차지해, 목록 조회 절반 이상을 아예 보내지 못했다. 적용 후 연결 실패 541건은 부하를 거는 k6 쪽에서 난 연결 시간 초과다. 서버 로그에는 남지 않았고, 목록 조회는 실패 0건이었다. 원인은 README의 "결과를 읽을 때 주의할 점"에 있다.

**S3 토큰 서버 지연** — Google 로그인 기준이다.

| 상태 | p50 | p95 | 저장된 credential |
|------|-----|-----|-------------------|
| 적용 전 (VU 10개) | 4.07s | 4.19s | 80건 중 80건 |
| 적용 후 (VU 10개) | **9.61ms** | **15.13ms** | 28,519건 중 36건 |
| 적용 후 (초당 1건) | 29.92ms | 42.06ms | 31건 중 31건 |

VU는 k6의 가상 사용자로, VU 10개는 동시에 쉬지 않고 요청하는 사용자 10명이다. 적용 후 VU 10개에서 28,387건은 대기열 포화로 건너뛰어졌고, 나머지 96건은 측정이 끝날 때 아직 대기열에 있거나 교환 중이었다.

로그인 응답은 토큰 서버를 기다리지 않게 됐다. 대신 교환 처리량에 상한이 생겼다. 토큰 서버가 4초씩 걸리면 스레드 4개로 초당 1건만 교환할 수 있다. VU 10개가 쉬지 않고 로그인하자(초당 약 950건) 대부분의 교환이 건너뛰어졌다. 적용 전에는 느려도 모두 저장됐던 부분이다. 초당 1건이면 모두 저장됐다. 이 트레이드오프는 리스크 표에 적었다.

## 검증 시나리오

### 정상

- [x] 방 정보 수정 → 같은 트랜잭션에서 알림 이력이 저장되고, 커밋 후 FCM이 트랜잭션 없이 호출된다 — `NotificationDispatchIntegrationTest`, `NotificationEventListenerTest`
- [x] 무효 토큰 응답 → 발송 후 짧은 트랜잭션에서 토큰이 실제로 삭제된다 — `NotificationDispatchIntegrationTest#unregisteredToken_isDeletedAfterSend`
- [x] 정기 리마인드 1,200명 → 500명 단위로 트랜잭션 3개가 열린다 — `ScheduleReminderBatchTest`
- [x] Google 로그인 시 토큰 교환이 3초 걸려도 로그인이 1초 안에 응답하고, 이후 credential이 저장된다 — `LoginCredentialExchangeIntegrationTest`
- [x] 카카오 동시 검증 20개 이하 → 지금과 같이 동작한다 — `KakaoTokenVerifierTest`, `KakaoUserInfoConcurrencyLimitTest`

### 엣지 · 실패

- [x] 원래 트랜잭션 롤백 → 알림 이력도 푸시도 없다 — `NotificationDispatchIntegrationTest`
- [x] FCM이 응답하지 않아도 DB 커넥션을 쥐지 않는다 — `NotificationDispatchIntegrationTest#fcmHang_holdsNoDatabaseConnection`
- [x] FCM 대기가 30초 상한을 넘으면 발송을 취소하고 로그를 남긴다 — `FcmServiceTest`
- [x] 발송 대기열이 가득 차면 푸시는 건너뛰고 예외가 밖으로 나가지 않는다 — `NotificationPushDispatcherTest`. 이력이 남는 것은 S1 측정에서 확인(10,809건 전부 저장)
- [x] 같은 방 동시 요청 각 30회 → 데드락·실패 0건 — `NotificationLockOrderIntegrationTest`. B-4의 flush를 빼면 일정 확인 완료끼리에서 30회 중 29회 데드락이 나 테스트가 실패하는 것을 확인했다. 조합은 다음 5가지다.
  - 일정 확인 완료끼리
  - 방 정보 수정과 일정 확인 완료
  - 방 정보 수정과 수신자 탈퇴
  - 확정과 수신자 탈퇴
  - 방 정보 수정과 참여
- [x] 방장 탈퇴가 `UPDATE trip`을 `UPDATE users`보다 먼저 보낸다 — `UserWithdrawalLockOrderIntegrationTest`(B-16 없이는 실패). 멤버의 일정 확인 완료와 방장 탈퇴 동시 실행 30회에서 잠금 실패·예상 밖 예외 0건 — `NotificationLockOrderIntegrationTest`
- [x] 정기 리마인드의 한 묶음이 실패해도 나머지 묶음은 계속 보낸다 — `ScheduleReminderBatchTest`
- [x] 교환 대기열이 가득 차면 교환을 건너뛰고 예외가 밖으로 나가지 않는다 — `LoginCredentialExchangeDispatcherTest`
- [x] 교환이 끝나기 전에 탈퇴 → credential이 남지 않고 받은 토큰이 revoke된다. 탈퇴 커밋 뒤에 교환이 끝난 경우(B-9)와, 탈퇴 도중 교환 저장이 끝난 경우(B-10)를 각각 재현 — `LoginCredentialExchangeIntegrationTest`. 각 방어 코드를 빼면 해당 테스트가 실패하는 것을 확인했다
- [x] 카카오 동시 검증 21번째 → 1초 안에 503 `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` (500이 아님) — `KakaoUserInfoConcurrencyLimitTest`. `@ConcurrencyLimit`을 빼면 실패하는 것을 확인했다
- [x] `@Async`가 코드에 남아 있지 않다 — `ArchitectureTest#asyncAnnotationIsNotUsed`

### 수동 / 통합 (해당 시)

- [x] 부하 테스트 S1~S3 적용 전후 측정 — 위 "측정 결과"
- [x] `#131`과 임시로 합친 상태(임시 worktree, 커밋 없음)에서 전체 테스트 569개 통과. `NotificationLockOrderIntegrationTest`·`UserWithdrawalLockOrderIntegrationTest`도 이 상태에서 함께 돌았다
- [x] (`#131`과 합친 상태) 커밋 직전 flush의 버전 충돌이 Spring 예외로 바뀌어 재시도된다 — 방 정보 수정과 참여 동시 실행 30회 전부 성공. `patchTrip`의 재시도를 빼면 30회 중 8회가 `ObjectOptimisticLockingFailureException`으로 실패하는 것을 확인했다

## 완료 기준

- [x] `./gradlew test` 통과
- [x] `./gradlew build` 성공
- [x] 검증 시나리오 중 정상·엣지 항목이 전부 테스트로 존재
- [x] `REMOVED` 항목 실제 삭제 확인
- [x] `oasdiff` 결과가 503 설명 변경 외에 없음
- [x] 측정 결과 기록
- [x] 변경 범위 MODIFIED의 문서 항목 전부 갱신

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| GitHub 이슈 · Milestone · `priority:` | 확정 | [`#134`](https://github.com/Central-MakeUs/TripFit-server/issues/134) · `출시 이후` · `priority: could` (2026-10-08 사용자 지정) |
| 발송 대기열이 가득 찼을 때 | 확정 | 이력은 원래 트랜잭션에서 저장, 푸시만 건너뜀 (2026-10-08 사용자 결정) |
| 교환 대기열이 가득 찼을 때 | 확정 | 건너뜀 (2026-10-08 사용자 결정). Apple은 다음 로그인에 복구되고, Google은 재동의 전까지 복구되지 않는다 — 기존 교환 실패와 같다 |
| 토큰 서버가 "느리지만 응답하는" 상태의 교환 처리량 | 확정(수용) | 이때 로그인이 몰리면 적용 전보다 더 많이 건너뛴다(S3: 초당 약 950건에서 28,387건 건너뜀, 적용 전에는 느려도 전부 저장). 스레드 4개 기준 처리량은 토큰 서버가 4초일 때 초당 1건, 수백 ms일 때 초당 수십 건이다 |
| 알림 이력 INSERT 실패 시 원래 작업도 실패 | 확정(수용) | B-3의 대가. 단순 INSERT라 실패 가능성은 낮다 |
| 이력 저장의 잠금 영향 | 확인 완료 | 동시 요청 5종 각 30회에서 데드락 0건(이 브랜치·`#131`과 합친 상태 모두). B-4의 flush가 빠지면 29/30 데드락 |
| B-2·B-6·B-8·B-11 수치 | 유지 | 부하 테스트에서 의도대로 동작해 최초값을 유지한다 |
| 정기 리마인드 대상이 아주 많을 때 | 수용 | 500명마다 발송 작업 1건이 대기열에 들어간다. FCM이 느린데 대상이 10만 명(작업 200건)을 넘으면 뒤쪽 푸시가 건너뛰어질 수 있다. 이력은 모두 저장된다 |
| 배포(종료) 중 제출되는 발송·교환 | 확인 완료 | 두 실행기는 종료 시 작업 완료를 기다리도록 설정해, Spring이 종료 직후 제출을 막지 않고(늦은 종료) 남은 작업을 최대 30초 기다린다(spring-context 7.0.8 `ExecutorConfigurationSupport` 확인). 그보다 늦게 남은 작업은 사라진다 — 알림 재시도 `[미정]`과 같은 범주 |
| `#131`과 겹치는 파일 | 확인 완료 | 임시로 합쳤을 때 `UserWithdrawalService`·`docs/specs/README.md`·`user-account-withdrawal.md`가 충돌했다. `UserWithdrawalService`는 `#131`의 재시도 줄(`versionConflictRetryExecutor.run(...)`)을 남기고 그 뒤에 B-10의 5단계를 붙이면 된다. 이렇게 풀면 전체 테스트 569개가 통과한다. 늦게 머지되는 쪽에서 같은 방식으로 푼다 |
| 알림 실패 재시도 | `[미정]` | `notification.md`와 동일 — 이번에 정하지 않는다 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-10-11 | **구현** — B-1~B-15 반영, 부하 테스트 적용 전후 측정 결과 기록. 계획과 달라진 점은 세 가지다. 부하 테스트 지연을 5초에서 4초로(응답 타임아웃 5초 때문), 목록 조회 부하를 VU 방식에서 초당 요청 고정으로 바꿨고, Apple 토큰 엔드포인트는 설정 키로 빼지 않았다. 측정 중 Tomcat이 503에 `Connection: close`를 붙인다는 점과 교환 처리량 트레이드오프를 확인해 기록. 문서 리뷰 반영(용어 "실행기"로 통일, 외부 호출 표, `#130`·`#131` 설명, 부하 테스트 조건은 README로 일원화). 코드 리뷰 반영: B-16 추가(방장 탈퇴 잠금 순서), B-7 묶음별 실패 격리, 발송·교환 작업의 예상 밖 예외를 로그로 처리, `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` 설명에 상한 초과 추가 |
| 2026-10-08 | 초안 — 범위(FCM·Apple/Google 코드 교환·카카오), 대기열 초과 시 정책 2건, 브랜치 기준, 부하 테스트 장치 커밋 사용자 확정. FCM 커넥션 점유는 실험으로, 라이브러리 기본값은 로컬 jar로 확인 |
