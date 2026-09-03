# 기능 스펙 (`docs/specs/`)

구현 **전**에 작성하는 기능·리팩터 설계 문서입니다.

**폴더 = 도메인** (2026-08-03 재구성) — `com.tripfit.tripfit` 최상위 패키지와 1:1 대응.

| 폴더 | 대응 패키지 | 개수 |
|------|-------------|------|
| [`auth/`](auth/) | `auth` | 9 |
| [`user/`](user/) | `user` (+`user/googlecalendar`) | 6 |
| [`user-schedule/`](user-schedule/) | `user/schedule` | 6 |
| [`trip/`](trip/) | `trip` (recommendation 포함 — 별도 최상위 패키지 아님, flat) | 20 |
| [`notification/`](notification/) | `notification` | 1 |
| [`cross-cutting/`](cross-cutting/) | 도메인 무관 (PK 전략·OpenAPI·CI·에이전트 하네스 등) | 8 |

새 스펙은 어느 도메인 패키지를 다루는지 먼저 정하고 그 폴더에 넣는다 (두 도메인에 걸치면 주로 바뀌는 상태가 속한 도메인 기준). 도메인 무관(PK 전략, CI, Swagger 설정 등)은 `cross-cutting/`.

**릴리즈 Milestone(MVP 출시/출시 이후) 축은 이 폴더 축과 별개** — 운영 SSOT: [`release-milestones.md`](../product/release-milestones.md).

### 구 도메인 축("MVP 출시~4")은 폐지됐다 (2026-08-26)

과거에는 스펙·이슈를 MVP 출시~4라는 도메인 축으로 나눴다. 2026-09-03에 그 표기를 **전부 현행 Milestone으로 치환**했다 — 이슈 39건을 대조한 결과 MVP 출시·2·3은 예외 없이 `MVP 출시`, 출시 이후는 `출시 이후`에 대응했다.

남아 있는 `wave` 언급은 **폐지 사실 자체를 설명하는 문서**(`release-milestones.md` 스텁, `release-milestones.md` §0, `harness-milestone.md`, `glossary.md`)와 **변경 이력 기록**뿐이다. 새 문서에는 쓰지 않는다.

## 작성 방법

1. `specify` 스킬 사용 (또는 Plan Mode)
2. 템플릿: [`.claude/skills/specify/references/spec-template.md`](../../.claude/skills/specify/references/spec-template.md)
3. 파일명: kebab-case — 예) `trip-room-create.md` (접미사 `mvp`, `phase`, `p2` 금지), 대응 도메인 폴더에 저장
4. 상단 메타는 H1 바로 아래 **인용구 3줄**(`> 상태:` / `> MVP:` / `> 관련 BR:`) — 템플릿과 동일. YAML frontmatter는 쓰지 않는다(2026-09-03 통일: 53개 중 51개가 인용구를 쓰고 있었고, 스펙 frontmatter를 읽는 도구가 없어 형식만 갈라져 있었다)
5. 사용자 승인 후 구현 시작

## `auth/`

