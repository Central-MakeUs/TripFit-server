# Dev 전용 Mock 로그인

> 상태: Approved (사용자 요청 즉시 승인 — 이슈 미생성, 긴급 처리)
> MVP: N/A — 제품 기능이 아닌 개발 편의 도구 (Wave 분류 대상 아님)
> 관련 BR: N/A
> deferred: [`auth-dev-stub-verifier.md`](auth-dev-stub-verifier.md) (#52, wave 4) — `/auth/login` 계약을 유지하는 스텁 검증기로 전환 예정. 전환 완료 시 이 스펙의 `DevAuthController`/`DevAuthService`/`DevLoginRequest`는 같은 PR에서 삭제

## 목표

프론트엔드가 실제 소셜 로그인(Google/Kakao/Apple) 없이 Swagger에서 바로 access/refresh 토큰을 받아 API 연동 테스트를 할 수 있게 한다.

## 배경

- 현재 `/api/v1/auth/login`은 실제 소셜 토큰 검증(`SocialTokenVerifierRegistry`)을 거쳐야만 토큰이 발급됨 — 프론트 테스트 시마다 실제 소셜 로그인 필요
- 프론트 요청: "테스트 계정 받아서 토큰 스웨거에 넣고 테스트하고 싶다"
- EC2 배포는 현재 `dev` 프로필만 존재(`deploy/README.md` "EC2 A — dev만") — springdoc이 `prod`에서만 비활성화되므로 `local`/`dev`에서는 Swagger UI 접근 가능

## 요구사항

### Must Have

- [x] 소셜 토큰 없이 테스트 계정으로 access/refresh 토큰을 발급하는 API
- [x] `local`·`dev` 프로필에서만 빈이 생성되어 **prod에는 엔드포인트 자체가 존재하지 않음** (`@Profile`)
- [x] 기존 `AuthController`/`AuthService`(prod 경로)는 변경하지 않음
- [x] 팀원별로 서로 다른 테스트 계정을 쓸 수 있어야 함 — `testUserId`로 구분, 팀원 3인(채연·소은·기연) 고정 계정 제공

### Out of Scope (이번 스펙에서 하지 않음)

- 테스트 계정 관리 UI·계정 목록 조회 API
- prod 환경 노출 (금지 — `@Profile`로 원천 차단, 실제 상용 전환 시 자동으로 사라짐)
- 소셜 로그인 흐름 자체 변경
- shared secret 등 추가 인증 계층 (요청으로 skip — 실제 상용 전환 시 이 API 자체를 삭제할 예정이므로 불필요 판단)

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/dev-login` | 불필요 (`security = {}`) | `local`/`dev` 전용. `testUserId`별 테스트 계정으로 access/refresh 발급 |

요청 (body 생략 가능 — 생략 시 `chaeyeon` 계정):

```json
{
  "testUserId": "soeun"
}
```

고정 3계정: `chaeyeon`(채연) · `soeun`(소은) · `giyeon`(기연). 그 외 값도 여전히 허용되며 해당 값 전용 계정이 새로 생성됨(닉네임은 `테스트유저-{값}`).

응답은 기존 `LoginResponse`와 동일한 envelope 재사용.

```json
{
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "550e8400-...",
    "expiresIn": 7200,
    "user": { "...": "UserSummaryResponse" }
  }
}
```

## 데이터 모델

- 신규 테이블 없음. 기존 `users` 테이블에 `provider=KAKAO`, `socialId=dev-test-user-{testUserId}`인 row를 식별자별로 **최초 호출 시** upsert (예: `dev-test-user-chaeyeon`, `dev-test-user-soeun`, `dev-test-user-giyeon`) — 배포만으로는 row가 생기지 않고, 실제로 해당 `testUserId`로 API를 한 번 호출해야 생성됨
- `email`=`dev-test-{testUserId}@tripfit.online`
- `nickname`: `chaeyeon`→채연, `soeun`→소은, `giyeon`→기연 고정 매핑. 그 외 값은 `테스트유저-{testUserId}` fallback
- `firstName`/`lastName`: `chaeyeon`→손/채연, `soeun`→김/소은, `giyeon`→방/기연으로 **계정 생성 시 프리필** — 이름 미입력 시 trip 생성·참여가 `PROFILE_NAME_REQUIRED`(403)로 막히는 걸 피하기 위함(`user-onboarding.md` BR). 그 외 임의 `testUserId`는 이름 없음(프론트가 필요 시 PATCH profile)
- `testUserId`는 `^[a-zA-Z0-9_-]{0,20}$`만 허용 (한글 불가 — 영문 로마자 표기 사용, 임의 문자열로 지저분한 row 생성 방지)

## 보안

- `@Profile({"local", "dev"})`을 컨트롤러·서비스 양쪽에 적용 — prod 프로필에서는 스프링 빈 자체가 생성되지 않아 라우트가 존재하지 않음(404)
- `SecurityConfig`에 `POST /api/v1/auth/dev-login` permitAll 추가 (login/refresh/logout과 동일 패턴)

## 검증 시나리오

### 정상

- [x] `local`/`dev` 프로필에서 최초 호출 시 테스트 유저 생성 + 토큰 발급
- [x] 재호출 시 기존 테스트 유저 재사용 + 새 토큰 발급
- [x] `testUserId`를 다르게 주면 서로 다른 계정이 생성됨 (chaeyeon ≠ soeun ≠ giyeon)
- [x] body 생략 시 `chaeyeon` 계정 사용 (하위 호환)
- [x] `chaeyeon`/`soeun`/`giyeon`은 닉네임이 실명(채연/소은/기연)으로 표시됨
- [x] `chaeyeon`/`soeun`/`giyeon`은 성·이름이 프리필돼 있어 `POST /trips`(생성)·`POST /trips/join`(참여)이 바로 통과됨

### 엣지 · 실패

- [x] 테스트 계정이 탈퇴(soft-delete) 상태면 기존 `/auth/login`과 동일하게 `AUTH_WITHDRAWN_ACCOUNT`
- [x] `testUserId`가 허용 패턴(`^[a-zA-Z0-9_-]{0,20}$`)을 벗어나면 400

## 완료 기준

- [x] `./gradlew test` 통과
- [x] Swagger에 `local`/`dev`에서만 노출, `prod`(application-prod.yml) 조건은 기존과 동일(springdoc 비활성화 + 라우트 부재 이중 방어)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | 초안·승인 — 프론트 테스트 계정 요청 대응, 긴급 처리로 이슈 미생성 |
| 2026-07-24 | `testUserId` 추가 — 팀원별(프론트 2명+백엔드 1명) 별도 계정 지원. shared secret은 요청으로 Out of Scope 확정 |
| 2026-07-24 | 팀원 3인 실명 기준 고정 계정으로 확정 — `chaeyeon`/`soeun`/`giyeon`, 기본값 `chaeyeon` |
| 2026-07-24 | 팀원 3인 성·이름 프리필 추가(손채연·김소은·방기연) — trip 생성·참여가 이름 미입력으로 막히지 않도록. 참여(join) 쪽 `PROFILE_NAME_REQUIRED` 가드 누락도 같은 턴에 수정(`TripCommandService.joinTrip`) |
