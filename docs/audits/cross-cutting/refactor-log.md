# cross-cutting Refactor Log

공통 계층(`cross-cutting`/`common`) 아키텍처 감사에서 승인된 항목을 실제로 반영한 이력이다. 라운드별 수정 사항과 검증 결과를 기록한다. 감사 결과 원본은 `audit.md` 및 `audit-round2.md`에 있다.

## 2026-08-14 (2차) — `Trip`·`User` 포함 전체 엔티티 `@Setter` 완전 제거

1차 라운드(아래)에서 `Trip`·`User`는 "도메인 메서드가 거의 없어 라벨링만 바뀌는 수준"이라는 이유로 범위 밖으로 남겼는데, 사용자가 "그냥 `@Setter`를 아예 전부 제거할 수는 없냐"고 재요청 — 남은 항목을 전부 도메인 메서드로 옮기는 더 큰 작업을 이어서 진행했다.

### 쉽게 설명하면

- **`Trip`(여행방)**: 확정(`confirm`)·확정취소(`unconfirm`)·종료(`expire`)·메타수정(`applyPatch`) 같은 실제 업무 흐름을 엔티티 메서드로 새로 만들고, `TripRecommendationService`·`TripCommandService`·`TripHomeMaintenanceService` 3곳에 흩어져 있던 "여러 필드를 한 번에 바꾸는" 코드를 그 메서드 안으로 옮겼어요. 예를 들어 "여행 확정"은 상태값 하나만 바뀌는 게 아니라 확정 날짜·참석 인원 수·연차 필요 인원 수·불확실 인원 수까지 5개 값이 한 번에 같이 바뀌는데, 이제는 `trip.confirm(...)` 한 줄만 호출하면 이 5개가 항상 같이 정확하게 바뀌도록 보장돼요. 예전에는 이 5줄 중 하나를 실수로 빠뜨려도 컴파일러가 못 잡아냈는데, 이제는 그럴 걱정이 없어요.
- **`User`(사용자)**: 이름 변경, 소셜 재로그인 시 프로필 동기화, 구글 캘린더 연동 on/off, 탈퇴 시 개인정보 지우기(PII 스크럽) 같은 흐름 각각을 전용 메서드로 만들었어요. 특히 탈퇴 처리는 원래 "삭제 시각 찍고 + 이메일 지우고 + 이름 지우고 + 닉네임 지우고 + 프로필사진 지우고 + 연동 끄고" 6줄을 매번 순서대로 정확히 다 호출해야 했는데, 이제 `scrubPiiForWithdrawal()` 하나만 부르면 됩니다.
- **테스트 코드의 "DB 흉내" 자리는 그대로 남김**: 실제 DB 없이 테스트를 돌리다 보니, "이 사용자는 이미 DB에 저장돼서 ID값이 있다"를 흉내내야 하는 경우가 많아요(`user.setId(...)` 같은 것). 이건 업무 규칙이 아니라 테스트 준비 작업이라 도메인 메서드로 옮길 수가 없어서, id 필드 하나만 예외로 남겨뒀어요(사용자와 합의됨). 그 외 소수의 "테스트에서만 특정 값을 강제로 넣어야 하는" 자리(예: 정기 일정 슬롯 상태를 특정 조합으로 고정)는 Spring이 제공하는 테스트 전용 도구(`ReflectionTestUtils`)로 옮겨서, 운영 코드에는 뒷문이 전혀 남지 않게 했어요.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| 1 | `SoftDeleteEntity.markDeleted()`/`clearDeleted()` 신설 — soft delete·부활 로직 공통화, `Trip`·`TripMember`·`User` 6개 호출부 교체 | `common/domain/SoftDeleteEntity.java`, `trip/service/TripCommandService.java`, `auth/service/AppleNotificationService.java`, `user/domain/User.java` |
| 2 | `GoogleCalendarCredential.applyRotatedRefreshToken()` 신설 — refresh token 회전 갱신 전용 메서드, 필드 레벨 `@Setter` 제거 | `user/googlecalendar/domain/GoogleCalendarCredential.java`, `user/googlecalendar/service/GoogleCalendarSyncPersistenceService.java` |
| 3 | `PersonalSchedule`/`RegularSchedule.slotStatuses` 필드 레벨 `@Setter` 제거 — 테스트 전용 우회 3곳을 `ReflectionTestUtils`로 전환 | 도메인 2개 + 테스트 2개 |
| 4 | `TripMember.joinedAt` 필드 레벨 `@Setter` 제거 — 테스트가 생성자 파라미터로 직접 값을 넘기도록 헬퍼 오버로드 추가 | `trip/membership/domain/TripMember.java`, `trip/service/TripServiceTest.java` |
| 5 | `User` 나머지 9개 필드 도메인 메서드화: `applySocialProfile`·`applyProfilePatch`·`connectGoogleCalendar`·`disconnectGoogleCalendar`·`applyAllFree`·`scrubPiiForWithdrawal` 신설, `id` 제외 필드 레벨 `@Setter` 전부 제거 | `user/domain/User.java` + 5개 Service + 약 20개 테스트 파일 |
| 6 | `Trip` 나머지 18개 필드 도메인 메서드화: `confirm`·`unconfirm`·`expire`·`applyPatch`·`applyDestination`·`applyLastRecommendationMode` 신설, `id` 제외 필드 레벨 `@Setter` 전부 제거. 순수 테스트 픽스처(예: "이미 CONFIRMED 상태"를 가정하되 확정 통계는 무관한 테스트)는 `ReflectionTestUtils`로 전환 | `trip/domain/Trip.java` + `TripRecommendationService`·`TripCommandService`·`TripHomeMaintenanceService` + 4개 테스트 파일 |

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test`(전체) — 통과, 실패 0건
- API 계약 변경 없음(순수 내부 구현, Controller·DTO·`ErrorCode` 미변경) — oasdiff 재실행 불필요

### 최종 상태

전체 엔티티 중 `@Setter`가 남은 곳은 다음 둘뿐:
- **각 엔티티의 `id` 필드** — DB가 채워주는 값을 단위 테스트에서 흉내내기 위한 목적, 도메인 메서드로 대체 불가능한 테스트 배관(사용자와 합의된 예외)
- **`RefreshToken.revokedAt`** — 이번 범위와 무관, `@Schema` 설명에 "출시 이후 RTR(rotation) 예정"이라고 명시된 계획된 필드라 손대지 않음

## 2026-08-14 — repo-wide: Service 생성자 주입 표준화(`@RequiredArgsConstructor`) + Entity `@Setter` 남발 정리

사용자가 "SOLID·객체지향 4대 원칙·`@RequiredArgsConstructor` 미적용·`@Setter` 남발"을 지적하며 `/refactor-audit`로 개선을 요청. 그런데 `@Setter` 남발은 이미 auth·trip·user-schedule·notification 4개 도메인 감사에서 "저장소 전역 컨벤션이라 도메인 하나만 좁혀 고치면 일관성이 깨진다"는 이유로 반복해서 C로 미뤄져 있던 사안이었다(`harness-follow-up.md` "반복 주제" 기준 충족) — 그래서 도메인별 순차 진행 대신, 먼저 사용자와 함께 **저장소 전체에 적용할 정책**을 정하고 한 번에 적용했다.

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **생성자 자동 생성(`@RequiredArgsConstructor`)**: 스프링에서는 한 클래스가 다른 클래스(의존성)를 여러 개 가져다 쓸 때, 그걸 전달받는 "생성자"라는 코드를 직접 손으로 써줘야 했어요. 필드 개수만큼 똑같은 패턴의 코드가 반복돼서, 필드 하나를 추가·삭제할 때마다 생성자도 같이 고쳐야 하는 번거로움과 실수 여지가 있었습니다. Lombok이라는 도구의 `@RequiredArgsConstructor`를 쓰면 이 반복 코드를 자동으로 만들어줘서, 27개 서비스 클래스에서 총 190줄 넘는 보일러플레이트를 지웠어요.
- **`@Setter` 남발 정리**: 지금까지 데이터 저장 클래스(엔티티) 대부분이 "모든 필드를 아무 데서나 마음대로 바꿀 수 있게" 열어두고 있었어요(클래스 전체에 `@Setter`를 붙이는 방식). 이미 "이 필드는 이런 규칙으로만 바뀌어야 한다"는 전용 메서드(예: 예약 확정, 알림 토큰 갱신)가 있는데도, 그 규칙을 건너뛰고 아무 값이나 바로 집어넣을 수 있는 뒷문이 같이 열려 있던 셈이죠. 8개 엔티티를 검토해서, 실제로 그 뒷문을 아무도 안 쓰는 필드는 완전히 잠그고, 테스트 코드에서만 꼭 필요한 자리(예: DB가 채워주는 ID값을 테스트에서 흉내내는 경우)만 그 필드 하나에만 열쇠를 남겨뒀어요.
- **작업 중 발견한 버그**: 자동 변환 과정에서 `FcmService`(푸시 알림 발송) 하나가 원래 "필요할 때만 늦게 초기화"(`@Lazy`)하도록 돼 있던 게, 자동 생성 방식으로 바꾸면서 이 설정이 유실될 뻔했어요. 이 설정이 없으면 앱이 켜질 때마다 Firebase(구글 푸시 서버) 인증키를 바로 요구해서, 로컬 개발 환경처럼 그 키가 없는 곳에서는 아예 서버가 안 켜지는 문제가 생길 뻔했습니다. 테스트로 미리 잡아서 그 파일만 예외로 손으로 쓴 생성자를 유지하도록 되돌렸어요.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| 1 | `spring-boot-java.md` Lombok 규칙 개정 — Service도 `@RequiredArgsConstructor` 허용(기존엔 Entity·`@ConfigurationProperties`만). 생성자 바디에 검증·파생 로직이 있거나(`JwtService`) `@Lazy`처럼 파라미터 전용 애너테이션이 필요하면(`FcmService`) 수동 생성자 예외 명시 | `.claude/rules/spring-boot-java.md` |
| 2 | `TripService.java`에 남아있던 커밋 안 된 주석 처리 구 생성자(dead code) 삭제 — `@RequiredArgsConstructor` 적용은 이미 돼 있었음 | `trip/service/TripService.java` |
| 3 | 수동 생성자를 쓰던 Service 27개를 `@RequiredArgsConstructor`로 전환(순수 필드 대입 생성자만 대상) — `JwtService`(secret 길이 검증), `FcmService`(`@Lazy` 파라미터)는 로직·애너테이션 유지 목적으로 수동 생성자 예외 | auth 9개·notification 3개·trip 8개·user 7개 Service 파일(목록은 diff 참고) |
| 4 | Entity 8개의 클래스 레벨 `@Setter`(모든 필드 raw 접근 허용)를 제거하고, 이미 도메인 메서드(`applyPin`·`applyFeedback`·`updateTokens`·`applySlots` 등)가 커버하는 필드는 setter 자체를 삭제, 남은 필드만 **필드 레벨** `@Setter`로 좁힘 | `Recommendation`·`RecommendationFeedback`·`TripMemberScheduleSnapshot`·`GoogleCalendarBusyDay`(setter 전부 삭제, 외부 raw 호출 0건 확인) / `TripMember`(id·joinedAt만 유지) / `GoogleCalendarCredential`(refreshTokenCiphertext만 유지) / `PersonalSchedule`·`RegularSchedule`(id·slotStatuses만 유지) / `User`(socialId·provider 완전 삭제, 나머지 9필드는 필드 레벨 유지 — 도메인 메서드 커버리지가 낮아 실질 캡슐화 이득은 제한적) |

### 남겨둔 항목과 이유

- **`Trip` 엔티티는 이번 라운드에서 손대지 않음**: 18개 필드 중 도메인 메서드가 있는 건 `touchLastActivity()`(1개)뿐이라, 클래스 레벨→필드 레벨로 바꿔도 사실상 모든 필드가 그대로 열린 채 남아 실질적 캡슐화 이득이 없다(순수 라벨링 변경). 진짜 개선(예: `confirm()`/`unconfirm()`/`expire()` 도메인 메서드 신설 + `TripRecommendationService`/`TripCommandService`/`TripHomeMaintenanceService` 3개 서비스의 필드 직접 대입 로직 이관)은 비즈니스 로직 이동을 동반하는 별도 설계 결정이라 이번 스코프 밖으로 분리 — 후속 이슈 후보.
- **`RefreshToken`·`SoftDeleteEntity`는 이미 준수 상태**라 변경 없음 — `RefreshToken`은 이미 필드 레벨 `@Setter`만 쓰고 있었고, `SoftDeleteEntity`는 필드가 `deletedAt` 하나뿐이라 클래스 레벨=필드 레벨이라 실질 차이 없음.

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test`(전체) — 통과, 실패 0건(진행 중 `FcmService` `@Lazy` 유실로 컨텍스트 로딩 실패 37건 발생 → 원인 파악 후 수동 생성자로 되돌려 재검증 완료)
- API 계약 변경 없음(Controller·DTO·`ErrorCode` 미변경, 순수 내부 구현) — oasdiff 재실행 불필요

