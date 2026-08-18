# 회원 탈퇴

> 상태: Implemented (기존 cascade·soft delete 범위) + `#64` Provider Revoke는 Draft(Must Have 편입, 미구현)
> MVP: In scope
> 관련 BR: BR-USER-004
> wave: 2 (Nice) · `#64`(Provider Revoke)는 Release Gate — Wave와 무관, 앱스토어 심사 필수(`harness-wave.md` 🚨)
> implements: BR-USER-004 `[미정]` 해소 — "진행 중 방" 처리 정책 확정
> deferred: (해당 없음)
> GitHub: 정책 근거 `#47`(hotfix, 확정) · 구현도 `#47` 브랜치(`docs/47-trip-status-policy-alignment`)에서 완료(별도 구현 이슈 없이 진행) · `#64`(소셜 provider revoke, Release Gate — 이번 amend로 Must Have 편입)
> 선행: [`trip-member-leave.md`](trip-member-leave.md) · [`user-my-page.md`](user-my-page.md) · `trip-room-api.md`(여행방 삭제) · `#64` Apple 부분은 Apple Developer Console `.p8`·Team ID·Key ID 발급 완료(GitHub Secrets 등록 완료, 2026-07-31) — CI/CD·docker-compose·application.yml 배선만 남음

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

### Must Have — `#64` 소셜 provider revoke (2026-07-31 amend, 미구현)

탈퇴 시 TripFit 내부 데이터 삭제와 별개로, 로그인에 쓴 소셜 provider 쪽에도 revoke·unlink를 호출한다. Apple은 App Store Review Guideline 5.1.1(v) 요건이라 Release Gate(`#65`). `#6`(계정 유지 상태의 다중 소셜 연결·개별 해제)과는 트리거가 다른 별개 흐름 — 혼동 금지.

Provider별로 선행 조건이 달라 **순차 완료 가능**하도록 분리한다.

**Google — 신규 인프라 없음**

- [ ] `UserWithdrawalService.withdraw()`에서 `GoogleCalendarCredentialRepository.deleteByUser_Id()` 호출 **전에**, credential이 존재하면 기존 `GoogleCalendarOAuthClient.revokeRefreshToken()`을 먼저 호출(best-effort, `GoogleCalendarService.disconnect()`와 동일 패턴 재사용 — 신규 클라이언트 코드 없음)

**Kakao — 신규 Admin Key 기반 unlink**

- [ ] 신규 env `KAKAO_ADMIN_KEY` — `.env.example` + `.github/workflows/ci-cd.yml`(secrets 참조·`envs:` 목록·deploy 스크립트 export) + `deploy/app/docker-compose.yml`(environment 매핑) + `application.yml`/`OAuthProperties.java`(바인딩 필드 추가) 4곳 동시 배선
- [ ] Kakao unlink 클라이언트 신규 구현 — `POST https://kapi.kakao.com/v1/user/unlink`, Admin Key 인증(`target_id_type=user_id` + 저장된 소셜 `socialId`). 사용자 access_token 저장 불필요
- [ ] `UserWithdrawalService.withdraw()`에서 `user.getProvider() == KAKAO`일 때만 호출(best-effort)

**Apple — 신규 인프라(가장 큼)**

