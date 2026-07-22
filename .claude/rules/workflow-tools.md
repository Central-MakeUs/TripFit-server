# Workflow Tools × TripFit

Cursor 시절 Superpowers 플러그인 스킬(`brainstorming`, `systematic-debugging` 등)을 쓰던 자리를 Claude Code 네이티브 기능으로 대체한 매핑이다.

## 우선순위

```
harness-workflow ⛔ (문서·스펙 정합)  >  TripFit specify  >  Claude Code 워크플로 도구  >  일반 코딩 관례
```

Harness 형제: `harness-wave.md` (Wave·`[미정]`) · `harness-follow-up.md` (후속·Defer·ERD)

## 진입 (매 턴)

1. **문서·스펙·decisions 확인** — 작업 시작 전 `harness-workflow.md` Before Coding 순서 따름
2. **`harness-workflow.md` ⛔** — 문서·스펙·decisions와 충돌 시 다른 어떤 관례보다 **우선**. 구현 중단·사용자 질문

## 작업 유형 → 도구

| 유형 | Claude Code | TripFit |
|------|-------------|---------|
| 새 기능·API·wave | `EnterPlanMode`(Plan Mode)로 탐색·설계 → 승인 | **`specify`** 스킬 → `docs/specs/` → **Approved 후** 구현 |
| Approved 스펙 구현 (#12 등) | Plan Mode 없이 단계별 직접 구현 | 스펙·GitHub 이슈 완료 기준 |
| 버그·테스트 실패 | 재현 → 원인 분리 → 수정 (전용 스킬 없음, 아래 절차 참고) | `./gradlew test`로 재현 |
| “완료/통과” 선언 전 | `verify` 스킬 | `./gradlew test` + 이슈·스펙 체크리스트 |
| Must Have급 구현 완료 후 (또는 사용자 요청) | — | `harness-follow-up.md` 💡 후속 제안 |
| 「다른 이슈로」범위 미루기 | — | `harness-follow-up.md` ✅ Defer |
| PR·머지 전 (요청 시) | `code-review` / `simplify` 스킬 | `.github/CONTRIBUTING.md` |
| 독립 작업 병렬 | `Agent` 툴 서브에이전트 (`Explore`, `general-purpose`) | 중복 시 하나만 사용 |

## 버그·테스트 실패 절차 (systematic-debugging 대체)

전용 스킬은 없으므로 아래 순서를 직접 따른다:

1. 실패를 `./gradlew test` 또는 재현 스텝으로 고정 — 추측으로 고치지 않는다
2. 로그·스택트레이스에서 원인을 좁힌다 (필요 시 `Explore` 서브에이전트로 관련 코드 탐색)
3. 최소 수정 → 같은 테스트로 재현 확인 → 회귀 확인을 위해 전체 `./gradlew test`

## TDD (test-driven-development 대체)

전용 스킬은 없다. **핵심 로직·스펙 Must**만 선행 테스트를 권장하고, 단순 DTO/와이어링은 “유의미한 테스트만” 원칙(`testing.md`)을 따른다.

## TripFit과 겹칠 때

- **스펙 SSOT:** API·DB·BR → `.claude/skills/specify/` (Plan Mode로 스펙 형식을 **대체 금지**)
- **Plan Mode vs specify:** 택1. 스펙 **Approved**면 Plan Mode 생략 → 바로 구현
- **서브에이전트:** 독립 조사·병렬 탐색은 `Agent` 툴 (`Explore`, `general-purpose`, 필요 시 `fork`)

## 금지

- Approved 스펙·decisions와 다른 값을 “더 나은 방법”으로 덮어쓰기
- `verify` 스킬 없이 “테스트 통과”·“이슈 완료” 주장
- wave·스펙·이슈 없이 대형 기능 구현 시작

## 워크플로 (한 줄)

```
wave 확인 → (Plan Mode) → specify/Approved → 구현 → ./gradlew test → verify → (후속 제안) → gh issue/PR
```
