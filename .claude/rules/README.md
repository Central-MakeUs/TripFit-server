---
paths:
  - ".claude/**"
---

# TripFit `.claude/rules` — AI 에이전트 규칙

Claude Code가 이 저장소에서 작업할 때 참조하는 **프로젝트 전용 AI 설정**입니다.
루트의 [`CLAUDE.md`](../../CLAUDE.md)(`@AGENTS.md` import)는 전체 프로젝트 지도, `.claude/`는 **에이전트 행동·워크플로·안전장치**를 담습니다.

이 폴더가 정의하는 "무엇을"의 서술형 총정리(왜 이렇게 됐는지, 실제 인시던트 이력, 수치)는 [`docs/harness-engineering.md`](../../docs/harness-engineering.md) 참고 — 이 README는 구조 인덱스, 그 문서는 발표·질의 대비용 내러티브다.

## 디렉터리 구조

```
.claude/
├── settings.json          ← PreToolUse 훅 등록 (버전 관리)
├── settings.local.json    ← 개인 권한 allowlist (버전 관리)
├── hooks/
│   ├── block-dangerous.sh
│   ├── block-db-migration.sh
│   ├── warn-breaking-change.sh
│   └── format-java.sh
├── agents/                ← 서브에이전트 정의 (파일 생성 시 즉시 등록)
│   ├── researcher.md              # G1 외부 문서 조사 전용 (Edit/Write 없음)
│   ├── doc-reviewer.md            # G3 문서 품질 리뷰 전용 (Edit/Write 없음)
│   └── senior-spring-backend-reviewer.md  # G3 Java 변경 리뷰 전용 (Edit/Write 없음)
├── rules/                 ← 상황별 AI 규칙 (.md + paths frontmatter)
│   ├── README.md                  ← 이 파일 (구조·사용법)
│   ├── harness-workflow.md        # ⛔ STOP · 3 트랙 × 4 게이트 (코어, always-load)
│   ├── harness-milestone.md       # 릴리즈 Milestone·priority(must/could)·Release Gate·[미정] 처리·일정 용어 (always-load)
│   ├── harness-follow-up.md       # 후속 제안 · Defer · ERD 제안 (always-load)
│   ├── workflow-tools.md          # Claude Code 도구 매핑 (always-load)
│   ├── plain-language-reporting.md # 비전공자용 쉬운 설명 (보고·채팅만, 코드 주석 제외, always-load)
│   ├── spring-boot-java.md
│   ├── openapi-conventions.md
│   ├── java-comments.md
│   ├── client-platform.md
│   ├── deployment.md
│   ├── testing.md
│   ├── doc-writing.md             # 문서 작성 (docs/·.claude/ 마크다운, path-scoped)
└── skills/                ← 반복 워크플로 스킬
    ├── specify/
    │   ├── SKILL.md
    │   └── references/
    │       └── spec-template.md
    ├── refactor-audit/
    │   ├── SKILL.md
    │   └── references/
    │       ├── audit-template.md
    │       └── audit-checklist.md
    ├── verify/
    │   └── SKILL.md
    ├── defer-followup/
    │   └── SKILL.md
    ├── debug-bug/
    │   └── SKILL.md
    └── retro/
        └── SKILL.md
```

## 파일별 역할

| 경로 | 역할 | 적용 시점 |
|------|------|-----------|
| `settings.json` | Bash 실행 전 등 **이벤트 → 훅 스크립트** 매핑 | 에이전트가 도구를 호출하기 직전 |
| `hooks/*.sh` | 훅 본문 — 위험 명령 차단 등 | `settings.json`이 지정한 이벤트 |
| `rules/*.md` (frontmatter 없음) | **항상** 로드되는 코딩·도메인 규칙 | 세션 시작 시 |
| `rules/*.md` (`paths:` frontmatter) | **glob에 매칭되는 파일**을 읽을 때만 로드되는 규칙 | 해당 파일 접근 시 |
| `skills/*/SKILL.md` | 다단계 워크플로 (스펙 작성 등) | 에이전트가 해당 작업을 인식할 때 |
| `agents/*.md` | 서브에이전트 정의 (frontmatter의 `tools`·`model` + 본문=시스템 프롬프트) | **파일을 만들면 등록** — 첫 호출이 실패하면 잠시 뒤 재시도하거나 새 세션에서 확인 |

## Rules (`rules/`)

`.md` = Markdown + YAML frontmatter(`paths:`). `paths`가 없으면 세션 시작 시 항상 로드되고, 있으면 매칭 파일을 읽을 때만 로드된다 (Cursor `.mdc`의 `globs`/`alwaysApply`에 대응).

### Always-load (하네스)