| 스펙 | Milestone | 상태 | 범위 | 선행 |
|------|------|------|------|------|
| [`auth-social-login.md`](auth/auth-social-login.md) | MVP 출시 | **Approved** / Implemented | Google·Kakao·Apple · JWT access/refresh | — |
| [`apple-oauth-multi-audience.md`](auth/apple-oauth-multi-audience.md) | MVP 출시 | Implemented (`#64` amend) | Apple 로그인 Bundle ID(네이티브)/Services ID(웹) 이원화 `aud` 검증 | auth-social-login |
| [`google-login-revoke.md`](auth/google-login-revoke.md) | MVP 출시 | **Implemented** (`#64` 후속, Closed) · 구 Release Gate | Google 로그인 시 authorization code 확보·저장 → 탈퇴 시 revoke | auth-social-login · user-account-withdrawal |
| [`auth-error-code-granularity.md`](auth/auth-error-code-granularity.md) | 무관 | **Implemented** (#57, Closed) | 소셜 로그인 토큰 검증 실패 세분화 — `AUTH_SOCIAL_TOKEN_EXPIRED`/`INVALID`/`PROVIDER_UNAVAILABLE` | auth-social-login |
| [`auth-token-rotation.md`](auth/auth-token-rotation.md) | 출시 이후 | **Superseded** (2026-09-15, `#4` Closed) — 전제 2개(refresh MySQL SSOT · access Blacklist)가 `auth-refresh-redis-cookie`로 뒤집힘. RTR 개념 설명 이력으로만 유지 | RTR (구 설계: MySQL + Redis Blacklist) | auth-social-login · decision 004·010 |
| [`auth-refresh-redis-cookie.md`](auth/auth-refresh-redis-cookie.md) | 무관 | **Implemented** (`#2` Closed, PR #121 — 2026-08-19) | Refresh token Redis 이관 + HttpOnly 쿠키 전달 — auth-token-rotation의 전제(MySQL SSOT·Access Blacklist)를 뒤집는 후속 개편 | auth-token-rotation |
| [`auth-apple-server-notifications.md`](auth/auth-apple-server-notifications.md) | 출시 이후 | Approved | Apple S2S webhook (스토어 제출 전) | auth-social-login |
| [`dev-mock-login.md`](auth/dev-mock-login.md) | 도구 | **Removed** (2026-08-15) | `local`/`dev` 전용 mock 로그인, 프론트 Swagger 테스트용 — 더 이상 필요 없어 삭제 | auth-social-login |
| [`google-login-native-sdk-decision.md`](auth/google-login-native-sdk-decision.md) | 도구 | **Resolved** (#77, 결정 불필요로 정정) | WebView 앱 Google 로그인 방식 — FE 확인 결과 네이티브 SDK 이미 구현·배포 완료 | google-login-revoke |

## `user/`

| 스펙 | Milestone | 상태 | 범위 | 선행 |
|------|------|------|------|------|
| [`user-onboarding.md`](user/user-onboarding.md) | MVP 출시 | **Approved** | 성·이름 · 재진입 D-REENTRY | auth-social-login |
| [`user-my-page.md`](user/user-my-page.md) | MVP 출시 | **Approved** / Implemented | 마이페이지 이름 PATCH | user-onboarding |
| [`google-calendar-oauth.md`](user/google-calendar-oauth.md) | MVP 출시 | **Approved** (#44) · **출시 이후 → MVP 출시 재분류**(2026-08-03) | Google Calendar OAuth · busy Merge · AES-256 | auth-social-login · user-onboarding |
| [`google-calendar-client-id-separation.md`](user/google-calendar-client-id-separation.md) | MVP 출시 | **Implemented** (#78, Closed) · **출시 이후 → MVP 출시 재분류** | 로그인·Calendar OAuth Client ID 분리 — GCP 콘솔 발급 가이드 포함 | google-calendar-oauth · google-login-revoke |
| [`user-account-withdrawal.md`](user/user-account-withdrawal.md) | MVP 출시 | cascade·soft delete **Implemented**(`#47`) · `#64` provider revoke **Implemented** · **MVP 출시 · must** | 회원 탈퇴 · BR-USER-004 `[미정]` 해소 · cascade · PII 스크럽 · Google/Kakao/Apple revoke | trip-member-leave · user-my-page |
| [`user-profile-image-s3-mirror.md`](user/user-profile-image-s3-mirror.md) | 출시 이후 | Draft | 프로필 이미지 S3 미러링 B안 | decision 006 |

## `user-schedule/`

| 스펙 | Milestone | 상태 | 범위 | 선행 |
|------|------|------|------|------|
| [`schedule-unified.md`](user-schedule/schedule-unified.md) | MVP 출시 | **Approved** (#11) | 정기(`regular_schedule`)·개별(`personal_schedule`) 2테이블 | MVP 출시 auth·onboarding |
| [`schedule-calendar-resolve.md`](user-schedule/schedule-calendar-resolve.md) | MVP 출시 | **Implemented** (#17) · S1·R2=A · **A1→#37** | regular+personal → 날짜별 정기+개별 합친 달력 조회 | schedule-unified (#11) · #37 |
| [`schedule-slot-override.md`](user-schedule/schedule-slot-override.md) | MVP 출시 | **Implemented** (#67 Closed) | S1(개별 전체 대체) → O1(슬롯 단위 오버라이드) 전환 | schedule-calendar-resolve.md |
| [`schedule-holiday-rest.md`](user-schedule/schedule-holiday-rest.md) | MVP 출시 | **Implemented** (#107 Closed) | `holidayRest` 근무일 판정 반영 — 공공데이터포털 특일정보 API + Redis 캐싱, 달력·추천 공통 | schedule-calendar-resolve (A4) · decision 011 |
| [`schedule-holiday-list-api.md`](user-schedule/schedule-holiday-list-api.md) | MVP 출시 | **Implemented** (#107) | `GET /api/v1/holidays` — 캘린더 화면 공휴일 빨간 글씨 표시용 날짜 목록 조회, `schedule-holiday-rest.md` Out of Scope amend | schedule-holiday-rest |
| [`vacation-policy-user-migration.md`](user-schedule/vacation-policy-user-migration.md) | 출시 이후 | **Implemented** (`#52` Closed, PR [#111](https://github.com/Central-MakeUs/TripFit-server/pull/111) merge 완료) | 연차·휴일 정보를 `RegularSchedule` → `User`로 이동 (스키마 리팩터, 정책 불변) | schedule-unified · trip-recommendation-algorithm |
| [`pre-schedule-entry-flow.md`](user-schedule/pre-schedule-entry-flow.md) | MVP 출시 | **Implemented** (2026-08-19, `#113`·`#114` Closed) | 사전 일정 입력 최초/갱신 판정을 `사전 신청일` 단일 마커로 확정 · 회원가입에서 사전 일정 제거 · `activate` 입력 완료 게이트 · 정기 일정 전체 삭제 API | schedule-participation-onboarding · trip-join-schedule-gate · vacation-policy-user-migration |
| [`schedule-state-response.md`](user-schedule/schedule-state-response.md) | MVP 출시 | **Superseded** (2026-08-18, 미구현) | 정기 "없어요" 선언 저장·`regularScheduleState`·`canEnterRoom` 노출 — 폐기. 유효한 진단만 `trip-join-schedule-gate`로 이관 | trip-join-schedule-gate |

## `trip/`

recommendation(추천)은 `trip/` 패키지 안에 flat하게 있어(별도 최상위 패키지 아님) 이 폴더에 함께 둔다 — 분리 여부는 `package-structure-refactor.md` Draft 검토 대상.

| 스펙 | Milestone | 상태 | 범위 | 선행 |
|------|------|------|------|------|
| [`schedule-participation-onboarding.md`](trip/schedule-participation-onboarding.md) | MVP 출시 | **Implemented** (#22 · **#39 · #113 · #114 amend**) · **도메인 축 재분류**(2026-08-03, trip 참여 흐름) | join 게이트 · 방장·참여자 모두 `SCHEDULE_PENDING`→activate · submit 폐기 · hold 폐지(#114) | user-onboarding |
| [`trip-room-api.md`](trip/trip-room-api.md) | MVP 출시 | **Approved** (#12) · D5 홈 · **#39** SCHEDULE_PENDING/activate | 여행방 CRUD·홈 목록·Pin · activate | #17 · #22 · #39 |
| [`trip-duration-range.md`](trip/trip-duration-range.md) | MVP 출시 | Implemented (hotfix, `trip-room-api` D9 amend) | 여행 일수(n박m일) 검증 범위를 `n+1`~`min(n+2,T)`로 확장 | trip-room-api |
| [`trip-last-activity-at.md`](trip/trip-last-activity-at.md) | MVP 출시 | **Approved** (#26) · L1~L4 | `last_activity_at` 갱신·`@TripActivity` AOP | #12 |
| [`trip-home-schedulers.md`](trip/trip-home-schedulers.md) | MVP 출시 | **Implemented** (#27) · S1~S4 | EXPIRED DB·Pin batch · 00:05 KST | #12 |
| [`trip-member-remove.md`](trip/trip-member-remove.md) | MVP 출시 | **Implemented** (#20) · **MVP 출시 · could** | 방장 MEMBER soft delete · 목록 응답 · recommendation 미터치 | #12 · #26 |
| [`trip-member-leave.md`](trip/trip-member-leave.md) | MVP 출시 | **Implemented** (`#47` 브랜치) · **MVP 출시 · must** | 멤버 자진 탈퇴 · 방 상태 무관(ONGOING/CONFIRMED/EXPIRED) | #12 · #20 · #26 |
| [`trip-member-status-derive.md`](trip/trip-member-status-derive.md) | MVP 출시 | **Implemented** (#54) | `TripMember.status` 컬럼 제거 → `respondedAt` null 여부로 파생 계산 | #12 |
| [`trip-member-fill-rate-refactor.md`](trip/trip-member-fill-rate-refactor.md) | MVP 출시 | **Implemented** (#60) | 상세 API 멤버 프리뷰 추가 · `memberFillRate` 전환 · `joinedMemberCount` API 미노출 | #12 |
| [`trip-schedule-calendar-window.md`](trip/trip-schedule-calendar-window.md) | MVP 출시 | **Implemented** (#37 Closed) | 마이페이지 today+2년 · 방=희망 기간 · ONGOING 칩 | #17 · #12 |
| [`trip-calendar-window-pre-join.md`](trip/trip-calendar-window-pre-join.md) | 출시 이후 | **Implemented** (2026-08-18, [#110](https://github.com/Central-MakeUs/TripFit-server/issues/110)) | 본래 증상은 `#114`가 해소 · `GET`/`PATCH` 윈도우 검증 비대칭은 해결안 D(저장에도 같은 윈도우)로 해소 | #37 · #53 · #114 |
| [`trip-schedule-snapshot.md`](trip/trip-schedule-snapshot.md) | MVP 출시 | **Implemented** (#38 Closed) | CONFIRMED/EXPIRED snapshot · R-model A | #27 · #17 · #37 |
| [`trip-recommendation.md`](trip/trip-recommendation.md) | MVP 출시 | **Implemented** (#13 Closed) | 추천 API 설계·요청/응답 껍데기·DTO·ERD·상태 전이·확정·취소 (계산 로직 제외) | #12 · #17 · #22 |
| [`trip-recommendation-algorithm.md`](trip/trip-recommendation-algorithm.md) | MVP 출시 | **Approved** (#50 Closed) · **2026-08-15 연차/반차 자동 반영 amend Implemented**(#105) | 추천 계산 로직 A to Z — 후보 윈도우·모드별 스코어링·`ALL_ATTEND` 필터·동점 · 연차/반차 자동 전환 시뮬레이션 | #13 · #17 |
| [`trip-recommendation-scoring-source.md`](trip/trip-recommendation-scoring-source.md) | MVP 출시 | 확정 (기획자 승인) | 추천 스코어링 원본 자료 — `trip-recommendation-algorithm`이 구현하는 패널티 구간표·가중치·동점 기준의 원본 출처(참고 자료, SSOT 아님) | trip-recommendation-algorithm |
| [`trip-join-capacity-hold.md`](trip/trip-join-capacity-hold.md) | 출시 이후 | **Superseded** (2026-08-19, `#114` — hold 완전 폐지·DB 비관적 락으로 대체) | join 정원 hold/TTL — 이력 문서 | trip-join-schedule-gate |
| [`trip-join-schedule-gate.md`](trip/trip-join-schedule-gate.md) | MVP 출시 | **Implemented** (2026-08-19, `#113`+`#114`) · **BR-USER-006·007 개정 / 011 삭제 포함** | 참여자 `join`을 `SCHEDULE_PENDING`으로 앞당겨 방 입장 일정 확인을 서버가 강제 · hold→DB 비관적 락 대체 · 전역 입장 게이트(`is_all_free`) 삭제 | #22 · #39 · #110 |
| [`package-structure-refactor.md`](trip/package-structure-refactor.md) | 출시 이후 | **Implemented** (`#100` Closed, PR #101) · **2026-08-26 포트/어댑터 부분 폐기**(구현체가 항상 1개라 concrete 직접 주입으로 회귀) — 현재 구조 SSOT는 [`audits/trip/refactor-log.md`](../audits/trip/refactor-log.md) | trip 도메인 패키지 재설계 — 이력 문서 | decision 003 |
| [`kakao-invite-share.md`](trip/kakao-invite-share.md) | MVP 출시 | **Approved** (#19) | 카카오·링크 공유 A/B/C · create에 inviteCode 없음 · 신규 API 없음 | trip-room-api D3 · #12 |
| [`trip-thumbnail-image.md`](trip/trip-thumbnail-image.md) | 미정 (#62) | Draft | 여행방 확정 기간을 베이스 이미지에 합성해 카카오 공유용 동적 썸네일 자동 생성 · S3 등 오브젝트 스토리지 신규 구축 필요 | kakao-invite-share (#19) |

## `notification/`

| 스펙 | Milestone | 상태 | 범위 | 선행 |
|------|------|------|------|------|
| [`notification.md`](notification/notification.md) | MVP 출시 | **Implemented** (#21 Closed) | FCM 푸시 · BR-NOTI-001~005·009 · BR-USER-005 · 알림센터 | #12 · #13 · 참여 완료 정의 |

## `cross-cutting/`

| 스펙 | Milestone | 상태 | 범위 | 선행 |
|------|------|------|------|------|
| [`uuid-primary-key.md`](cross-cutting/uuid-primary-key.md) | MVP 출시 | **Implemented** | 전 테이블 PK/FK → UUID CHAR(36), JWT `sub` | — |
| [`swagger-openapi-docs.md`](cross-cutting/swagger-openapi-docs.md) | 도구 | Draft (이슈 미생성) | Swagger/OpenAPI 문서 가독성 개선 — `@ApiResponse` 부재·예시 부재·`OpenApiConfig` Info·`@Tag` 표기법 | — |
| [`api-contract-diff-ci.md`](cross-cutting/api-contract-diff-ci.md) | 도구 | **Approved** (이슈 미생성) | oasdiff CLI로 breaking change 감지 + Discord `#frontend` push 알림, 별도 프론트 저장소 동기화 보조 | — |
| [`openapi-response-schema-generics.md`](cross-cutting/openapi-response-schema-generics.md) | 도구 | **Approved** (이슈 미생성) | `SuccessResponse<T>` 응답 스키마가 스펙에 필드 노출 안 되는 문제 — `useReturnTypeSchema = true`로 해결 | api-contract-diff-ci |
| [`social-integration-structured-logging.md`](cross-cutting/social-integration-structured-logging.md) | 도구 | Draft (`#65`) | 소셜 로그인(Google/Kakao/Apple)·Google Calendar 연동 구조화 JSON 로깅 — provider/action/httpStatus/providerErrorReason 필드, PII 마스킹 | google-calendar-oauth · auth-social-login |
| [`terraform-iac-migration.md`](cross-cutting/terraform-iac-migration.md) | 무관 | Draft (`#125`) | 수동 생성된 AWS 인프라(EC2 4대·SG·EIP·Route 53)를 Terraform으로 1:1 import — `infra/terraform/`, S3+native lockfile state | decision 002·009·010 |
| [`add-prometheus.md`](cross-cutting/add-prometheus.md) | 무관 | Draft (`#126`) | Prometheus 지표 수집 및 Grafana 연동 | decision 009 |
| [`harness-track-gate-restructure.md`](cross-cutting/harness-track-gate-restructure.md) | 무관 | Draft (`#127`) | 에이전트 하네스를 3 트랙(기능·감사/리팩터·버그) × 4 게이트(리서치·승인·검증·회고)로 재구성 + `researcher`·`doc-reviewer` 서브에이전트와 `doc-writing.md` 문서 작성 규칙 신설 | — |

**구현 순서 (MVP 출시):** uuid → schedule-unified(#11) → calendar(#17) → trip-room(#12) → recommendation API 껍데기(#13) → recommendation 계산 로직(#50)

## GitHub 이슈 매핑

| 이슈 | 스펙 | 상태 |
|------|------|------|
| #11 | schedule-unified | Closed |
| #17 | schedule-calendar-resolve (본인 calendar) | Closed |
| #12 | trip-room-api | Closed / Implemented |
| #13 | trip-recommendation (API 껍데기·DTO·ERD) | Closed |
| **#50** | trip-recommendation-algorithm (계산 로직) | Closed (PR #72) |
| **#19** | kakao-invite-share | **Approved** · MVP 출시 Must · create inviteCode 미노출 Implemented |
| #20 | trip-member-remove | Implemented · **MVP 출시 · could** |
| **#21** | notification | Closed |
| **#26** | trip-last-activity-at | Implemented |
| **#27** | trip-home-schedulers | Implemented |
| **#54** | trip-member-status-derive | Implemented |
| **#22** | schedule-participation-onboarding | Closed · **MVP 출시**(2026-08-03 도메인 축 재분류) |
| **#35** | trip-join-capacity-hold (**Superseded** — `#114`로 hold 폐지) | Closed (2026-08-14) |
| **#37** | trip-schedule-calendar-window | Closed |
| **#38** | trip-schedule-snapshot | Closed |
| **#110** | trip-calendar-window-pre-join — `#114`로 본래 증상 해소 + 저장 윈도우 검증 추가 | Closed |
| **#44** | google-calendar-oauth | Closed |
| **#47** | 나가기·내보내기·삭제·탈퇴 상태 정책 정합성 (hotfix) — `trip-member-leave`·`user-account-withdrawal` 정책 SSOT | Closed · **MVP 출시 · must** |
| **#48** | `TripStatus.CANCELED` 삭제 + `TERMINATED`→`EXPIRED` 리네임 (chore) | Implemented |
| **#52** | 연차·휴일 정보를 `RegularSchedule`→`User`로 이동(스키마 리팩토링, 출시 이후) — `#105` 임시 우회("가장 먼저 등록된 행 기준")의 근본 수정. **이슈 번호 재사용**(구 "auth-dev-stub-verifier"는 `dev-login` 삭제로 폐기, 이 chore가 대체) | Closed |
| **#105** | trip-recommendation-algorithm 연차/반차 자동 반영 amend | Closed (PR #108) |
| **#107** | 대한민국 공휴일·대체공휴일 근무일 판정 반영(`holidayRest`) — 데이터 소스는 [`011`](../decisions/011-holiday-data-source.md)(공공데이터포털 특일정보 API)로 확정. `schedule-holiday-rest`·`schedule-holiday-list-api` 구현 완료 | Closed |
| **#64** | 탈퇴 시 소셜 provider revoke 호출(Google/Kakao/Apple) — `user-account-withdrawal` 정책 SSOT · Google 부분은 `google-login-revoke` | **Closed** · 구 Release Gate(2026-08-03 완료 확인) |
| **#65** | social-integration-structured-logging — 과거 Release Gate 메타 트래커(전부 Closed)를 이 스펙 이슈로 재사용(2026-08-03) | Closed |
| **#77** | google-login-native-sdk-decision (Resolved, 결정 불필요로 정정) | Closed |
| **#78** | google-calendar-client-id-separation (Implemented — 백엔드 배선·GCP 콘솔 발급·FE 전환 완료, **MVP 출시**(2026-08-03 출시 이후→3 이동)) | Closed |
| **#62** | trip-thumbnail-image (Draft) — 2026-08-02 재작성, 구 "OAuth 콘솔 설정값" 내용은 `#86`으로 이관 | Open |
| **#86** | OAuth 콘솔 설정값 채우기 (구 #62 내용 이관) | **Closed** · 구 Release Gate(전부 완료) |
| **#125** | terraform-iac-migration (Draft — 스펙 승인 대기) | Open · **출시 이후** · `priority: could` |
| **#126** | add-prometheus (Draft) | Open |

## 완료 후

- 스펙의 완료 기준 체크
- API·스키마 변경이 있으면 `docs/architecture/erd.md` 동기화 검토
