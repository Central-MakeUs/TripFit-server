# Apple Sign In — Server-to-Server Notification

> 상태: Approved (2026-07-30)  
> 범위: **스토어 제출 전** (MVP 로그인 스펙과 별도)  
> 관련: [`auth-social-login.md`](auth-social-login.md), [`docs/product/platform.md`](../product/platform.md)  
> 결정: [`docs/decisions/001-auth-mobile-token-verification.md`](../decisions/001-auth-mobile-token-verification.md)

## 목표

한국 App Store 앱에서 Sign in with Apple을 지원할 때, Apple이 push하는 **계정 변경 이벤트**를 TripFit 백엔드가 수신·검증·처리하여 개인정보를 동기화하고 심사 요건을 충족한다.

## 배경

- TripFit은 카카오·구글·애플 소셜 로그인을 지원한다 (`auth-social-login.md`)
- 2026년 이후 한국 앱에서 Sign in with Apple 사용 시, **Server-to-Server Notification** 엔드포인트 구현이 권장·심사에 영향을 줄 수 있다
- 목적: 사용자의 이메일 전달 설정 변경, 앱 내 계정 삭제, 애플 계정 영구 삭제 등 → TripFit `user` 및 연관 데이터 동기화
- 참고: [Apple News — account change notifications](https://developer.apple.com/news/?id=j9zukcr6)
- 가이드: [App Store Review Guidelines — Login Services](https://developer.apple.com/kr/app-store/review/guidelines/#login-services)

### 본 스펙과 MVP 로그인 스펙의 관계

| 스펙 | 시점 | 내용 |
|------|------|------|
| `auth-social-login.md` | MVP | `POST /auth/login` — Apple `id_token` 검증 + JWT 발급 |
| **본 스펙** | 스토어 직전 | Apple → TripFit **webhook** — 계정 lifecycle 이벤트 |

로그인 API만 구현하고 webhook 없이 제출하면 심사에서 지적될 수 있다.

## 아키텍처 개요

```
[Apple 서버]
  사용자 계정 변경 (이메일 설정, 앱 연결 해제, 계정 삭제 등)
        ↓ HTTPS POST (signed JWT payload)
POST /api/v1/auth/apple/notifications
        ↓
┌─────────────────────────────────────┐
│ AppleNotificationVerifier            │
│  - Apple JWK로 incoming JWT 서명 검증 │
│  - events[] 파싱                     │
└─────────────────────────────────────┘
        ↓
이벤트 유형별 처리 (user soft delete, refresh token 폐기 등)
        ↓
200 OK (Apple 재시도 정책 고려)
```

Apple Developer Console에 **동일 URL**을 Server-to-Server Notification Endpoint로 등록한다.

## 요구사항

### Must Have (스토어 제출 전)

- [ ] Apple이 POST하는 **signed JWT** 수신 엔드포인트
- [ ] Apple JWK (`https://appleid.apple.com/auth/keys`)로 payload 서명 검증 — `AppleTokenVerifier`와 키 캐시 로직 재사용 검토
- [ ] 이벤트 유형별 최소 처리 (아래 표)
- [ ] Apple Developer Console에 production URL 등록
- [ ] `./gradlew test` — verifier·핸들러 단위 테스트

### 이벤트 처리 (확정 — Apple 공식 문서 "Processing changes for Sign in with Apple accounts" 기준)

Apple의 outer JWT는 `{ "iss": "https://appleid.apple.com", "aud": "<client_id>", "exp", "iat", "jti", "events": "<JSON 문자열>" }` 구조이고, `events` 필드는 **문자열로 인코딩된 JSON**(추가 파싱 필요)이며 그 안에 `type`(이벤트 종류) · `sub`(Apple user id) · `event_time`이 들어있다. 이벤트 `type`은 아래 4종이 전부다(추가 신설 없음, 2026-07 기준).

| 이벤트 `type` | 의미 | TripFit 처리 |
|---------------|------|---------------|
| `consent-revoked` | 사용자가 설정에서 TripFit의 Apple 연동을 해제(로그아웃 의도, 계정 자체는 유지) | 해당 `sub` → `user.social_id` where `provider = APPLE` 조회 → `refresh_token` 전부 삭제(로그아웃 처리). **soft delete 안 함** — 계정 삭제 이벤트가 아님 |
| `account-delete` | 사용자가 Apple ID 자체를 영구 삭제 | 해당 user `deleted_at` 설정(soft delete) + `refresh_token` 삭제 |
| `email-enabled` | Private Relay 이메일 전달 재활성화 | MVP `user.email` 미보유 — 로그만 (wave 4 email 컬럼 추가 시 반영) |
| `email-disabled` | Private Relay 이메일 전달 비활성화 | MVP `user.email` 미보유 — 로그만 |

**식별**: 이벤트의 Apple user identifier(`sub`) → `user.social_id` where `provider = APPLE`.
**미인식 `type`**: 위 4종 외 값이 오면 로그만 남기고 200 반환(Apple이 향후 이벤트를 추가해도 5xx로 재시도 폭주가 나지 않게).

### Nice to Have

- [ ] 이벤트 idempotency (동일 `event id` 중복 처리 방지 테이블)
- [ ] 수신 로그·모니터링 (민감정보 마스킹)

### Out of Scope

- 카카오·구글 계정 변경 webhook
- GDPR export API
- 사용자에게 push 알림 (“계정이 삭제되었습니다”)

## API / 인터페이스

### `POST /api/v1/auth/apple/notifications`

| 항목 | 값 |
|------|-----|
| Auth | 불필요 (Apple 서버 호출) |
| Content-Type | `application/json` (Apple 스펙 따름) |
| Security | **요청 body 내 signed JWT** 검증 필수. 미검증 payload 신뢰 금지 |

**Request** (Apple 공식 구조 확정 — "Processing changes for Sign in with Apple accounts")

```json
{
  "payload": "<signed JWT from Apple>"
}
```

`payload`를 디코드한 outer JWT 클레임:

```json
{
  "iss": "https://appleid.apple.com",
  "aud": "<APPLE_CLIENT_ID>",
  "exp": 1234567890,
  "iat": 1234567890,
  "jti": "...",
  "events": "{\"type\":\"consent-revoked\",\"sub\":\"001234.abcd...\",\"event_time\":1234567890}"
}
```

`events`는 **JSON 문자열**이다 — outer JWT 파싱 후 한 번 더 `JSON.parse` 필요. `email-enabled`/`email-disabled`는 `events` 안에 `email`, `is_private_email` 필드가 추가로 붙는다.

**Response**

| HTTP | 상황 |
|------|------|
| 200 | 수신·처리 완료 — 존재하지 않는 `sub`, 미인식 `type`도 200(no-op)으로 응답해 Apple 재시도 폭주 방지 |
| 400 | payload 형식 오류 (JSON parse 실패 등) |
| 401 | JWT 서명 검증 실패 (`iss`/`aud` 불일치 포함) |

### SecurityConfig

- `/api/v1/auth/apple/notifications` — `permitAll` (Apple IP·서명 검증으로 보호)
- 일반 사용자 JWT 불필요

## 데이터 모델

MVP login 스펙의 `user`, `refresh_token`을 **수정·삭제**한다. 신규 테이블은 Nice to Have(idempotency)에서만 검토.

| 동작 | 대상 |
|------|------|
| `account-delete` | `user.deleted_at` 설정 + `refresh_token` 삭제 |
| `consent-revoked` | `refresh_token` 삭제만 (계정 유지) |

`erd.md` 변경이 필요하면 구현 PR에서 동기화.

## 환경 변수

login 스펙과 완전히 공유 — **신규 env 없음**. outer JWT는 Apple JWKS(공개키)로 서명 검증하고 `aud`만 `APPLE_CLIENT_ID`와 비교하면 되므로 `.p8`/Team ID/Key ID로 만드는 client_secret JWT(발신용)는 필요 없다 — 이 webhook은 수신 전용.

| 변수 | 용도 |
|------|------|
| `APPLE_CLIENT_ID` | outer JWT `aud` 검증 (login 스펙과 동일 값) |

> `APPLE_TEAM_ID`/`APPLE_KEY_ID`/`APPLE_PRIVATE_KEY`는 `#64`(탈퇴 시 Apple revoke — client_secret JWT **발신** 필요)에서만 쓰인다. 이 스펙(#5)과는 무관.

## 검증 시나리오

- [ ] 유효한 Apple signed JWT(`consent-revoked`) → 200 + 해당 user `refresh_token` 삭제, `deleted_at`은 유지
- [ ] 유효한 Apple signed JWT(`account-delete`) → 200 + 해당 user soft delete + `refresh_token` 삭제
- [ ] 유효한 Apple signed JWT(`email-enabled`/`email-disabled`) → 200 + 로그만, DB 변경 없음
- [ ] 위조 JWT(서명 불일치) → 401
- [ ] `aud` 불일치 JWT → 401
- [ ] 존재하지 않는 `sub` → 200 (no-op)
- [ ] 미인식 `type` → 200 (no-op, 로그만)

## 완료 기준

- [ ] 엔드포인트 staging에서 Apple Console 테스트 알림 수신 성공
- [ ] production URL 등록
- [ ] `./gradlew test` 통과
- [ ] `platform.md` 스토어 직전 체크리스트 반영

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 엔드포인트 path | **확정** `/api/v1/auth/apple/notifications` | 구현 완료 후 Apple Developer Console에 동일 URL을 Server-to-Server Notification Endpoint로 등록(#5 담당 구분 표 참고) |
| 이벤트 type 전체 목록 | **확정** — `consent-revoked` / `account-delete` / `email-enabled` / `email-disabled` 4종 (Apple 공식 문서 기준) | 신규 type이 추가되면 스펙 amend 후 반영 — 그 전까진 미인식 type은 200 no-op |
| 존재하지 않는 `sub` 응답 | **확정** — 200 no-op | Apple 재시도 폭주 방지 우선 |
| `user.email` 미보유 시 email 이벤트 처리 | Out for MVP | 로그만, wave 4에서 email 컬럼 추가 시 재검토 |
| idempotency(동일 event 중복 처리) | Nice to Have로 유지 | `consent-revoked`(refresh_token 삭제)·`account-delete`(soft delete) 모두 자연히 idempotent한 연산이라 dedup 테이블 없이도 안전 — Must 승격 불필요 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-06 | 초안 — 하이브리드 앱·스토어 심사 맥락에서 분리 작성 |
| 2026-07-30 | Approved — 이벤트 4종·payload 구조(Apple 공식 문서 확인)·path·미인식 sub 응답 확정. `consent-revoked`는 soft delete 아닌 로그아웃(refresh_token 삭제)만으로 정정. 신규 env 불필요함을 명시 |
