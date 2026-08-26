# User Refactor Log

## 2026-08-05 — A-1~2, B-1~6 반영

감사([`audit.md`](audit.md)) 기준 A(반드시 수정) 2개, B(유지보수성) 6개 전부 반영. 사용자 승인: "A/B 전부".

### 쉽게 설명하면 (`plain-language-reporting.md`)

- **A-1 (가장 중요):** Google Calendar를 연동하거나 30분마다 자동으로 일정을 다시 가져올 때, 구글 서버에 최대 9번까지 순서대로 "일정 좀 알려줘" 요청을 보내는 동안 우리 DB에 접속하는 자리를 하나 계속 붙잡고 있었어요. 구글 서버가 느려지면 이 DB 접속 자리가 오래 묶여서, Google Calendar와 상관없는 다른 기능(여행방 만들기 등)까지 DB 접속 자리가 부족해져 같이 느려질 위험이 있었어요. 이제 구글 서버와 다 얘기를 끝낸 다음에만 DB에 짧게 저장하도록 순서를 바꿔서 그 위험을 없앴어요.
- **A-2:** 회원 탈퇴 시 구글 캘린더·구글 로그인·카카오·애플 이렇게 4곳에 "이 사람 연결 끊어주세요" 요청을 순서대로 보내는데, 이것도 하나의 DB 접속 자리를 계속 붙잡은 채로 진행되고 있었어요. 4곳 중 하나라도 응답이 느리면 탈퇴 처리 하나가 DB 접속 자리를 20초 넘게 묶어둘 수 있었는데, 이제 4곳에 연결 끊어주세요 요청을 다 보낸 다음에만 DB 저장(탈퇴 처리)을 짧게 하도록 순서를 바꿨어요.
- **B-1~6:** 기능 변화는 없고 코드 정리예요 — 완전히 똑같은 코드를 가진 함수 2개를 하나로 합침, 이미 저장된 값에 불필요하게 또 저장 명령을 내리던 부분 정리, 절대 실행되지 않는 죽은 코드 삭제, 매번 반복해서 새로 만들던 시간대 값을 한 번만 만들어 재사용하도록 정리, 카카오 연결 해제 실패 로그를 다른 소셜 로그인 실패 로그와 같은 형식으로 통일(장애 조사 시 한 곳에서 검색 가능해짐), Google Calendar 연동 화면 API에 테스트가 없어서 새로 추가.

### 반영 항목

| # | 요약 | 변경 파일 |
|---|------|-----------|
| A-1 | `GoogleCalendarService.connect()`/`syncUser()` — Google 서버 통신(코드 교환·access token 갱신·freeBusy 조회 최대 9청크)을 트랜잭션 밖에서 먼저 끝내고, DB 쓰기(credential 저장·busy_day 갱신·flag 변경)는 `GoogleCalendarSyncPersistenceService`(신규)의 짧은 `@Transactional`에 위임 (auth 도메인 `AuthLoginPersistenceService`와 동일 패턴 — self-invocation 회피를 위해 별도 빈으로 분리) | `GoogleCalendarService.java`, `GoogleCalendarSyncPersistenceService.java`(신규), `AccessTokenResolution.java`(신규) |
| A-2 | `UserWithdrawalService.withdraw()` — 소셜 provider revoke 4종(Google Calendar·Google 로그인·Kakao·Apple, 외부 HTTP 최대 4회)을 먼저 끝낸 뒤, cascade(방 나가기·삭제)·개인 데이터 hard delete·User soft delete는 `UserWithdrawalPersistenceService`(신규)의 짧은 `@Transactional`로 원자 처리 | `UserWithdrawalService.java`, `UserWithdrawalPersistenceService.java`(신규) |
| B-1 | `UserSummaryService`의 완전히 동일한 구현이던 `markAllFreeIfNoSchedules`/`markAllFreeIfSchedulesCleared`를 `markAllFreeIfNoSchedules` 하나로 통합, `ScheduleService.deleteRegular()` 호출부 갱신 | `UserSummaryService.java`, `ScheduleService.java` |
| B-2 | `GoogleCalendarService`의 이미 관리 중(managed)인 credential에 대한 불필요한 명시적 `save()` 반복 제거 — A-1 트랜잭션 재구성으로 자연히 dirty checking에 위임됨(추가 변경 불요, 결과 확인만) | (A-1과 동일 파일) |
| B-3 | `GoogleCalendarBusyMapper.applyInterval()`의 도달 불가능한 죽은 `if` 분기 삭제 | `GoogleCalendarBusyMapper.java` |
| B-4 | `GoogleCalendarOAuthClient.queryFreeBusyChunk()`의 인라인 `ZoneId.of("Asia/Seoul")` 중복 생성을 `SEOUL` 상수로 추출(같은 패키지 다른 클래스와 스타일 통일) | `GoogleCalendarOAuthClient.java` |
| B-5 | `KakaoUnlinkClient.unlink()`의 평문 로그를 `SocialIntegrationLog`/`SocialLogContext` 구조화 로그로 통일, `logback-spring.xml`에 `user.client` 패키지 `STRUCTURED_JSON` 대상 추가 | `KakaoUnlinkClient.java`, `logback-spring.xml` |
| B-6 | `GoogleCalendarController`에 대응 테스트가 없어 신규 작성 — connect/disconnect 성공·검증 실패(400)·502·409 케이스 | `GoogleCalendarControllerTest.java`(신규) |