## 2026-08-08 — 후속 제안 2건 반영 (스케줄러 스레드풀 공유, Lombok 규칙 문서 drift)

`user/audit-round2.md` §15("신규 — Concurrency: `@Scheduled` 기본 단일 스레드 풀을 3개 도메인 스케줄러가 공유")와 `cross-cutting/audit-round2.md`(1차 C/D 재확인, `common/domain` Lombok vs 룰 문서 drift)에서 Later/문서 후속으로 남겨뒀던 2건을 사용자 요청으로 반영. 둘 다 API 계약·비즈니스 로직에 영향 없음.

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **스케줄러 스레드풀**: 이 서버에는 30분마다 도는 구글 캘린더 동기화, 매달 2번 도는 알림 리마인드, 매일 새벽 도는 여행방 정리 — 이렇게 자동으로 도는 작업이 3개 있는데, 스프링 부트가 기본으로 이런 작업 전용 일꾼(스레드)을 딱 1명만 배정해줘서 셋이 그 한 명을 순서대로 나눠 써야 했어요. 구글 캘린더 동기화가 사람마다 구글 서버에 순차로 요청을 보내느라 오래 걸리면, 그동안 나머지 두 작업이 밀릴 수 있는 구조였습니다. 실제로 지연이 관측된 건 아니었지만, 설정 한 줄로 일꾼을 3명으로 늘려서 각 작업이 서로를 기다리지 않게 미리 손봤어요.
- **Lombok 규칙 문서**: 코딩 규칙 문서에 "Lombok(반복 코드를 자동 생성해주는 도구)을 안 쓴다"고 적혀 있었는데, 실제로는 DB 테이블과 매핑되는 엔티티 클래스들과 설정값을 담는 클래스들에서는 이미 쓰고 있었어요. 코드를 바꾼 게 아니라, 문서가 실제 상황과 다르게 적혀 있던 걸 사실대로 고쳤습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| 1 | `@Scheduled` 3개(`GoogleCalendarSyncScheduler`·`ScheduleReminderBatch`·`TripHomeScheduler`)가 Spring Boot 기본 단일 스레드 풀(size=1)을 공유하며 서로 지연시킬 수 있던 구조 — `spring.task.scheduling.pool.size: 3`을 `application.yml`에 추가해 스케줄러별 전용 스레드 확보 | `src/main/resources/application.yml` |
| 2 | `spring-boot-java.md` Style 절의 "Lombok 미사용" 문구가 실제 코드(Entity·`@ConfigurationProperties` 17개 파일에서 `@Getter`/`@Setter`/`@NoArgsConstructor`/`@Data` 등 사용 확인)와 어긋나 있던 것을 실제 범위(Entity·Properties만 사용, Service/Controller/DTO record는 미사용)로 정정 | `.claude/rules/spring-boot-java.md` |

