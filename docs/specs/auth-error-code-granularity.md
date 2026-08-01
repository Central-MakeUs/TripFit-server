# AuthErrorCode 세분화 — 소셜 로그인 토큰 검증 실패 분리

> 상태: Implemented (2026-07-27 · #57)
> wave: 무관 (wave 1 `auth-social-login.md` 리팩터)
> amend: [`auth-social-login.md`](auth-social-login.md) 공통 에러 응답 표
> MVP: In scope (관측성·프론트 UX 분기 개선)
> 관련 BR: N/A
> 선행: [`auth-social-login.md`](auth-social-login.md) (Approved/Implemented)

## 목표

소셜 로그인(`POST /api/v1/auth/login`) 토큰 검증 실패가 전부 `AUTH_INVALID_TOKEN` 401 하나로 뭉쳐 있어, "재로그인하면 해결되는지"(만료) vs "재로그인해도 소용없는지"(설정 오류·provider 장애)를 프론트도 서버 로그도 구분할 수 없다. 실패 원인별로 코드를 나눈다.

## 배경

- 2026-07-27 카카오 로그인 401 문의 조사 중, 원인이 "카카오가 토큰 만료로 응답"임을 확인하는 데 서버 로그 판독 + 직접 curl 재현이 모두 필요했음 — 코드만으로 구분 불가능했음.
- 같은 조사에서 Google/Apple verifier의 `catch(Exception)` 블록에 원인 로깅 자체가 없었음을 발견해 우선 로깅만 추가함(완료, 본 브랜치).
- 대상 코드: `KakaoTokenVerifier`, `GoogleTokenVerifier`, `AppleTokenVerifier`, `AuthErrorCode`
- 대상 스펙: `auth-social-login.md`(Approved/Implemented) — 이 스펙의 "공통 에러 응답" 표를 amend함

## 범위 — 소셜 토큰 검증만 (액세스 JWT 검증은 범위 밖)

`AUTH_INVALID_TOKEN`은 현재 두 가지 다른 문맥에 쓰이고 있음:

1. **로그인 시 소셜 provider 토큰 검증 실패** (`auth/oauth/*TokenVerifier`) — 이번 스펙의 대상
2. **인증 필요 API에서 우리 서버가 발급한 액세스 JWT가 무효** (`JwtService`, `JwtAuthenticationFilter`, `AuthorizedUserArgumentResolver`, `TripAuthorizationInterceptor`, 컨트롤러 15곳 이상 Swagger 설명) — **건드리지 않음**. 여기까지 나누면 무관한 컨트롤러 Swagger 문구 20곳 이상을 고쳐야 해 요청 범위를 벗어남.

`AUTH_INVALID_TOKEN`은 코드 값은 유지하되, 앞으로 **"액세스 JWT(서버 발급) 무효"만**을 의미하도록 `@Schema` 설명을 좁힌다.

## 요구사항

### Must Have

- [x] `AuthErrorCode`에 소셜 토큰 검증 실패 전용 코드 3종 추가 (아래 표)
- [x] `KakaoTokenVerifier` — HTTP 에러 응답(만료/그 외) vs 네트워크 예외를 구분해 새 코드로 throw
- [x] `GoogleTokenVerifier` / `AppleTokenVerifier` — 예외 타입별로 새 코드로 throw
- [x] client id 미설정(서버 설정 누락)은 `AuthErrorCode`가 아니라 기존 `CommonErrorCode.INTERNAL_ERROR`(500)로 전환 — 토큰 문제가 아니라 서버 배포 문제이므로 (이번 브랜치의 google-calendar client_secret 배포 버그와 같은 클래스)
- [x] `AuthController` `POST /auth/login` Swagger 에러 설명·예시 갱신
- [x] `auth-social-login.md` 공통 에러 응답 표 amend (레거시 `AUTH_INVALID_TOKEN` 소셜 로그인 관련 서술 제거)
- [x] `AppleTokenVerifierTest`의 기존 2개 테스트(`verify_invalidToken_throwsAuthInvalidToken`, `verify_missingClientId_throwsAuthInvalidToken`) 기대값 갱신 + Kakao/Google/Apple 신규 분기 테스트 추가

### Nice to Have

- [ ] Kakao 응답 `msg` 문자열 매칭 대신 provider가 공식 문서화한 negative `code`별 매핑 테이블 — 카카오가 "만료"를 별도 code로 명시하지 않아 현재는 보류

### Out of Scope

- 액세스 JWT(`AUTH_INVALID_TOKEN` 2번 용법) 세분화 — 필요해지면 별도 스펙
- `AUTH_INVALID_REFRESH`(리프레시 토큰) 세분화
- 프론트 UX 분기 구현 — 이 repo는 백엔드만

## 신규/변경 에러 코드

| HTTP | code | 상황 | 판별 로직 |
|------|------|------|-----------|
| 401 | `AUTH_SOCIAL_TOKEN_EXPIRED` | 소셜 provider가 "토큰 만료"로 명시 응답 | Kakao: 응답 바디 `msg`에 `expired` 포함(대소문자 무시) · Google/Apple: nimbus `BadJWTException` 메시지가 `"Expired JWT"`(9.47 바이트코드로 확인한 리터럴) |
| 401 | `AUTH_SOCIAL_TOKEN_INVALID` | 그 외 무효 — 서명 불일치, audience 불일치, subject/`id` 없음, 파싱 실패, 카카오 만료 외 4xx 등. **기본값(catch-all)** | 나머지 `BadJOSEException`/`ParseException`/Kakao 4xx 전부 |
| 503 | `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` | 토큰 자체가 아니라 provider API 접근 실패 — 타임아웃·연결 실패·JWK 조회 실패·provider 5xx | Kakao: `RestClientException`(HTTP 응답 자체를 못 받음) · Google/Apple: JWK 조회 중 `IOException`/`JOSEException` |
| 500 (신규 코드 없음) | 기존 `CommonErrorCode.INTERNAL_ERROR` 재사용 | Google/Apple client id 서버 미설정 | 기존 설정 누락 체크 로직 그대로, throw만 `CommonErrorCode.INTERNAL_ERROR`로 교체 |

`AUTH_INVALID_TOKEN`은 값 유지, `@Schema` 설명만 "액세스 JWT(서버 발급) 무효"로 좁힘.

## API / 인터페이스

`POST /api/v1/auth/login` 에러 응답 갱신 (요청 바디·URL 불변):

```json
{"code": "AUTH_SOCIAL_TOKEN_EXPIRED", "message": "소셜 로그인 토큰이 만료되었습니다. 다시 로그인해 주세요."}
```

```json
{"code": "AUTH_SOCIAL_TOKEN_INVALID", "message": "유효하지 않은 소셜 로그인 토큰입니다."}
```

```json
{"code": "AUTH_SOCIAL_PROVIDER_UNAVAILABLE", "message": "소셜 로그인 서버에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."}
```

## 검증 시나리오

### 정상 (기존 유지 — 회귀 없음)

- [ ] Google / Kakao / Apple 정상 토큰 로그인 성공

### 엣지 · 실패

- [ ] Kakao 만료 토큰(`msg`에 `expired` 포함) → `AUTH_SOCIAL_TOKEN_EXPIRED` 401
- [ ] Kakao 무효 토큰(서명 불일치 등, `msg`에 `expired` 없음) → `AUTH_SOCIAL_TOKEN_INVALID` 401
- [ ] Kakao 응답에 `id` 필드 없음 → `AUTH_SOCIAL_TOKEN_INVALID` 401
- [ ] Kakao API 연결 실패(`RestClientException`) → `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` 503
- [ ] Google/Apple 만료 id_token(`exp` 지남, 서명은 유효) → `AUTH_SOCIAL_TOKEN_EXPIRED` 401
- [ ] Google/Apple 서명 무효·audience 불일치·형식 오류 → `AUTH_SOCIAL_TOKEN_INVALID` 401
- [ ] Google/Apple JWK 조회 실패(네트워크) → `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` 503
- [ ] Google/Apple client id 미설정 → `CommonErrorCode.INTERNAL_ERROR` 500 (기존 401 `AUTH_INVALID_TOKEN`에서 변경)

## 완료 기준

- [ ] `./gradlew test` 통과
- [ ] `AppleTokenVerifierTest` 기존 케이스 갱신 + Kakao/Google 신규 테스트 추가
- [ ] `docs/specs/auth-social-login.md` 에러 표 amend
- [ ] `AuthController` Swagger `@ApiResponse`/예시 갱신
- [ ] `/v3/api-docs`에 신규 enum 값 반영 확인

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| Kakao "만료" 판별을 응답 `msg` 문자열 포함 여부로 하는 것 | 확인 필요 | 카카오가 "만료"를 별도 negative code로 문서화하지 않아 문자열 매칭이 유일한 신호. 문구가 바뀌면 `AUTH_SOCIAL_TOKEN_INVALID`로 fallback(치명적이지 않음, EXPIRED만 덜 정확해짐) |
| Google/Apple 만료 판별을 nimbus `BadJWTException` 메시지(`"Expired JWT"`) 매칭으로 하는 것 | 확인 필요 | nimbus-jose-jwt 9.47 클래스 파일로 리터럴 확인함. 라이브러리 버전 업그레이드 시 문구가 바뀌면 fallback도 `AUTH_SOCIAL_TOKEN_INVALID` — 치명적 아님. 대안(클레임 `exp` 직접 비교)은 nimbus 기본 클레임 검증과 로직이 중복돼 채택 안 함 |
| `AUTH_SOCIAL_PROVIDER_UNAVAILABLE` HTTP status 503 vs 401 통일 | 확인 필요 | 503 제안 — "토큰 문제 아님"을 status만으로도 구분 가능. `AuthErrorCode`에 503 사례가 처음 생김 (기존은 400/401/403만) |
| 액세스 JWT(`AUTH_INVALID_TOKEN` 2번 용법)도 나중에 세분화할지 | [미정] | 이번 스펙 범위 밖 — 필요해지면 별도 스펙 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-27 | 초안 — 카카오 401 문의 후속. 사용자 요청으로 브랜치/이슈 분리 없이 현재 브랜치(`fix/56-google-calendar-secret-deploy-env`)에서 작성 |
