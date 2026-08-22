# 회원 탈퇴

> 상태: Implemented (기존 cascade·soft delete 범위) + `#64` Provider Revoke는 Kakao/Apple Implemented(코드) — Google 로그인 자체 revoke는 [`google-login-revoke.md`](google-login-revoke.md)로 구현(Draft), 캘린더 revoke는 기존 Implemented. Apple·Google 모두 프론트 공지·실계정 수동 검증 별도 진행
> MVP: In scope
> 관련 BR: BR-USER-004
> wave: 2 (Nice) · `#64`(Provider Revoke)는 Release Gate — Wave와 무관, 앱스토어 심사 필수(`harness-wave.md` 🚨)
> implements: BR-USER-004 `[미정]` 해소 — "진행 중 방" 처리 정책 확정
> deferred: (해당 없음)
> GitHub: 정책 근거 `#47`(hotfix, 확정) · 구현도 `#47` 브랜치(`docs/47-trip-status-policy-alignment`)에서 완료(별도 구현 이슈 없이 진행) · `#64`(소셜 provider revoke, Release Gate — 이번 amend로 Must Have 편입)
> 선행: [`trip-member-leave.md`](trip-member-leave.md) · [`user-my-page.md`](user-my-page.md) · `trip-room-api.md`(여행방 삭제) · `#64` Apple 부분은 Apple Developer Console `.p8`·Team ID·Key ID 발급 + CI/CD·docker-compose·application.yml 배선까지 완료(2026-07-31) — `authorizationCode` 누락 시 로그인 자체를 400으로 막도록(B안) 강제해뒀으므로, **프론트가 이 필드를 보내기 시작한 뒤에 배포**해야 함(순서 바뀌면 배포 즉시 Apple 로그인 전체 실패)

## 목표

사용자가 본인 계정을 탈퇴할 수 있게 한다. 참여 중인 방은 **차단 없이 자동으로 정리(cascade)** 한 뒤 탈퇴를 진행한다. 개인 전용 데이터(일정·구글 캘린더 연동·리프레시 토큰)는 즉시 제거하고, 다른 사용자의 여행방 이력을 보존하기 위해 `User` row 자체는 soft delete + 개인정보 스크럽 방식으로 처리한다.

## 배경

- Figma: [`figma-wireframe-v1.md`](../product/design/figma-wireframe-v1.md) — 마이페이지(설정·탈퇴·캘린더 연동)
- `docs/product/business-rules/user.md` BR-USER-004: "확인 후 탈퇴 / `[미정]` 진행 중 방" — 본 스펙으로 확정
- `user-my-page.md`: 탈퇴 API를 명시적으로 Out of Scope로 남겨둠 — 본 스펙이 후속

### 설계 결정 배경

**1차 결정(2026-07-23, 폐기됨)**: `ONGOING`인 방이 있으면 탈퇴 자체를 차단하고, 사용자가 먼저 방을 삭제·나가기를 선행하도록 요구하는 정책이었음.

**정책 전면 수정(2026-07-24, `#47` hotfix, 기획자 확인 완료)**: 방 나가기·참여자 내보내기(`#20`)·방 삭제·회원 탈퇴 네 액션의 상태별 허용 조건을 정합성 있게 재정리하면서, 탈퇴 정책도 **차단 → 자동 cascade**로 뒤집힘.

1. **진행 중 방 처리(확정)**: 탈퇴를 차단하지 않는다.
   - **참여자(MEMBER)**: 참여 중인 모든 방(상태 무관, `ONGOING` 포함)에서 자동으로 나가기 처리([`trip-member-leave.md`](trip-member-leave.md) 로직 내부 재사용) 후 탈퇴 진행.
   - **방장(OWNER)**: 소유한 모든 방(상태 무관, `ONGOING` 포함)을 자동으로 삭제(`deleteTrip()` 로직 재사용, soft delete) 후 탈퇴 진행. 기획자 근거: "방장이 탈퇴를 누르는 건 여행이 무산됐거나 더 이상 방을 유지할 필요가 없는 상황으로 볼 수 있다 — 탈퇴 전에 방 삭제를 유도하면 번거로운 절차만 추가된다."
   - `CANCELED` 상태는 `#48`에서 **enum 자체가 삭제 완료** — 더 이상 별도로 고려할 상태가 아님.
2. **데이터 처리(확정, 변경 없음)**: 최초 "Hard delete"로 검토했으나, `Trip.owner_id`/`TripMember.user_id`가 `nullable=false` FK이고 `deleteTrip()`이 soft delete만 하므로(row 존속), User row를 진짜 hard delete하면 그 사람이 방장이었던 Trip(다른 참여자 포함)까지 연쇄 삭제해야 하는 충돌이 발견됨. 사용자 결정: **User도 다른 엔티티와 동일하게 Soft Delete 사용** — Trip·TripMember는 FK 그대로 두어 다른 참여자의 이력을 보존하고, 개인 전용 테이블(일정·구글 캘린더·리프레시 토큰)만 실제로 hard delete.
3. **방장 소유 방 자동 삭제의 부수 효과(리스크로 인지, 수용 확정)**: `deleteTrip()`은 방 자체와 그 방의 모든 `TripMember`를 soft delete하므로, 방장이 탈퇴하면 그 방은 **방장뿐 아니라 다른 멤버 전원에게도** 더 이상 조회되지 않는다. "방장이 탈퇴하면 여행이 무산된 것"이라는 전제를 받아들인 결과이며, `src/new_decision.md`가 `CANCELED`(별도 "취소됨" 표시로 이력을 남기는 안) 자체를 없애기로 확정했으므로 이 부수효과도 그대로 수용하는 것으로 확정됨.

## 요구사항

### Must Have