- [ ] `LoginRequest.authorizationCode` 필드는 **이미 존재**(`cb5a23f`, optional·nullable, APPLE 전용) — 단 현재 `AuthController`/`AuthService.login(provider, token)`이 이 값을 완전히 무시하고 버림(연결 로직 없는 dead field). 이번 amend가 이 필드를 실제로 소비하는 첫 구현 — 신규 필드 추가 아님, 기존 필드 배선
- [ ] `AuthService.login()`(또는 신규 Apple 전용 후속 메서드)에서 `provider == APPLE && authorizationCode != null`이면 Apple token 엔드포인트(`https://appleid.apple.com/auth/token`)에서 `authorizationCode` → refresh token 교환
- [ ] `.p8`+`APPLE_TEAM_ID`+`APPLE_KEY_ID`로 서명한 client_secret JWT 생성(Apple token/revoke 엔드포인트 인증에 필요)
- [ ] 신규 엔티티(예: `AppleCredential`, `GoogleCalendarCredential`과 동일하게 user당 1행) — refresh token은 **AES-256 암호화 저장**(평문 저장 금지, 아래 구현 가드레일 참고)
- [ ] `UserWithdrawalService.withdraw()`에서 credential 존재 시 저장된 refresh token으로 `https://appleid.apple.com/auth/revoke` 호출(best-effort) 후 credential row 삭제
- [ ] 신규 env `APPLE_TEAM_ID`/`APPLE_KEY_ID`/`APPLE_PRIVATE_KEY` — GitHub Secrets 등록 완료(2026-07-31), `.env.example` placeholder도 이미 있음. 단 `.github/workflows/ci-cd.yml` + `deploy/app/docker-compose.yml` + `application.yml`/`OAuthProperties.java` 배선은 아직 없음(확인 완료) — 이번 구현에서 마저 연결
- [ ] `LoginRequest.authorizationCode` 필드 자체는 optional 추가라 `#64` 구현 커밋에서 새로 `Breaking-Change-Reason` 트레일러를 달 필요는 없음(계약 자체는 이미 프론트에 공개돼 있었다고 간주) — 단, 이 필드를 실제로 프론트가 채워 보내야 하므로 **구현 착수 전 프론트에 "이제부터 Apple 로그인 시 이 필드를 채워 보내야 한다"는 공지 필요**(계약 상 필드는 있었지만 지금까지 아무도 안 보내도 무방했음 → 이제부터는 안 보내면 Apple revoke가 항상 no-op)

### 구현 가드레일 (프로덕션 인시던트 재발 방지 — `#64` 전용)

`e500e1ece`(GET /trips 401 오인 마스킹 수정) 인시던트 원인: ① `Collection<UUID>` 네이티브 쿼리 IN절 바인딩이 Hibernate 기본값(BINARY)으로 떨어져 MySQL 타입 불일치 예외 발생 → ② 그 예외가 `GlobalExceptionHandler`에 안 잡히고 Tomcat `/error`로 forward됐는데 당시 `/error`가 `permitAll`이 아니어서 SecurityContext 없이 재평가되며 401로 둔갑(마스킹). 현재는 catch-all `@ExceptionHandler(Exception.class)` + `/error` permitAll로 전역 패치됨. `#64` 구현에서 같은 클래스의 실수를 반복하지 않도록:

- **외부 provider 호출은 반드시 서비스 레벨 try/catch로 best-effort 처리** — Kakao unlink·Apple token 교환/revoke 실패가 탈퇴 트랜잭션 자체를 막으면 안 됨(로컬 데이터 삭제·soft delete는 provider 상태와 무관하게 항상 성공해야 함). `GoogleCalendarOAuthClient.revokeRefreshToken()`의 `catch (Exception ignored)` 패턴을 그대로 따를 것
- **신규 네이티브 SQL 쿼리 추가 금지** — Apple credential 조회·삭제는 `GoogleCalendarCredentialRepository.deleteByUser_Id()`처럼 derived method/JPQL만 사용. `Collection<UUID>` IN절 네이티브 쿼리를 새로 만들지 않는다
- **Apple refresh token 평문 저장 금지** — `GoogleCalendarCredential`과 동일한 AES-256-GCM 암호화 패턴(`GoogleCalendarTokenCrypto`) 재사용. `RefreshToken.token`이 현재 평문 저장 중인 것은 별도 열려있는 follow-up이며, `#64`에서 같은 실수를 새로 추가하지 않는다

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
| 용도 | 소셜 로그인. `#64`는 이 API의 **기존** `authorizationCode` 필드(APPLE 전용, optional)를 실제로 소비하게 만든다 |

`LoginRequest.authorizationCode`는 신규 필드가 아니라 `cb5a23f`에서 이미 계약에 추가된 필드다. 지금까지는 `AuthService.login(provider, token)`이 이 값을 그대로 버려서 아무 동작도 하지 않았다 — `#64` Apple Must Have 구현이 이 필드를 처음으로 실제 소비하는 지점이다. 필드 자체가 이미 optional로 공개돼 있었으므로 이번 amend로 새 `Breaking-Change-Reason` 트레일러는 불필요하지만, **프론트가 실제로 이 필드를 채워 보내지 않으면 Apple revoke는 항상 no-op**이 되므로 구현 착수 전 프론트 공지가 필요하다(위 Must Have 항목 참고).