### 검증 결과

- `./gradlew compileJava` — 통과
- `./gradlew test` (전체) — 통과, 실패 0건
- API 계약 변경 없음(설정값·문서 변경뿐, Controller·DTO·`ErrorCode` 미변경) — oasdiff 재실행 불필요

## 2026-08-05 — 2차 라운드 (A-1, A-2, B-1, B-2 반영)

감사 문서: [`audit-round2.md`](audit-round2.md)

2차 감사 기준 A(반드시 수정) 2건, B(유지보수성) 2건 전부 반영. 사용자 승인: "A-1·A-2 진행", "B-1은 코드까지 적용", "B-2 진행". B-1은 원 스펙(`docs/specs/cross-cutting/social-integration-structured-logging.md`)의 "전사 로깅 정책 확장은 Out of Scope" 결정과 충돌 여지가 있어 착수 전 별도로 사용자 확인을 받았다.

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **A-1**: API 성공 응답을 만드는 공통 코드(`SuccessResponse`)에, 실제로는 아무도 안 쓰는 방법이 하나 더 있었는데, 그 방법은 파라미터를 넣는 순서와 실제 저장되는 순서가 서로 뒤바뀌어 있는 함정이 있었어요. 지금은 안 써서 문제가 없었지만, 나중에 누가 처음 이걸 쓰면 오해하기 쉬운 상태라 아예 지웠습니다.
- **A-2**: 프로젝트 설계도 역할을 하는 문서 2개(`architecture.md`, `auth-social-login.md`)가 1차 리팩토링 때 있었던 변경(설정 파일 삭제·이동)을 반영하지 못한 채로 남아 있었어요. 실제 코드와 설계도가 어긋나 있으면 다음에 코드를 보는 사람이 잘못된 그림을 보고 판단할 수 있어서, 설계도를 실제 코드에 맞게 고쳤습니다.
- **B-1**: 소셜 로그인 관련 로그는 이미 이메일 같은 개인정보를 가려서(마스킹) 기록하고 있었는데, 그 외 "예상 못한 서버 오류"를 전부 받아 처리하는 공통 안전망 코드는 그 가림 처리 없이 그대로 로그를 남기고 있었어요. 이 로그도 결국 같은 곳(Loki, 로그를 모아 보는 시스템)으로 나가기 때문에, 이 공통 안전망에도 똑같이 개인정보 가림 처리를 적용했습니다. 이 과정에서 기존 마스킹 로직이 "예외 메시지에 넘긴 인자"만 가리고 실제로 로그에 찍히는 예외 객체 자체(스택트레이스 포함 부분)는 안 가려지는 허점을 발견해서, 소셜 로그인 쪽에서 이미 쓰던 "스택트레이스는 보존하면서 메시지만 가리는" 안전한 방법을 공통 유틸(`PiiMasker`)로 옮겨 양쪽에서 재사용하도록 정리했어요.
- **B-2**: 소셜 로그인 마스킹의 핵심 규칙("이 경로로만 채우게 해서 반드시 가려지게 한다")을 검증하는 테스트가 하나도 없었어요. 이번에 그 규칙과, 로그에 붙는 부가 정보(제공자·행동·에러 사유 등)가 정확한 이름으로 잘 채워지고 로그가 끝나면 깨끗이 지워지는지를 확인하는 테스트를 추가했습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `SuccessResponse.of(T, String, String)` — 전체 코드베이스 미사용 + 파라미터 순서 함정을 가진 오버로드 삭제 | `common/api/SuccessResponse.java` |
| A-2 | `docs/architecture.md`·`docs/specs/auth/auth-social-login.md`의 `common/` 패키지 트리를 실제 코드와 일치시킴(`WebConfig` 삭제·`OpenApiConfig` 이동 반영, `logging/`·`security/` 서브패키지 추가) | `docs/architecture.md`, `docs/specs/auth/auth-social-login.md` |
| B-1 | `GlobalExceptionHandler.handleUnexpectedException()`에 `PiiMasker` 마스킹 적용. 기존 `SocialIntegrationLog`가 private으로 갖고 있던 "스택트레이스 보존 + 메시지만 마스킹" 로직(`maskMessages`/`MaskedThrowable`)을 `PiiMasker.maskThrowable(Throwable)` public 메서드로 승격해 두 곳에서 공용으로 사용 — 로직 중복 제거 + `common` 전 도메인 catch-all 안전망도 동일한 마스킹 보장을 갖도록 확장 | `common/logging/PiiMasker.java`, `common/logging/SocialIntegrationLog.java`, `common/exception/GlobalExceptionHandler.java`, `common/exception/GlobalExceptionHandlerTest.java`(마스킹 검증 케이스 추가) |
| B-2 | `SocialLogContext.withProviderError()`의 마스킹 강제 계약, `SocialIntegrationLog.toMdcFields()`의 MDC 키 매핑(정확한 이름으로 채워지는지·로깅 종료 후 제거되는지)을 검증하는 테스트 신설 | `common/logging/SocialLogContextTest.java`(신규) |

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test` (전체) — 통과, 실패 0건
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (diff 0건)

**결론: cross-cutting(common) 도메인 API 응답·요청·에러코드·엔드포인트 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목

`audit-round2.md`의 C 2개(`GlobalExceptionHandler.handleMessageNotReadable`/`handleTypeMismatch` 로그 미기록 — 400 클라이언트 오류라 불필요, 1차 C 재확인), D 1개(1차 D 전부 재확인, 상황 변화 없음) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round2.md` 해당 절 참고.