- [x] `DELETE /api/v1/users/me` — JWT 필수
- [x] 차단 없이 항상 진행. 호출자가 활성(`deleted_at IS NULL`) `TripMember` row로 역할이 `MEMBER`인 것이 있으면(상태 무관) 전부 [`trip-member-leave.md`](trip-member-leave.md) 로직으로 자동 나가기 처리
- [x] 호출자가 활성 `TripMember` row로 역할이 `OWNER`인 것이 있으면(상태 무관) 소유한 해당 Trip을 전부 `deleteTrip()` 로직으로 자동 삭제 처리
- [x] 위 cascade 완료 후 탈퇴 진행:
  - [x] 개인 전용 데이터 **hard delete**: `PersonalSchedule`, `RegularSchedule`, `GoogleCalendarCredential`, `GoogleCalendarBusyDay`, `RefreshToken` (전부 `userId` 기준)
  - [x] `User` row **soft delete**(`deleted_at` set) + 개인정보 스크럽: `email`·`firstName`·`lastName`·`nickname`·`profileImageUrl` → `null`, `isGoogleCalendarConnected` → `false`
  - [x] `socialId`·`provider`·`id`는 그대로 유지 — FK 무결성(다른 사용자의 Trip/TripMember 참조) 및 재로그인 차단 판별에 필요
- [x] `AuthService` 로그인 흐름: `findByProviderAndSocialId`로 찾은 `User`가 이미 soft-deleted면 그대로 **부활**시켜 로그인 진행 — `deletedAt=null`, `isAllFree=false`로 초기화 후 `updateFromProfile`로 email·nickname·profileImageUrl을 소셜 프로필값으로 갱신. `firstName`/`lastName`/`isGoogleCalendarConnected`는 탈퇴 시 초기화된 채로 유지되어 재로그인 후 온보딩·재연동이 필요함(신규 가입과 동일한 경험)
- [x] `DevAuthService`(dev 전용 테스트 로그인)도 동일하게 부활 처리 — 프로덕션 로그인과 동작 일치
- [x] 성공 시 `204 No Content`
- [x] `./gradlew test` 통과, OpenAPI 반영

### Must Have — `#64` 소셜 provider revoke (2026-07-31 amend, Kakao/Apple Implemented · Google 로그인 revoke는 [`google-login-revoke.md`](google-login-revoke.md) 참고)

탈퇴 시 TripFit 내부 데이터 삭제와 별개로, 로그인에 쓴 소셜 provider 쪽에도 revoke·unlink를 호출한다. Apple은 App Store Review Guideline 5.1.1(v) 요건이라 Release Gate였음(`#64`, Closed — 완료). `#6`(계정 유지 상태의 다중 소셜 연결·개별 해제)과는 트리거가 다른 별개 흐름 — 혼동 금지.

Provider별로 선행 조건이 달라 **순차 완료 가능**하도록 분리한다.

**Google — 캘린더 revoke (2026-07-31 Implemented) + 로그인 자체 revoke (신규, [`google-login-revoke.md`](google-login-revoke.md))**

- [x] `UserWithdrawalService.withdraw()`에서 `GoogleCalendarCredentialRepository.deleteByUser_Id()` 호출 **전에**, credential이 존재하면 기존 `GoogleCalendarOAuthClient.revokeRefreshToken()`을 먼저 호출(best-effort, `GoogleCalendarService.disconnect()`와 동일 패턴 재사용 — 신규 클라이언트 코드 없음)
- **🔴 재발견 (2026-07-31) — 위만으로는 불충분**: 위 항목은 Google Calendar를 **연동한 유저에게만** 적용된다. 그런데 Calendar 연동은 아직 FE에 구현된 적이 없어(목업, Wave 4 미착수), 실제로는 **Google로 로그인한 유저 전원**이 탈퇴해도 Google 쪽에 revoke가 전혀 호출되지 않는 gap이었다. 원인: 로그인이 `id_token` 검증만 하고 Google 서버로 토큰 교환을 하지 않아, 서버가 애초에 revoke할 토큰을 가져본 적이 없음. `#64` 재오픈, 상세 Before/After는 이슈 코멘트 참고
- [x] **해결 (Implemented)** — Apple과 동일한 패턴 적용: `LoginRequest.authorizationCode`를 GOOGLE도 소비하도록 확장(신규 `auth/oauth/GoogleOAuthClient`가 code→refresh token 교환, `auth/domain/GoogleLoginCredential`에 암호화 저장), 탈퇴 시 `GoogleLoginCredentialService.revokeAndDeleteIfPresent()`가 `GoogleCalendarOAuthClient`와 별개로 로그인 refresh token을 revoke. 상세 설계·리스크: [`google-login-revoke.md`](google-login-revoke.md)

**Kakao — 신규 Admin Key 기반 unlink (2026-07-31 Implemented)**

- [x] 신규 env `KAKAO_ADMIN_KEY` — `.env.example` + `.github/workflows/ci-cd.yml`(secrets 참조·`envs:` 목록·deploy 스크립트 export) + `deploy/app/docker-compose.yml`(environment 매핑) + `application.yml`/`OAuthProperties.java`(바인딩 필드 추가) 4곳 동시 배선
- [x] Kakao unlink 클라이언트 신규 구현 — `POST https://kapi.kakao.com/v1/user/unlink`, Admin Key 인증(`target_id_type=user_id` + 저장된 소셜 `socialId`). 사용자 access_token 저장 불필요 (`user/client/KakaoUnlinkClient.java`)
- [x] `UserWithdrawalService.withdraw()`에서 `user.getProvider() == KAKAO`일 때만 호출(best-effort)

**Apple — 신규 인프라(가장 큼, 2026-07-31 Implemented)**

