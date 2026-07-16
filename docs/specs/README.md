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
| [`schedule-participation-onboarding.md`](schedule-participation-onboarding.md) | **`[미定]`** (#22) | submit · onboarding skip · sparse · BR-USER-006/007 재설계 | user-onboarding · schedule-unified |

## wave 2

| 스펙 | 상태 | 범위 | 선행 |
|------|------|------|------|
| [`schedule-unified.md`](schedule-unified.md) | **Approved** (#11) | 정기(`regular_schedule`)·개별(`personal_schedule`) 2테이블 | wave 1 auth·onboarding |
| [`schedule-calendar-resolve.md`](schedule-calendar-resolve.md) | **Implemented** (#17) · S1·R2=A·A1=730일 | regular+personal → 날짜별 effective 달력 조회 | schedule-unified (#11) |
| [`trip-room-api.md`](trip-room-api.md) | **Approved** (#12) · submit·D1 → **#22 deferred** | 여행방 CRUD·참여·Pin | schedule-unified · #17 · **#22** |
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
| **#22** | schedule-participation-onboarding (`[미定]`) | Open |

## 완료 후

- 스펙의 완료 기준 체크
- API·스키마 변경이 있으면 `docs/architecture/erd.md` 동기화 검토
