# FE Context (`docs/product/fe-context/`)

스펙(`docs/specs/`)·구현(코드)을 프론트가 그대로 쓸 "구현 규칙"으로 번역한 문서. SSOT는 여전히 스펙·코드다 — 여기는 파생물. 에이전트 행동 규칙(도메인 폴더 구조·드리프트 체크리스트·상호 링크 규칙): [`.claude/rules/fe-context.md`](../../../.claude/rules/fe-context.md).

**`product/flows/`와의 차이:** `flows/`는 제품 정책·엣지 케이스 시나리오(왜 이렇게 동작하는가), 여기는 프론트 개발자가 화면·API 연동을 구현할 때 그대로 따를 수 있는 명령형 규칙(무엇을 어떻게 호출하는가)이다. 같은 기능이라도 두 문서가 같은 사실을 각자 다시 서술하지 않도록, API 호출 순서·에러 매핑처럼 자주 바뀌는 세부는 한쪽만 갖고 다른 쪽은 링크한다.

## 폴더 (도메인별)

| 폴더 | 대응 패키지 | 파일 |
|------|-------------|------|
| `trip/` | `trip` | [`trip-room-create-join.md`](trip/trip-room-create-join.md) · [`trip-owner-activate-api.md`](trip/trip-owner-activate-api.md) · [`trip-recommendation-confirm-flow.md`](trip/trip-recommendation-confirm-flow.md) · [`trip-room-exit-policies.md`](trip/trip-room-exit-policies.md) |
| `user-schedule/` | `user.schedule` | [`schedule-calendar-merge.md`](user-schedule/schedule-calendar-merge.md) · [`schedule-personal-override-scenarios.md`](user-schedule/schedule-personal-override-scenarios.md) · [`vacation-policy.md`](user-schedule/vacation-policy.md) |
| `user/` | `user`, `user.googlecalendar` | [`user-onboarding.md`](user/user-onboarding.md) · [`google-calendar-merge.md`](user/google-calendar-merge.md) |

새 문서는 위 표의 대응 패키지 기준으로 해당 폴더에 추가한다 — 루트에 평평하게 두지 않는다.