## 데이터 모델

- ERD 참조: `docs/architecture/erd.md` — 기존 cascade·soft delete 범위는 스키마 컬럼 변경 없음. `#64` Apple 부분은 **신규 테이블 1개** 추가(아래)
- `AuthErrorCode`에 신규 코드 없음(`AUTH_WITHDRAWN_ACCOUNT`는 재가입 정책 확정으로 폐기). `#64` 자체도 provider revoke는 전부 best-effort라 탈퇴 API에 신규 에러 코드를 추가하지 않는다
- hard delete 대상 테이블: `personal_schedule`, `regular_schedule`, `google_calendar_credential`, `google_calendar_busy_day`, `refresh_token` (모두 `user_id` 단독 소유, 타 사용자 참조 없음). `#64` 구현 후 Apple credential 테이블도 이 목록에 추가(탈퇴 시 revoke 호출 후 hard delete)
- soft delete + 스크럽 대상: `users` (row 유지, PII 컬럼만 null)
- cascade 대상: 호출자가 MEMBER인 `trip_member` row(soft delete, [`trip-member-leave.md`](trip-member-leave.md) 재사용) · 호출자가 OWNER인 `trip` row(soft delete, `deleteTrip()` 재사용 — 해당 방의 다른 멤버 `trip_member` row도 함께 soft delete됨)
- **`#64` 신규 테이블 (Apple credential, 가칭)**: `google_calendar_credential`과 동일 패턴 — `user_id`(FK, UNIQUE, user당 1행) · `refresh_token_ciphertext`(AES-256-GCM, TEXT, 평문 저장 금지) · 발급 시각 등 `BaseTimeEntity` 상속. PK는 프로젝트 컨벤션대로 UUID v4(`docs/specs/uuid-primary-key.md`). 정확한 테이블명·컬럼명은 구현 착수 시 확정해 이 절 + `erd.md`를 같은 턴에 갱신(`harness-follow-up.md` 💡 ERD)

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| BR-USER-004 | 확인 후 탈퇴, 차단 없이 자동 cascade(참여 방 자동 나가기 · 소유 방 자동 삭제) | `UserWithdrawalService`(신규) |
| (BR 없음, 외부 요건) | Apple App Store Review Guideline 5.1.1(v) — Sign in with Apple 지원 + 앱 내 계정 삭제 지원 시 삭제 시점에 Apple revoke 엔드포인트 호출 필수 | `UserWithdrawalService` + 신규 Apple credential 저장·client_secret JWT 서명 |

## 검증 시나리오

### 정상

- [x] 활성 방 멤버십이 전혀 없는 사용자 → 탈퇴 성공(204), 개인 데이터 hard delete, `users.deleted_at` set, PII null
- [x] `ONGOING` 방에 MEMBER로 참여 중인 사용자 → 탈퇴 시 해당 방 자동 나가기 처리 후 탈퇴 성공
- [x] `ONGOING` 방에 OWNER인 사용자 → 탈퇴 시 해당 방 자동 삭제(soft delete) 후 탈퇴 성공. 그 방의 다른 멤버도 더 이상 해당 방을 조회할 수 없음(기존 `deleteTrip()` cascade 재사용 — 별도 신규 검증 없이 회귀 없음 확인)
- [x] `CONFIRMED`/`EXPIRED` 방에 OWNER·MEMBER로 남아 있어도 동일하게 cascade 처리 후 탈퇴 성공(상태 게이트 없는 `leaveTrip`/`deleteTrip` 재사용)
- [x] 탈퇴 후 같은 소셜 계정으로 재로그인 시도 → 기존 soft-deleted `User` row가 부활(`deletedAt=null`)하며 로그인 성공. `firstName`/`lastName`/구글 캘린더 연동은 초기화된 상태라 재온보딩 필요
- [ ] 탈퇴한 사용자가 과거 멤버였던(soft-deleted) 다른 방의 `TripMember` 이력은 그대로 남음(FK 위반 없음) — 기존 soft delete 패턴 재사용으로 구조상 보장, 별도 통합 테스트는 생략
- [ ] **(`#64`)** Google Calendar 연동돼 있던 사용자가 탈퇴 → `GoogleCalendarOAuthClient.revokeRefreshToken()` 호출 후 credential hard delete(mock으로 `verify()` — 실제 호출 자체가 일어나는지 확인)
- [ ] **(`#64`)** Kakao로 로그인한 사용자가 탈퇴 → Kakao unlink 호출(mock `verify()`) 후 탈퇴 정상 완료
- [ ] **(`#64`)** Apple로 로그인(`authorizationCode` 포함)한 사용자가 탈퇴 → 저장된 refresh token으로 Apple revoke 호출(mock `verify()`) 후 credential hard delete