- [x] `LoginRequest.authorizationCode` 필드는 **이미 존재**(`cb5a23f`, optional·nullable, APPLE 전용) — `AuthService.login(provider, token, authorizationCode)`가 세 번째 파라미터로 받아 실제 소비하도록 변경(`AuthController`도 동일하게 전달). 신규 필드 추가 아님, 기존 필드 배선
- [x] `AuthService.login()`에서 `provider == APPLE`이면 `AppleCredentialService.saveIfAuthorizationCodePresent(user, authorizationCode, clientId)` 호출 — 내부에서 Apple token 엔드포인트(`https://appleid.apple.com/auth/token`)로 `authorizationCode` → refresh token 교환(`AppleOAuthClient`)
- [x] `.p8`+`APPLE_TEAM_ID`+`APPLE_KEY_ID`로 서명한 client_secret JWT 생성(`AppleOAuthClient.buildClientSecretJwt()`, ES256, nimbus-jose-jwt, 호출마다 새로 발급·캐싱 없음)
- [x] 신규 엔티티 `AppleCredential`(`auth/domain/`) — `GoogleCalendarCredential`과 동일 패턴이되 **탈퇴 시 1회 revoke만 필요**해 access token 캐시·동기화 필드는 제외한 최소 구조. refresh token은 `SocialTokenCrypto` 재사용해 AES-256-GCM 암호화 저장(신규 AES 키 없음)
- [x] `UserWithdrawalService.withdraw()`에서 `AppleCredentialService.revokeAndDeleteIfPresent(userId)` 호출 — credential 존재 시 저장된 refresh token으로 `https://appleid.apple.com/auth/revoke` 호출(best-effort) 후 credential row 삭제(존재 여부 무관 항상 delete 시도)
- [x] 신규 env `APPLE_TEAM_ID`/`APPLE_KEY_ID`/`APPLE_PRIVATE_KEY` — `.env.example`(루트·`deploy/app/`) + `.github/workflows/ci-cd.yml`(secrets 참조·`envs:` 목록·export) + `deploy/app/docker-compose.yml`(environment 매핑) + `application.yml`/`OAuthProperties.java`(바인딩) 전부 배선 완료
- [x] `AuthService.login()`에서 `provider == APPLE`인데 `authorizationCode`가 없으면 소셜 토큰 검증 전 즉시 `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED`(400)로 거부(B안, 2026-07-31 사용자 결정 — 아래 "세부 정책" 절 참고). 조건부 필수화라 `Breaking-Change-Reason` 트레일러 **필요** — **프론트 배포와 조율 후 배포할 것**, 안 그러면 배포 즉시 Apple 로그인 전체 실패
- [x] **(2026-07-31 amend — `#64` 재오픈)** `APPLE_CLIENT_ID` 단일값 → `APPLE_BUNDLE_ID`(iOS 네이티브)/`APPLE_SERVICE_ID`(모바일 브라우저) 이원화. `AppleCredential`에 `apple_client_id` 컬럼 추가해 로그인 시 매칭된 client_id를 저장하고, 탈퇴 revoke 호출에 그대로 재사용 — 상세: [`apple-oauth-multi-audience.md`](apple-oauth-multi-audience.md)

**Apple 구현 시 확정한 세부 정책 (2026-07-31, 사용자 위임 — "앱스토어 심사 통과에 가장 유리한 방향"으로 결정)**

- **credential 컬럼 범위:** `GoogleCalendarCredential`의 access token 캐시·`last_synced_at`·`last_sync_error` 필드는 Apple에 해당 사항이 없어(주기적 동기화 없음, 탈퇴 시 1회 revoke만) 제외 — `user_id`·`refresh_token_ciphertext`·`apple_client_id`(2026-07-31 추가)·`BaseTimeEntity`만 있는 최소 구조
- **token exchange 실패 시 로그인 처리:** `authorizationCode`는 있는데 Apple 서버 token 교환 자체가 실패(네트워크 오류·invalid code 등)해도 **로그인은 그대로 성공**시키고 credential 저장만 스킵(`AppleCredentialService`가 try/catch로 흡수) — Apple 인프라 장애가 TripFit 로그인 성공률에 영향을 주지 않도록 best-effort 가드레일과 동일한 원칙 적용
- **재로그인 시 refresh token 갱신:** 재로그인마다 새로 오는 `authorizationCode`로 **매번 최신 값으로 덮어씀**(스킵하지 않음) — 사용자가 Apple ID 설정에서 수동으로 TripFit 연결을 해제한 뒤에도 저장된 refresh token이 stale하게 남아 탈퇴 시 revoke가 조용히 실패하는 상황을 최소화(심사 시 "삭제 시 실제로 revoke가 되는지"가 핵심이므로 최신성을 우선)
- **authorizationCode 누락 시 처리(B안, 2026-07-31 amend):** 최초엔 "없으면 조용히 skip"(best-effort)으로 구현했다가, 프론트 미배포·회귀를 아무도 못 알아챌 위험 때문에 **로그인 자체를 400으로 거부**하도록 강제 전환. 근거: 심사 통과 그 순간만이 아니라 **서비스 운영 내내** 지켜야 하는 요건이라, 로그 모니터링에 의존하기보다 프론트가 안 보내는 순간 즉시(자체 QA에서부터) 드러나는 쪽이 소규모 팀에 더 안전하다고 판단. **트레이드오프:** 프론트 배포 전에 이 변경이 먼저 나가면 Apple 로그인 전체가 막힘 — 배포 순서 조율 필수

### 구현 가드레일 (프로덕션 인시던트 재발 방지 — `#64` 전용)

