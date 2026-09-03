# 인증 토큰 저장·전달 방식 개편 — Refresh Redis 이관 + HttpOnly 쿠키

> 상태: Implemented (`#2` Closed · PR #121 merge·배포 완료 — 2026-08-19)
> MVP: In scope (기존 인증 흐름 전체에 영향 — 신규 기능이 아니라 인프라·보안 개편)
> 관련 BR: 해당 없음
> 이슈: [#2](https://github.com/Central-MakeUs/TripFit-server/issues/2)

## 목표

리프레시 토큰을 MySQL이 아닌 Redis에 저장하고, 클라이언트에는 JSON 바디가 아닌 HttpOnly 쿠키로 내려줘서 (1) 프론트가 리프레시 토큰을 저장·관리하다 실수로 잃어버리는 문제(새로고침 시 소실 — 아래 배경 참고)를 구조적으로 없애고, (2) XSS로 탈취되더라도 JS가 절대 읽을 수 없게 한다. 그 대신 액세스 토큰 블랙리스트(즉시 무효화)를 폐기해 인증 검증을 완전히 무상태로 만들고, 그로 인한 "로그아웃해도 잠깐 유효함" 리스크는 액세스 토큰 수명을 2시간 → 15분으로 크게 줄여서 보완한다.

## 배경

- 사용자가 "리프레시 토큰이 갱신 안 돼서 2시간마다 로그아웃된다"고 신고 (2026-08-17).
- 조사 결과: `JwtAuthenticationFilter`가 `permitAll` 경로(`/auth/refresh` 등)까지 막던 백엔드 버그는 이미 `c08a545`로 수정·배포 완료 확인됨(운영 서버에서 직접 재현 테스트로 검증).
- 실제 원인은 프론트(`TripFit-client`) `stores/authStore.ts`의 zustand `persist` `partialize` 허용 목록에 `refreshToken`이 빠져 있어, 새로고침·앱 재실행 시 메모리의 `refreshToken`이 사라지는 것이었음 — `accessToken`(2h)만 복구되고 `refreshToken`이 없으니, 그 접속에서 액세스 토큰이 만료되는 순간 재발급이 원천 불가능해 강제 로그아웃됨.
- 이 조사를 계기로 사용자가 토큰 저장·전달 방식 자체를 아래 3가지로 개편하기로 결정:
  1. `refreshToken`을 Redis에 저장
  2. 액세스 토큰 블랙리스트 폐기 (무상태성 강화)
  3. `accessToken`은 클라이언트 메모리 유지, `refreshToken`만 HttpOnly 쿠키로 전달 + 보안 옵션 추가
- 관련 문서: [`docs/decisions/004-auth-token-rotation.md`](../../decisions/004-auth-token-rotation.md), [`docs/decisions/010-redis-infra.md`](../../decisions/010-redis-infra.md), [`docs/specs/auth/auth-token-rotation.md`](auth-token-rotation.md)(출시 이후, Approved) — 이번 스펙은 그 출시 이후 스펙의 핵심 전제 두 가지(refresh는 MySQL SSOT, access는 Blacklist)를 **뒤집는** 후속 개편이다.

## 변경 범위 (기존 Approved 스펙 amend)

`auth-token-rotation.md`(출시 이후, Approved 2026-08-10)의 아래 전제를 변경한다:

### ADDED

- Redis에 refresh token 4종 키 저장 (아래 데이터 모델 참고): `auth:refresh:active:{token}`, `auth:refresh:family:{familyId}`, `auth:refresh:revoked:{token}`(tombstone, reuse 탐지용), `auth:refresh:user:{userId}`(탈퇴 시 일괄 회수용 인덱스)
- `POST /auth/login`, `POST /auth/refresh` 응답에 `Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Lax; Domain=...; Path=/api/v1/auth; Max-Age=2592000`
- `POST /auth/logout` 응답에 `Set-Cookie: refreshToken=; Max-Age=0`(쿠키 삭제)
- 환경변수 `COOKIE_DOMAIN` (local/dev: `localhost`, prod: `.tripfit.online`)
- reuse detection 발생 시 WARN 로그(`userId`, `familyId` 포함) — Redis TTL 만료로 사라지는 감사 추적성을 로그(EC2 C Loki 수집 대상)로 보완

### MODIFIED

- `refresh_token` 저장소: MySQL 테이블 → Redis 키 (SSOT 이동)
- `tripfit.jwt.access-expiration-seconds` 기본값: `7200`(2시간) → `900`(15분)
- `LoginResponse`: `refreshToken` 필드 제거 (쿠키로 이동), `accessToken`/`expiresIn`/`user`만 남음
- `RefreshResponse`: `refreshToken` 필드 제거, `accessToken`/`expiresIn`만 남음
- `POST /auth/refresh`: 요청 바디의 `refreshToken` → `@CookieValue`로 수신. 바디 자체가 불필요해짐
- `POST /auth/logout`: 요청 바디의 `refreshToken`(쿠키로 이동), `accessToken`(블랙리스트 폐기로 무의미) 모두 제거 — 바디 없는 POST로 단순화

### REMOVED

- `RefreshToken` JPA 엔티티, `RefreshTokenRepository`, DB `refresh_token` 테이블(엔티티 삭제 → `ddl-auto`가 자동 반영, 마이그레이션 불필요)
- `RedisTokenRevocationChecker`, `TokenRevocationChecker` 인터페이스, `auth:bl:{jti}` 키 로직 전체
- `AuthService.logout()`의 `accessTokenValue` 파라미터·블랙리스트 등록 분기
- `UserWithdrawalService.withdraw()`의 `accessTokenJti`/`accessTokenExpiresAt` 파라미터와 `tokenRevocationChecker.revoke(...)` 호출부, 그리고 이 값을 만들어 넘기던 Controller/AOP 쪽 코드
- `AccessTokenClaims`의 `jti` 클레임 — 구현 중 grep해서 블랙리스트 외 다른 용도로 쓰이지 않는 것 확인되면 같이 제거
- `LogoutRequest`, `RefreshRequest` DTO — 필드가 전부 사라져 빈 레코드가 되면 클래스 자체 삭제
- 관련 테스트: `RedisTokenRevocationCheckerTest`, `AuthServiceTest.logout_withAccessToken_alsoBlacklistsJti`류, `JwtAuthenticationFilterTest`의 블랙리스트 조회 케이스, `RefreshTokenRepository` 관련 테스트 등 — grep으로 전수 확인 후 삭제
- `docs/decisions/004-auth-token-rotation.md`의 "Redis access 전략 — Blacklist" 절, "고려한 대안" 표의 "Refresh Redis 저장: 미채택" 행 — 이번 스펙 승인 시 amend
- `docs/architecture/erd.md`의 `refresh_token` 엔티티·`users ||--o{ refresh_token` 관계

## 요구사항

### Must Have

- [x] `RefreshTokenService`를 Redis 4-키 설계로 재구현(active/family/revoked tombstone + user 인덱스) — 기존 RTR(rotation)·reuse detection 시맨틱을 100% 유지 (아래 데이터 모델 참고)
- [x] `login`/`refresh` 컨트롤러가 `ResponseCookie`로 `refreshToken`을 Set-Cookie 응답, JSON 바디에서 `refreshToken` 제거
- [x] `refresh`/`logout` 컨트롤러가 `@CookieValue(value = "refreshToken", required = false)`로 수신 — 쿠키 없으면 `AUTH_INVALID_REFRESH`(401)
- [x] 쿠키 속성: `HttpOnly`, `Secure`(로컬만 false), `SameSite=Lax`, `Domain=${COOKIE_DOMAIN}`, `Path=/api/v1/auth`, `Max-Age`=`refresh-expiration-days`와 동일(30일)
- [x] `logout` 응답이 `Max-Age=0`으로 쿠키를 지움
- [x] `tripfit.jwt.access-expiration-seconds` 기본값 900으로 변경 (`application.yml`, `application-test.yml` 동기화)
- [x] 액세스 토큰 블랙리스트 관련 코드 전체 삭제 (`RedisTokenRevocationChecker`/`TokenRevocationChecker`/`auth:bl:` 키)
- [x] `logout`·`withdraw` 흐름에서 액세스 토큰 즉시 무효화 호출 제거 + 위 REMOVED 목록의 미사용 파라미터·클레임·DTO·테스트 정리
- [x] reuse detection 발생 시 WARN 로그 (userId, familyId) 추가
- [x] `docs/decisions/004-auth-token-rotation.md`, `docs/architecture/erd.md`, `docs/specs/auth/auth-token-rotation.md`(상호 링크 또는 대체 명시) 동기화
- [ ] STOP §5 대상 — 커밋에 `Breaking-Change-Reason:` 트레일러 포함 (`LoginResponse`/`RefreshResponse`/`LogoutRequest`/`RefreshRequest` 필드 삭제·API 계약 변경) — 커밋 시점에 체크
- [x] `./gradlew test` 전체 통과 (491개, 0 실패)

### Nice to Have

- [ ] Redis 이중화(Sentinel/Cluster) 검토 — 이번 라운드는 미채택(단일 인스턴스 SPOF 리스크 감수로 확정), 트래픽 증가 시 재검토
- [ ] CSRF 방어 강화(예: double-submit 쿠키) — 이번 라운드는 `SameSite=Lax`로만 대응

### Out of Scope (이번 스펙에서 하지 않음)

- 네이티브 앱(RN 등)의 인증 방식 결정 — 쿠키를 그대로 쓸지, 헤더+바디 방식을 유지할지는 네이티브 착수 시점에 별도 결정
- `accessToken`을 쿠키로 옮기는 것 — 메모리 저장 + `Authorization` 헤더 유지
- Redis 이중화 구축

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/login` | 없음 | 응답 바디에서 `refreshToken` 제거, `Set-Cookie`로 전달 (그 외 계약 동일) |
| POST | `/api/v1/auth/refresh` | 쿠키(`refreshToken` 필수) | 요청 바디 없음. 응답 바디 `{accessToken, expiresIn}`만, `Set-Cookie`로 새 `refreshToken` |
| POST | `/api/v1/auth/logout` | 쿠키(`refreshToken`, optional) | 요청 바디 없음. 204. `Set-Cookie`로 쿠키 삭제 |

성공 (`refresh`):

```json
{
  "data": {
    "accessToken": "<jwt>",
    "expiresIn": 900
  }
}
```

실패 — 쿠키 없음/만료/폐기 (동일):

```json
{
  "code": "AUTH_INVALID_REFRESH",
  "message": "유효하지 않은 리프레시 토큰입니다."
}
```

실패 — 재사용 감지 (동일, family 전체 revoke):

```json
{
  "code": "AUTH_REFRESH_REUSE",
  "message": "재사용된 리프레시 토큰이 감지되어 다시 로그인해야 합니다."
}
```

## 데이터 모델

- ERD 변경: `docs/architecture/erd.md`에서 `refresh_token` 테이블·관계 삭제. Redis는 ERD(RDB 스키마 다이어그램) 대상이 아니므로 이 스펙 문서에 키 설계만 남긴다.

### Redis 키 설계 (신규)

기존 DB 방식의 "revoked_at 컬럼으로 폐기된 row를 남겨 reuse를 구분"하는 능력을 Redis에서도 동일하게 재현하기 위해 3개 키 네임스페이스를 쓴다 (단순 `SET`+`DEL`만으로는 "폐기됨"과 "애초에 없음"을 구분할 수 없어 reuse detection이 깨지기 때문):

| 키 패턴 | 값 | TTL | 용도 |
|---------|-----|-----|------|
| `auth:refresh:active:{token}` | `{userId}\|{familyId}` | 30일 | 현재 유효한 refresh token 1건 |
| `auth:refresh:family:{familyId}` | 현재 active token 값 | 30일 (rotate마다 갱신) | family의 "지금 살아있는 토큰"을 찾기 위한 역인덱스 — reuse 탐지 시 이 토큰을 revoke |
| `auth:refresh:revoked:{token}` | `{userId}\|{familyId}` | 30일 (rotate 시점 기준 재설정) | tombstone — 이미 rotate로 폐기된 토큰이 다시 제출되면 "reuse"로 식별하기 위함 |
| `auth:refresh:user:{userId}` | familyId SET | 30일 (family 추가마다 갱신) | 그 유저가 가진 로그인 체인(family) 목록 — 탈퇴 시 `revokeAllForUser`가 이 SET을 순회해 전부 회수. 개별 family가 자연 만료돼도 이 SET엔 stale하게 남을 수 있지만, 존재하지 않는 항목 삭제 시도만 하는 no-op이라 무해 |

**rotate(token) 흐름:**

1. `EXISTS auth:refresh:revoked:{token}` → 있으면 **reuse 탐지**: 값에서 `familyId` 조회 → `GET auth:refresh:family:{familyId}`로 현재 active 토큰 찾아 `DEL auth:refresh:active:{그 토큰}` + `DEL auth:refresh:family:{familyId}` (family 전체 무효화) → WARN 로그 → `AUTH_REFRESH_REUSE`
2. 아니면 `GET auth:refresh:active:{token}` → 없으면 (`만료돼 TTL로 자연 소멸` 또는 `애초에 존재한 적 없음`) → `AUTH_INVALID_REFRESH`
3. 있으면 정상 rotate: `SETEX auth:refresh:revoked:{token} 30d {familyId}` (tombstone 생성) → `DEL auth:refresh:active:{token}` → 새 token 발급 → `SETEX auth:refresh:active:{new} 30d {userId}|{familyId}` → `SETEX auth:refresh:family:{familyId} 30d {new}`

**로그인(신규 family) 흐름:** `familyId` UUID 신규 생성 → active/family 키 SET + `auth:refresh:user:{userId}`에 familyId 추가.

**탈퇴(`revokeAllForUser`) 흐름:** `auth:refresh:user:{userId}` SET을 순회 → 각 familyId의 현재 active 토큰을 찾아 active/family 키 삭제 → 마지막에 user 인덱스 키 자체도 삭제.

**메모리 영향:** tombstone까지 유지해 기존보다 키 수가 늘지만, `010-redis-infra.md`가 이미 "토큰 블랙리스트·카운터·락 키 정도는 데이터량이 작아 t3.micro로 충분"이라고 판단한 근거가 refresh 이관 후에도 유효한 규모(사용자당 활성 로그인 체인 수준)로 판단됨 — 실제 배포 후 메모리 사용량 확인 필요(완료 기준 항목).

## 비즈니스 규칙

해당 없음 (인증 인프라 개편, 도메인 BR 아님)

## 검증 시나리오

### 정상

- [ ] login → Set-Cookie로 refreshToken 수신, 바디엔 accessToken만
- [ ] refresh(쿠키) → 새 accessToken + 새 Set-Cookie, 이전 쿠키 값으로 재요청 시 실패
- [ ] login → refresh → refresh 체인 시 동일 `familyId` 유지 (Redis 값으로 확인)
- [ ] logout → 쿠키 삭제 응답(Max-Age=0) + Redis에서 해당 active/family 키 삭제

### 엣지 · 실패

- [ ] 쿠키 없이 `/refresh` 호출 → 401 `AUTH_INVALID_REFRESH`
- [ ] 이미 rotate로 폐기된(tombstone 존재) refreshToken 재사용 → 401 `AUTH_REFRESH_REUSE` + 같은 family로 발급된 현재 active 토큰도 무효화됨(그 토큰으로 재차 refresh 시도 시 `AUTH_INVALID_REFRESH`) + WARN 로그 기록 확인
- [ ] 자연 만료(30일 경과, Redis TTL로 자동 삭제)된 토큰으로 refresh → 401 `AUTH_INVALID_REFRESH`
- [ ] logout/탈퇴 후 이미 발급된 accessToken으로 API 호출 → 자연 만료(15분) 전까지는 정상 통과됨을 확인 (의도된 트레이드오프 — 회귀 아님)

### 수동 / 통합 (해당 시)

- [ ] `curl -c cookie.txt`로 login → cookie.txt에 `HttpOnly`, `Secure`, `Domain`, `SameSite` 속성 실제로 찍히는지 확인
- [ ] 로컬(`COOKIE_DOMAIN=localhost`)·운영(`COOKIE_DOMAIN=.tripfit.online`) 두 환경 모두 쿠키가 정상 왕복되는지 확인 (Vercel `tripfit.online` ↔ EC2 `api.tripfit.online` 서브도메인 간)

## 프론트엔드 권장 사항 (이 저장소 구현 범위 밖 — `TripFit-client`에 전달용)

이 스펙으로 `refreshToken`이 HttpOnly 쿠키로 옮겨가면, 프론트가 토큰을 다루는 방식도 같이 바뀌어야 이번 개편의 보안 목적(액세스 토큰은 메모리에만, 브라우저 저장소엔 토큰 흔적 없음)이 실제로 완성된다. 백엔드 코드 변경은 아니므로 Must Have에 넣지 않지만, 승인 시 프론트팀에 그대로 전달한다.

- **`accessToken`을 더 이상 `localStorage`에 저장하지 않는다.** 현재 `stores/authStore.ts`의 zustand `persist` `partialize`가 "새로고침 편의"를 이유로 `accessToken`을 저장 대상에 포함하고 있는데, 이 이유 자체가 아래 silent refresh로 대체되므로 없어진다. `accessToken`은 순수 JS 상태(zustand의 비영속 필드)로만 유지한다.
- **앱 부팅 시 "silent refresh"를 도입한다.** 새로고침·앱 재실행 직후에는 메모리에 `accessToken`이 없는 게 정상이므로, 앱 초기화 시점에 (쿠키에 실려 자동으로 오는) `refreshToken`으로 `POST /api/v1/auth/refresh`를 먼저 호출해 `accessToken`을 받아온 뒤 화면을 그린다. 이 호출이 401(`AUTH_INVALID_REFRESH`)로 실패하면 비로그인 상태로 처리한다.
- 결과적으로 `localStorage`(또는 그 외 JS가 읽을 수 있는 저장소)에는 토큰류가 전혀 남지 않게 되어, XSS로 스크립트가 실행되더라도 토큰을 그 자리에서 훔쳐갈 수 있는 표면이 없어진다.
- `apis/request.ts`의 401 인터셉터·`refreshAccessToken()` 로직도 body에서 `refreshToken`을 읽고 쓰던 부분을 제거하고, 쿠키 기반(별도 코드 없이 브라우저가 자동 전송)으로 단순화한다.

## 완료 기준

- [x] `./gradlew test` 통과 (491개, 0 실패)
- [x] `./gradlew build` 성공
- [x] `REMOVED` 항목 실제 삭제 확인 (엔티티·Repository·블랙리스트 코드·구 DTO 필드·관련 테스트 — grep으로 전수 확인)
- [x] OpenAPI/Swagger 반영 — `@Parameter(in = ParameterIn.COOKIE)`로 `refresh`/`logout` 문서화, `LoginResponse`/`RefreshResponse` 스키마에서 `refreshToken` 제거 확인
- [ ] 배포 후 Redis 메모리 사용량 확인 (EC2 D, t3.micro 한도 내인지) — 실제 배포 후에만 가능, 이번 라운드는 커밋·PR·배포 전 단계
- [x] 프론트(`TripFit-client`) 쪽에 API 계약 변경 + "프론트엔드 권장 사항"(accessToken 비영속화, silent refresh) 전달 — 채팅으로 전달 완료(2026-08-19). `apis/request.ts`의 refresh 로직(현재 body로 refreshToken 전송)을 쿠키 기반으로 재작성은 프론트팀 작업

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| Redis 단일 인스턴스(EC2 D) SPOF | 확정 (리스크 감수) | Redis 장애 시 로그인 갱신 전체 불가(fail-closed) — `010-redis-infra.md`가 애초에 "App 확장 시 재검토" 대상으로 남겨둔 리스크와 동일선상. 트래픽 증가 시 이중화 재검토 |
| 액세스 토큰 즉시 무효화 불가 | 확정 (TTL 900초로 보완) | 로그아웃·탈퇴 후에도 이미 발급된 accessToken은 최대 15분 유효 |
| 감사 추적성(audit trail) 약화 | 확정 (로그로 보완) | 기존엔 MySQL에 폐기된 row가 남아 질의 가능했으나, Redis TTL 만료 후엔 흔적이 사라짐 — reuse 탐지 시 WARN 로그로 최소한의 사후 조회 경로 확보 |
| 네이티브 앱 쿠키 호환 | [미정] | 네이티브 착수 시점에 별도 결정 — 이번 스펙 Out of Scope |
| CSRF 방어 수준 | [미정] | `SameSite=Lax` 1차 대응, 부족 시 후속 스펙에서 CSRF 토큰 검토 |
| GitHub 이슈·브랜치 | 미생성 | 이 저장소 규칙상 새 이슈/브랜치 생성은 사용자 확인 후 진행 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-17 | 초안 — 사용자 요청(refreshToken Redis 이관 + 블랙리스트 폐기 + HttpOnly 쿠키 전환) 반영, TTL 900초·Redis SPOF 리스크 감수·login도 쿠키 통일 확정 |
| 2026-08-17 (같은 날, 후속) | "프론트엔드 권장 사항" 절 추가 — accessToken localStorage 비영속화 + 앱 부팅 시 silent refresh(사용자 요청, 백엔드 구현 범위 밖이라 Must Have 아닌 전달용 절로 분리) |
| 2026-08-19 | **Implemented** — Redis 4-키 설계(active/family/revoked/user 인덱스) 구현, 블랙리스트·jti 클레임 전체 삭제, 쿠키 전환, TTL 900초 반영. `docs/decisions/004`·`erd.md`·`auth-token-rotation.md` 동기화. `./gradlew test` 491개 전체 통과. PR #121 merge·배포 완료(같은 날) — `TripFit-client` 쪽 실제 클라이언트 반영은 프론트팀 별도 작업 |
