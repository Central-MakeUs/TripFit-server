# TripFit `.claude/rules` — AI 에이전트 규칙

Claude Code가 이 저장소에서 작업할 때 참조하는 **프로젝트 전용 AI 설정**입니다.  
루트의 [`CLAUDE.md`](../../CLAUDE.md)(`@AGENTS.md` import)는 전체 프로젝트 지도, `.claude/`는 **에이전트 행동·워크플로·안전장치**를 담습니다.

## 디렉터리 구조

```
.claude/
├── settings.json          ← PreToolUse 훅 등록 (버전 관리)
├── settings.local.json    ← 개인 권한 allowlist (버전 관리)
├── hooks/
│   └── block-dangerous.sh
├── rules/                 ← 상황별 AI 규칙 (.md + paths frontmatter)
│   ├── README.md                  ← 이 파일 (구조·사용법)
│   ├── harness-workflow.md        # ⛔ STOP · Before/While/After (코어, always-load)
│   ├── harness-wave.md            # Wave Must/Nice/Out · [미정]#2 · 일정 용어 (always-load)
│   ├── harness-follow-up.md       # 후속 제안 · Defer · ERD 제안 (always-load)
│   ├── workflow-tools.md          # Claude Code 도구 매핑 (always-load)
│   ├── spring-boot-java.md
│   ├── figma-product.md
│   ├── client-platform.md
│   ├── deployment.md
│   └── testing.md
└── skills/                ← 반복 워크플로 스킬
    └── specify/
        ├── SKILL.md
        └── references/
            └── spec-template.md
```

## 파일별 역할

| 경로 | 역할 | 적용 시점 |
|------|------|-----------|
| `settings.json` | Bash 실행 전 등 **이벤트 → 훅 스크립트** 매핑 | 에이전트가 도구를 호출하기 직전 |
| `hooks/*.sh` | 훅 본문 — 위험 명령 차단 등 | `settings.json`이 지정한 이벤트 |
| `rules/*.md` (frontmatter 없음) | **항상** 로드되는 코딩·도메인 규칙 | 세션 시작 시 |
| `rules/*.md` (`paths:` frontmatter) | **glob에 매칭되는 파일**을 읽을 때만 로드되는 규칙 | 해당 파일 접근 시 |
| `skills/*/SKILL.md` | 다단계 워크플로 (스펙 작성 등) | 에이전트가 해당 작업을 인식할 때 |

## Rules (`rules/`)

`.md` = Markdown + YAML frontmatter(`paths:`). `paths`가 없으면 세션 시작 시 항상 로드되고, 있으면 매칭 파일을 읽을 때만 로드된다 (Cursor `.mdc`의 `globs`/`alwaysApply`에 대응).

### Always-load (하네스)

| 파일 | 요약 | SSOT 범위 |
|------|------|-----------|
| `harness-workflow.md` | ⛔ 문서 정합 · ErrorCode/AOP · DB · **레거시(교체=같은 PR 삭제)** · Before/While/After | **코어 STOP·코딩 흐름** |
| `harness-wave.md` | Wave Must/Nice/Out 단정 금지 · `[미정]`→#2 · 희망기간/조회윈도우/A1 | Wave·용어 |
| `harness-follow-up.md` | 💡 후속 제안 · ✅ Defer 이슈 분리 · 💡 ERD 적극 제안 | 완료 후·범위 미루기 |
| `workflow-tools.md` | **도구 우선순위**(Claude Code 기본 > OMC > Superpowers > 프로젝트 문서) · Claude Code 도구 매핑 | 워크플로 도구 연동·채택 판단 |

우선순위: `harness-workflow` ⛔ > specify > workflow-tools > 일반 관례

### Path-scoped (`paths:` frontmatter)