`e500e1ece`(GET /trips 401 오인 마스킹 수정) 인시던트 원인: ① `Collection<UUID>` 네이티브 쿼리 IN절 바인딩이 Hibernate 기본값(BINARY)으로 떨어져 MySQL 타입 불일치 예외 발생 → ② 그 예외가 `GlobalExceptionHandler`에 안 잡히고 Tomcat `/error`로 forward됐는데 당시 `/error`가 `permitAll`이 아니어서 SecurityContext 없이 재평가되며 401로 둔갑(마스킹). 현재는 catch-all `@ExceptionHandler(Exception.class)` + `/error` permitAll로 전역 패치됨. `#64` 구현에서 같은 클래스의 실수를 반복하지 않도록:

- **외부 provider 호출은 반드시 서비스 레벨 try/catch로 best-effort 처리** — Kakao unlink·Apple token 교환/revoke 실패가 탈퇴 트랜잭션 자체를 막으면 안 됨(로컬 데이터 삭제·soft delete는 provider 상태와 무관하게 항상 성공해야 함). `GoogleCalendarOAuthClient.revokeRefreshToken()`의 `catch (Exception ignored)` 패턴을 그대로 따를 것
- **신규 네이티브 SQL 쿼리 추가 금지** — Apple credential 조회·삭제는 `GoogleCalendarCredentialRepository.deleteByUser_Id()`처럼 derived method/JPQL만 사용. `Collection<UUID>` IN절 네이티브 쿼리를 새로 만들지 않는다
- **Apple refresh token 평문 저장 금지** — `GoogleCalendarCredential`과 동일한 AES-256-GCM 암호화 패턴(`SocialTokenCrypto`) 재사용. `RefreshToken.token`이 현재 평문 저장 중인 것은 별도 열려있는 follow-up이며, `#64`에서 같은 실수를 새로 추가하지 않는다

### Out of Scope (이번 스펙)

- 액세스 토큰(JWT) 즉시 무효화 — RTR/블랙리스트 인프라 없음(Wave 4 `#4`), 자연 만료까지는 유효할 수 있음. 리프레시 토큰은 hard delete로 즉시 무효화됨
- 소셜 계정 다중 연결·개별 해제(계정은 유지) — `#6`. `#64`(탈퇴 트리거 revoke)와는 별개 흐름
- Apple 서버가 보내는 S2S webhook 수신 — `#5`(반대 방향)
- 탈퇴 확인 모달·UX — FE 책임 (다만 방장의 경우 소유한 모든 방이 삭제된다는 경고 문구가 필요할 수 있음 — FE 확인 필요)

## API

### `DELETE /api/v1/users/me`

| 항목 | 값 |
|------|-----|
| Auth | Bearer JWT **필수** |
| 용도 | 회원 탈퇴 |

성공:

```json
{
  "data": null
}
```

### 에러

해당 없음 — soft-deleted 계정으로 재로그인해도 차단하지 않고 그대로 부활(재가입) 처리한다.

> 이전 초안에 있던 `USER_HAS_OWNED_TRIPS`/`USER_HAS_JOINED_TRIPS`(409 차단 에러)는 **폐기** — 차단 대신 자동 cascade로 정책이 바뀌어 더 이상 발생하지 않음. `AUTH_WITHDRAWN_ACCOUNT`(401)도 같은 이유로 **폐기**(2026-07-27, 재가입 정책 확정) — `AuthErrorCode`·`AuthService`·`DevAuthService`에서 제거.

### `POST /api/v1/auth/login` (기존 API — `#64` 관련 변경만 기술)

| 항목 | 값 |
|------|-----|
| Auth | 불필요 |
| 용도 | 소셜 로그인. `#64`는 이 API의 **기존** `authorizationCode` 필드(원래 APPLE 전용, optional)를 실제로 소비하게 만들고, **provider가 APPLE 또는 GOOGLE이면 이 필드를 필수로 강제**한다(GOOGLE 확장은 [`google-login-revoke.md`](google-login-revoke.md)) |

`LoginRequest.authorizationCode`는 신규 필드가 아니라 `cb5a23f`에서 이미 계약에 추가된 필드다. 지금까지는 `AuthService.login(provider, token)`이 이 값을 그대로 버려서 아무 동작도 하지 않았다 — `#64` Apple Must Have 구현이 이 필드를 처음으로 실제 소비하는 지점이다.

**2026-07-31 amend(B안, 사용자 결정)**: 처음엔 "프론트가 안 보내도 조용히 skip"(best-effort)으로 구현했으나, 이 경우 프론트가 배포를 안 했거나 회귀가 생겨도 로그인·탈퇴가 계속 "성공"으로 보여서 Apple revoke가 조용히 멈추는 걸 아무도 못 알아차리는 리스크가 있었다. App Store Guideline 5.1.1(v)는 "제출 시점"만이 아니라 "앱이 서비스되는 내내" 지켜야 하는 요건이라, 소규모 팀 운영 기준으로는 **자동으로 걸리는 안전장치가 로그·수동 체크리스트보다 안전**하다고 판단해 강제로 전환:

- provider가 APPLE인데 `authorizationCode`가 없거나 공백이면 `AuthService.login()`이 소셜 토큰 검증 전 즉시 `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED`(400)를 던진다 — 로그인 자체가 실패
- 기존 optional 필드를 조건부 필수로 바꾸는 계약 변경이라 이 커밋에 `Breaking-Change-Reason` 트레일러 필요(STOP §5) — **프론트가 이미 authorizationCode를 보내고 있지 않다면 이 커밋 배포와 동시에 Apple 로그인이 전부 실패한다.** 반드시 프론트 배포와 조율 후 배포할 것
- Kakao는 영향 없음(이 필드 자체를 안 씀)