| 파일 | 요약 | SSOT 범위 |
|------|------|-----------|
| `harness-workflow.md` | ⛔ 문서 정합 · ErrorCode/AOP · DB · **레거시(교체=같은 PR 삭제)** · **API Breaking-Change-Reason 트레일러** · **3 트랙 × 4 게이트 사이클** | **코어 STOP·코딩 흐름** |
| `harness-milestone.md` | priority(must/could) 단정 금지 · Release Gate · `[미정]` 문서 표기(중앙 트래커 폐지) · 희망기간/조회윈도우/A1 | 릴리즈·용어 |
| `harness-follow-up.md` | 💡 후속 제안 · ✅ Defer 이슈 분리 · 💡 ERD 적극 제안 | 완료 후·범위 미루기 |
| `workflow-tools.md` | **도구 우선순위**(Claude Code 기본 > OMC > Superpowers > 프로젝트 문서) · **트랙 × 게이트 → 도구** 매핑 | 워크플로 도구 연동·채택 판단 |
| `plain-language-reporting.md` | 사용자 보고(채팅·`refactor-log.md`·완료 요약)는 용어 풀어쓰기·비유 위주로 쉽게. **코드 `//` 주석은 대상 아님**(`spring-boot-java.md` Comments가 SSOT) | 사용자 대상 설명 vs 코드 주석 스타일 분리 |

우선순위: `harness-workflow` ⛔ > specify > workflow-tools > 일반 관례

### Path-scoped (`paths:` frontmatter)

| 파일 | `paths` | 요약 |
|------|---------|------|
| `spring-boot-java.md` | `**/*.java` | 레이어·enum·Entity·**ErrorCode·AOP**·**SOLID/OOP·ACID**·스타일·테스트 |
| `openapi-conventions.md` | `**/*.java` | `@Schema`·`@Operation`·`@Parameter`·`@ApiResponses`(FE용 섹션 템플릿·JWT) — 2026-08-27 `spring-boot-java.md`에서 분리 |
| `java-comments.md` | `**/*.java` | `//`·Javadoc 작성 스타일(역할 줄·다단계 Why·레이어별 초점) — 2026-08-27 `spring-boot-java.md`에서 분리 |
| `client-platform.md` | controller, service, config, specs | React 앱·스토어·API·인증 |
| `deployment.md` | yml, Docker, deploy | 배포 가드레일 — 절차는 `deploy/README.md` (MySQL 예약어·quoting은 `spring-boot-java.md`로 이동) |
| `testing.md` | `**/*Test.java`, `src/test/**` | JUnit 5·프로필·테스트 네이밍 |
| `doc-writing.md` | `docs/**/*.md`, `.claude/**/*.md` | 문서 유형(학습·문제해결·참조·설명) → 정보 구조(개요 필수·가치 먼저·제목) → 문장(한 문장 한 생각·메타 담화 제거·용어 일관). **채팅 보고는 `plain-language-reporting.md`, 코드 주석은 `java-comments.md`** — 독자가 달라 겹치지 않음 |
| `README.md`(이 파일) | `.claude/**` | 구조 인덱스 — 사람이 보는 디렉터리 맵이라 행동 규칙이 아님, 2026-08-11부터 `.claude/` 작업 시에만 로드 |

### 규칙 추가·분리 가이드

1. **한 규칙 = 한 관심사** (코어 하네스 ~120줄, 형제 ~70줄 권장)
2. 전역 STOP·코딩 흐름 → `harness-workflow` (frontmatter 없음, always-load)
3. Milestone·용어 → `harness-milestone` · 후속/Defer/ERD → `harness-follow-up` (**중복 금지**, 링크만)
4. 파일 타입별 → `paths:` frontmatter
5. 반복 실수 → 해당 규칙에 짧게 추가

## Skills

에이전트가 **특정 요청**을 받으면 스킬 파일을 읽고 단계를 따른다.

| 스킬 | 트리거 예시 | 산출물 |
|------|-------------|--------|
| `specify` | **A 트랙** — 새 기능, 리팩터 계획, 아키텍처 결정 | `docs/specs/{domain}/{feature}.md` (**스펙 SSOT**, 도메인 amend 시 `ADDED`/`MODIFIED`/`REMOVED` delta 섹션) |
| `refactor-audit` | **B 트랙** — 기존 코드 아키텍처 감사·무손실 리팩토링 (API 계약·비즈니스 로직 불변) | `docs/audits/{domain}/audit.md`(A/B/C/D 분류) · `refactor-log.md`(반영 이력) — 도메인 1개씩 순차, 매 단계 승인 게이트 |
| `debug-bug` | **C 트랙** — 버그 리포트·`./gradlew test` 실패 (로컬 재현 + 프로덕션 전용 재현) | 없음(재현·조사 절차) — Superpowers `systematic-debugging` 대체, 승인 게이트 없는 절차형 스킬 |
| `verify` | **G3 게이트** — "완료/통과" 선언 전, 특히 Must Have급·API·DB 변경 | 없음(검증 절차) — `./gradlew test` + 스펙·이슈 체크리스트 대조 + API 변경 시 `oasdiff` + 문서 50줄+ 변경 시 `doc-reviewer` |
| `defer-followup` | **G4 게이트** — 「다른 이슈로 빼」·「후속 이슈로」·「이번 Milestone 밖」 | Draft 스펙 + Approved 스펙 amend + `docs/specs/README.md` 갱신 + (확인 후) GitHub 이슈 |
| `retro` | **G4 게이트** — 작업 완료 후 회고, 「이번에 배운 거 정리」 | `docs/audits/harness-retro.md` append (승인 후) — 하네스 개선 후보만, 코드·설계 개선은 `harness-follow-up.md` 담당. **메인 컨텍스트 실행**(세션 대화 이력이 입력이라 fork 금지) |