## 2026-08-05 — 1차 라운드 (A-1~A-3, B-1, B-2 반영)

감사 문서: [`audit.md`](audit.md)

### 반영한 항목

| 항목 | 내용 | 변경 파일 |
|------|------|-----------|
| A-1 | 죽은 CORS 설정(`WebConfig`) 삭제 — `SecurityConfig`가 이미 CORS를 전담 처리 중이라 실행되지 않던 코드였고, 두 파일이 각자 다른 허용 origin 목록을 갖고 있던 drift도 함께 제거됨 | `common/config/WebConfig.java` (삭제, -23줄) |
| A-2 | `SocialTokenCrypto.secretKey` 필드에 `volatile` 추가 — double-checked locking의 JMM 위반(동시 요청 초기 구간 레이스 컨디션 가능성) 제거 | `common/security/SocialTokenCrypto.java` (+1줄) |
| A-3 | `SocialIntegrationLog`가 `Throwable`을 로깅할 때 메시지를 `PiiMasker`로 마스킹하도록 변경(cause 체인 포함, 스택트레이스는 보존) — 기존엔 예외 객체를 직접 로깅하는 경로(18곳)에서 이메일 등 PII가 마스킹 없이 구조화 로그(Loki)로 나갈 수 있었음 | `common/logging/SocialIntegrationLog.java` (+31/-4줄, 18개 호출부는 변경 없음) |
| B-1 | `OpenApiConfig`를 `common/config/`에서 `auth/config/`로 이동 — `common`이 `auth` 도메인(`@AuthorizedUser`)에 역방향 의존하던 구조 해소. ArchUnit 룰(`commonPackageDoesNotDependOnOtherDomains`) 신설로 재발 방지(기존부터 있던 `SocialLogContext`→`user.domain.SocialProvider` 공유 enum 의존은 명시적으로 예외 처리) | `common/config/OpenApiConfig.java` → `auth/config/OpenApiConfig.java` (이동), `test/architecture/ArchitectureTest.java` (+18줄) |
| B-2 | `common` 패키지 보안 핵심 클래스 단위 테스트 신설 — 기존엔 테스트가 전혀 없었음 | `test/common/security/SocialTokenCryptoTest.java`(신규), `test/common/logging/PiiMaskerTest.java`(신규), `test/common/logging/SocialIntegrationLogTest.java`(신규, A-3 마스킹 검증), `test/common/exception/GlobalExceptionHandlerTest.java`(신규) |

