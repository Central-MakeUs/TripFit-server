---
paths:
  - "docs/product/fe-context/**"
---

# FE Context (`docs/product/fe-context/`)

`docs/product/fe-context/`는 스펙(`docs/specs/`)·구현(코드)을 프론트가 그대로 쓸 "구현 규칙"으로 번역한 문서다. SSOT는 여전히 스펙·코드다 — 이 폴더는 파생물이므로 **원본이 바뀌면 같이 바뀌어야 한다.** `harness-workflow.md` STOP §1(문서·구현 정합)과 방향이 반대라는 점에 주의: 스펙은 "코드가 스펙과 다르면 질문"이지만, fe-context는 "코드·스펙이 이미 SSOT로 확정됐다면 fe-context를 조용히 최신화해도 된다"(승인 게이트 없음, 번역 대상만 갱신).

## 도메인별 폴더 구조 (2026-07-30 도입)

`spring-boot-java.md` Package Layout과 동일하게 도메인별로 나눈다 — 새 파일을 루트에 평평하게 추가하지 않는다.

| 폴더 | 대응 패키지 | 담을 문서 |
|------|-------------|-----------|
| `user/` | `user`, `user.googlecalendar` | 온보딩, 프로필, Google Calendar 연동 |
| `user-schedule/` | `user.schedule` | 정기·개별 일정, 병합 달력 |
| `trip/` | `trip` | 방 생성·참여·나가기·activate |

새 문서 추가 시 어느 도메인 패키지를 다루는지 먼저 정하고 그 폴더에 넣는다. 두 도메인에 걸치면(예: activate가 trip 상태 전이 + schedule 데이터를 같이 참조) **주로 바뀌는 상태가 속한 도메인** 기준으로 정한다 — `trip-owner-activate-api.md`가 `trip/`에 있는 이유(상태 전이가 주제, 일정 CRUD는 참조만).

## 드리프트 체크리스트 — 이름은 안 바뀌어도 같은 턴에 확인

이름 변경(rename)은 `spring-boot-java.md` 네이밍 우선 원칙에서 이미 다룬다. 아래는 **이름은 그대로인데 계약이 바뀌는** 경우 — 2026-07-30에 실제로 이 유형의 drift가 발견됐다(`UserSummaryResponse`에 `notificationEnabled` 필드가 추가됐는데 `user-onboarding.md`의 예시 JSON·필드 표에는 반영 안 됨).

| 변경 | 확인할 fe-context 문서 |
|------|------------------------|
| 공용 응답 DTO(`UserSummaryResponse` 등)에 필드 추가·삭제 | 그 DTO가 예시 JSON·필드 표로 등장하는 **모든** 문서 — 한 도메인 폴더에 국한하지 말고 grep |
| 새 `ErrorCode` 상수·HTTP status 변경 | 해당 도메인 문서의 에러 표 |
| 요청 DTO(`slots`/`items` 등)의 필수·선택 여부 변경 | `user-schedule/schedule-calendar-merge.md`, `schedule-personal-override-scenarios.md` |
| 신규 API 추가(예: `PATCH /users/profile`) | 관련 기존 문서에 "이 값 변경은 이 API로만 가능" 언급 추가 |

코드를 직접 다시 확인하지 않은 채 "아마 이럴 것"으로 fe-context를 고치지 않는다 — 고칠 근거(파일:줄)를 확인한 뒤에만 갱신한다.

## 상호 링크

fe-context 문서끼리, 그리고 `docs/specs/*.md` → fe-context로 가는 마크다운 링크는 **상대 경로**를 쓴다. 파일을 옮기거나 이름을 바꾸면:

1. `grep -rn "fe-context/{옛파일명}" docs/ .claude/`로 마크다운 링크(`](...)`)와 평문 언급을 모두 찾는다
2. 마크다운 링크만 새 상대 경로로 갱신한다 — 평문 백틱 언급(예: `` `user-onboarding.md` 규칙 2``)은 파일명이 안 바뀌면 그대로 둬도 된다
3. `.claude/worktrees/`(별도 작업 트리)는 건드리지 않는다 — 현재 작업 트리의 `docs/`·`.claude/`만 대상
