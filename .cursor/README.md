# TripFit `.cursor` — AI 에이전트 설정

Cursor가 이 저장소에서 작업할 때 참조하는 **프로젝트 전용 AI 설정**입니다.  
루트의 [`AGENTS.md`](../AGENTS.md)는 전체 프로젝트 지도, `.cursor/`는 **에이전트 행동·워크플로·안전장치**를 담습니다.

## 디렉터리 구조

```
.cursor/
├── README.md              ← 이 파일 (구조·사용법)
├── settings.json          ← Cursor 플러그인 설정 (프로젝트 스코프)
├── hooks.json             ← 에이전트 이벤트 훅 등록
├── hooks/                 ← 훅 실행 스크립트
│   └── block-dangerous.sh
├── rules/                 ← 상황별 AI 규칙 (.mdc)
│   ├── harness-workflow.mdc      # ⛔ STOP · Before/While/After (코어)
│   ├── harness-wave.mdc          # Wave Must/Nice/Out · [미정]#2 · 일정 용어
│   ├── harness-follow-up.mdc     # 후속 제안 · Defer · ERD 제안
│   ├── superpowers-workflow.mdc  # Superpowers 스킬 매핑
│   ├── spring-boot-java.mdc
│   ├── figma-product.mdc
│   ├── client-platform.mdc
│   ├── deployment.mdc
│   └── testing.mdc
└── skills/                ← 반복 워크플로 스킬
    └── specify/
        ├── SKILL.md
        └── references/
            └── spec-template.md
```

## 파일별 역할

| 경로 | 역할 | 적용 시점 |
|------|------|-----------|
| `settings.json` | 프로젝트에서 켤 Cursor 플러그인 (예: deploy-on-aws) | Cursor가 프로젝트 열 때 |
| `hooks.json` | 셸 실행 전 등 **이벤트 → 스크립트** 매핑 | 에이전트가 터미널 명령 실행 직전 |
| `hooks/*.sh` | 훅 본문 — 위험 명령 차단, 감사 로그 등 | `hooks.json`이 지정한 이벤트 |
| `rules/*.mdc` | **항상 또는 특정 파일**에 대한 코딩·도메인 규칙 | 대화 전체 또는 glob 매칭 파일 작업 시 |
| `skills/*/SKILL.md` | 다단계 워크플로 (스펙 작성 등) | 에이전트가 해당 작업을 인식할 때 |

## Rules (`rules/`)

`.mdc` = Markdown + YAML frontmatter. Cursor 규칙 피커에 `description`이 표시됩니다.

### Always-on (하네스 · Superpowers)

| 파일 | 요약 | SSOT 범위 |
|------|------|-----------|
| `harness-workflow.mdc` | ⛔ 문서 정합 · ErrorCode/AOP · DB · **레거시(교체=같은 PR 삭제)** · Before/While/After | **코어 STOP·코딩 흐름** |
| `harness-wave.mdc` | Wave Must/Nice/Out 단정 금지 · `[미정]`→#2 · 희망기간/조회윈도우/A1 | Wave·용어 |
| `harness-follow-up.mdc` | 💡 후속 제안 · ✅ Defer 이슈 분리 · 💡 ERD 적극 제안 | 완료 후·범위 미루기 |
| `superpowers-workflow.mdc` | 스킬 트리거 · harness 우선순위 | Superpowers 연동 |

우선순위: `harness-workflow` ⛔ > specify > Superpowers > 일반 관례

### File-scoped

| 파일 | `alwaysApply` | glob | 요약 |
|------|---------------|------|------|
| `spring-boot-java.mdc` | false | `**/*.java` | 레이어·enum·Entity·**ErrorCode·AOP**·OpenAPI JWT·주석 |
| `figma-product.mdc` | false | domain, service, specs | 도메인·BR·와이어프레임 |
| `client-platform.mdc` | false | controller, service, config, specs | React 앱·스토어·API·인증 |
| `deployment.mdc` | false | yml, Docker, domain, deploy | 배포 가드레일 — 절차는 `deploy/README.md` |
| `testing.mdc` | false | `**/*Test.java` | JUnit 5·프로필·테스트 네이밍 |