### 검증 결과

- `./gradlew test` — 전체 통과 (32개 신규/변경 테스트 포함, 기존 테스트 회귀 없음)
- `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` — breaking change 없음
- `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` — 변경 전(main, stash로 확인)과 변경 후 diff 내용이 완전히 동일(`PATCH /api/v1/trips/{tripId}/recommendations/{rank}/feedback` 요청 바디 설명 문구 "PUT"→"PATCH" 1건, 이번 리팩토링과 무관하게 이미 존재하던 `docs/api/openapi.json` 스냅샷 drift) — **이번 라운드가 만든 API 계약 변화는 0건**임을 확인. 해당 drift 자체는 `trip/recommendation` 영역이라 이번 `cross-cutting` 범위 밖이므로 별도 후속으로 남김(아래 참고)

### 남겨둔 C/D 항목과 이유

`audit.md`의 C(3건)·D(5건) 참고 — 요약:

- **C**: `SocialTokenCryptoProperties`/`FcmProperties` Lombok 미사용 스타일 차이(유출 위험 없음, 기존 전례 있음), `SocialLogContext` wither 보일러플레이트(규모상 Builder 전환은 과잉), `SocialTokenCrypto.decrypt()` 방어 코드 미추가(실사용 경로 없음), `common/domain` Lombok 사용과 `spring-boot-java.md` "Lombok 미사용" 문서 간 drift(코드가 아닌 문서 쪽 후속 필요)
- **D**: `JpaConfig`/`SchedulingConfig` 통합 안 함, `equals`/`hashCode` 미추가, `ErrorCode.toErrorResponse()` 미추가, `CommonErrorCode` 상수 선추가 안 함, 미발생 예외용 핸들러 미추가, `PiiMasker` 스코프 확장 안 함 — 각 이유는 `audit.md` D절 참고

