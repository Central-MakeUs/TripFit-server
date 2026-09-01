# 인증 토큰 Rotation — RTR + Redis

- **wave:** 4
- **상태:** 확정
- **날짜:** 2026-07-06 (Redis access 전략 확정: 2026-08-08)
- **관련:**
  - [`docs/decisions/001-auth-mobile-token-verification.md`](001-auth-mobile-token-verification.md) — 안 B (wave 1)
  - [`docs/decisions/010-redis-infra.md`](010-redis-infra.md) — Redis 인프라 배치(EC2 D)
  - [`docs/specs/auth/auth-social-login.md`](../specs/auth/auth-social-login.md) — wave 1 구현
  - [`docs/specs/auth/auth-token-rotation.md`](../specs/auth/auth-token-rotation.md) — wave 4 구현 스펙

## 맥락

wave 1(MVP)은 `POST /auth/login`, `/refresh`, `/logout`과 DB 기반 opaque refresh token, stateless access JWT(2h)로 시작한다.

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
   - 용도: access JWT 관련 **상태 저장·조회** — **Blacklist**로 확정(아래)
   - wave 1 구현 시 Redis **미포함**. wave 4 스펙 Approved 후 추가

### Redis access JWT 전략 — Blacklist (2026-08-08 확정)

- 평소엔 Redis 미조회(JWT 서명·만료만으로 통과), logout·탈퇴 시에만 `SET auth:bl:{jti} 1 EX {remaining_ttl}`
- **채택 이유:** Whitelist는 로그인·갱신마다 쓰기가 발생하고 활성 세션 전체를 Redis가 들고 있어야 하는데, `010-redis-infra.md`의 EC2 D는 이중화 없는 **단일 인스턴스**라 Redis 장애 시 "허용 목록 확인 불가 = 전체 로그인 마비"(fail-closed)가 된다. Blacklist는 같은 장애 상황에서 "즉시 무효화 기능만 잠깐 못 씀, 로그인 자체는 wave 1처럼 정상 동작"(fail-open)으로 훨씬 안전하게 열화된다. 데이터량·쓰기 부하도 Blacklist가 구조적으로 적다(로그아웃한 사람만 기록, 로그인마다 기록 아님)
- 상세 키 설계·JwtFilter 동작은 [`docs/specs/auth/auth-token-rotation.md`](../specs/auth/auth-token-rotation.md)가 SSOT

## wave 1 → wave 4 관계

전환 상세(API 응답 변경·데이터 모델·환경 변수)는 [`docs/specs/auth/auth-token-rotation.md`](../specs/auth/auth-token-rotation.md)가 SSOT. 요지만: wave 1 코드는 wave 4를 막지 않도록 **`jti`**, **`family_id`**, **`TokenRevocationChecker` interface(NoOp)** 를 이미 포함한다 — wave 4는 RTR 로직 + Redis 연동을 **추가**할 뿐 wave 1 API 계약(`POST /auth/login`)은 바꾸지 않는다.

## 고려한 대안

| 대안 | 채택 여부 | 사유 |
|------|-----------|------|
| Refresh Redis 저장 | **미채택** | opaque token + RTR은 DB가 audit·family revoke에 유리 |
| wave 1부터 RTR | **미채택** | MVP 일정·프론트 계약 단순화 우선 |
| Redis 없이 access만 stateless | wave 1만 | logout 후 access 2h 유효 — wave 4에서 Redis로 해소 |
| Refresh rotation 없음 | **미채택** | refresh 탈취 시 장기 세션 위험 |

## 트레이드오프 · 후속 리스크

- **프론트 계약 변경:** wave 4 refresh 응답에 `refreshToken` 필드 추가 — 클라이언트 저장 로직 필요
- **Redis 운영:** 신규 EC2 D(self-managed, ElastiCache 미채택) — 상세 근거는 [`010-redis-infra.md`](010-redis-infra.md). 이중화 없는 단일 인스턴스라 fail-open(Blacklist) 전제가 중요
- **Reuse false positive:** 네트워크 재시도로 구 refresh 재전송 시 family revoke — 클라이언트는 새 refresh만 사용하도록 가이드
- **Family revoke ↔ access token 연결 미정:** reuse detection으로 refresh family가 revoke돼도, 현재 `AccessTokenClaims`엔 `family_id`가 없어 그 family로 발급된 access token을 추적해 같이 블랙리스트에 올릴 방법이 없음 — 스펙 작업 시 Must Have 포함 여부 결정

## 후속 작업

- [x] Redis access 전략(blacklist vs whitelist) 팀 합의 → 본 decisions amend (2026-08-08, Blacklist 확정)
- [x] Redis 인프라 배치 합의 → [`010-redis-infra.md`](010-redis-infra.md) (2026-08-08, EC2 D 확정)
- [ ] [`auth-token-rotation.md`](../specs/auth/auth-token-rotation.md) Draft → Approved
- [ ] wave 1 구현 완료 후 wave 4 착수 (JwtFilter + Redis + RTR)