### 규칙 추가·분리 가이드

1. **한 규칙 = 한 관심사** (코어 하네스 ~120줄, 형제 ~70줄 권장)
2. 전역 STOP·코딩 흐름 → `harness-workflow` (`alwaysApply: true`)
3. Wave/용어 → `harness-wave` · 후속/Defer/ERD → `harness-follow-up` (**중복 금지**, 링크만)
4. 파일 타입별 → `globs` + `alwaysApply: false`
5. 반복 실수 → 해당 규칙에 짧게 추가

## Skills

에이전트가 **특정 요청**을 받으면 스킬 파일을 읽고 단계를 따릅니다.

### 로컬 (`.cursor/skills/`)

| 스킬 | 트리거 예시 | 산출물 |
|------|-------------|--------|
| `specify` | 새 기능, 리팩터 계획, 아키텍처 결정 | `docs/specs/{feature}.md` (**스펙 SSOT**) |

### Superpowers (Cursor 플러그인)

| 스킬 | TripFit에서 |
|------|-------------|
| `using-superpowers` | 매 세션·턴 진입 |
| `brainstorming` | wave 신규·모호한 요구 (Approved 스펙 있으면 생략) |
| `systematic-debugging` | 버그·CI 실패 |
| `verification-before-completion` | 완료·PR 전 |
| `executing-plans` | Approved 스펙 다단계 구현 (#12 등) |
| `writing-plans` | specify **대체 ❌** · 큰 작업 계획만 |

**워크플로:** `wave 확인 → (brainstorming) → specify/Approved → 구현 → ./gradlew test → verification → (후속 제안) → gh issue/PR`

상세: `.cursor/rules/superpowers-workflow.mdc`

템플릿·참고 문서는 `skills/{name}/references/`에 둡니다.

## Hooks (`hooks.json` + `hooks/`)

| 이벤트 | 현재 동작 |
|--------|-----------|
| `beforeShellExecution` | `block-dangerous.sh` — force push, `rm -rf`, `git reset --hard`, `docker compose down -v` 차단 |

`failClosed: true` — 스크립트 오류 시에도 명령 실행을 막습니다.

## `settings.json`

팀원 간 동일한 Cursor 플러그인 활성화를 위해 버전 관리합니다.  
개인 UI 설정(테마, 폰트)은 여기 넣지 않습니다.

## AGENTS.md와의 관계

```
AGENTS.md          → 무엇을, 어디서 찾는지 (프로젝트 지도)
docs/README.md     → 기획·아키텍처·스펙 문서 인덱스
deploy/README.md   → Docker·EC2 배포
.dev/README.md     → 임시 세션 로그 (장기 문서는 docs/로)
.cursor/rules/     → 어떻게 코딩·배포·검증하는지 (행동 규칙)
  harness-workflow / harness-wave / harness-follow-up / superpowers-workflow
.cursor/skills/    → 큰 작업의 단계별 절차 (specify = 스펙 SSOT)
docs/specs/        → 기능별 설계 산출물 (specify 스킬 결과)
```

## 유지보수 체크리스트

- [ ] 클라이언트·스토어 전제 변경 시 `docs/product/platform.md` + `client-platform.mdc` 동기화
- [ ] 새 도메인 enum·상태 추가 시 `figma-product.mdc` 또는 glossary 동기화
- [ ] ddl-auto·프로필 변경 시 `docs/architecture.md` + `deployment.mdc` 동기화
- [ ] Wave/`[미정]`/용어 규칙 변경 → `harness-wave.mdc`만 (workflow에 중복 금지)
- [ ] 후속·Defer·ERD 제안 규칙 변경 → `harness-follow-up.mdc`만
- [ ] 반복되는 코드 리뷰 코멘트 → 해당 `rules/*.mdc`에 한 줄 규칙으로 승격
- [ ] 위험 명령 패턴 추가 필요 시 `hooks/block-dangerous.sh` + `hooks.json` matcher 동시 수정
