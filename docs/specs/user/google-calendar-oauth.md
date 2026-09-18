# Google Calendar OAuth 연동

> wave: 3 (2026-08-03 Wave 4→3 이동 — 외부 API 연동, 도메인축 재분류)
> implements: (온보딩 `is_google_calendar_connected` 실연동 — BR 번호 N/A · decisions/007)
> deferred: 네이버 캘린더 · 소셜 계정 다중 연결 → [#6](https://github.com/Central-MakeUs/TripFit-server/issues/6) · Calendar 전용 OAuth Client ID 분리(백엔드 배선·GCP 콘솔 발급·FE 전환 완료, 2026-08-08) → [`google-calendar-client-id-separation.md`](google-calendar-client-id-separation.md)
> 상태: **Approved**
> MVP: Out of scope (Wave 3)
> Issue: [#44](https://github.com/Central-MakeUs/TripFit-server/issues/44)
> 선행: [`auth-social-login.md`](../auth/auth-social-login.md), [`user-onboarding.md`](user-onboarding.md), [`schedule-calendar-resolve.md`](../user-schedule/schedule-calendar-resolve.md)

## 목표

사용자가 Google Calendar를 OAuth로 연동·해제하고, `user.is_google_calendar_connected`가 실제 연동 상태를 반영하게 한다.
연동 중에는 Google busy를 읽어 TripFit 일정(오전/오후/저녁)에 **병합(Merge)** 한다.

## env 키 (확정)

| 키 | 용도 |
|----|------|
| `GOOGLE_CALENDAR_CLIENT_ID` | Calendar 전용 token 교환 client_id (웹/서버) — 로그인용 `GOOGLE_CLIENT_ID`와 분리(`google-calendar-client-id-separation.md`). 실제 값은 GCP 콘솔에서 Calendar FE 착수 시 발급 |
| `GOOGLE_CALENDAR_CLIENT_SECRET` | 위 Calendar 전용 client의 secret (웹 타입 OAuth 클라이언트 전용) |
| `SOCIAL_TOKEN_AES_KEY` | Base64 인코딩 **32바이트** AES-256 키 |

환경 B 복귀 URL은 **프론트 범위** (백엔드 Must 아님).

## 배경

- wave 1: boolean 필드 + 온보딩 PATCH만 — OAuth 본체 없음 (`user-onboarding` Deferred)
- `schedule-unified` · `auth-social-login`에서 Google Calendar OAuth는 **wave 4 Deferred**
- Wave 4 Backlog `#32` 후보를 **#44**로 고정 (2026-07-22 · 레거시 이슈 재사용)
- ERD: `user.is_google_calendar_connected` — `docs/architecture/erd.md`
- Google **로그인**(`#1`)과 Calendar **연동**은 별 scope · 별 API · 별 토큰 저장
- 모바일 전제: decisions/001 — **서버 리다이렉트 OAuth2 Client 아님** · 앱이 토큰/code를 받아 REST로 전달
- 런타임: [`platform.md`](../../product/platform.md) 환경 A(앱) · B(카카오 인앱·모바일 웹) · **둘 다 로그인 필수**

## 요구사항

### Must Have

- [ ] Google OAuth (Calendar **읽기** / freeBusy scope) · authorization code → refresh 교환
- [ ] 연동·해제 API (아래 path) · credential **AES-256 암호화** 저장·갱신·revoke
- [ ] 연동 성공 시 `google_account_email` 저장 (API Must 미노출 · 재연동 UX·운영용)
- 구글 캘린더 연동(`connect`) 시 해당 구글 계정의 이메일 주소를 `google_calendar_credential.google_account_email`에 저장한다.
- 이메일 조회 순서: Google Userinfo API 우선 → 실패 시 Primary Calendar ID fallback.
- 조회에 실패하더라도 연동 자체는 중단하지 않으며 `NULL`로 저장한다.
- **재연동 시**: `updateTokens()` 호출 시 non-blank 값이면 덮어쓰고, null이면 기존 값 유지.
- **API 응답에는 포함하지 않는다** (재연동 UX·운영 추적 전용 내부 필드).
- [ ] 연동 성공 → `is_google_calendar_connected=true`
- [ ] 권한 만료·유저가 Google에서 연동 해제·refresh 실패 → **`is_google_calendar_connected=false`** (+ credential·busy_day 정리)
- [ ] `freeBusy.query` 읽기 → 날짜×슬롯 · **수동 일정과 Merge**
- [ ] **폴링 30분** + 연동 직후 1회 sync (지터)
- [ ] 유저 **의도적 해제**(`DELETE`) 시 Google 레이어만 삭제 · 수동 `regular`/`personal` 유지
- [ ] `.env.example` · deploy (client id/secret · 암호화 키)
- [ ] OpenAPI · `./gradlew test`
- [ ] `user-onboarding` / `schedule-unified` / `auth-social-login` deferred · `#32` · README 동기화

### Nice to Have

- [ ] **쓰기:** 여행 확정 일정을 Google Calendar에 이벤트 생성
- [ ] Push (`events.watch`) + 채널 갱신
- [ ] `POST .../sync` **수동 즉시 동기화** — 기획·디자인 미전달 → Must 아님
- [ ] `/me`에 `lastSyncedAt` 노출 · “마지막 동기화” UI
- [ ] 재연동 유도 전용 API/`ErrorCode` · “연동이 만료되었습니다. 다시 연동해 주세요.” 팝업 계약 — 기획·디자인 미전달 → Must 아님
  (Must는 flag=`false`만; FE는 기존 연동 버튼·`isGoogleCalendarConnected`로 충분)

### Out of Scope

- 네이버 캘린더
- 소셜 login provider 다중 연결 → `#6`
- regular/personal CRUD 본체 → `#11` (완료)
- 구 `#44` Swagger FE 문서 chore (이미 main 반영)
- tentative 슬롯별 uncertain 매핑 — **freeBusy busy면 IMPOSSIBLE**
- 서버 **302** Google OAuth

## 동기화 방향 (확정)

| 방향 | 범위 | 설명 |
|------|------|------|
| **읽기** | **Must** | Google `freeBusy` → 슬롯 IMPOSSIBLE |
| **쓰기** | **Nice** | TripFit 확정 여행 → Google 이벤트 |
| **수동 sync API** | **Nice** | 기획·디자인 없음 |
| 양방향 지속 sync | Out | — |

자동 반영: Google이 DB를 직접 채우지 않음. Must = **30분 폴링** + 연동 직후 1회 sync. Push = Nice.

### 폴링 주기 (확정)

| 항목 | 값 |
|------|-----|
| **주기** | **30분** — 연동 유저 전원 매 사이클 동기화(구 유저별 그룹 순번 분산 방식 폐지, `#86`) |
| **대상** | `is_google_calendar_connected=true` 유저만 |
| **부가** | freeBusy 윈도우 = C1 |

### freeBusy 조회 윈도우 (확정)

`freeBusy.query`는 **`timeMin` / `timeMax` 필수**.

| 항목 | 값 |
|------|-----|
| **윈도우** | **`today` ~ `max(today+2년−1, 참여 중 ONGOING 여행 endRange 최댓값)`** (Asia/Seoul) = 마이페이지 **C1**(#53 R4 반영, `GoogleCalendarService`가 `ScheduleService.resolveCalendarWindowEnd` 재사용) |
| **과거** | 미조회 |
| **매 sync** | 슬라이딩 · 윈도우 밖 Google busy 행 **삭제** |

**부하 완화:** freeBusy만 · busy 있는 날짜만 sparse upsert · 연동 유저만. (2026-08-15: 유저별 그룹 순번 분산은 폐지 — 최대 3시간까지 벌어지던 유저별 동기화 지연을 없애기 위해, API 호출량 증가를 감수하고 전원 30분마다 동기화하기로 결정, `#86`)

## 슬롯 변환 (확정)

| Google (`freeBusy`) | TripFit |
|---------------------|---------|
| `busy[]` `{ start, end }` | `TimeSlot.overlaps` → 겹치면 `IMPOSSIBLE` |
| 종일 busy | 해당 일 오전·오후·저녁 전부 `IMPOSSIBLE` |

### Tentative (확정)

**SSOT = `freeBusy` `busy[]`.** events `status=tentative` 별도 분기 없음.

## 병합(Merge) (확정)

1. 슬롯별 **OR(IMPOSSIBLE)** — 수동∨Google 하나라도 IMPOSSIBLE이면 IMPOSSIBLE.
2. **의도적 해제** 또는 **권한 실패 정리:** Google credential + busy_day만 삭제 · 수동 일정 유지.

## 토큰 · 실패 처리 (확정 — 2026-07-22)

| 항목 | 결정 |
|------|------|
| refresh 저장 | **AES-256** 암호화 (키는 env · `.env.example` placeholder) |
| 권한 만료 / Google에서 연동 끊김 / refresh·API 영구 실패 | **`is_google_calendar_connected=false`** · credential 삭제 · busy_day 삭제 (Merge 중단) |
| 재연동 팝업·전용 ErrorCode API | **Nice** (기획·디자인 미전달). Must는 flag만 내림 → FE는 `isGoogleCalendarConnected==false`로 연동 CTA 표시 가능 |

## `lastSyncedAt` (확정 방향 — 2026-07-22)

| | Must | Nice |
|---|------|------|
| **API (`/me` 필드)** | **없음** — 수동 sync·“마지막 동기화” UI 기획 없음 | 노출 가능 |
| **DB credential** | 내부용 `last_synced_at` **둬도 됨** (스케줄러·운영 로그). 공개 계약 아님 | — |

→ **Must에서 `lastSyncedAt`은 필요 없다.** 연동 여부는 `isGoogleCalendarConnected`만.

## 데이터 모델 (확정 방향)

### `users.is_google_calendar_connected`

연동 SSOT (기존).

### `google_calendar_credential` (가칭) · user당 1행

| 컬럼 (초안) | 설명 |
|-------------|------|
| `id` | UUID PK |
| `user_id` | FK UNIQUE |
| `google_account_email` | 연동 Google 계정 이메일 (**Must 저장**, `/me` 미노출). 조회 실패 시 null |
| `refresh_token` | **AES-256 암호문** |
| `access_token` / `access_token_expires_at` | 선택 캐시 (암호화 권장) |
| `last_synced_at` | 내부용 (API Must 미노출) |
| `last_sync_error` | 내부용 nullable |
| `created_at` / `updated_at` | |

### `google_calendar_busy_day` — **A안 날짜×슬롯**

`(user_id, schedule_date)` + `morning_busy` / `afternoon_busy` / `evening_busy` boolean.

Approved 시 `erd.md` 반영.

## API / 인터페이스 (확정)

**서버 302 OAuth 금지.** 환경 A/B → 동일 REST.

### Path

| Method | Path | 범위 | 설명 |
|--------|------|------|------|
| `POST` | `/api/v1/users/google-calendar` | **Must** | body `{ "authorizationCode", "redirectUri"(옵션, 브라우저 리다이렉트 전용) }` → flag=true + `google_account_email` 저장 + 즉시 1회 sync |
| `DELETE` | `/api/v1/users/google-calendar` | **Must** | 의도적 해제 · revoke · credential·busy_day 삭제 · flag=false |
| `POST` | `/api/v1/users/google-calendar/sync` | **Nice** | 수동 즉시 sync (기획 없음) |
| — | `/me`의 `isGoogleCalendarConnected` | **Must** | 상태 SSOT |
| — | `/me`의 `lastSyncedAt` | **Nice** | Must 미포함 |

응답 envelope: [`api-response.md`](../../architecture/api-response.md)

> FE 잔여(스펙 밖·프론트): 환경 B 복귀 URL. 백엔드 Must와 무관.

## 비즈니스 규칙

| 규칙 | 적용 |
|------|------|
| decisions/007 | 실제 연동 성공 시에만 `isGoogleCalendarConnected=true` |
| login ≠ calendar | 별 scope · 별 API |
| freeBusy SSOT | tentative 별도 분기 없음 |
| Merge | OR(IMPOSSIBLE) |
| Disconnect / 권한 실패 | Google 레이어+credential 정리 · flag=false · 수동 유지 |
| 암호화 | refresh **AES-256** |
| 폴링 | **30분** |
| 수동 sync · 만료 팝업 API | **Nice** |

## `[미정]`

- Nice 착수 여부만 남음 (쓰기 · push · 수동 sync · lastSyncedAt · 재연동 팝업). **Must 블로커 없음.**
- 환경 B 복귀 URL — 프론트

## 완료 기준

- [ ] `#44` Must Have 체크
- [ ] ERD·env·deploy 동기화
- [ ] `./gradlew test` (Merge · 해제 · 권한 실패 시 flag=false · 수동 보존)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-22 | Draft — `#44` 레거시 재사용 |
| 2026-07-22 | 읽기 Must / Merge / A안 / C1 / API / 환경 A/B / 폴링 30분 / AES-256 |
| 2026-07-22 | **Approved** — Must 블로커 없음 · env 키 확정 · 구현 착수 |
| 2026-07-22 | Must — `google_account_email` credential 저장 (API 미노출) |
| 2026-08-01 | **정정** — `GoogleCalendarOAuthClient.exchangeAuthorizationCode()`가 `redirect_uri=""` 고정이라 브라우저 리다이렉트(hybrid flow) 경로에서 실패하는 걸 FE 착수 전 선제 발견(`google-login-revoke.md` 정정 2와 동일 원인). `ConnectGoogleCalendarRequest`에 `redirectUri`(옵션, 브라우저 전용) 필드 추가해 네이티브(null→`""`)/브라우저(실제 URL)를 구분. 동시에 `GoogleCalendarService.connect()`가 code 교환 실패 원인을 로그 없이 삼키던 관측성 gap도 `log.warn`으로 보강. `./gradlew test` 통과 |
| 2026-08-01 | **버그 수정** — 연동 성공 직후 `DELETE`가 `GOOGLE_CALENDAR_NOT_CONNECTED`로 실패하는 사용자 리포트 확인. 원인: `GoogleCalendarOAuthClient.queryFreeBusy()`가 401뿐 아니라 403·429·5xx·네트워크/파싱 오류까지 전부 `GoogleCalendarAuthException`으로 던졌고, `GoogleCalendarService.syncUserInternal()`이 이를 전부 "영구 인증 실패"로 취급해 `connect()`와 **같은 트랜잭션 안에서** 방금 저장한 credential을 삭제·flag=false 처리 — connect() 응답은 200으로 성공하지만 DB에는 연동이 즉시 롤백돼 있었다. 본 표 "토큰·실패 처리"의 의도(권한 만료·refresh 영구 실패만 정리)와 다르게 구현돼 있던 코드 버그로, 스펙 값 변경은 없음. 401(genuine 인증 실패)만 `GoogleCalendarAuthException`으로 남기고 그 외는 일반 예외로 던져 `markSyncError`·30분 폴링 재시도로 흡수하도록 수정. 회귀 테스트 추가, `./gradlew test` 통과 |
| 2026-08-08 | **관측성 보강** — 같은 Google 계정을 이미 다른 TripFit 유저가 연동한 뒤 재동의 없이 또 연동하면, Google이 `refresh_token`을 생략한 토큰 응답을 줄 수 있다(Google 정책: 동일 client_id에 이미 offline 권한을 준 계정은 재동의 강제(`prompt=consent` 등) 없이는 `refresh_token`을 재발급하지 않음 — TripFit 유저 단위가 아니라 Google 계정+client_id 단위로 추적됨). `GoogleCalendarOAuthClient.parseTokenResponse()`가 이 케이스를 `TripFitException(GOOGLE_CALENDAR_CONNECT_FAILED)`으로 던지는데, `GoogleCalendarService.connect()`의 catch는 `GoogleCalendarAuthException`만 로깅해 이 실패가 로그 없이 삼켜지고 있었음. `connect()`에 `TripFitException` catch를 추가해 `userId`·`hasRedirectUri`(네이티브/브라우저 경로 구분)와 함께 `log.warn`으로 남기도록 보강 — 재현 없이도 Grafana(WARN 패널)에서 발생 시점·유저·경로를 바로 확인 가능. 서버가 Google에 보내는 authorize 요청 파라미터(`prompt=consent` 강제 여부)를 직접 통제할 수 없어 근본 원인은 클라이언트 쪽 대응(예: Android `requestServerAuthCode(webClientId, forceCodeForRefreshToken=true)`)이 필요 — 스펙 값 변경 없음, 로깅만 추가 |
| 2026-08-08 | **관측성 보강 (2)** — `GoogleCalendarService.syncUserInternal()`(연동 직후 1회 sync + 30분 폴링 공용 경로)의 `catch (Exception exception)` 분기가 `credential.markSyncError(...)`만 하고 로그를 전혀 안 남기고 있었음 — freeBusy 403/429/5xx·네트워크·파싱 오류 등 일시적 실패가 재현 없이는 Grafana에서도 전혀 안 보였음. `log.warn`에 `userId` 포함해 남기도록 보강(credential 보존·30분 재시도 흡수 동작 자체는 변경 없음) — 스펙 값 변경 없음, `./gradlew test` 통과 |
| 2026-08-08 | **원인 확정** — 위 "관측성 보강" 항목의 가설을 실제 프로덕션 재현으로 확인. 같은 Google 계정을 다른 TripFit 유저로 재연동 시도 → 로그에 `userId`·`hasRedirectUri=false`가 예측한 그대로 찍힘(네이티브 경로만 재현, 브라우저 경로는 항상 `prompt=consent`를 써서 재현 안 됨). FE가 Android에 `GoogleSignin.requestServerAuthCode(webClientId, forceCodeForRefreshToken=true)`를 적용한 빌드로 재테스트해 동일 계정 재연동 성공을 백엔드 로그·DB로 교차 확인. **iOS는 아직 동등 옵션이 없어 같은 증상이 재현될 수 있음** — 스토어 심사 이후 네이티브 SDK 방식을 웹과 동일한 redirectUri 리다이렉트 방식으로 통합 리팩토링 예정(클라이언트 저장소 [TripFit-client#97](https://github.com/Central-MakeUs/TripFit-client/pull/97)). 백엔드 코드 변경 없음(원인이 클라이언트 SDK 설정에 있어 대응도 클라이언트 전용) |
| 2026-08-06 | **버그 수정** — freeBusy sync가 매번(재현율 100%) `freeBusy request failed`로 실패하던 건 별개 원인으로 확인·수정. `GoogleCalendarOAuthClient.queryFreeBusy()`가 C1 윈도우(최대 today+2년) 전체를 Google `freeBusy.query` 한 번 호출에 넣고 있었는데, Google이 이 범위를 미문서화된 제한으로 거부(`400 BAD_REQUEST`, `reason: timeRangeTooLong`, `"The requested time range is too long."`) — 계정·권한 문제가 아니라 순수 요청 범위 버그였음. Google 공식 문서·커뮤니티 어디에도 정확한 상한 일수가 없어(2026-08-06 검색 확인), 정확한 임계값에 의존하지 않도록 90일 단위로 청크 분할해 여러 번 호출 후 병합하도록 수정(`queryFreeBusyChunk`) — 기존 "2년 전체 동기화" 범위·의미는 그대로 유지. 프로덕션 재현으로 수정 확인(`last_synced_at` 최초로 채워짐). `./gradlew test` 통과 |
| 2026-08-15 | **정책 변경** — `GoogleCalendarSyncScheduler`가 유저를 `userId.hashCode() % 6` 기준 6개 그룹으로 나눠 30분마다 그중 한 그룹만 동기화하던 방식(그룹 순번 분산) 폐지. 스케줄러 실행 주기는 30분이었지만 유저 한 명 기준 실제 동기화 주기는 3시간(30분×6그룹)이었고, 이 지연이 트립 매칭·추천 정확도에 영향을 줄 수 있다고 판단해 모든 연동 유저를 매 30분 사이클마다 동기화하도록 변경. Google API 호출량은 최대 6배 증가하는 트레이드오프를 감수(`#86`). `shouldSkipThisCycle`(그룹 순번 로직) 제거, 유저 사이 짧은 sleep(`sleepBetweenUsers`)만 유지해 호출이 순간적으로 몰리지 않게 함. `./gradlew test` 통과 |
| 2026-08-20 | **분류 보강** — 특정 유저의 freeBusy sync가 `403 insufficientPermissions`/`ACCESS_TOKEN_SCOPE_INSUFFICIENT`(scope 부족)로 30분마다 무한 반복 실패하는 걸 프로덕션 로그로 확인. 2026-08-01 수정 이후 401 외 모든 403은 "일시적 오류"로 넓게 묶여 재시도됐는데, scope 부족은 재시도로 절대 스스로 안 풀리는 진짜 영구 실패라 credential이 계속 방치되고 있었음. `GoogleCalendarOAuthClient`에 `PERMANENT_PERMISSION_FAILURE_REASONS`(Google이 같은 실패를 신·구 에러 포맷으로 각각 표기하는 `insufficientPermissions`/`ACCESS_TOKEN_SCOPE_INSUFFICIENT` 두 reason 등록) 추가 — 이 reason만 401과 동일하게 `GoogleCalendarAuthException`으로 승격해 자동 연동 해제되도록 분류를 세분화. `rateLimitExceeded` 등 다른 403은 여전히 일시적 재시도 대상으로 남아 2026-08-01 회귀 버그는 재발하지 않음. 본 표 "권한 만료 / Google에서 연동 끊김 / refresh·API 영구 실패" 결정 자체는 변경 없음(어떤 HTTP 응답을 영구로 볼지의 구현 분류만 확장). `./gradlew test` 통과 |