**GOOGLE amend ([`google-login-revoke.md`](google-login-revoke.md), 2026-07-31)**: 처음부터 강제(Apple의 "best-effort → 나중에 강제" 2단계를 반복하지 않기로 결정) — provider가 GOOGLE인데 `authorizationCode`가 없거나 공백이면 즉시 `AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED`(400)를 던진다. 마찬가지로 FE가 Google OAuth 요청을 hybrid flow(`response_type=code id_token`)로 전환해 이 필드를 보내기 시작한 뒤에 배포해야 함 — 순서가 바뀌면 배포 즉시 Google 로그인 전체 실패.

| HTTP | code | 상황 |
|------|------|------|
| 400 | `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED` | provider가 APPLE인데 `authorizationCode` 누락·공백 |
| 400 | `AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED` | provider가 GOOGLE인데 `authorizationCode` 누락·공백 |

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` — 기존 cascade·soft delete 범위는 스키마 컬럼 변경 없음. `#64` Apple 부분은 **신규 테이블 1개**(`apple_credential`) 추가 완료, Google 로그인 부분은 **신규 테이블 1개**(`google_login_credential`, [`google-login-revoke.md`](google-login-revoke.md)) 추가(아래)
- 탈퇴 API(`DELETE /api/v1/users/me`) 자체엔 신규 에러 코드 없음(`AUTH_WITHDRAWN_ACCOUNT`는 재가입 정책 확정으로 폐기, provider revoke는 전부 best-effort). 단 로그인 API(`POST /api/v1/auth/login`)에는 `AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED`/`AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED`(400) 신규 추가 — 위 API 절 참고
- hard delete 대상 테이블: `personal_schedule`, `regular_schedule`, `google_calendar_credential`, `google_calendar_busy_day`, `refresh_token`, `apple_credential`, `google_login_credential`(모두 `user_id` 단독 소유, 타 사용자 참조 없음)
- soft delete + 스크럽 대상: `users` (row 유지, PII 컬럼만 null)
- cascade 대상: 호출자가 MEMBER인 `trip_member` row(soft delete, [`trip-member-leave.md`](trip-member-leave.md) 재사용) · 호출자가 OWNER인 `trip` row(soft delete, `deleteTrip()` 재사용 — 해당 방의 다른 멤버 `trip_member` row도 함께 soft delete됨)
- **`apple_credential` (신규 테이블, 확정)**: `user_id`(FK, UNIQUE, user당 1행) · `refresh_token_ciphertext`(AES-256-GCM, TEXT, 평문 저장 금지 — `SocialTokenCrypto` 재사용) · `apple_client_id`(varchar, 2026-07-31 추가 — 로그인 시 매칭된 Bundle ID/Services ID 원문, 탈퇴 revoke에 재사용) · `BaseTimeEntity`(생성·수정 시각)만 있는 최소 구조(`google_calendar_credential`의 access token 캐시·동기화 필드는 Apple에 해당 사항 없어 제외). PK는 프로젝트 컨벤션대로 UUID v4(`docs/specs/uuid-primary-key.md`). `docs/architecture/erd.md` 반영 완료
- **`google_login_credential` (신규 테이블, [`google-login-revoke.md`](google-login-revoke.md))**: `user_id`(FK, UNIQUE, user당 1행) · `refresh_token_ciphertext`(AES-256-GCM, TEXT, `SocialTokenCrypto` 재사용) · `BaseTimeEntity`만 있는 최소 구조. Apple과 달리 client_id 컬럼 없음(Google revoke 엔드포인트가 client_id를 요구하지 않아 저장할 필요 자체가 없음 — 상세 근거는 해당 스펙 설계 노트). PK는 UUID v4. `docs/architecture/erd.md` 반영 완료

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-USER-004 | 확인 후 탈퇴, 차단 없이 자동 cascade(참여 방 자동 나가기 · 소유 방 자동 삭제) | `UserWithdrawalService`(신규) |
| (BR 없음, 외부 요건) | Apple App Store Review Guideline 5.1.1(v) — Sign in with Apple 지원 + 앱 내 계정 삭제 지원 시 삭제 시점에 Apple revoke 엔드포인트 호출 필수 | `UserWithdrawalService` + `AppleCredentialService` + `AppleOAuthClient`(client_secret JWT 서명) |
| (BR 없음, `#64` 재발견 gap) | Google 로그인 자체에 대한 동의도 탈퇴 시 revoke — 캘린더 연동 여부와 무관 | `UserWithdrawalService` + `GoogleLoginCredentialService` + `GoogleOAuthClient` ([`google-login-revoke.md`](google-login-revoke.md)) |

## 검증 시나리오

### 정상