### 참고 — 범위 밖 발견 사항 (이번 라운드에서 손대지 않음)

- `docs/api/openapi.json`이 `trip/recommendation` 피드백 API의 Javadoc 설명 문구와 살짝 어긋나 있음(엔드포인트가 실제로는 `PATCH`인데 문서 문구에 구 `PUT` 표현이 남아 있었다가 코드에서는 이미 고쳐진 상태 — 스냅샷만 재생성 안 됨). `cross-cutting` 감사 범위·이번 세션 작업과 무관해 그대로 두었음 — 사용자 확인 후 별도로 `docs/api/openapi.json` 재생성 필요.

## 도메인 감사 완료

이로써 `docs/audits/README.md` 진행 현황의 6개 도메인(auth, user, user-schedule, trip, notification, cross-cutting) 감사·구현이 모두 1라운드 완료됨.

## 2026-08-27 — Round 3 (SOLID/OOP 중심) B-1 반영

감사([`audit-round3.md`](audit-round3.md)) 기준 A 항목 없음, B(유지보수성) 1개 반영. 사용자 승인: "B-1 구현".

### 쉽게 설명하면 (`plain-language-reporting.md`)

에러를 400(잘못된 요청)으로 처리하는 코드 조각 3개가, 실제로는 "어떤 종류의 잘못된 요청이냐"만 다를 뿐 안에서 하는 일이 완전히 똑같았어요(같은 에러 코드·같은 메시지 반환). Spring이 기본으로 제공하는 "여러 종류를 한 곳에서 같이 처리" 기능으로 하나로 합쳤어요 — 사용자에게 보이는 응답(상태 코드·에러 메시지)은 전혀 바뀌지 않습니다.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| B-1 | `GlobalExceptionHandler`의 `handleMessageNotReadable`/`handleTypeMismatch`/`handleMissingParameter` 3개(동일 로직, 예외 타입만 다름)를 `@ExceptionHandler({...})` 배열 문법으로 `handleClientInputError` 하나로 통합 | `GlobalExceptionHandler.java`, `GlobalExceptionHandlerTest.java`(호출 대상 메서드명만 갱신) |