**워크플로:** `트랙 분류 → G1 리서치 → G2 승인 → 구현 → G3 검증 → G4 회고 → gh issue/PR`

## Agents (`agents/`)

트랙과 무관하게 **게이트에서 호출하는 조사·리뷰 전용 서브에이전트**다. 셋 다 `tools` 화이트리스트에서 `Edit`/`Write`를 뺐고, 별도 컨텍스트에서 실행돼 메인 대화의 토큰을 아낀다.

⚠️ **도구 목록이 쓰기를 완전히 막지는 못한다.** 셋 다 `Bash`를 갖고 있어 `sed -i`·리다이렉션으로 파일을 고칠 수 있고, `format-java.sh` 훅은 `Edit|Write` 매처라 Bash 경유 수정을 잡지 못한다. 마지막 한 겹은 각 에이전트 지침의 "수정하지 않는다" 규범이다 — 이 층을 결정론적 차단으로 오해하지 않는다 (2026-09-04 `senior-spring-backend-reviewer` 스모크 테스트에서 발견).

| 에이전트 | 게이트 | 역할 |
|----------|--------|------|
| `researcher` | G1 | 외부 라이브러리·SDK·provider 문서 조사. **로컬 `build.gradle` 버전 확인 → 공식 문서(버전 고정) → 릴리즈 노트** 순서 강제, 블로그·StackOverflow 근거 인용 금지. 결론·근거 URL·문서 버전만 고정 포맷으로 반환 |
| `doc-reviewer` | G3 | 문서 유형·정보 구조·문장 3단계 리뷰 (기준: `doc-writing.md`). advisory — 커밋을 막지 않음 |
| `senior-spring-backend-reviewer` | G3 | Java 변경 diff를 트랜잭션 경계·N+1·하네스 계약(ErrorCode·`@TripActivity`·트레일러)·레이어 재사용·캡슐화 5축으로 리뷰 (기준: `spring-boot-java.md`). Critical/High/Medium/Low 4등급 + `파일:줄` 강제. **ArchUnit이 이미 검증하는 규칙은 지적 대상에서 제외** |

**호출 기준:** 2개 이상 문서를 비교해야 하면 `researcher`, 단일 페이지면 인라인 `WebFetch`. 새 문서·50줄+ 문서 변경이면 `doc-reviewer`, 오타 수정이면 생략. Java를 3파일 이상·API·DB 범위로 고쳤으면 커밋 전 `senior-spring-backend-reviewer` — 범용 `code-review` 스킬과 **대체 관계가 아니다**(그쪽은 언어 무관 일반 결함, 이쪽은 이 저장소의 Spring·JPA·하네스 계약).

상세: `.claude/rules/workflow-tools.md`

템플릿·참고 문서는 `skills/{name}/references/`에 둔다.

## Hooks (`settings.json` + `hooks/`)

| 이벤트 | 매처 | 현재 동작 |
|--------|------|-----------|
| `PreToolUse` | `Bash` | `block-dangerous.sh` — force push, `rm -rf`, `git reset --hard`, `docker compose down -v` 차단(exit 2, fail-closed) |
| `PreToolUse` | `Bash` | `warn-breaking-change.sh` — `git commit`에 DTO/ErrorCode/Controller 변경이 스테이징됐는데 `Breaking-Change-Reason:` 트레일러가 없으면 advisory 경고(항상 exit 0, 커밋을 막지 않음) |
| `PreToolUse` | `Write\|Edit` | `block-db-migration.sh` — `db/migration/` 경로 또는 Flyway 네이밍(`V1__x.sql`, `R__x.sql`) 파일 생성 차단(exit 2, fail-closed) — `harness-workflow.md` STOP §3 |
| `PostToolUse` | `Edit\|Write` | `format-java.sh` — Java 파일 저장 시 `spotlessApply` 자동 포맷(non-blocking) |