- [x] 활성 방 멤버십이 전혀 없는 사용자 → 탈퇴 성공(204), 개인 데이터 hard delete, `users.deleted_at` set, PII null
- [x] `ONGOING` 방에 MEMBER로 참여 중인 사용자 → 탈퇴 시 해당 방 자동 나가기 처리 후 탈퇴 성공
- [x] `ONGOING` 방에 OWNER인 사용자 → 탈퇴 시 해당 방 자동 삭제(soft delete) 후 탈퇴 성공. 그 방의 다른 멤버도 더 이상 해당 방을 조회할 수 없음(기존 `deleteTrip()` cascade 재사용 — 별도 신규 검증 없이 회귀 없음 확인)
- [x] `CONFIRMED`/`EXPIRED` 방에 OWNER·MEMBER로 남아 있어도 동일하게 cascade 처리 후 탈퇴 성공(상태 게이트 없는 `leaveTrip`/`deleteTrip` 재사용)
- [x] 탈퇴 후 같은 소셜 계정으로 재로그인 시도 → 기존 soft-deleted `User` row가 부활(`deletedAt=null`)하며 로그인 성공. `firstName`/`lastName`/구글 캘린더 연동은 초기화된 상태라 재온보딩 필요
- [ ] 탈퇴한 사용자가 과거 멤버였던(soft-deleted) 다른 방의 `TripMember` 이력은 그대로 남음(FK 위반 없음) — 기존 soft delete 패턴 재사용으로 구조상 보장, 별도 통합 테스트는 생략
- [x] **(`#64`)** Google Calendar 연동돼 있던 사용자가 탈퇴 → `GoogleCalendarOAuthClient.revokeRefreshToken()` 호출 후 credential hard delete(mock으로 `verify()` — 실제 호출 자체가 일어나는지 확인) — `UserWithdrawalServiceTest#withdraw_whenGoogleCalendarConnected_revokesRefreshTokenBeforeDeletingCredential`
- [x] **(`#64`)** Kakao로 로그인한 사용자가 탈퇴 → Kakao unlink 호출(mock `verify()`) 후 탈퇴 정상 완료 — `UserWithdrawalServiceTest#withdraw_whenKakaoProvider_callsKakaoUnlink`
- [x] **(`#64`)** Apple로 로그인(`authorizationCode` 포함)한 사용자가 탈퇴 → 저장된 refresh token으로 Apple revoke 호출(mock `verify()`) 후 credential hard delete — `AppleCredentialServiceTest#revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesThenDeletes`
- [x] **(`#64` 재발견, [`google-login-revoke.md`](google-login-revoke.md))** Google로 로그인(`authorizationCode` 포함, refresh_token 응답)한 사용자가 탈퇴 → 저장된 refresh token으로 Google revoke 호출(mock `verify()`) 후 credential hard delete — `GoogleLoginCredentialServiceTest#revokeAndDeleteIfPresent_whenCredentialExists_decryptsRevokesThenDeletes`, `UserWithdrawalServiceTest#withdraw_callsGoogleLoginCredentialRevokeAndDelete`
- [x] **(`#64` 재발견)** 캘린더 연동 상태에서 탈퇴한 Google 유저 → 캘린더 revoke·로그인 revoke **둘 다** 호출됨(각 credential이 독립적으로 존재·삭제)

### 엣지 · 실패

- [x] 방장으로 있는 방이 여러 개(상태 혼합) → 전부 자동 삭제 후 탈퇴 성공
- [x] 멤버로 있는 방과 방장인 방이 동시에 있음 → 멤버인 방은 나가기, 방장인 방은 삭제, 둘 다 처리 후 탈퇴 성공(각 cascade facade 메서드 호출 검증)
- [x] **(`#64`)** Google/Kakao/Apple revoke 호출이 provider 쪽 오류(4xx·네트워크 실패)로 예외를 던져도 **탈퇴 자체는 성공** — Kakao(`KakaoUnlinkClientTest`), Apple(`AppleOAuthClientTest#revokeRefreshToken_providerFailure_doesNotThrow`, `AppleCredentialServiceTest#revokeAndDeleteIfPresent_whenDecryptThrows_doesNotThrowAndStillDeletes`)는 클라이언트/서비스 레벨에서 삼킴. Google은 기존 `GoogleCalendarOAuthClient.revokeRefreshToken()`의 best-effort 패턴 재사용 + `UserWithdrawalService.revokeGoogleCalendarIfConnected()`에 복호화 실패까지 흡수하는 try/catch 보강(2026-07-31, 기존 `catch(Exception ignored)`에 로그가 없던 gap도 함께 수정)
- [x] **(`#64`, B안 강제)** Apple 로그인 시 `authorizationCode`를 안 보낸 경우(구버전 클라이언트 등) → 소셜 토큰 검증 전 즉시 `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED`(400)로 로그인 자체가 실패 — `AuthServiceTest#login_whenAppleWithoutAuthorizationCode_throwsAuthorizationCodeRequired`·`#login_whenAppleWithBlankAuthorizationCode_throwsAuthorizationCodeRequired`·`AuthControllerTest#login_appleWithoutAuthorizationCode_returns400`. `AppleCredentialService.saveIfAuthorizationCodePresent()`의 blank-guard(`AppleCredentialServiceTest#saveIfAuthorizationCodePresent_whenCodeBlank_doesNothing`)는 이제 login() 경로에선 도달 불가능한 방어 코드로 남지만, public 서비스 메서드의 defense-in-depth로 유지
- [x] **(`#64` 재발견, 처음부터 강제)** Google 로그인 시 `authorizationCode`를 안 보낸 경우 → 소셜 토큰 검증 전 즉시 `AUTH_GOOGLE_AUTHORIZATION_CODE_REQUIRED`(400)로 로그인 자체가 실패 — `AuthServiceTest#login_whenGoogleWithoutAuthorizationCode_throwsAuthorizationCodeRequired`·`#login_whenGoogleWithBlankAuthorizationCode_throwsAuthorizationCodeRequired`
- [x] **(`#64` 재발견)** Google 코드 교환 응답에 `refresh_token`이 없는 경우(재로그인 등 정상 케이스) → 예외 없이 로그인 성공, credential 저장은 스킵(기존 값 유지) — `GoogleLoginCredentialServiceTest#saveIfAuthorizationCodePresent_whenRefreshTokenAbsent_skipsSaveAndKeepsExisting`

### 수동 / 통합 (해당 시)

- [ ] 탈퇴 → 로그인 재시도 → 부활·로그인 성공 흐름 수동 확인
- [ ] 방장 탈퇴 후 그 방의 다른 멤버 계정으로 로그인해 방 목록 조회 → 해당 방이 보이지 않음을 확인
- [ ] **(`#64`)** 실제 Kakao 개발자 콘솔 테스트 계정으로 unlink 호출 후 콘솔에서 연결 해제 확인
- [ ] **(`#64`)** 실제 Apple 테스트 계정으로 authorizationCode 교환 → 탈퇴 → Apple ID 설정 화면에서 TripFit 연결이 해제됐는지 확인

