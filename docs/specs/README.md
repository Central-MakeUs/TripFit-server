# 기능 스펙 (`docs/specs/`)

구현 **전**에 작성하는 기능·리팩터 설계 문서입니다.

## 작성 방법

1. `specify` 스킬 사용 (또는 Plan Mode)
2. 템플릿: [`.claude/skills/specify/references/spec-template.md`](../../.claude/skills/specify/references/spec-template.md)
3. 파일명: kebab-case — 예) `trip-room-create.md` (접미사 `mvp`, `phase`, `p2` 금지)
4. 상단 메타: `wave`, `implements`, `deferred` — [`waves.md`](../product/waves.md)
5. 사용자 승인 후 구현 시작

## wave 1 (인프라 + 참여 흐름 재설계)

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`uuid-primary-key.md`](uuid-primary-key.md) | **Implemented** | 전 테이블 PK/FK → UUID CHAR(36), JWT `sub` | — |
| [`auth-social-login.md`](auth-social-login.md) | **Approved** / Implemented | Google·Kakao·Apple · JWT access/refresh | — |
| [`user-onboarding.md`](user-onboarding.md) | **Approved** | 성·이름 · 재진입 D-REENTRY | auth-social-login |
| [`user-my-page.md`](user-my-page.md) | **Approved** / Implemented | 마이페이지 이름 PATCH | user-onboarding |
| [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) | **Implemented** (#22 · **#39 amend**) | join 게이트 · `SCHEDULE_PENDING`→activate · submit 폐기 · hold→#35 | user-onboarding |

## wave 2

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`schedule-unified.md`](schedule-unified.md) | **Approved** (#11) | 정기(`regular_schedule`)·개별(`personal_schedule`) 2테이블 | wave 1 auth·onboarding |
| [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) | **Implemented** (#17) · S1·R2=A · **A1→#37** (today~+2년) | regular+personal → 날짜별 정기+개별 합친 달력 조회 | schedule-unified (#11) · #37 |
| [`schedule-slot-override.md`](schedule-slot-override.md) | **Approved** (#67) | S1(개별 전체 대체) → O1(슬롯 단위 오버라이드) 전환 — 슬롯 nullable, 정기 정보가 부분 개별 편집으로 사라지는 문제 해결 | schedule-calendar-resolve.md |
| [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) | **Approved** (#37) · **구현 중/본 브랜치** · Wave 2 Must | 마이페이지 today+2년 · 방=희망 기간 · ONGOING 칩 (구 CANCELED 거부 — #48 enum 삭제로 해당 없음) | #17 · #12 |
| [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md) | **Approved** (#38) · **구현 중** · Wave 2 Must | CONFIRMED/EXPIRED snapshot · R-model A | #27 · #17 · #37 |
| [`trip-room-api.md`](trip-room-api.md) | **Approved** (#12) · D5 홈 · **#39** SCHEDULE_PENDING/activate | 여행방 CRUD·홈 목록·Pin · activate | #17 · #22 · #39 |
| [`trip-last-activity-at.md`](trip-last-activity-at.md) | **Approved** (#26) · L1~L4 | `last_activity_at` 갱신·`@TripActivity` AOP | #12 |
| [`trip-home-schedulers.md`](trip-home-schedulers.md) | **Implemented** (#27) · S1~S4 | EXPIRED DB·Pin batch · 00:05 KST | #12 |
| [`trip-member-remove.md`](trip-member-remove.md) | **Implemented** (#20) · **Wave 2 Nice** | 방장 MEMBER soft delete · 목록 응답 · recommendation 미터치 | #12 · #26 |
| [`trip-member-leave.md`](trip-member-leave.md) | **Implemented** (`#47` 브랜치) · **Wave 2 Nice** | 멤버 자진 탈퇴 · 방 상태 무관(ONGOING/CONFIRMED/EXPIRED) | #12 · #20 · #26 |
| [`user-account-withdrawal.md`](user-account-withdrawal.md) | cascade·soft delete는 **Implemented**(`#47` 브랜치) · `#64` 소셜 provider revoke는 **Draft**(미구현, Release Gate) · **Wave 2 Nice** | 회원 탈퇴 · BR-USER-004 `[미정]` 해소 · 차단 없이 자동 cascade · User soft delete + PII 스크럽 · Google/Kakao/Apple revoke | trip-member-leave · user-my-page |
| [`google-login-revoke.md`](google-login-revoke.md) | **Implemented** (`#64` 재오픈 후속, 코드·테스트 완료 · PR 대기) · Release Gate | Google 로그인 시 authorization code 확보·저장 → 탈퇴 시 revoke. `GoogleLoginCredential` 신규, Apple 패턴 재사용 | auth-social-login · user-account-withdrawal |
| [`trip-recommendation.md`](trip-recommendation.md) | Draft (#13) | 추천 API 설계·요청/응답 껍데기·DTO·ERD·상태 전이·확정·취소 (계산 로직 제외) | #12 · #17 · #22 |
| [`trip-recommendation-algorithm.md`](trip-recommendation-algorithm.md) | Draft (#50) | 추천 계산 로직 A to Z — 후보 윈도우·모드별 스코어링·`ALL_ATTEND` 필터·동점 | #13 · #17 |
| [`trip-member-status-derive.md`](trip-member-status-derive.md) | **Implemented** (#54) | `TripMember.status` 컬럼 제거 → `respondedAt` null 여부로 파생 계산 (내부 리팩터, API 계약 불변) | #12 |
| [`trip-member-fill-rate-refactor.md`](trip-member-fill-rate-refactor.md) | **Implemented** (#60) | 상세 API 멤버 프리뷰 추가 · `memberFillRate`=activeMemberCount 기준 전환 · `joinedMemberCount` API 미노출(3개 DTO) | #12 |

## wave 3

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`kakao-invite-share.md`](kakao-invite-share.md) | **Approved** (#19) | 카카오·링크 공유 A/B/C · create에 inviteCode 없음 · 신규 API 없음 | trip-room-api D3 · #12 |
| [`notification.md`](notification.md) | **Approved** (#21) · 구현 중 | FCM 푸시 · BR-NOTI-001~005·009 · BR-USER-005 · 알림센터 | #12 · #13 · 참여 완료 정의 |

## wave 4

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`trip-join-capacity-hold.md`](trip-join-capacity-hold.md) | **Draft** (#35) | join 정원 hold/TTL — MVP는 409 감수 | #22 late-join |
| [`google-calendar-oauth.md`](google-calendar-oauth.md) | **Approved** (#44) | Google Calendar OAuth · busy Merge · AES-256 | auth-social-login · user-onboarding |
| [`google-calendar-client-id-separation.md`](google-calendar-client-id-separation.md) | Draft (#78, 백엔드 배선 완료·콘솔 발급·FE 전환 대기) | 로그인·Calendar OAuth Client ID 분리 — GCP 콘솔 발급 가이드 포함 | google-calendar-oauth · google-login-revoke |
| [`auth-token-rotation.md`](auth-token-rotation.md) | Draft | RTR + Redis | auth-social-login · decision 004 |
| [`auth-apple-server-notifications.md`](auth-apple-server-notifications.md) | Approved | Apple S2S webhook (스토어 제출 전) | auth-social-login |
| [`user-profile-image-s3-mirror.md`](user-profile-image-s3-mirror.md) | Draft | 프로필 이미지 S3 미러링 B안 | decision 006 |
| [`auth-dev-stub-verifier.md`](auth-dev-stub-verifier.md) | Draft (#52) | `/auth/login` 계약 유지형 dev 스텁 검증기 — `dev-mock-login` 엔드포인트 대체 예정 | dev-mock-login |
| [`auth-error-code-granularity.md`](auth-error-code-granularity.md) | **Approved** (#57) · 구현 중 | 소셜 로그인 토큰 검증 실패 세분화 — `AUTH_SOCIAL_TOKEN_EXPIRED`/`INVALID`/`PROVIDER_UNAVAILABLE` · `auth-social-login.md` 에러 표 amend | auth-social-login |

## 도구 (Wave 무관)

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`dev-mock-login.md`](dev-mock-login.md) | **Approved** (이슈 미생성 — 긴급) · **deferred→#52** | `local`/`dev` 전용 mock 로그인, 프론트 Swagger 테스트용 | auth-social-login |
| [`swagger-openapi-docs.md`](swagger-openapi-docs.md) | Draft (이슈 미생성) | Swagger/OpenAPI 문서 가독성 개선 — `@ApiResponse` 부재·예시 부재·`OpenApiConfig` Info·`@Tag` 표기법 | — |
| [`api-contract-diff-ci.md`](api-contract-diff-ci.md) | **Approved** (이슈 미생성) | oasdiff CLI로 breaking change 감지 + Discord `#frontend` push 알림(커밋 트레일러로 사유 전달), 별도 프론트 저장소 동기화 보조 | — |
| [`google-login-native-sdk-decision.md`](google-login-native-sdk-decision.md) | **Resolved** (#77, 결정 불필요 — 이미 네이티브 SDK로 구현됨, 2026-07-31 정정) | WebView 앱에서 Google 로그인 방식 — FE 확인 결과 네이티브 SDK 이미 구현·배포 완료 | google-login-revoke |
| [`openapi-response-schema-generics.md`](openapi-response-schema-generics.md) | **Approved** (이슈 미생성) | `SuccessResponse<T>` 응답 스키마가 스펙에 필드 노출 안 되는 문제 — `useReturnTypeSchema = true`로 해결, oasdiff 응답 필드 변경 감지 복구 | api-contract-diff-ci |

**구현 순서 (wave 2 축):** uuid → schedule-unified(#11) → calendar(#17) → trip-room(#12) → recommendation API 껍데기(#13) → recommendation 계산 로직(#50)

## GitHub 이슈 매핑

| 이슈 | 스펙 | 상태 |
|------|------|------|
| #11 | schedule-unified | Closed |
| #17 | schedule-calendar-resolve (본인 calendar) | Closed |
| #12 | trip-room-api | Closed / Implemented |
| #13 | trip-recommendation (API 껍데기·DTO·ERD) | Open |
| **#50** | trip-recommendation-algorithm (계산 로직) | Open |
| **#19** | kakao-invite-share | **Approved** · Wave 3 Must · create inviteCode 미노출 Implemented |
| #20 | trip-member-remove | Implemented · **Wave 2 Nice** |
| **#21** | notification | Open · **Wave 3 Must** · 구현 중 |
| **#26** | trip-last-activity-at | Implemented |
| **#27** | trip-home-schedulers | Implemented |
| **#54** | trip-member-status-derive | Implemented |
| **#22** | schedule-participation-onboarding | Closed |
| **#35** | trip-join-capacity-hold (Draft — wave 4) | Open |
| **#37** | trip-schedule-calendar-window | Closed |
| **#38** | trip-schedule-snapshot | Closed |
| **#44** | google-calendar-oauth | Open · **Wave 4 Must** (구 Swagger chore 폐기) |
| **#47** | 나가기·내보내기·삭제·탈퇴 상태 정책 정합성 (hotfix) — `trip-member-leave`·`user-account-withdrawal` 정책 SSOT | Open · **Wave 2 Nice** |
| **#48** | `TripStatus.CANCELED` 삭제 + `TERMINATED`→`EXPIRED` 리네임 (chore) | Implemented |
| **#52** | auth-dev-stub-verifier (`dev-mock-login` 후속, wave 4) | Open |
| **#64** | 탈퇴 시 소셜 provider revoke 호출(Google/Kakao/Apple) — `user-account-withdrawal` 정책 SSOT · Google 부분은 `google-login-revoke` | Open · **Release Gate**(Wave 아님) |
| **#77** | google-login-native-sdk-decision (Resolved, 결정 불필요로 정정) | Open — 클로즈 검토 필요 |
| **#78** | google-calendar-client-id-separation (백엔드 배선 완료, GCP 콘솔 발급·FE 전환 대기, Wave 4) | Open |

## 완료 후

- 스펙의 완료 기준 체크
- API·스키마 변경이 있으면 `docs/architecture/erd.md` 동기화 검토
