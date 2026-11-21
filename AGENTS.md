# TripFit Server

TripFit 백엔드 API 서버. AI 에이전트가 작업할 때 참고하는 프로젝트 지도입니다.

## How We Build

**0. 문서·구현 정합 (최우선)** — 스펙·결정·문서 간 값·계약이 어긋나면 **구현하지 말고 사용자에게 질문**. 상세: `.claude/rules/core-guardrails.md` ⛔ STOP.
**priority: must/could** — 이슈 `## 완료 조건`·스펙 `MVP: In scope`만으로 must를 단정하지 않음, 판단 기준·용어는 임의 재서술하지 말고 SSOT 확인. SSOT: `docs/product/release-milestones.md` §2 · `.claude/rules/core-scope.md`.

기획·검증 기준을 먼저 고정하고, 그에 맞춰 구현합니다. **계획 축**은 `docs/product/release-milestones.md`(운영 SSOT — Milestone `MVP 출시`/`출시 이후` + `priority:` 라벨), **기획**은 `docs/product/`, **기능 설계**는 `docs/specs/`, **아키텍처 선택**은 `docs/decisions/`에 둡니다. DB·인증·다파일 변경 시 스펙 필수 (`core-workflow` 규칙). 구현 후 `./gradlew test`와 PR·CI로 검증합니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.0
- Gradle (wrapper 포함)
- MySQL 8.0 (런타임 · 테스트 — Testcontainers)
- JUnit 5 (테스트)
- Docker + GHCR (배포)

## Conventions

- 패키지: `com.tripfit.tripfit` — 도메인 기반 레이어드 (`{domain}/controller|dto|service|domain|repository|client`, 필요 시 `{domain}/{feature}/…`, 공통 `common/`)
- DB/API 네이밍은 기능 추가 시 `docs/architecture.md` 기준으로 통일
- Java 주석: Swagger·`@Schema`와 중복 금지, **완전한 문장으로 쓰는 산문체** — **이름·시그니처만으로 안 드러나는 것만** 메서드 위 `//` 역할 주석(이름이 곧 설명인 facade·자명한 위임은 생략 가능) · 다단계는 `// 1.`+Why · Controller는 권한·검증만, API 설명은 `@Operation(summary)` + Javadoc(`therapi-runtime-javadoc`) — `.claude/rules/java-comments.md`·`.claude/rules/openapi-conventions.md`
- 범위 밖 리팩터링·포맷 변경 금지 — 요청된 작업만 수정
- **절대 마음대로 커밋하지 않는다** — 사용자가 명시적으로 요청할 때만 실행
- 목적·주제별로 나눠 **최대 5개**까지 커밋할 수 있다
- **작업이 끝나면 묻지 않아도 커밋 분할안을 먼저 제안한다** (제안 ≠ 실행 — 승인 후 실행) (상세: `.github/CONTRIBUTING.md`, `.claude/rules/core-workflow.md`)
- **문서·스펙·결정 정합 최우선** — 문서 간·문서-구현 간 충돌 시 질문 없이 구현·기본값 변경 금지 (`.claude/rules/core-guardrails.md` ⛔ 섹션)
- **ErrorCode·AOP** — 실패 분기·`last_activity_at` touch·권한 게이트 변경 시 **같은 턴**에 enum·어노테이션·스펙 갱신 (하네스 ⛔ ErrorCode 절)
- **`[미정]` 항목:** 중앙 트래커(구 `#2`) 폐지(2026-08-19) — 기획 미확정 항목은 해당 문서에 표기만 남김 (하네스 `core-scope` · CONTRIBUTING)
- **priority: must/could:** 근거 없이 단정 금지, 애매하면 사용자 확인 — `core-scope.md`
- **DB:** 상용 보존 데이터 없음 → Flyway/SQL 마이그레이션 **작성 금지**. 엔티티 최신본 + `ddl-auto`, 필요 시 DB 리셋 (`.claude/rules/core-guardrails.md`)
- **레거시:** 현행 Approved와 다른 코드·호환 레이어·**교체된 구 메서드/상수**는 **같은 PR에서 즉시 삭제** (dev·구 클라/DB 호환 금지 — `core-guardrails` STOP §4)
- **ERD:** 고정이 아님 — 스키마 개선을 **적극 제안**, 승인 시 엔티티+`erd.md`만 최신화 (`core-followup.md`)
- 비밀값(`.env`, API 키)은 코드·커밋에 포함하지 않음

## Important Paths

