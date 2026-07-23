# Google Calendar OAuth 연동

> wave: 4
> implements: (온보딩 `is_google_calendar_connected` 실연동 — BR 번호 N/A · decisions/007)
> deferred: 네이버 캘린더 · 소셜 계정 다중 연결 → [#6](https://github.com/Central-MakeUs/TripFit-server/issues/6)
> 상태: **Approved**
> MVP: Out of scope (Wave 4)
> Issue: [#44](https://github.com/Central-MakeUs/TripFit-server/issues/44)
> 선행: [`auth-social-login.md`](auth-social-login.md), [`user-onboarding.md`](user-onboarding.md), [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md)

## 목표

사용자가 Google Calendar를 OAuth로 연동·해제하고, `user.is_google_calendar_connected`가 실제 연동 상태를 반영하게 한다.
연동 중에는 Google busy를 읽어 TripFit 일정(오전/오후/저녁)에 **병합(Merge)** 한다.

## env 키 (확정)

| 키 | 용도 |
|----|------|
| `GOOGLE_CLIENT_ID` (기존) | token 교환 client_id·client_secret 겸용 (웹/서버) |
| `GOOGLE_CALENDAR_TOKEN_AES_KEY` | Base64 인코딩 **32바이트** AES-256 키 |

환경 B 복귀 URL은 **프론트 범위** (백엔드 Must 아님).

## 배경

- wave 1: boolean 필드 + 온보딩 PATCH만 — OAuth 본체 없음 (`user-onboarding` Deferred)
- `schedule-unified` · `auth-social-login`에서 Google Calendar OAuth는 **wave 4 Deferred**
- Wave 4 Backlog `#32` 후보를 **#44**로 고정 (2026-07-22 · 레거시 이슈 재사용)
- ERD: `user.is_google_calendar_connected` — `docs/architecture/erd.md`
- Google **로그인**(`#1`)과 Calendar **연동**은 별 scope · 별 API · 별 토큰 저장
- 모바일 전제: decisions/001 — **서버 리다이렉트 OAuth2 Client 아님** · 앱이 토큰/code를 받아 REST로 전달
- 런타임: [`platform.md`](../product/platform.md) 환경 A(앱) · B(카카오 인앱·모바일 웹) · **둘 다 로그인 필수**

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
| **주기** | **30분** |
| **대상** | `is_google_calendar_connected=true` 유저만 |
| **부가** | 유저별 **지터** · freeBusy 윈도우 = C1 |

### freeBusy 조회 윈도우 (확정)

`freeBusy.query`는 **`timeMin` / `timeMax` 필수**.

| 항목 | 값 |
|------|-----|
| **윈도우** | **`today` ~ `today+2년−1`** (Asia/Seoul) = 마이페이지 **C1** |
| **과거** | 미조회 |
| **매 sync** | 슬라이딩 · 윈도우 밖 Google busy 행 **삭제** |

**부하 완화:** freeBusy만 · busy 있는 날짜만 sparse upsert · 연동 유저만 · 지터.

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
| `POST` | `/api/v1/users/google-calendar` | **Must** | body `{ "authorizationCode" }` → flag=true + `google_account_email` 저장 + 즉시 1회 sync |
| `DELETE` | `/api/v1/users/google-calendar` | **Must** | 의도적 해제 · revoke · credential·busy_day 삭제 · flag=false |
| `POST` | `/api/v1/users/google-calendar/sync` | **Nice** | 수동 즉시 sync (기획 없음) |
| — | `/me`의 `isGoogleCalendarConnected` | **Must** | 상태 SSOT |
| — | `/me`의 `lastSyncedAt` | **Nice** | Must 미포함 |

응답 envelope: [`api-response.md`](../architecture/api-response.md)

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