## 완료 기준

- [x] Must Have 전부 (기존 cascade·soft delete 범위)
- [x] **(`#64`)** Must Have — Kakao/Apple revoke 전부 (2026-07-31 Implemented, 코드 기준)
- [x] **(`#64` 재발견, [`google-login-revoke.md`](google-login-revoke.md))** Google 로그인 자체 revoke — 코드 기준 Implemented
- [x] `docs/product/business-rules/user.md` BR-USER-004 `[미정]` 해소 반영
- [x] `user-my-page.md` Out of Scope에 본 스펙 deferred 링크 추가
- [x] `docs/specs/README.md` wave 2 표·이슈 매핑 갱신
- [x] Wave 2 Backlog(`#30`) Nice 섹션에 추가
- [x] **(`#64`)** `docs/architecture/erd.md`에 신규 `apple_credential`·`google_login_credential` 테이블 반영
- [x] **(`#64`)** `./gradlew test` — Kakao/Apple/Google 전부 mock 기반 단위 테스트(revoke 호출 `verify()`, client_secret JWT 서명 검증 포함) 통과
- [ ] **(`#64`, 코드 밖, 배포 순서 조율 필수)** 프론트가 Apple 로그인 시 `authorizationCode`를 실제로 보내기 시작한 뒤에 이 브랜치를 배포할 것 — B안(강제)으로 확정돼 프론트 미배포 상태에서 먼저 나가면 Apple 로그인이 그 즉시 전부 401→400으로 실패함
- [ ] **(`#64`, 코드 밖)** 실제 Apple 테스트 계정으로 수동 검증(아래 "수동/통합" 절)
- [ ] **(`#64` 재발견, 코드 밖, 배포 순서 조율 필수)** 프론트가 Google 로그인 시 hybrid flow(`response_type=code id_token`)로 전환해 `authorizationCode`를 실제로 보내기 시작한 뒤에 배포할 것 — 순서가 바뀌면 배포 즉시 Google 로그인 전체 401→400 실패
- [ ] **(`#64` 재발견, 코드 밖)** 실제 Google 테스트 계정으로 가입 → 탈퇴 → `myaccount.google.com` 연결된 앱 목록에서 삭제 확인 → 재가입 시 동의 화면 재표시 확인

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 탈퇴 계정 재가입(부활) 정책 | **확정(2026-07-27, 사용자 결정)** | 재가입 무조건 가능. 같은 (provider, socialId)로 재로그인하면 soft-deleted `User` row를 그대로 부활(`deletedAt=null`) — 신규 row 생성이 아님(테이블 `UNIQUE(provider, social_id)` 제약과 충돌 방지). `AUTH_WITHDRAWN_ACCOUNT` 차단 제거 |
| 액세스 토큰 즉시 무효화 | 확정(Out of Scope) | Wave 4 `#4` RTR/Redis 인프라 선행 필요 |
| 방장 탈퇴 시 소유 방이 다른 멤버에게도 통째로 안 보이게 됨 | 확정(수용된 결과) | `deleteTrip()` soft delete가 방 전체를 대상으로 함(방장만이 아님). 별도 "취소됨" 표시로 이력을 남기는 기능은 두지 않기로 확정(`CANCELED` 삭제 결정과 일관) |
| `CANCELED` 상태 방 처리 | **#48 Implemented** — 해당 없음 | enum 자체 삭제 완료 |
| `LoginRequest.authorizationCode` 필드가 이미 존재하지만 미소비 | **해소(2026-07-31 Implemented)** | `AuthService.login()`이 세 번째 파라미터로 받아 소비. APPLE인데 누락 시 이제 `AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED`(400)로 강제 |
| authorizationCode 누락 시 best-effort(조용히 skip) vs 강제(400 거부) | **확정(2026-07-31, B안, 사용자 결정)** | 최초 best-effort로 구현 후, App Store 요건이 "제출 시점"이 아니라 "운영 내내" 지켜야 하는 성격이라 회귀를 조용히 넘기면 위험하다고 판단해 강제로 전환. 프론트 배포와 순서 조율 필수(안 하면 배포 즉시 Apple 로그인 전체 실패) |
| Apple credential 신규 테이블명·컬럼명 | **확정(2026-07-31)** | `apple_credential` — `google_calendar_credential` 패턴에서 Apple에 불필요한 access token 캐시·동기화 필드만 제외한 최소 구조(`user_id`·`refresh_token_ciphertext`·`BaseTimeEntity`). `erd.md` 반영 완료 |
| Apple token exchange 실패 시 로그인 처리 | **확정(2026-07-31, 사용자 위임)** | 로그인은 성공, credential 저장만 스킵(best-effort) — Apple 인프라 장애가 TripFit 로그인 성공률에 영향 주지 않도록 |
| Apple 재로그인 시 refresh token 갱신 정책 | **확정(2026-07-31, 사용자 위임)** | 매번 최신 `authorizationCode`로 덮어씀(스킵 안 함) — stale token으로 인한 탈퇴 시 revoke 실패 가능성을 최소화 |
| Kakao unlink 실패 시 `socialId` 형식(카카오 회원번호) 검증 방식 | `[미정]` | 실제 Kakao 콘솔 테스트 계정으로 수동 검증 시 확정 예정(위 "수동/통합" 절) |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-31 | `#64` 재오픈 — Apple `APPLE_CLIENT_ID` 단일값을 `APPLE_BUNDLE_ID`(iOS 네이티브)/`APPLE_SERVICE_ID`(모바일 브라우저)로 이원화. `apple_credential`에 `apple_client_id` 컬럼 추가, 로그인 시 매칭된 값을 저장해 탈퇴 revoke에 재사용. 상세: [`apple-oauth-multi-audience.md`](apple-oauth-multi-audience.md) |
| 2026-07-31 | `#64` Apple authorizationCode 누락 처리를 best-effort(조용히 skip)에서 **강제(400 거부, B안)** 로 amend — 신규 `AuthErrorCode.AUTH_APPLE_AUTHORIZATION_CODE_REQUIRED` 추가, `AuthService.login()`이 provider==APPLE인데 authorizationCode 없으면 소셜 토큰 검증 전 즉시 거부. `LoginRequest`·`AuthController` Swagger(`@Schema`·`@Operation`·`@ApiResponses`) 갱신. 조건부 필수화라 `Breaking-Change-Reason` 트레일러 필요 — **프론트 배포 순서 조율 필수** |
| 2026-07-31 | `#64` Apple Implemented(코드) — `authorizationCode`를 `AuthService.login()`이 소비하도록 시그니처 변경(`AuthController`도 동일 전달). 신규 `auth/domain/AppleCredential`(최소 구조, user당 1행) · `auth/repository/AppleCredentialRepository` · `auth/oauth/AppleOAuthClient`(ES256 client_secret JWT 서명 — nimbus-jose-jwt, token exchange·revoke, 둘 다 호출마다 신규 JWT 발급) · `auth/service/AppleCredentialService`(로그인 시 저장 best-effort, 탈퇴 시 revoke+delete best-effort). `APPLE_TEAM_ID`/`APPLE_KEY_ID`/`APPLE_PRIVATE_KEY` env 전체 배선(.env.example 2곳·ci-cd.yml·docker-compose.yml·application.yml/OAuthProperties). `docs/architecture/erd.md`에 `apple_credential` 테이블 반영. `UserWithdrawalService.revokeGoogleCalendarIfConnected()`에도 복호화 실패 흡수 try/catch 보강. 세부 정책 3건(credential 컬럼 범위·token exchange 실패 시 로그인 처리·재로그인 갱신 정책) 사용자 위임으로 확정. 남은 것은 프론트 `authorizationCode` 전송 공지·실계정 수동 검증(코드 밖) |
| 2026-07-31 | `#64` Google/Kakao Implemented — `UserWithdrawalService`에 `revokeGoogleCalendarIfConnected()`/`unlinkKakaoIfProvider()` 추가(둘 다 best-effort). 신규 `user/client/KakaoUnlinkClient` + `KAKAO_ADMIN_KEY` env 4곳(.env.example·ci-cd.yml·docker-compose.yml·application.yml/OAuthProperties) 배선. `UserWithdrawalServiceTest`·신규 `KakaoUnlinkClientTest`로 revoke 호출·provider 실패 시 best-effort 검증. Apple은 테이블명 확정·프론트 공지가 남아 있어 이번 범위에서 제외(별도 진행) |
| 2026-07-31 | `#64`(탈퇴 시 소셜 provider revoke 호출) Must Have로 편입 — Out of Scope "#6으로 위임" 문구를 실제 요구사항 절로 교체. Google(기존 클라이언트 재사용)·Kakao(신규 Admin Key unlink)·Apple(신규 인프라 — authorizationCode 소비·token 교환·암호화 저장·client_secret JWT·revoke) 순차 진행 가능하도록 Must Have 분리. `e500e1ece` 401 마스킹 인시던트 재발 방지용 구현 가드레일(외부 호출 best-effort try/catch·네이티브 쿼리 지양·평문 저장 금지) 절 추가. Apple `.p8`/Team ID/Key ID는 GitHub Secrets 등록 완료, CI/CD·docker-compose·application.yml 배선은 구현 시 진행 |
| 2026-07-28 | Out of Scope "소셜 provider 측 unlink" 위임 대상을 `#6`→`#64`로 amend. `#64`는 Release Gate(앱 배포·심사 필수, `development-wave.md` §7)로 신규 분류 — Apple은 App Store Review Guideline 5.1.1(v) 요건 |
| 2026-07-27 | 리스크·미결정 "탈퇴 계정 재가입(부활) 정책" 확정(사용자 결정) — **무조건 재가입 가능**. soft-deleted 계정 재로그인 시 차단하던 `AUTH_WITHDRAWN_ACCOUNT`(401)를 폐기하고, 기존 row를 부활시켜 로그인 진행하는 방식으로 구현·문서 amend |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임. 본 스펙 코드 참조 동기화 |
| 2026-07-24 | 구현 완료(`#47` 브랜치) — `UserWithdrawalService`(cascade→hard delete→soft delete/PII 스크럽), `AUTH_WITHDRAWN_ACCOUNT` 로그인 차단, `./gradlew test` 통과 |
| 2026-07-24 | `src/new_decision.md` 최종 확정 반영 — `CANCELED` 관련 항목을 "결과 대기"에서 "해당 없음(enum 삭제 확정)"으로 정리 |
| 2026-07-24 | 정책 전면 수정(`#47` hotfix, 기획자 확인) — "ONGOING 있으면 차단" 폐기, **차단 없이 자동 cascade**로 전환(참여자: 전 방 자동 나가기, 방장: 전 방 자동 삭제). `USER_HAS_OWNED_TRIPS`/`USER_HAS_JOINED_TRIPS` 에러 폐기 |
| 2026-07-23 | 탈퇴 차단 조건을 `ONGOING`으로 좁힘 — `#20`·`#47`과 게이트 대칭 (**2026-07-24 폐기**) |
| 2026-07-23 | 초안 — BR-USER-004 `[미정]` 해소, User soft delete 결정 반영 |
