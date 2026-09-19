# 아키텍처 감사 (`docs/audits/`)

`refactor-audit` 스킬([`.claude/skills/refactor-audit/SKILL.md`](../../.claude/skills/refactor-audit/SKILL.md))의 산출물. 새 기능 스펙(`docs/specs/`)과 달리, **기존 코드**를 API 계약·비즈니스 로직 변경 없이 리팩토링한 감사·이력 기록이다.

`auth` 도메인 라운드는 이 스킬을 처음으로 전체 사이클(감사→승인→구현→검증) 적용한 사례로 [`docs/harness-engineering.md`](../harness-engineering.md) §4에서 케이스 스터디로 다룬다.

**폴더 = 도메인** — `docs/specs/`와 동일 축(`auth`, `user`, `user-schedule`, `trip`, `notification`, `cross-cutting`).

## 폴더당 파일

| 파일 | 내용 |
|------|------|
| `audit.md` | 최신 감사 라운드 결과 — `audit-checklist.md` 기준 A(반드시 수정)/B(유지보수성)/C(참고)/D(현행 유지) 분류 |
| `refactor-log.md` | 실제 반영 이력 (append, Changelog 스타일) — 반영한 항목, 변경 파일·라인 수, 검증 결과(`./gradlew test`, oasdiff diff 0), 남겨둔 C/D와 이유 |

## 진행 현황

| 도메인 | 감사 | 구현 상태 |
|--------|------|-----------|
| `auth` | 완료 (2026-08-15, SOLID/OOP 3차 라운드까지) | 1차 A/B 9건 반영 + 2차 라운드 A-1/B-1 반영 + 3차(SOLID/OOP) A-1/B-1 반영, oasdiff diff 0(auth 관련) 확인 |
| `user` | 완료 (2026-08-26, SOLID/OOP 3차 라운드까지) | 1차 A/B 반영 + 2차 라운드 A-1/B-1 반영 + 3차(SOLID/OOP) A-1/B-1/B-2 반영, oasdiff diff 0(user 관련) 확인 |
| `user-schedule` | 완료 (2026-08-26, SOLID/OOP 3차 라운드까지) | 1차 A/B 반영 + 2차 라운드 A-1/B-1 반영 + 3차(SOLID/OOP) B-1~B-3 반영, oasdiff diff 0 확인 |
| `trip` | 완료 (2026-08-26, SOLID/OOP 3차 라운드까지) | 1차 A-1·A-2·B-1~B-3 반영 + 2차 라운드 B-1·B-2 반영 + 3차(SOLID/OOP) B-1/B-2 반영, oasdiff diff 0 확인 |
| `notification` | 완료 (2026-08-27, SOLID/OOP 3차 라운드까지) | 1차 A-1·B-1~B-3 반영 + 2차 라운드 A-1·A-2·B-1 반영 + 3차(SOLID/OOP) A/B 없음(코드 변경 없음), oasdiff diff 0 확인 |
| `cross-cutting` | 완료 (2026-08-27, SOLID/OOP 3차 라운드까지) | 1차 A-1~A-3·B-1·B-2 반영 + 3차(SOLID/OOP) B-1 반영, oasdiff diff 0 확인(기존 무관 drift 1건은 범위 밖으로 별도 기록) |

## 현재 단계 (2026-08-03)

로컬 실험 단계 — GitHub 이슈 트래킹 없이 진행 중. 실제 PR을 올릴 단계가 되면 `CONTRIBUTING.md`의 이슈 번호 기반 브랜치 규칙이 다시 필요하다.
