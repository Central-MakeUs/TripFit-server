# Workflow Tools × TripFit

이 프로젝트는 **Claude Code를 기본 개발 환경**으로 쓴다. 워크플로 도구(계획·검증·리뷰·병렬 작업 등)를 고르거나 문서를 개선할 때는 아래 우선순위를 **반드시** 따른다.

## 도구 우선순위

```
1. Claude Code 기본 기능
2. OMC (Oh My Claude Code)
3. Superpowers
4. 프로젝트 문서 (.claude/rules/, .claude/skills/)
```

상위 단계로 필요를 충분히 채우면 하위 단계로 내려가지 않는다. OMC·Superpowers는 **서드파티 플러그인**이며 이 저장소에는 설치돼 있지 않다 — 실제로 쓰려면 `/plugin marketplace add`로 먼저 설치해야 하고, 이는 새 의존성을 들이는 결정이므로 사용자 승인 없이 임의로 설치하지 않는다. ("OMC"라는 이름은 서로 무관한 여러 서드파티 프로젝트가 공유하고 있어 공식으로 통용되는 단일 표준이 없다 — 특정 OMC를 지목하지 않는 한 실질적으로 "해당 없음"으로 취급한다.)

### OMC·Superpowers를 채택하는 경우 (전부 만족해야 함)

- 문서를 더 단순하게 만들 수 있다
- 반복 작업을 제거할 수 있다
- 유지보수성을 높인다
- 토큰 사용량 또는 작업량이 감소한다

### 채택하지 않는 경우 (하나라도 해당하면 배제)

- 기능이 있다는 이유만으로 쓴다
- 기존 문서보다 복잡해진다
- 토큰 소비만 증가한다
- Claude Code 기본 기능으로 충분히 해결 가능하다

**이 저장소의 현재 결론 (2026-07-23 감사):** Superpowers가 제공하는 `brainstorming`/`writing-plans`/`executing-plans`는 Plan Mode로, `requesting-code-review`는 이미 설치된 `code-review`/`simplify` 스킬로, `dispatching-parallel-agents`는 `Agent` 툴로 이미 충분히 대체돼 있어 위 배제 조건("Claude Code 기본 기능으로 충분히 해결 가능")에 해당 — 설치하지 않는다. 유일한 gap인 `systematic-debugging`도 아래 3줄 프로즈 절차로 충분해 플러그인 설치 비용을 정당화하지 못한다. `test-driven-development`는 오히려 이 저장소의 "유의미한 테스트만" 원칙(`testing.md`)보다 엄격해 **기존 문서보다 복잡해지는** 배제 조건에 해당한다. 상황이 바뀌면(예: 이 gap이 실제로 반복 비용을 유발하면) 이 절을 갱신하고 재평가한다.

Harness 형제: `harness-wave.md` (Wave·`[미정]`) · `harness-follow-up.md` (후속·Defer·ERD)

## 진입 (매 턴)

1. **문서·스펙·decisions 확인** — 작업 시작 전 `harness-workflow.md` Before Coding 순서 따름
2. **`harness-workflow.md` ⛔** — 문서·스펙·decisions와 충돌 시 다른 어떤 관례보다 **우선**. 구현 중단·사용자 질문

## 작업 유형 → 도구

