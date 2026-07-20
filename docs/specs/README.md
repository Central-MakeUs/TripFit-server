# 기능 스펙 (`docs/specs/`)

구현 **전**에 작성하는 기능·리팩터 설계 문서입니다.

## 작성 방법

1. Cursor에서 `specify` 스킬 사용 (또는 Plan 모드)
2. 템플릿: [`.cursor/skills/specify/references/spec-template.md`](../../.cursor/skills/specify/references/spec-template.md)
3. 파일명: kebab-case — 예) `trip-room-create.md` (접미사 `mvp`, `phase`, `p2` 금지)
4. 상단 메타: `wave`, `implements`, `deferred` — [`waves.md`](../product/waves.md)
5. 사용자 승인 후 구현 시작

## wave 1 (인프라 + 참여 흐름 재설계)

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`uuid-primary-key.md`](uuid-primary-key.md) | **Implemented** | 전 테이블 PK/FK bigint → UUID CHAR(36), JWT `sub`, Cursor 규칙 | — |
| [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) | **Draft** (#22, 부분 확정) | D-NAME-1~D-JOIN-4 확정 · sparse/submit **`[미정]`** | user-onboarding · schedule-unified |

## wave 2

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`schedule-unified.md`](schedule-unified.md) | **Approved** (#11) | 정기(`regular_schedule`)·개별(`personal_schedule`) 2테이블 | wave 1 auth·onboarding |
| [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) | **Implemented** (#17) · S1·R2=A·A1=730일 | regular+personal → 날짜별 effective 달력 조회 | schedule-unified (#11) |
| [`trip-room-api.md`](trip-room-api.md) | **Approved** (#12) · D5 홈 2뷰 · submit→**#22** | 여행방 CRUD·홈 목록·Pin | #17 · **#22** |
| [`trip-last-activity-at.md`](trip-last-activity-at.md) | **Approved** (#26) · L1~L4 | `last_activity_at` 갱신·`@TripActivity` AOP | #12 |
| [`trip-home-schedulers.md`](trip-home-schedulers.md) | **Implemented** (#27) · S1~S4 | TERMINATED DB·Pin batch · 00:05 KST | #12 |
| [`trip-recommendation.md`](trip-recommendation.md) | Draft | 추천 4모드·TOP 3·확정·취소 | 위 2개 |

**구현 순서:** uuid-primary-key → schedule-unified(#11) → calendar resolve(#17) → trip-room-api(#12) → trip-recommendation(#13)

## GitHub 이슈 매핑 (wave 2)

| 이슈 | 스펙 | 상태 |
|------|------|------|
| #11 | schedule-unified | Closed |
| #17 | schedule-calendar-resolve (본인 calendar) | Closed |
| #12 | trip-room-api | Open |
| #13 | trip-recommendation | Open |
| #19 | join 미리보기 (Out) | Open |
| #20 | 참여자 내보내기 (Out) | Open |
| **#26** | trip-last-activity-at | Open |
| **#27** | trip-home-schedulers | Open |
| **#22** | schedule-participation-onboarding (Draft — **2026-07-19 부분 확정**: 이름·재진입·join 게이트) | Open |

## 완료 후

- 스펙의 완료 기준 체크
- API·스키마 변경이 있으면 `docs/architecture/erd.md` 동기화 검토