### 변경 규모

- 기존 파일 수정 8개 (main 7 · test 1 문서 수정 제외 실제로는 test 2개 대체): +163 / -274줄 (`git diff --stat`)
- 신규 파일 8개 (main 3 · test 5): — `GoogleCalendarSyncPersistenceService`(+단위 테스트+통합 테스트), `AccessTokenResolution`, `UserWithdrawalPersistenceService`(+단위 테스트+통합 테스트), `GoogleCalendarControllerTest`
- API 계약(Request/Response/HTTP Status/ErrorCode/Endpoint) 변경 없음 — Controller·DTO·`ErrorCode` enum·`@Operation`/`@Schema` 파일 전부 미변경

### 검증 결과

- `./gradlew compileJava compileTestJava` — 통과
- `./gradlew test` (Testcontainers 실제 MySQL 8 컨테이너 포함, `ArchitectureTest` 포함) — **전체 통과**
- **`oasdiff` API 계약 검증:**
  1. `./gradlew test --tests OpenApiSpecExportTest` → `build/openapi/openapi.json` 생성 성공
  2. `oasdiff breaking docs/api/openapi.json build/openapi/openapi.json` → **"No changes detected"**
  3. `oasdiff diff docs/api/openapi.json build/openapi/openapi.json` → **`{}`** (스키마 변화 전무 — 가장 엄격한 확인)
- **실제 DB(Testcontainers MySQL) 통합 테스트 추가 반영 (2026-08-05 앱 스토어 심사 대응 재확인 요청 후):** A-1·A-2로 새로 만든 `GoogleCalendarSyncPersistenceService`·`UserWithdrawalPersistenceService`는 Mockito 단위 테스트만으로는 "다른 빈에서 호출했을 때 `@Transactional` 프록시가 실제로 걸리는지", "Hibernate dirty checking·lazy loading이 트랜잭션 경계를 넘어 정상 동작하는지"를 검증할 수 없다 — 이 둘은 mock이 항상 성공한 것처럼 흉내 내기 때문. 실제 MySQL로 아래 5개 테스트를 추가해 재확인했다.
  - `GoogleCalendarSyncPersistenceIntegrationTest` 3개 — credential 저장 후 재조회로 실제 커밋 확인, busy_day 실제 반영 확인(이 과정에서 테스트 코드 자체의 시간대 슬롯 가정 오류 1건을 발견·수정 — `TimeSlot.MORNING`은 00:00~13:00이라 10~11시가 오전 슬롯이 맞음, 프로덕션 코드 결함 아님), 권한 영구 실패 시 실제 삭제·플래그 해제 확인
  - `UserWithdrawalPersistenceIntegrationTest` 2개 — soft delete·PII 스크럽 실제 반영 확인, 재호출 idempotent 확인
  - 5개 전부 통과. 이후 `oasdiff` 재실행 결과도 동일하게 `{}`(diff 없음) 재확인.

**결론: 리팩토링 전/후로 API 응답·요청·에러코드·엔드포인트 스펙이 문자 그대로 100% 동일하고, 새로 분리한 트랜잭션 경계도 실제 DB로 정상 동작함을 증명함.**

### 남겨둔 C/D 항목

`audit.md`의 C 3개(스케줄러 전체 조회 페이징 미도입, revoke URL 문자열 결합, `SCHEDULE_ACTIVATION_REQUIRED` 도메인 경계), D 4개(`User` equals/hashCode 미구현 유지, `UserLookupService` 유지, `GoogleCalendarBusyMapper` 비-빈 유지, 4개 Service 미통합) — 이번 라운드에서 변경하지 않음. 이유는 `audit.md` 해당 절 참고.

### Later 후속 제안 (audit.md §15, 이번 라운드 미반영)

- Google Calendar 외부 호출 Circuit Breaker (A-1 선행으로 우선순위 낮아짐)
- 탈퇴 시 provider revoke 4종 `@Async`화 (A-2와 중복 설계 방지를 위해 A-2 이후로 순연)
- `GoogleCalendarSyncScheduler` 다중 인스턴스 분산 락 (현재 단일 인스턴스 배포에서는 불필요)
