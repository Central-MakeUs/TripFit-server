# 감사 기록 (`docs/audits/`)

새 기능 스펙(`docs/specs/`)이 "앞으로 만들 것"이라면, 이 폴더는 **이미 있는 것을 점검한 결과와 그 반영 이력**이다. 두 종류가 있다.

| 종류 | 대상 | 위치 | 만드는 주체 |
|------|------|------|-------------|
| **코드 감사** | 도메인별 구현 코드 — API 계약·비즈니스 로직은 바꾸지 않고 내부 품질만 개선 | `docs/audits/{domain}/` | [`refactor-audit`](../../.claude/skills/refactor-audit/SKILL.md) 스킬 (B 트랙) |
| **문서 품질 감사** | 저장소 전체 마크다운 문서 — 유형·구조·문장·용어 | [`doc-writing-audit.md`](doc-writing-audit.md) (이 폴더 루트) | G3 문서 품질 게이트 · [`doc-reviewer`](../../.claude/agents/doc-reviewer.md) |

문서 품질 감사는 도메인 축에 속하지 않으므로 **도메인 폴더 안이 아니라 루트에 둔다.** `{domain}/` 하위는 `refactor-audit` 산출물 전용으로 유지해, 폴더만 보고 무엇이 들어 있는지 예측할 수 있게 한다.

`auth` 도메인 라운드는 이 스킬을 처음으로 전체 사이클(감사→승인→구현→검증) 적용한 사례로 [`docs/harness-engineering.md`](../harness-engineering.md) §4에서 케이스 스터디로 다룬다.

**폴더 = 도메인** — `docs/specs/`와 동일 축(`auth`, `user`, `user-schedule`, `trip`, `notification`, `cross-cutting`).

## 도메인 폴더당 파일 (코드 감사)

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

## 운영 방식 (2026-08-27 기준)

로컬 실험 단계로 시작했고, 6개 도메인 모두 3차 라운드까지 완료된 지금도 여전히 GitHub 이슈 트래킹 없이 진행하고 있다 — `refactor-audit` 감사·구현은 각 단계 승인 게이트로 대신하며 별도 이슈 번호를 붙이지 않는다. 실제로 PR을 올릴 단계가 되면 `CONTRIBUTING.md`의 이슈 번호 기반 브랜치 규칙이 다시 필요하다.