| 경로 | 용도 |
|------|------|
| `src/main/java/com/tripfit/tripfit/` | 애플리케이션·도메인 코드 |
| `src/main/resources/` | `application.yml`, 프로필별 `application-{profile}.yml` |
| `src/test/java/` | 단위·통합 테스트 |
| [`docs/how-it-works.md`](docs/how-it-works.md) | **"지금 이렇게 동작합니다"** — 쉬운 말로 쓴 현재 동작 요약(스펙 아님). 보안·아키텍처 로직 변경 시 같은 턴 갱신 |
| [`docs/product/release-milestones.md`](docs/product/release-milestones.md) | **릴리즈 Milestone·priority 운영·판단** SSOT |
| [`docs/README.md`](docs/README.md) | **문서 SSOT** — 기획·아키텍처·스펙 인덱스 |
| [`deploy/README.md`](deploy/README.md) | **배포 SSOT** — Docker·EC2·검증 스크립트 |
| [`.claude/rules/README.md`](.claude/rules/README.md) | TripFit 규칙과 작업별 적용 기준 |
| [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) | **Git SSOT** — 브랜치·커밋·PR·PN 리뷰 |

## Product Context

- **클라이언트**: React 프론트 2명, 최종 Play·App Store 앱 — `docs/product/platform.md`
- 새 기능 구현 전 `docs/product/mvp.md`로 범위 확인
- 상세 요구는 `docs/product/prd.md` 참조
- 도메인 규칙은 `docs/product/business-rules/{domain}.md` 참조
- 용어는 `docs/product/glossary.md` 기준
- DB 설계는 `docs/architecture/erd.md` 참조
- UI·도메인 출처: `docs/product/design/figma-wireframe-v1.md` (Figma Wireframe v1)

## Codex 워크플로

- 작업 전에 [TripFit 규칙 목록](.claude/rules/README.md)의 공통 규칙과 해당 경로의 규칙을 읽는다. Approved 스펙·결정·문서의 값이나 계약이 충돌하면 작업을 멈추고 확인한다.
- 기능·DB·인증·다파일 변경은 `superchaeyeons:write-spec`으로 스펙을 작성하고 승인 후 구현한다. 후속 이슈 분리에도 같은 스킬을 쓰며 TripFit의 스펙·이슈 양식은 [local-track-issues](.agents/skills/local-track-issues/SKILL.md)를 따른다.
- 버그는 `superchaeyeons:fix-bug`, 무손실 리팩터는 `superchaeyeons:refactor-safely`, 완료 검증은 `superchaeyeons:verify-work`, 하네스 회고는 `superchaeyeons:improve-harness`를 사용한다.
- 기능 스펙은 [TripFit 스펙 양식](docs/templates/spec-template.md)을 사용한다. 무손실 리팩터는 도메인 하나씩 [감사 체크리스트](docs/templates/audit-checklist.md)와 [감사 양식](docs/templates/audit-template.md)을 적용하고, `OpenApiSpecExportTest`로 내보낸 계약을 기준 스냅샷과 대조해 차이가 0인지 확인한다.
- Java를 편집하면 `./gradlew spotlessApply -PspotlessIdeHook=<수정한 Java 파일의 절대경로>`로 해당 파일을 포맷한다. 커밋할 때는 저장소의 Git 훅이 스테이징한 변경을 다시 검사한다.
- 조사·테스트·문서 리뷰 역할은 [.codex/agents/](.codex/agents/)에 있다. Java·API·DB 변경의 별도 리뷰에는 [Spring 리뷰 기준](.claude/rules/spring-review.md)을 적용한다.
- 공통 설정은 [.claude/harness.conf](.claude/harness.conf), 실제 Gradle·기동 명령은 [scripts/dev.conf](scripts/dev.conf)에 있다. Codex에서 테스트는 공통 설정의 `TEST_CMD`를 프로젝트 루트에서 단독 실행한다.
- Codex가 승인 대상으로 분류하는 작업은 플러그인의 `scripts/check-codex-approval.py`로 사전 검사하고 구체적인 변경에 대한 승인을 확인한다. PR을 만들 때는 [기존 PR 템플릿](.github/pull_request_template.md)을 사용한다.

## Commands

```bash
cp .env.example .env             # 최초 1회 — Auth env 등 채우기
./scripts/install-git-hooks.sh   # 최초 1회 — pre-commit·commit-msg 훅 설치
docker compose up -d             # MySQL만 (로컬 DB)
./gradlew bootRun                # Spring 로컬 실행 (local 프로필, .env 자동 로드)
./gradlew test                   # 테스트
./gradlew build                  # 빌드

# 선택: Spring까지 Docker로 띄울 때
docker compose --profile app up -d --build
./scripts/verify-deploy.sh
```

## 금기사항

- `git push --force` (main/master)
- `rm -rf` 등 파괴적 shell 명령
- 테스트 없이 핵심 로직만 추가 (요청이 없는 한)