| 유형 | Claude Code | TripFit |
|------|-------------|---------|
| 새 기능·API·wave | `EnterPlanMode`(Plan Mode)로 탐색·설계 → 승인 | **`specify`** 스킬 → `docs/specs/` → **Approved 후** 구현 |
| 기존 코드 아키텍처 감사·무손실 리팩토링 | `Agent`(`Explore`/`general-purpose`, 읽기 전용)로 신선한 컨텍스트 감사 | **`refactor-audit`** 스킬 → `docs/audits/{domain}/` → 도메인 1개씩, 감사·구현 각각 승인 후 진행 |
| Approved 스펙 구현 (#12 등) | Plan Mode 없이 단계별 직접 구현 | 스펙·GitHub 이슈 완료 기준 |
| 버그·테스트 실패 | 재현 → 원인 분리 → 수정 (전용 스킬 없음, 아래 절차 참고) | `./gradlew test`로 재현 |
| “완료/통과” 선언 전 | **`verify`** 스킬 | `./gradlew test` + 이슈·스펙 체크리스트 + API 변경 시 `oasdiff` |
| Must Have급 구현 완료 후 (또는 사용자 요청) | — | `harness-follow-up.md` 💡 후속 제안 |
| 「다른 이슈로」범위 미루기 | **`defer-followup`** 스킬 | 이슈 생성은 `harness-workflow.md` "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절 적용 |
| PR·머지 전(요청 시) · **API·DB 등 Must Have급 변경은 커밋 전 기본 권장** | `code-review` / `simplify` 스킬 — 둘 다 fresh 서브에이전트 컨텍스트에서 diff만 보고 판단 | `.github/CONTRIBUTING.md` |
| 독립 작업 병렬 | `Agent` 툴 서브에이전트 (`Explore`, `general-purpose`) | 중복 시 하나만 사용 |

**왜 서브에이전트 컨텍스트인가:** 방금 짠 코드를 같은 대화에서 스스로 리뷰하면 자기 판단을 재확인하는 편향이 생기기 쉽다(생성과 평가를 같은 컨텍스트에서 하면 self-grading 편향). `code-review`/`simplify`는 별도 컨텍스트에서 diff·기준만 보고 판단하므로 이 편향을 줄인다 — 한 줄·단일 파일 핫픽스까지 매번 강제하진 않되, API·DB·다파일 변경처럼 되돌리기 비싼 작업은 커밋 전에 기본으로 돌린다.

## 버그·테스트 실패 절차 (systematic-debugging 대체)

전용 스킬은 없으므로 아래 순서를 직접 따른다:

1. 실패를 `./gradlew test` 또는 재현 스텝으로 고정 — 추측으로 고치지 않는다
2. 로그·스택트레이스에서 원인을 좁힌다 (필요 시 `Explore` 서브에이전트로 관련 코드 탐색)
3. 최소 수정 → 같은 테스트로 재현 확인 → 회귀 확인을 위해 전체 `./gradlew test`

**프로덕션에서만 재현되는 버그(로컬 코드 검토로 원인이 안 잡힘, 클라이언트가 보는 증상이 실제 서버 동작과 다를 수 있음):** 추측으로 결론 내리지 말고 실제 EC2 로그·DB를 먼저 본다.

- SSH 접속 정보를 에이전트 메모리(reference 타입)에서 먼저 확인 — 있으면 사용자에게 다시 묻지 않고 바로 접속해 `docker logs`/DB 조회로 원인을 좁힌다. 없으면 사용자에게 요청
- **EC2 A(App+Nginx)·B(MySQL)·C(Monitoring: Loki+Grafana) 전부 직접 접근 가능** — 셋 중 필요한 인스턴스에 바로 SSH해 조사한다. 실제 IP·키 경로는 에이전트 메모리(`ec2-ssh-access`)에서 확인
- **AWS CLI는 항상 `--profile tripfit`으로 실행** — 프로필 생략 시 무관한 개인 기본 계정으로 빠진다 (TripFit 인프라 계정이 아님). 상세: 에이전트 메모리(`aws-cli-profile`)
- `dev` 프로필의 `POST /api/v1/auth/dev-login`으로 즉시 유효 토큰을 발급받아 실제 엔드포인트를 직접 호출해본다 — 클라이언트 재현을 기다리지 않는다
- **읽기(로그 조회·DB SELECT)는 바로 진행**하되, 원인이 애플리케이션 코드면 이 레포에서 고쳐 정상 워크플로(PR·CI/CD)로 배포 — 운영 서버에 직접 手patch·설정 변경은 하지 않음
- 이 정책 자체는 커밋되지만 실제 IP·키 경로 등 민감한 접속 정보는 여기 적지 않는다 — 에이전트 메모리에만 보관

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
