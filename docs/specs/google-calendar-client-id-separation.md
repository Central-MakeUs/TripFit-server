# Google Calendar 전용 OAuth Client ID 분리

> 상태: Draft (백엔드 코드 배선 + GCP 콘솔 Client ID 발급 완료 — FE 전환 대기)
> MVP: 해당 없음 (Google Calendar 자체가 Wave 4 Out of scope)
> 관련 BR: 해당 없음
> Issue: [#78](https://github.com/Central-MakeUs/TripFit-server/issues/78)
> deferred from: [`google-login-revoke.md`](google-login-revoke.md) 리스크 절
> 선행: [`google-calendar-oauth.md`](google-calendar-oauth.md)(Wave 4, FE 미착수)

## 목표

Google 로그인용 OAuth Client ID와 Google Calendar 연동용 OAuth Client ID를 분리해, 두 기능이 서로의 revoke·설정에 영향을 주지 않게 한다.

## 배경

- **(2026-08-22 해소, 코드 레벨)** 로그인(`google-login-revoke.md`)과 Calendar 연동(`google-calendar-oauth.md`)이 코드상으로는 이제 별도 env(`GOOGLE_CALENDAR_CLIENT_ID`/`GOOGLE_CALENDAR_CLIENT_SECRET`)를 쓰도록 분리됨. GCP 콘솔에서 Calendar 전용 Client ID도 2026-07-31에 발급 완료해 GitHub Actions secrets(`GOOGLE_CALENDAR_CLIENT_ID`/`GOOGLE_CALENDAR_CLIENT_SECRET`)에 등록돼 있다. 다만 FE는 아직 로그인과 같은 Client ID로 Calendar 연동을 요청 중이라(Calendar 자체가 Wave 4 FE 미착수) 아래 리스크는 여전히 유효하다.
- Google Calendar 연동은 **KAKAO/APPLE/GOOGLE 로그인 유저 전부**가 쓸 수 있는, TripFit 로그인 provider와 무관한 기능이다(`google-calendar-oauth.md` "Google 로그인과 Calendar 연동은 별 scope·별 API·별 토큰 저장"). "인증"과 "외부 캘린더 연동"은 개념적으로 서로 다른 관심사인데 지금은 우연히 같은 Client ID 설정을 공유하고 있다.
- 근거 두 가지:
  1. **기술적**: Google이 동의(consent)를 client_id 단위로 묶는다면, 같은 Google 계정으로 로그인도 하고 캘린더도 연동한 유저가 "캘린더만 연동 해제"해도 로그인 쪽 grant까지 같이 revoke될 수 있음
  2. **개념적**: 캘린더는 로그인 provider와 무관한 기능이라 애초에 로그인용 Client ID에 얹혀갈 이유가 약함
- Calendar가 아직 FE에 구현된 적이 없어(목업, Wave 4 미착수) 지금 당장 급한 작업은 아니다 — Calendar를 실제로 FE에 붙이는 시점에 이 분리를 함께 진행한다.

## Must Have (Calendar 실제 구현 착수 시)

- [x] Google Cloud Console에서 Calendar 전용 OAuth Client ID(Web application 타입) 신규 발급 — 2026-07-31, GitHub Actions secrets(`GOOGLE_CALENDAR_CLIENT_ID`/`GOOGLE_CALENDAR_CLIENT_SECRET`)에 등록 완료
- [x] `OAuthProperties`에 `googleCalendarClientId`/`googleCalendarClientSecret` 필드 추가(기존 `googleClientId`/`googleClientSecret`은 로그인 전용으로 유지) — 2026-08-22
- [x] `GoogleCalendarOAuthClient`가 신규 Calendar 전용 client_id/secret을 사용하도록 변경 — 2026-08-22
- [x] env 4곳 배선(`.env.example`·`ci-cd.yml`·`docker-compose.yml`·`OAuthProperties`/`application.yml`) — 2026-08-22, Apple/Kakao 때와 동일 패턴. `.env.example`은 관례상 빈 값 placeholder, 실값은 GitHub Actions secrets에만 등록
- [ ] FE가 Calendar 연동(`POST /api/v1/users/google-calendar`) 시 로그인과 다른 Client ID로 OAuth 요청을 보내도록 변경(FE 작업)

## GCP 콘솔 가이드 (콘솔 담당자용, 참고 — 2026-07-31 발급 완료)

Google Cloud Console에서 직접 진행하는 절차. 승인·검수 없이 즉시 발급 가능한 부분과, Google 쪽 검증이 필요할 수 있는 부분을 구분해뒀다. Client ID 자체는 이미 발급·secrets 등록됐으므로, 재발급이 필요해지는 경우를 위한 참고용으로 남겨둔다.

1. **프로젝트 확인**: 기존 로그인용 Client ID를 발급한 것과 **같은 GCP 프로젝트**를 그대로 사용(새 프로젝트 불필요) — API·Services 좌측 메뉴에서 현재 프로젝트가 맞는지 확인
2. **OAuth 동의 화면(consent screen) 확인**: `APIs & Services → OAuth consent screen` — 이미 로그인용으로 등록돼 있을 것. 여기에 **Calendar scope**(`https://www.googleapis.com/auth/calendar.readonly` 등, 실제 요청 scope는 `google-calendar-oauth.md` 참고)가 아직 없으면 "Scopes" 섹션에 추가
   - ⚠️ Calendar 읽기 scope는 Google이 "민감한 scope(sensitive scope)"로 분류할 수 있다. 테스트 유저 100명 이하로 운영하는 동안은(Testing 모드) 검증 없이 바로 쓸 수 있지만, **일반 유저 전체에게 열려면(Production 모드) Google의 OAuth 앱 검증(verification) 절차**를 거쳐야 할 수 있음 — 이 과정은 며칠~몇 주 소요될 수 있으니 Calendar 기능 출시 일정에 미리 반영
3. **신규 Client ID 발급**: `APIs & Services → Credentials → + Create Credentials → OAuth client ID`
   - Application type: **Web application** (지금 로그인용과 동일 타입 — 서버가 client_secret으로 코드를 교환하는 기존 `GoogleCalendarOAuthClient` 방식을 그대로 씀)
   - Name: 예) `TripFit - Calendar` (로그인용과 구분되는 이름)
   - Authorized redirect URIs: FE가 Calendar 연동 버튼을 눌렀을 때 돌아올 콜백 URL 등록(로그인용과 다른 경로 권장 — 예: `https://tripfit.online/calendar/callback`)
   - 저장하면 **즉시** `client_id`/`client_secret` 발급됨(승인 대기 없음)
