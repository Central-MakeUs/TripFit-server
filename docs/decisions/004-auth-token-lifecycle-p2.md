# 인증 토큰 Lifecycle P2 — RTR + Redis

- **상태:** 확정 (Redis access 전략만 `[미정]`)
- **날짜:** 2026-07-06
- **관련:**
  - [`docs/decisions/001-auth-mobile-token-verification.md`](001-auth-mobile-token-verification.md) — 안 B (Phase 1)
  - [`docs/specs/auth-social-login-mvp.md`](../specs/auth-social-login-mvp.md) — Phase 1 구현
  - [`docs/specs/auth-token-lifecycle-p2.md`](../specs/auth-token-lifecycle-p2.md) — P2 구현 스펙

## 맥락

Phase 1(MVP)은 `POST /auth/login`, `/refresh`, `/logout`과 DB 기반 opaque refresh token, stateless access JWT(2h)로 시작한다.

운영·보안 요구가 커지면서 아래가 필요해진다.

- **Refresh Token Rotation (RTR):** refresh 호출마다 새 refresh token 발급, 구 token 폐기, 탈취·재사용 탐지
- **Redis:** access JWT 또는 세션 상태를 인프라 레벨에서 관리 (logout 즉시 무효, 다중 인스턴스 공유)

## 결정

### 확정

1. **Refresh Token Rotation (RTR)을 도입한다.**
   - refresh 시 새 opaque refresh token 발급 + 기존 token revoke
   - `family_id`로 같은 로그인 체인 묶음, **폐기된 refresh 재사용 시 해당 family 전체 revoke** (reuse detection)
   - refresh token SSOT는 **MySQL `refresh_token` 테이블** (Redis에 refresh 저장하지 않음)

2. **Redis를 인증 인프라에 도입한다.**
   - 용도: access JWT 관련 **상태 저장·조회** (블랙리스트 또는 화이트리스트 — 아래 `[미정]`)
   - Phase 1 구현 시 Redis **미포함**. P2 스펙 Approved 후 추가

### `[미정]` — Redis access JWT 전략

블랙리스트 vs 화이트리스트는 **P2 구현 착수 전** 팀 합의한다.

| 전략 | 개요 | 장점 | 단점 |
|------|------|------|------|
| **Blacklist** | logout·강제 revoke 시 `jti`를 Redis에 TTL=access 남은 수명으로 등록 | stateless 발급 유지, 2h TTL이라 키 수 적음 | 평소에는 Redis 미조회, revoke 시에만 기록 |
| **Whitelist** | 유효 access마다 `jti`를 Redis에 TTL=access 수명으로 등록, 매 API 요청 시 존재 확인 | 즉시 전역 세션 통제·강제 로그아웃 용이 | 모든 인증 API마다 Redis 조회 |

**현재 가이드 (결정 전 참고):** access TTL 2h·하이브리드 앱 기준 **blacklist 우선 검토**. whitelist는 “전 기기 즉시 차단” 요구가 명확해질 때 decisions amend.

## Phase 1 → P2 관계

| 영역 | Phase 1 (MVP) | P2 (본 결정) |
|------|---------------|--------------|
| login API | `POST /auth/login` | 변경 없음 |
| refresh API | access JWT만 재발급, refresh row **유지** | access + **새 refreshToken** 재발급, 구 token revoke |
| refresh 저장 | MySQL | MySQL (RTR) |
| access JWT | stateless, `jti` claim **포함** (P2 대비) | + Redis 검증 (blacklist 또는 whitelist) |
| logout | refresh row 삭제 | refresh revoke + access `jti` Redis 등록 (전략 확정 후) |

Phase 1 코드는 P2를 막지 않도록 **`jti`**, **`family_id`**, **`TokenRevocationChecker` interface(NoOp)** 를 포함한다. 상세는 [`auth-social-login-mvp.md`](../specs/auth-social-login-mvp.md).

## 고려한 대안

| 대안 | 채택 여부 | 사유 |
|------|-----------|------|
| Refresh Redis 저장 | **미채택** | opaque token + RTR은 DB가 audit·family revoke에 유리 |
| Phase 1부터 RTR | **미채택** | MVP 일정·프론트 계약 단순화 우선 |
| Redis 없이 access만 stateless | Phase 1만 | logout 후 access 2h 유효 — P2에서 Redis로 해소 |
| Refresh rotation 없음 | **미채택** | refresh 탈취 시 장기 세션 위험 |

## 트레이드오프 · 후속 리스크

- **프론트 계약 변경:** P2 refresh 응답에 `refreshToken` 필드 추가 — 클라이언트 저장 로직 필요
- **Redis 운영:** EC2 colocated vs ElastiCache `[미정]` — `deploy/`·P2 스펙에서 확정
- **Reuse false positive:** 네트워크 재시도로 구 refresh 재전송 시 family revoke — 클라이언트는 새 refresh만 사용하도록 가이드

## 후속 작업

- [ ] [`auth-token-lifecycle-p2.md`](../specs/auth-token-lifecycle-p2.md) Draft → Approved
- [ ] Redis access 전략(blacklist vs whitelist) 팀 합의 → 본 decisions amend
- [ ] Phase 1 구현 완료 후 P2 착수 (JwtFilter + Redis + RTR)
