@AGENTS.md

## Claude Code 보충

이 파일은 `AGENTS.md`(프로젝트 지도)를 그대로 불러온다. `AGENTS.md`는 CLAUDE.md 전용이 아니라 다른 코딩 에이전트와도 공유하는 캐노니컬 문서이므로, Claude Code 전용 세부사항은 아래와 `.claude/`에 둔다.

- **행동 규칙(rules):** `.claude/rules/` — `core-guardrails.md`(⛔ STOP, 항상 로드) 등 always-load 규칙 + `paths:` frontmatter로 파일 접근 시에만 로드되는 규칙(`spring-boot-java.md`, `testing.md` 등). 구조: [`.claude/rules/README.md`](.claude/rules/README.md)
- **스킬(skills):** `.claude/skills/specify/` — 큰 변경 전 스펙 작성을 강제하는 승인 게이트
- **훅(hooks):** `.claude/settings.json`이 `PreToolUse`/`PostToolUse` 훅 4개(`deny-dangerous-bash.sh`·`deny-db-migration.sh`·`warn-breaking-change.sh`·`auto-format-java.sh`)를 등록한다 — 목록·역할은 [`.claude/rules/README.md`](.claude/rules/README.md) Hooks 표가 SSOT
- **권한(permissions):** `.claude/settings.local.json` — 자주 쓰는 안전한 명령 allowlist