4. **Calendar API 활성화 확인**: `APIs & Services → Library`에서 **Google Calendar API**가 이 프로젝트에 활성화돼 있는지 확인(이미 활성화돼 있을 가능성 높음 — 기존 Calendar 코드가 이미 freeBusy를 호출 중이므로)
5. **발급된 값 전달**: 새 `client_id`/`client_secret`을 백엔드 담당자에게 전달(`.env` 등 평문 커밋 금지 — 이 저장소 관례상 GitHub Actions secrets·서버 env로만 등록)

## Out of Scope (이번 문서에서 하지 않음)

- FE의 Calendar 연동 화면 실제 OAuth 연결(로그인과 다른 Client ID로 요청 전환) — `google-calendar-oauth.md`(Wave 4) 본연의 범위, 별도 저장소

## 완료 기준

- [x] 백엔드 코드 배선(`OAuthProperties`·`GoogleCalendarOAuthClient`·env 4곳) — 2026-08-22
- [x] GCP 콘솔에서 실제 Calendar 전용 Client ID 발급 + secrets 등록 — 2026-07-31
- [ ] FE 전환(Calendar 연동 요청을 Calendar 전용 Client ID로) — Calendar 실제 FE 구현 착수 시점에 진행

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| FE 전환 시점 | `[미정]` | Calendar Wave 4 FE 착수와 함께 진행 |
| Calendar scope Google 검증 필요 여부·소요 기간 | `[미정]` | Production 모드 전환 시 확인 필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-31 | 초안 — `google-login-revoke.md` 리스크 절에서 분리. GCP 콘솔에서 Calendar 전용 Client ID 발급, GitHub Actions secrets 등록 완료 |
| 2026-08-22 | 백엔드 코드 배선 완료(`OAuthProperties`·`GoogleCalendarOAuthClient`·env 4곳). "GCP 콘솔 발급 전" 서술이 7/31 발급 완료 사실을 놓쳐 stale했던 것을 8/23 재확인 후 정정 |
