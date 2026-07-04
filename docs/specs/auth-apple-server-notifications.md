# Apple Sign In — Server-to-Server Notification

> 상태: Draft  
> 범위: **스토어 제출 전** (MVP 로그인 스펙과 별도)  
> 관련: [`auth-social-login-mvp.md`](auth-social-login-mvp.md), [`docs/product/platform.md`](../product/platform.md)  
> 결정: [`docs/decisions/001-auth-mobile-token-verification.md`](../decisions/001-auth-mobile-token-verification.md)

## 목표

한국 App Store 앱에서 Sign in with Apple을 지원할 때, Apple이 push하는 **계정 변경 이벤트**를 TripFit 백엔드가 수신·검증·처리하여 개인정보를 동기화하고 심사 요건을 충족한다.

## 배경

- TripFit은 카카오·구글·애플 소셜 로그인을 지원한다 (`auth-social-login-mvp.md`)
- 2026년 이후 한국 앱에서 Sign in with Apple 사용 시, **Server-to-Server Notification** 엔드포인트 구현이 권장·심사에 영향을 줄 수 있다
- 목적: 사용자의 이메일 전달 설정 변경, 앱 내 계정 삭제, 애플 계정 영구 삭제 등 → TripFit `user` 및 연관 데이터 동기화
- 참고: [Apple News — account change notifications](https://developer.apple.com/news/?id=j9zukcr6)
- 가이드: [App Store Review Guidelines — Login Services](https://developer.apple.com/kr/app-store/review/guidelines/#login-services)

### 본 스펙과 MVP 로그인 스펙의 관계

| 스펙 | 시점 | 내용 |
|------|------|------|
| `auth-social-login-mvp.md` | MVP | `POST /auth/login` — Apple `id_token` 검증 + JWT 발급 |
| **본 스펙** | 스토어 직전 | Apple → TripFit **webhook** — 계정 lifecycle 이벤트 |

로그인 API만 구현하고 webhook 없이 제출하면 심사에서 지적될 수 있다.

## 아키텍처 개요

```
[Apple 서버]
  사용자 계정 변경 (이메일 설정, 앱 연결 해제, 계정 삭제 등)
        ↓ HTTPS POST (signed JWT payload)
POST /api/v1/auth/apple/notifications  (또는 팀 합의 path)
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
- [ ] `deploy/app/.env.example` — Apple 관련 env 정리 (login 스펙과 공유 가능한 항목)

### 이벤트 처리 (초안)

Apple payload의 `events` 배열 항목 `type` 기준. 정확한 enum은 Apple 문서·수신 샘플로 구현 시 확정.

| 이벤트 (예시) | TripFit 처리 (초안) |
|---------------|---------------------|
| `email-disabled` / `email-enabled` | MVP에 `user.email` 없음 — 로그만 또는 P2에서 email 컬럼 추가 시 반영 |
| `consent-revoked` | 해당 `sub`(Apple `social_id`) user — refresh token 전부 삭제, 필요 시 soft delete |
| `account-delete` | 해당 user soft delete + refresh token 삭제 + 연관 데이터 정책 (`user.md` BR 확인) |

**식별**: 이벤트의 Apple user identifier → `user.social_id` where `provider = APPLE`.

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

**Request** (Apple 형식 — 구현 시 Apple 문서 SSOT)

```json
{
  "payload": "<signed JWT from Apple>"
}
```

> 실제 필드명·구조는 Apple Sign in with Apple Server-to-Server Notifications 문서를 따른다. 구현 PR에서 Apple 공식 스펙 링크를 코드 주석·OpenAPI에 명시.

**Response**

| HTTP | 상황 |
|------|------|
| 200 | 수신·처리 완료 (Apple 재전송 방지) |
| 400 | payload 형식 오류 |
| 401 | JWT 서명 검증 실패 |

### SecurityConfig

- `/api/v1/auth/apple/notifications` — `permitAll` (Apple IP·서명 검증으로 보호)
- 일반 사용자 JWT 불필요

## 데이터 모델

MVP login 스펙의 `user`, `refresh_token`을 **수정·삭제**한다. 신규 테이블은 Nice to Have(idempotency)에서만 검토.

| 동작 | 대상 |
|------|------|
| `account-delete` / `consent-revoked` | `user.deleted_at` 설정, `refresh_token` 삭제 |

`erd.md` 변경이 필요하면 구현 PR에서 동기화.

## 환경 변수

login 스펙과 공유 가능:

| 변수 | 용도 |
|------|------|
| `APPLE_CLIENT_ID` | JWT `aud` 검증 (Services ID) |
| `APPLE_TEAM_ID` | `[미정]` — Apple 콘솔 설정과 함께 확정 |

## 검증 시나리오

- [ ] 유효한 Apple signed JWT → 200 + 해당 user 처리
- [ ] 위조 JWT → 401
- [ ] `account-delete` → Apple `sub` user soft delete + refresh token 없음
- [ ] 존재하지 않는 `sub` → 200 (no-op, Apple 재시도 방지) 또는 404 — **팀 합의** `[미정]`

## 완료 기준

- [ ] 엔드포인트 staging에서 Apple Console 테스트 알림 수신 성공
- [ ] production URL 등록
- [ ] `./gradlew test` 통과
- [ ] `platform.md` 스토어 직전 체크리스트 반영

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 정확한 path (`/auth/apple/notifications` vs 다른 path) | `[미정]` | Apple Console 등록 URL과 일치 필요 |
| `user.email` 미보유 시 email 이벤트 처리 | Out for MVP | 로그만 또는 P2 |
| 이벤트 type 전체 목록 | `[미정]` | Apple 문서·실제 payload로 확정 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-06 | 초안 — 하이브리드 앱·스토어 심사 맥락에서 분리 작성 |
