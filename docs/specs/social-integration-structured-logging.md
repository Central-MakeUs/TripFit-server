# 소셜 로그인·Google Calendar 연동 구조화 에러 로깅

> 상태: Draft
> MVP: N/A — 제품 기능이 아닌 개발 인프라/관측성 개선 (Wave 분류 대상 아님)
> 관련 BR: 해당 없음
> Issue: [#65](https://github.com/Central-MakeUs/TripFit-server/issues/65) (과거 Release Gate 메타 트래커 — 전부 Closed로 재사용, 2026-08-03)

## 목표

소셜 로그인(Google/Kakao/Apple)·Google Calendar 연동에서 실패가 발생했을 때, "임시 디버그 로그 추가 커밋 → 재현 대기 → 로그 확인 → 원복 커밋" 사이클 없이 Loki+Grafana에서 즉시 원인(provider·action·userId·HTTP 상태·provider 에러 사유)을 구조화된 필드로 조회할 수 있게 한다.

## 배경

- 반복된 사고: `GET /trips` 401 masking(2026-07-30, agent memory `incident-trips-401-masking`), freeBusy `timeRangeTooLong`(커밋 `bd3ddac`~`774ba57`), 오늘(2026-08-02) 확인한 freeBusy 403 `ACCESS_TOKEN_SCOPE_INSUFFICIENT` — 매번 `log.warn(message, exception)` 형태의 비구조화 텍스트 로그만 있어 원인 파악에 "임시 로깅 추가 → 재현 대기 → 확인 → 원복" 커밋 사이클(`8fe5e85`, `6df4789`)을 반복했다.
- 오늘 사고는 마침 `8fe5e85`에서 임시로 추가한 `errorBody` 로깅 덕분에 운영 로그에서 바로 원인(`ACCESS_TOKEN_SCOPE_INSUFFICIENT`)을 확인할 수 있었다 — 그 임시 조치가 없었다면 또 한 번 같은 사이클이 필요했을 것. 이번 스펙은 그 임시 조치를 상시 구조로 만든다.
- Loki+Grafana(EC2 C, `deploy/monitoring/`)는 이미 구축 완료. 현재 대시보드는 텍스트 substring 매칭(`|= "ERROR"`, `|= "WARN"`)만 사용하고, 구조화 필드 기반 조회는 없다.
- 현재 provider 실패 분류가 얕다 — 예: `GoogleCalendarOAuthClient.queryFreeBusyChunk`는 401만 `GoogleCalendarAuthException`으로 구분하고 403(스코프 부족 포함) 등 나머지는 전부 일반 `RuntimeException`으로 뭉뚱그린다. **이 스펙은 그 분류 로직 자체는 바꾸지 않는다** — 판단에 필요한 정보를 구조화 필드로 남기는 데만 집중한다. 분류 로직 변경(예: 403 스코프 부족을 영구 실패로 취급할지)은 별도 논의·스펙이 필요하다.
- 오늘 사고에서 FE의 authorize 요청·GCP 콘솔 스코프 등록은 사용자가 별도로 확인해 정상이라고 확인했음에도 런타임에서는 `ACCESS_TOKEN_SCOPE_INSUFFICIENT`가 발생했다 — "설정은 맞는데 왜 부족한지" 같은 모순을 재현 없이 확인하려면, 실제 토큰 교환 응답의 `scope` 필드 자체를 구조화 로그로 남겨야 한다(Must Have 항목).

## 요구사항

### Must Have

- [ ] `logstash-logback-encoder` 의존성 추가, `logback-spring.xml` 신설 — `com.tripfit.tripfit.auth.oauth`, `com.tripfit.tripfit.auth.service`, `com.tripfit.tripfit.user.googlecalendar` 패키지 로거 전용 JSON appender(`additivity=false`)로 구조화 로그 출력. **그 외 패키지는 기존 텍스트 패턴 그대로 유지** — nginx 로그·기존 Grafana ERROR/WARN 패널·다른 도메인 로그에 영향 없음.
- [ ] 구조화 로그 공통 필드 계약:

  | 필드 | 타입 | 설명 |
  |------|------|------|
  | `provider` | `SocialProvider`(기존 enum 재사용) | GOOGLE / KAKAO / APPLE |
  | `action` | 신규 닫힌 집합(아래 매핑 표) | 로그가 발생한 유스케이스 |
  | `userId` | UUID, nullable | 조회 전 실패 등 유저 특정 전이면 null |
  | `httpStatus` | Integer, nullable | provider 응답 HTTP 상태 |
  | `providerErrorReason` | String, nullable | provider가 반환한 세분화 에러 코드(예: `ACCESS_TOKEN_SCOPE_INSUFFICIENT`, `invalid_grant`) |
  | `providerErrorMessage` | String, nullable, **마스킹 적용 후** | provider 에러 바디 요약 |

- [ ] PII 마스킹 유틸 신설(`common/logging/` 등) — 이메일 패턴 등을 마스킹. provider 에러 바디를 `providerErrorMessage`로 옮길 때 항상 이 유틸을 거친다. **로그와 `GoogleCalendarCredential.last_sync_error` 컬럼(같은 예외 메시지가 흘러 들어감) 양쪽 다** 마스킹 적용 — 컬럼 자체 구조는 바꾸지 않고 저장되는 문자열 내용만 마스킹.
- [ ] Google OAuth 토큰 교환 응답의 실제 `scope` 필드를 구조화 필드(`grantedScope`)로 `GoogleLoginCredentialService`(로그인)·`GoogleCalendarService.connect()`(캘린더) 양쪽에서 로그 — 콘솔·FE 설정이 맞다고 확인돼도 실제 발급된 토큰의 스코프를 재현 없이 확인 가능하게 한다.
- [ ] 아래 "구현 위치" 표에 있는 기존 `log.warn`/`log.error` 호출 전체를 신규 구조화 로거로 교체.
- [ ] `GoogleCalendarSyncScheduler`·`GoogleCalendarService.syncUserInternal` 실패 로그는 `action=calendar-sync` 고정 + 공통 필드 포함(스케줄러 자동 폴링과 `connect()` 직후 동기 sync를 로그에서 구분할 수 있게 `trigger`=`SCHEDULED`/`MANUAL_CONNECT` 서브필드 추가).

### Nice to Have

- [ ] Grafana에 `provider`/`action`별 실패 카운트 패널 추가(EC2 C) — 이번 스펙 구현·검증 후 별도 작업으로 진행.

### Out of Scope (이번 스펙에서 하지 않음)

- 전사(trip·notification 등 다른 도메인) 로깅 정책 확장
- Grafana 대시보드 패널 변경(위 Nice to Have로 미룸)
- 요청 단위 correlation/trace id 도입
- 403 등 실패 유형의 비즈니스 로직 재분류(예: 스코프 부족을 영구 실패로 승격할지) — 로깅만 다루고 판단 로직은 바꾸지 않음
- 위 세 패키지 외 로그 포맷 변경

## 구현 위치 (기존 로그 호출 → 신규 구조화 로거)

| 파일 | 현재 라인 | provider | action(제안) |
|------|-----------|----------|----------------|
| `auth/oauth/GoogleOAuthClient.java` | 88 | GOOGLE | `login-token-revoke` |
| `auth/oauth/GoogleTokenVerifier.java` | 88, 95, 99, 103, 107 | GOOGLE | `login-token-verify` |
| `auth/oauth/AppleTokenVerifier.java` | 73, 80, 84, 88, 92 | APPLE | `login-token-verify` |
| `auth/oauth/AppleOAuthClient.java` | 98 | APPLE | `login-token-revoke` |
| `auth/oauth/AppleNotificationVerifier.java` | 88, 91, 95, 99 | APPLE | `apple-notification-verify` |
| `auth/oauth/KakaoTokenVerifier.java` | 54, 95, 99 | KAKAO | `login-token-verify` (95는 `login-userinfo-fetch`로 세분화 검토) |
| `auth/service/AppleNotificationService.java` | 45, 51, 71 | APPLE | `apple-notification-process` |
| `auth/service/GoogleLoginCredentialService.java` | 67, 83 | GOOGLE | 67=`login-credential-exchange`, 83=`login-credential-revoke` |
| `auth/service/AppleCredentialService.java` | 58, 74 | APPLE | 58=`login-credential-exchange`, 74=`login-credential-revoke` |
| `user/googlecalendar/scheduler/GoogleCalendarSyncScheduler.java` | 47 | GOOGLE | `calendar-sync` |
| `user/googlecalendar/service/GoogleCalendarService.java` | 82, 92, 236 | GOOGLE | 82·92=`calendar-connect`, 236=`calendar-sync` |
| `user/googlecalendar/client/GoogleCalendarOAuthClient.java` | 189 | GOOGLE | `calendar-token-revoke` |

정확한 `action` 이름·세분화는 구현 착수 시 실제 코드 구조에 맞춰 소폭 조정 가능(리스크·미결정 참고).

## API / 인터페이스

API 없음 — 내부 로깅만 변경, 외부 API 계약 변경 없음.

## 데이터 모델

신규 테이블·컬럼 없음. 기존 `google_calendar_credential.last_sync_error`(String) 컬럼 구조는 불변 — 저장되는 문자열 내용에만 마스킹을 적용.

## 비즈니스 규칙

해당 없음 — 로깅 인프라 변경.

## 검증 시나리오

### 정상

- [ ] Google Calendar freeBusy 403(`ACCESS_TOKEN_SCOPE_INSUFFICIENT`) 재현 시 Loki에서 `action="calendar-sync" AND providerErrorReason="ACCESS_TOKEN_SCOPE_INSUFFICIENT"`로 한 번에 필터링되는지 확인
- [ ] Kakao/Apple 토큰 검증 실패 시에도 `provider`/`action` 필드로 동일하게 필터링되는지 확인
- [ ] 이번 변경 후에도 다른 도메인(trip 등) 로그 포맷이 안 바뀌었는지 확인 — 기존 Grafana ERROR/WARN 패널 정상 동작

### 엣지 · 실패

- [ ] provider 에러 바디에 이메일이 포함된 경우 로그·`last_sync_error` 컬럼 양쪽에서 마스킹되는지 확인
- [ ] logback JSON 인코더 설정 오류가 있어도 앱 부팅이 막히지 않는지(`./gradlew bootRun`으로 확인)

### 수동 / 통합

- [ ] 로컬에서 의도적으로 스코프를 좁혀 재현 후 콘솔 JSON 로그 필드 확인
- [ ] dev 프로필에서 콘솔 출력 JSON 라인을 `jq`로 파싱해 필드 확인

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `./gradlew build` 성공
- [ ] "구현 위치" 표의 모든 로그 호출이 신규 구조화 로거로 교체됨
- [ ] 실제 Loki 조회로 최소 1개 실패 시나리오(예: 403 스코프 부족) 필드 기반 필터링 확인(dev 또는 운영)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 세 패키지 로그가 JSON으로 바뀌면 Grafana 전체 로그 스트림 패널에서 텍스트보다 가독성이 떨어짐(기능은 정상 — `"level":"WARN"` 등 substring 매칭은 계속 동작) | 확정 | 구조화 조회 속도를 가독성보다 우선하기로 결정(2026-08-02) |
| 마스킹 정규식이 이메일 외 PII(전화번호 등)까지 커버해야 하는지 | [미정] | provider 에러 바디에 전화번호가 실린 사례는 아직 없음 — 발견 시 정규식 추가 |
| `action` enum 정확한 이름·세분화(위 표 KakaoTokenVerifier:95 등) | [미정] | 구현 중 코드 구조에 맞춰 최종 확정 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-03 | `#65`(과거 Release Gate 메타 트래커, 전부 Closed로 재사용)에 이슈로 등록. 즉시 재동기화 엔드포인트는 검토 후 불필요 판단으로 제외 |
| 2026-08-02 | 초안 — GET /trips 401 masking·freeBusy 403 스코프 부족 반복 사고 계기 |
