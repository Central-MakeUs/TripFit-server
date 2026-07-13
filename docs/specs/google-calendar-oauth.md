# Google Calendar OAuth 연동

> wave: 4  
> implements: (온보딩 `is_google_calendar_connected` 실연동 — BR 번호 N/A · decisions/007)  
> deferred: 네이버 캘린더 · 소셜 계정 다중 연결 → [#6](https://github.com/Central-MakeUs/TripFit-server/issues/6)  
> 상태: Draft  
> MVP: Out of scope (Wave 4)  
> Issue: [#44](https://github.com/Central-MakeUs/TripFit-server/issues/44)  
> 선행: [`auth-social-login.md`](auth-social-login.md), [`user-onboarding.md`](user-onboarding.md)

## 목표

사용자가 Google Calendar를 OAuth로 연동·해제하고, `user.is_google_calendar_connected`가 실제 연동 상태를 반영하게 한다.

## 배경

- wave 1: boolean 필드 + 온보딩 PATCH만 — OAuth 본체 없음 (`user-onboarding` Deferred)
- `schedule-unified` · `auth-social-login`에서 Google Calendar OAuth는 **wave 4 Deferred**
- Wave 4 Backlog `#32` 후보를 **#44**로 고정 (2026-07-22 · 레거시 이슈 재사용)
- ERD: `user.is_google_calendar_connected` — `docs/architecture/erd.md`

## 요구사항

### Must Have

- [ ] Google OAuth (Calendar scope) · authorization code / refresh 흐름
- [ ] 연동·해제 API · 토큰 저장·갱신·revoke
- [ ] 연동 성공 → `is_google_calendar_connected=true` · 해제/실패 정리 → `false`
- [ ] `.env.example` · deploy 문서 (client id/secret · redirect URI)
- [ ] OpenAPI · `./gradlew test`
- [ ] `user-onboarding` / `schedule-unified` / `auth-social-login` deferred · `#32` · README 동기화

### Nice to Have

- [ ] 연동 직후 busy 구간을 `personal_schedule`로 1회 import

### Out of Scope

- 네이버 캘린더
- 소셜 login provider 다중 연결 → `#6`
- regular/personal CRUD 본체 → `#11` (완료)
- 구 `#44` Swagger FE 문서 chore → 이슈 미배정

## API / 인터페이스

| Method | Path (예시) | Auth | 설명 |
|--------|-------------|------|------|
| GET/POST | `/api/v1/users/google-calendar/...` | JWT | 연동 시작·콜백·상태 `[미정]` |
| DELETE | 동일 또는 `/disconnect` | JWT | 연동 해제 · 토큰 revoke |

요청/응답 envelope: [`docs/architecture/api-response.md`](../architecture/api-response.md)

## 데이터 모델

- `user.is_google_calendar_connected` (기존)
- 신규: Google refresh/access 토큰 저장 컬럼 또는 별도 테이블 — **스펙 Approved 시 ERD 반영** `[미정]`

## 비즈니스 규칙

| 규칙 | 적용 |
|------|------|
| decisions/007 | 실제 OAuth 연동 성공 시에만 `isGoogleCalendarConnected=true` · 건너뛰기=`false` |
| `#1` login | Google **로그인**과 Calendar **연동**은 별 scope · 별 API |

## `[미정]`

- 동기화 방향: **읽기(busy import)** vs 쓰기(TripFit→Google) vs 양방향
- 토큰 저장 위치·암호화 · refresh 실패 시 플래그
- 연동 이벤트 → `personal_schedule` 매핑 규칙

## 완료 기준

- [ ] 스펙 **Approved**
- [ ] `#44` Must Have 체크
- [ ] ERD·env·deploy 동기화
- [ ] `./gradlew test`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-22 | Draft — `#44` 레거시 재사용 · Wave 4 Google Calendar OAuth |