### 엣지 · 실패

- [x] 방장으로 있는 방이 여러 개(상태 혼합) → 전부 자동 삭제 후 탈퇴 성공
- [x] 멤버로 있는 방과 방장인 방이 동시에 있음 → 멤버인 방은 나가기, 방장인 방은 삭제, 둘 다 처리 후 탈퇴 성공(각 cascade facade 메서드 호출 검증)
- [ ] **(`#64`)** Google/Kakao/Apple revoke 호출이 provider 쪽 오류(네트워크 실패·이미 unlink된 토큰 등)로 예외를 던져도 **탈퇴 자체는 204로 성공** — best-effort이므로 provider 실패가 로컬 데이터 삭제·soft delete를 막지 않음을 확인(구현 가드레일 절 참고)
- [ ] **(`#64`)** Apple 로그인 시 `authorizationCode`를 안 보낸 경우(구버전 클라이언트 등) → Apple credential 저장을 건너뛰고 로그인은 정상 진행, 이후 탈퇴 시 저장된 credential이 없으므로 Apple revoke는 자연히 스킵(에러 아님)

### 수동 / 통합 (해당 시)

- [ ] 탈퇴 → 로그인 재시도 → 부활·로그인 성공 흐름 수동 확인
- [ ] 방장 탈퇴 후 그 방의 다른 멤버 계정으로 로그인해 방 목록 조회 → 해당 방이 보이지 않음을 확인
- [ ] **(`#64`)** 실제 Kakao 개발자 콘솔 테스트 계정으로 unlink 호출 후 콘솔에서 연결 해제 확인
- [ ] **(`#64`)** 실제 Apple 테스트 계정으로 authorizationCode 교환 → 탈퇴 → Apple ID 설정 화면에서 TripFit 연결이 해제됐는지 확인

## 완료 기준

