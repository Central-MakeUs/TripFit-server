# Dev 소셜 인증 스텁 검증기 (auth/login 계약 유지형 테스트 로그인)

> 상태: Draft (#52)
> MVP: N/A — 제품 기능이 아닌 개발/테스트 인프라 (Wave 분류 대상 아님, wave 4 배치는 우선순위 편의상)
> 관련 BR: N/A
> deferred from: [`dev-mock-login.md`](dev-mock-login.md)

## 목표

`/api/v1/auth/login` 계약을 바꾸지 않고, `local`/`dev` 프로필에서 실제 Google/Kakao/Apple 호출 없이 로그인 가능하게 해서 **자동화된 API/e2e 테스트**에서도 그대로 재사용할 수 있게 한다.

## 배경

- 현재는 [`dev-mock-login.md`](dev-mock-login.md)(`POST /api/v1/auth/dev-login`, `DevAuthController`/`DevAuthService`)로 별도 엔드포인트를 두는 방식으로 급하게 대응함 — 사람이 Swagger에서 수동으로 토큰 받기엔 충분하지만, 실제 로그인 계약(`/auth/login`)과 분리돼 있어 자동화 테스트 시나리오(예: e2e가 `/auth/login`을 그대로 호출)에는 재사용이 안 됨
- 대안: `SocialTokenVerifierRegistry`에 `local`/`dev` 전용 `StubTokenVerifier`를 추가 등록해, 정해진 토큰 문자열(예: `dev:chaeyeon`)이 오면 실제 외부 API 호출 없이 고정 프로필을 반환하게 한다 — 클라이언트는 항상 같은 `/auth/login` 계약만 알면 됨
- 이 방식이 자리잡으면 `dev-mock-login.md`의 별도 엔드포인트(`DevAuthController`/`DevAuthService`/`DevLoginRequest`)는 **레거시가 되어 같은 PR에서 삭제** 대상 (`harness-workflow.md` STOP §4)

## 요구사항

### Must Have

- [ ] `local`/`dev` 프로필에서만 활성화되는 `StubTokenVerifier` (`SocialTokenVerifier` 구현체) 추가
- [ ] 정해진 규칙의 토큰 문자열(예: `dev:{testUserId}`)을 실제 외부 API 호출 없이 파싱해 `OAuthProfile` 반환
- [ ] 팀원 3인(채연·소은·기연) 식별자를 그대로 승계 — `dev-mock-login.md`와 동일 닉네임 매핑
- [ ] `prod`에서는 해당 verifier 빈이 생성되지 않음 — real verifier만 등록되어 위조 토큰으로 우회 불가
- [ ] 전환 완료 시 `DevAuthController`/`DevAuthService`/`DevLoginRequest`/`SecurityConfig`의 `dev-login` permitAll 삭제

### Out of Scope (이번 스펙에서 하지 않음)

- 실제 소셜 로그인 흐름 변경
- CI e2e 테스트 스위트 자체 구축 (이 스펙은 verifier만 제공, 테스트 작성은 별도)

## API / 인터페이스

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/login` | 불필요 (`security = {}`, 기존과 동일) | `local`/`dev`에서 `provider`/`token` 규칙만 맞으면 실제 외부 호출 없이 로그인 |

기존 `LoginRequest`/`LoginResponse` 계약 변경 없음 — 신규 엔드포인트 없음.

## 데이터 모델

- 스키마 변경 없음. `dev-mock-login.md`와 동일하게 `users` 테이블에 고정 테스트 계정을 lazy upsert

## 검증 시나리오

### 정상

- [ ] `local`/`dev`에서 `POST /auth/login`에 스텁 규칙 토큰을 보내면 실제 외부 API 호출 없이 200 + 토큰 발급
- [ ] `prod`에서는 동일 요청이 실제 verifier로 라우팅되어 `AUTH_INVALID_TOKEN`

### 엣지 · 실패

- [ ] 스텁 규칙에 맞지 않는 임의 문자열은 기존과 동일하게 `AUTH_INVALID_TOKEN`

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `dev-mock-login.md` 경로(`DevAuthController` 등) 삭제 완료
- [ ] `docs/specs/dev-mock-login.md` 상태를 Superseded로 갱신

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 토큰 문자열 규칙(`dev:{id}` 등) | [미정] | 구현 착수 시 확정 |
| 기존 `dev-login` 엔드포인트 제거 시점 | [미정] | 이 스펙 구현 완료 직후 같은 PR에서 삭제 권장 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-24 | 초안 — `dev-mock-login.md` 후속으로 분리, wave 4 배치 |
