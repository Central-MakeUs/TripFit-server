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
| [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) | **Draft** (#22 · **#39 amend**) | join 게이트 · `JOINED`→confirm · submit 폐기 · hold→#35 | user-onboarding · trip-create-join-flow-redesign |
| [`trip-create-join-flow-redesign.md`](trip-create-join-flow-redesign.md) | **Implemented (core)** · **[#39](https://github.com/Central-MakeUs/TripFit-server/issues/39)** | 생성→일정 confirm | trip-room-api · [가이드](../product/flows/trip-create-join-guide.md) |

## wave 2

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`schedule-unified.md`](schedule-unified.md) | **Approved** (#11) | 정기(`regular_schedule`)·개별(`personal_schedule`) 2테이블 | wave 1 auth·onboarding |
| [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) | **Implemented** (#17) · S1·R2=A · **A1→#37** (today~+2년) | regular+personal → 날짜별 effective 달력 조회 | schedule-unified (#11) · #37 |
| [`trip-schedule-calendar-window.md`](trip-schedule-calendar-window.md) | **Approved** (#37) · **구현 중/본 브랜치** · Wave 2 Must | 마이페이지 today+2년 · 방=희망 기간 · CANCELED 거부 · ONGOING 칩 | #17 · #12 |
| [`trip-schedule-snapshot.md`](trip-schedule-snapshot.md) | **Approved** (#38) · **구현 중** · Wave 2 Must | CONFIRMED/TERMINATED snapshot · R-model A | #27 · #17 · #37 |
| [`trip-room-api.md`](trip-room-api.md) | **Approved** (#12) · D5 홈 · **#39** JOINED/confirm | 여행방 CRUD·홈 목록·Pin · schedule/confirm | #17 · #22 · #39 |
| [`trip-last-activity-at.md`](trip-last-activity-at.md) | **Approved** (#26) · L1~L4 | `last_activity_at` 갱신·`@TripActivity` AOP | #12 |
| [`trip-home-schedulers.md`](trip-home-schedulers.md) | **Implemented** (#27) · S1~S4 | TERMINATED DB·Pin batch · 00:05 KST | #12 |
| [`trip-member-remove.md`](trip-member-remove.md) | **Implemented** (#20) · **Wave 2 Nice** | 방장 MEMBER soft delete · 목록 응답 · recommendation 미터치 | #12 · #26 |
| [`trip-recommendation.md`](trip-recommendation.md) | Draft (#13) | 추천 4모드·TOP 3·확정·취소 | #12 · #17 · #22 |

## wave 3

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`kakao-invite-share.md`](kakao-invite-share.md) | **Approved** (#19) | 카카오·링크 공유 A/B/C · create에 inviteCode 없음 · 신규 API 없음 | trip-room-api D3 · #12 |
| (알림) | Draft 예정 (#21) | FCM/APNs · BR-NOTI-001~004 | #12 · #13 · 참여 완료 정의 |

## wave 4

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`trip-join-capacity-hold.md`](trip-join-capacity-hold.md) | **Draft** (#35) | join 정원 hold/TTL — MVP는 409 감수 | #22 late-join |
| [`google-calendar-oauth.md`](google-calendar-oauth.md) | **Approved** (#44) | Google Calendar OAuth · busy Merge · AES-256 | auth-social-login · user-onboarding |
| [`auth-token-rotation.md`](auth-token-rotation.md) | Draft | RTR + Redis | auth-social-login · decision 004 |
| [`auth-apple-server-notifications.md`](auth-apple-server-notifications.md) | Draft | Apple S2S webhook (스토어 제출 전) | auth-social-login |
| [`user-profile-image-s3-mirror.md`](user-profile-image-s3-mirror.md) | Draft | 프로필 이미지 S3 미러링 B안 | decision 006 |

**구현 순서 (wave 2 축):** uuid → schedule-unified(#11) → calendar(#17) → trip-room(#12) → recommendation(#13)

## GitHub 이슈 매핑

| 이슈 | 스펙 | 상태 |
|------|------|------|
| #11 | schedule-unified | Closed |
| #17 | schedule-calendar-resolve (본인 calendar) | Closed |
| #12 | trip-room-api | Closed / Implemented |
| #13 | trip-recommendation | Open |
| **#19** | kakao-invite-share | **Approved** · Wave 3 Must · create inviteCode 미노출 Implemented |
| #20 | trip-member-remove | Implemented · **Wave 2 Nice** |
| **#21** | 알림 (Draft 예정) | Open · **Wave 3 Must** |
| **#26** | trip-last-activity-at | Implemented |
| **#27** | trip-home-schedulers | Implemented |
| **#22** | schedule-participation-onboarding | Closed |
| **#35** | trip-join-capacity-hold (Draft — wave 4) | Open |
| **#37** | trip-schedule-calendar-window | Closed |
| **#38** | trip-schedule-snapshot | Closed |
| **#44** | google-calendar-oauth | Open · **Wave 4 Must** (구 Swagger chore 폐기) |

## 완료 후

- 스펙의 완료 기준 체크
- API·스키마 변경이 있으면 `docs/architecture/erd.md` 동기화 검토