- [x] Must Have 전부 (기존 cascade·soft delete 범위)
- [ ] **(`#64`)** Must Have — Google/Kakao/Apple revoke 전부
- [x] `docs/product/business-rules/user.md` BR-USER-004 `[미정]` 해소 반영
- [x] `user-my-page.md` Out of Scope에 본 스펙 deferred 링크 추가
- [x] `docs/specs/README.md` wave 2 표·이슈 매핑 갱신
- [x] Wave 2 Backlog(`#30`) Nice 섹션에 추가
- [ ] **(`#64`)** `docs/architecture/erd.md`에 신규 Apple credential 테이블 반영
- [ ] **(`#64`)** `./gradlew test` — provider별 mock 기반 단위 테스트(revoke 호출 `verify()`) 포함

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 탈퇴 계정 재가입(부활) 정책 | **확정(2026-07-27, 사용자 결정)** | 재가입 무조건 가능. 같은 (provider, socialId)로 재로그인하면 soft-deleted `User` row를 그대로 부활(`deletedAt=null`) — 신규 row 생성이 아님(테이블 `UNIQUE(provider, social_id)` 제약과 충돌 방지). `AUTH_WITHDRAWN_ACCOUNT` 차단 제거 |
| 액세스 토큰 즉시 무효화 | 확정(Out of Scope) | Wave 4 `#4` RTR/Redis 인프라 선행 필요 |
| 방장 탈퇴 시 소유 방이 다른 멤버에게도 통째로 안 보이게 됨 | 확정(수용된 결과) | `deleteTrip()` soft delete가 방 전체를 대상으로 함(방장만이 아님). 별도 "취소됨" 표시로 이력을 남기는 기능은 두지 않기로 확정(`CANCELED` 삭제 결정과 일관) |
| `CANCELED` 상태 방 처리 | **#48 Implemented** — 해당 없음 | enum 자체 삭제 완료 |
| `LoginRequest.authorizationCode` 필드가 이미 존재하지만 미소비 | **확인됨(2026-07-31)** | `cb5a23f`에서 optional 필드로 미리 추가됐으나 `AuthService.login()`이 무시 — dead field. `#64` Apple 구현이 이 필드를 처음 소비. 프론트가 아직 이 필드를 채워 보내는지 별도 확인 필요(구현 착수 전 프론트 공지 권장) |
| Apple credential 신규 테이블명·컬럼명 | `[미정]` | `#64` 구현 착수 시 확정 — 확정과 동시에 이 스펙 데이터 모델 절 + `erd.md` 갱신 |
| Kakao unlink 실패 시 `socialId` 형식(카카오 회원번호) 검증 방식 | `[미정]` | 구현 착수 시 카카오 API 문서 기준으로 확정 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-07-31 | `#64`(탈퇴 시 소셜 provider revoke 호출) Must Have로 편입 — Out of Scope "#6으로 위임" 문구를 실제 요구사항 절로 교체. Google(기존 클라이언트 재사용)·Kakao(신규 Admin Key unlink)·Apple(신규 인프라 — authorizationCode 소비·token 교환·암호화 저장·client_secret JWT·revoke) 순차 진행 가능하도록 Must Have 분리. `e500e1ece` 401 마스킹 인시던트 재발 방지용 구현 가드레일(외부 호출 best-effort try/catch·네이티브 쿼리 지양·평문 저장 금지) 절 추가. Apple `.p8`/Team ID/Key ID는 GitHub Secrets 등록 완료, CI/CD·docker-compose·application.yml 배선은 구현 시 진행 |
| 2026-07-28 | Out of Scope "소셜 provider 측 unlink" 위임 대상을 `#6`→`#64`로 amend. `#64`는 Release Gate(앱 배포·심사 필수, `development-wave.md` §7)로 신규 분류 — Apple은 App Store Review Guideline 5.1.1(v) 요건 |
| 2026-07-27 | 리스크·미결정 "탈퇴 계정 재가입(부활) 정책" 확정(사용자 결정) — **무조건 재가입 가능**. soft-deleted 계정 재로그인 시 차단하던 `AUTH_WITHDRAWN_ACCOUNT`(401)를 폐기하고, 기존 row를 부활시켜 로그인 진행하는 방식으로 구현·문서 amend |
| 2026-07-24 | **#48 Implemented** — `TripStatus.CANCELED` enum 삭제, `TERMINATED` → `EXPIRED` 리네임. 본 스펙 코드 참조 동기화 |
| 2026-07-24 | 구현 완료(`#47` 브랜치) — `UserWithdrawalService`(cascade→hard delete→soft delete/PII 스크럽), `AUTH_WITHDRAWN_ACCOUNT` 로그인 차단, `./gradlew test` 통과 |
| 2026-07-24 | `src/new_decision.md` 최종 확정 반영 — `CANCELED` 관련 항목을 "결과 대기"에서 "해당 없음(enum 삭제 확정)"으로 정리 |
| 2026-07-24 | 정책 전면 수정(`#47` hotfix, 기획자 확인) — "ONGOING 있으면 차단" 폐기, **차단 없이 자동 cascade**로 전환(참여자: 전 방 자동 나가기, 방장: 전 방 자동 삭제). `USER_HAS_OWNED_TRIPS`/`USER_HAS_JOINED_TRIPS` 에러 폐기 |
| 2026-07-23 | 탈퇴 차단 조건을 `ONGOING`으로 좁힘 — `#20`·`#47`과 게이트 대칭 (**2026-07-24 폐기**) |
| 2026-07-23 | 초안 — BR-USER-004 `[미정]` 해소, User soft delete 결정 반영 |