**agent-type 훅 관련 교훈:** `warn-breaking-change.sh`는 처음엔 `agent`-type(서브에이전트가 diff를 읽고 판단)으로 시도했으나, staged 아닌 working tree 변경까지 오판해 "절대 막지 마라"는 명시적 지시에도 커밋을 막는 사고가 있었다 — non-blocking을 LLM 판단에 맡기지 않고 `command`-type(exit code로 결정론적 통제)으로 확정했다. advisory-only 훅은 command-type을 기본으로 한다.

## `settings.json` / `settings.local.json`

`settings.json`은 훅 등 팀 공통 설정으로 버전 관리한다. `settings.local.json`은 개인 권한 allowlist — **전역 `~/.config/git/ignore`로 gitignore돼 있어 실제로는 이 머신에만 있고 커밋되지 않는다**(2026-08-27 확인). 팀원 간 공유되지 않으므로 개인별로 자유롭게 정리해도 된다.

## CLAUDE.md / AGENTS.md와의 관계

```
CLAUDE.md          → "@AGENTS.md" import + Claude Code 전용 보충
AGENTS.md          → 무엇을, 어디서 찾는지 (프로젝트 지도)
docs/README.md     → 기획·아키텍처·스펙 문서 인덱스
deploy/README.md   → Docker·EC2 배포
.dev/README.md     → 임시 세션 로그 (장기 문서는 docs/로)
.claude/rules/      → 어떻게 코딩·배포·검증하는지 (행동 규칙)
  harness-workflow / harness-milestone / harness-follow-up / workflow-tools
.claude/skills/    → 큰 작업의 단계별 절차 (specify = 스펙 SSOT)
docs/specs/        → 기능별 설계 산출물 (specify 스킬 결과)
```

## 유지보수 체크리스트

- [ ] 클라이언트·스토어 전제 변경 시 `docs/product/platform.md` + `client-platform.md` 동기화
- [ ] 새 도메인 enum·상태 추가 시 `docs/product/glossary.md` 동기화
- [ ] ddl-auto·프로필 변경 시 `docs/architecture.md` + `deployment.md` 동기화
- [ ] Milestone/`[미정]`/용어 규칙 변경 → `harness-milestone.md`만 (workflow에 중복 금지)
- [ ] 후속·Defer·ERD 제안 규칙 변경 → `harness-follow-up.md`만
- [ ] 반복되는 코드 리뷰 코멘트 → 해당 `rules/*.md`에 한 줄 규칙으로 승격
- [ ] 위험 명령 패턴 추가 필요 시 `hooks/block-dangerous.sh` + `settings.json` matcher 동시 수정
- [ ] 훅 추가·삭제 시 이 README **디렉터리 구조 다이어그램 + Hooks 절 표**, [`docs/harness-engineering.md`](../../docs/harness-engineering.md) §5 표, [`docs/harness/layer3-deterministic-hooks.md`](../../docs/harness/layer3-deterministic-hooks.md)의 훅 표·흐름까지 **4곳** 동시 갱신 (이번 감사에서 이 README 자체의 디렉터리 다이어그램이 훅 1개 누락된 채 방치됐던 사례 있음 — 같은 파일 안에서도 표와 다이어그램이 따로 놀 수 있으니 둘 다 확인)
- [ ] 규칙·스킬 개수가 바뀌면(추가/삭제) 이 README **Always-load 표 + Skills 표**, [`docs/harness-engineering.md`](../../docs/harness-engineering.md) §3·§4의 대응 표, [`docs/harness/layer1-human-gate.md`](../../docs/harness/layer1-human-gate.md)(규칙)·[`docs/harness/layer2-workflow-skills.md`](../../docs/harness/layer2-workflow-skills.md)(스킬)의 파일 표를 함께 갱신 (harness-engineering.md·docs/harness/는 발표·질의 대비용 서술형 문서라 별도 유지되며, 내용이 어긋나면 이 README가 맞음 — 하지만 방치하면 발표 자료가 stale해짐)
- [ ] 서브에이전트(`agents/*.md`) 추가·삭제 시 이 README **디렉터리 구조 다이어그램 + Agents 절 표**, [`docs/harness-engineering.md`](../../docs/harness-engineering.md) §4, [`docs/harness/layer2-workflow-skills.md`](../../docs/harness/layer2-workflow-skills.md) 서브에이전트 문단을 함께 갱신. **에이전트는 파일을 만들면 같은 세션에서 즉시 등록**된다(2026-09-03 실측 정정 — 이전에는 "세션 시작 시 등록"으로 잘못 기재돼 있었음) — 첫 호출이 드물게 실패하면 잠시 뒤 재시도하거나 새 세션에서 확인
- [ ] 레이어·PK 등 구조 규칙 변경 시 `src/test/java/com/tripfit/tripfit/architecture/ArchitectureTest.java`(ArchUnit) 반영 검토 — 일부 규칙은 prose가 아니라 `./gradlew test`가 실제로 검증함