### 변경 규모

- 기존 파일 수정 2개 (main 1 · test 1): `GlobalExceptionHandler.java`(약 -13줄), `GlobalExceptionHandlerTest.java`(테스트 3개 메서드명·호출부만 갱신, 검증 내용 동일)
- 신규 파일 없음
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — 세 예외 타입 모두 여전히 400 `INVALID_INPUT`으로 매핑됨

### 검증 결과

- `./gradlew compileTestJava` — 통과
- `./gradlew test`(전체, Testcontainers 실제 MySQL 8 컨테이너 포함, `ArchitectureTest` 포함) — **514개 전체 통과, 0개 실패**
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **"No changes"**(가장 엄격한 확인)

**결론: cross-cutting(common) API 응답·에러코드 스펙은 리팩토링 전/후로 100% 동일함을 실제 실행으로 증명함.**

### 남겨둔 C/D 항목 (Round 3)

`audit-round3.md`의 C 2개(`common/holiday/*` 패키지 위치 — Approved 스펙의 명시적 결정이라 amend 필요, 사용자 확인 별도 필요 / 1·2차 C 재검증), D 2개(`GlobalExceptionHandler`의 OCP 구조 유지 · 1·2차 D 재확인) — 이번 라운드에서 변경하지 않음. 이유는 `audit-round3.md` 해당 절 참고.

### Later 후속 제안 (audit-round3.md §15, 상태 변화 없음)

1·2차 §15의 제안 재확인 결과 상태 변화 없음 — 판단 그대로 유지. `holiday/*`가 이미 Redis 캐시 계층(TTL 7일, staging-then-rename 원자 교체, fail-open)을 쓰고 있음을 신규로 확인했으나 추가 개선 여지 없음.

## SOLID/OOP 3차 감사 시리즈 완료

`auth`(2026-08-15) → `user`(2026-08-26) → `user-schedule`(2026-08-26) → `trip`(2026-08-26) → `notification`(2026-08-27, 변경 없음) → `cross-cutting`(2026-08-27, 이번 라운드)까지 6개 도메인 전체의 SOLID/OOP 관점 3차 라운드가 완료됐다. 각 도메인 `docs/audits/{domain}/audit-round3.md`·`refactor-log.md` 참고.