| 파일 | `paths` | 요약 |
|------|---------|------|
| `spring-boot-java.md` | `**/*.java` | 레이어·enum·Entity·**ErrorCode·AOP**·OpenAPI(FE용 섹션 템플릿·JWT)·주석 |
| `figma-product.md` | domain, service, specs | 도메인·BR·와이어프레임 |
| `client-platform.md` | controller, service, config, specs | React 앱·스토어·API·인증 |
| `deployment.md` | yml, Docker, domain, deploy | 배포 가드레일 — 절차는 `deploy/README.md` |
| `testing.md` | `**/*Test.java`, `src/test/**` | JUnit 5·프로필·테스트 네이밍 |

### 규칙 추가·분리 가이드

1. **한 규칙 = 한 관심사** (코어 하네스 ~120줄, 형제 ~70줄 권장)
2. 전역 STOP·코딩 흐름 → `harness-workflow` (frontmatter 없음, always-load)
3. Wave/용어 → `harness-wave` · 후속/Defer/ERD → `harness-follow-up` (**중복 금지**, 링크만)
4. 파일 타입별 → `paths:` frontmatter
5. 반복 실수 → 해당 규칙에 짧게 추가

## Skills

에이전트가 **특정 요청**을 받으면 스킬 파일을 읽고 단계를 따른다.

| 스킬 | 트리거 예시 | 산출물 |
|------|-------------|--------|
| `specify` | 새 기능, 리팩터 계획, 아키텍처 결정 | `docs/specs/{feature}.md` (**스펙 SSOT**) |

**워크플로:** `wave 확인 → (Plan Mode) → specify/Approved → 구현 → ./gradlew test → verify → (후속 제안) → gh issue/PR`

상세: `.claude/rules/workflow-tools.md`

템플릿·참고 문서는 `skills/{name}/references/`에 둔다.

## Hooks (`settings.json` + `hooks/`)

| 이벤트 | 현재 동작 |
|--------|-----------|
| `PreToolUse` (matcher: `Bash`) | `block-dangerous.sh` — force push, `rm -rf`, `git reset --hard`, `docker compose down -v` 차단 |

exit code 2 반환 시 차단(fail-closed 동작).

## `settings.json` / `settings.local.json`

`settings.json`은 훅 등 팀 공통 설정으로 버전 관리한다. `settings.local.json`은 개인 권한 allowlist — 버전 관리하되 개인별 조정 가능.

## CLAUDE.md / AGENTS.md와의 관계

```
CLAUDE.md          → "@AGENTS.md" import + Claude Code 전용 보충
AGENTS.md          → 무엇을, 어디서 찾는지 (프로젝트 지도)
docs/README.md     → 기획·아키텍처·스펙 문서 인덱스
deploy/README.md   → Docker·EC2 배포
.dev/README.md     → 임시 세션 로그 (장기 문서는 docs/로)
.claude/rules/      → 어떻게 코딩·배포·검증하는지 (행동 규칙)
  harness-workflow / harness-wave / harness-follow-up / workflow-tools
.claude/skills/    → 큰 작업의 단계별 절차 (specify = 스펙 SSOT)
docs/specs/        → 기능별 설계 산출물 (specify 스킬 결과)
```

## 유지보수 체크리스트

- [ ] 클라이언트·스토어 전제 변경 시 `docs/product/platform.md` + `client-platform.md` 동기화
- [ ] 새 도메인 enum·상태 추가 시 `figma-product.md` 또는 glossary 동기화
- [ ] ddl-auto·프로필 변경 시 `docs/architecture.md` + `deployment.md` 동기화
- [ ] Wave/`[미정]`/용어 규칙 변경 → `harness-wave.md`만 (workflow에 중복 금지)
- [ ] 후속·Defer·ERD 제안 규칙 변경 → `harness-follow-up.md`만
- [ ] 반복되는 코드 리뷰 코멘트 → 해당 `rules/*.md`에 한 줄 규칙으로 승격
- [ ] 위험 명령 패턴 추가 필요 시 `hooks/block-dangerous.sh` + `settings.json` matcher 동시 수정
