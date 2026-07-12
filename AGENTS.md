# TripFit Server

TripFit 백엔드 API 서버. AI 에이전트가 작업할 때 참고하는 프로젝트 지도입니다.

## How We Build

**0. 문서·구현 정합 (최우선)** — 스펙·결정·문서 간 값·계약이 어긋나면 **구현하지 말고 사용자에게 질문**. 상세: `.cursor/rules/harness-workflow.mdc` 최우선 섹션.

기획·검증 기준을 먼저 고정하고, 그에 맞춰 구현합니다. **계획 축**은 `docs/product/waves.md` (wave 1~4), **기획**은 `docs/product/`, **기능 설계**는 `docs/specs/`, **아키텍처 선택**은 `docs/decisions/`에 둡니다. DB·인증·다파일 변경 시 스펙 필수 (`harness-workflow` 규칙). 구현 후 `./gradlew test`와 PR·CI로 검증합니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.0
- Gradle (wrapper 포함)
- MySQL 8.0 (런타임) / H2 (test)
- JUnit 5 (테스트)
- Docker + GHCR (배포)

## Conventions

- 패키지: `com.tripfit.tripfit` — 도메인 기반 레이어드 (`{domain}/controller|dto|service|domain|repository|client`, 필요 시 `{domain}/{feature}/…`, 공통 `common/`)
- DB/API 네이밍은 기능 추가 시 `docs/architecture.md` 기준으로 통일
- Java 주석: Swagger·`@Schema`와 중복 금지 — Service·client는 단계(`// 1.`) + Why 병행, Controller는 권한·검증만 — `.cursor/rules/spring-boot-java.mdc` Comments 절
- 범위 밖 리팩터링·포맷 변경 금지 — 요청된 작업만 수정
- 커밋은 사용자가 명시적으로 요청할 때만
- 커밋 요청 시 주제별 **최대 3개**로 분할 (상세: `.github/CONTRIBUTING.md`, `.cursor/rules/harness-workflow.mdc`)
- **문서·스펙·결정 정합 최우선** — 문서 간·문서-구현 간 충돌 시 질문 없이 구현·기본값 변경 금지 (`.cursor/rules/harness-workflow.mdc` ⛔ 섹션)
- **DB:** 상용 보존 데이터 없음 → Flyway/SQL 마이그레이션 **작성 금지**. 엔티티 최신본 + `ddl-auto`, 필요 시 DB 리셋 (`.cursor/rules/harness-workflow.mdc`)
- 비밀값(`.env`, API 키)은 코드·커밋에 포함하지 않음

## Important Paths

| 경로 | 용도 |
|------|------|
| `src/main/java/com/tripfit/tripfit/` | 애플리케이션·도메인 코드 |
| `src/main/resources/` | `application.yml`, 프로필별 `application-{profile}.yml` |
| `src/test/java/` | 단위·통합 테스트 |
| [`docs/product/waves.md`](docs/product/waves.md) | **계획·우선순위 SSOT** (wave 1~4) |
| [`docs/README.md`](docs/README.md) | **문서 SSOT** — 기획·아키텍처·스펙 인덱스 |
| [`deploy/README.md`](deploy/README.md) | **배포 SSOT** — Docker·EC2·검증 스크립트 |
| [`.cursor/README.md`](.cursor/README.md) | Cursor 규칙·스킬·훅 |
| [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) | **Git SSOT** — 브랜치·커밋·PR·PN 리뷰 |

## Product Context

- **클라이언트**: React 프론트 2명, 최종 Play·App Store 앱 — `docs/product/platform.md`
- 새 기능 구현 전 `docs/product/mvp.md`로 범위 확인
- 상세 요구는 `docs/product/prd.md` 참조
- 도메인 규칙은 `docs/product/business-rules/{domain}.md` 참조
- 용어는 `docs/product/glossary.md` 기준
- DB 설계는 `docs/architecture/erd.md` 참조
- UI·도메인 출처: `docs/product/design/figma-wireframe-v1.md` (Figma Wireframe v1)

## Workflow

1. **계획** — 큰 기능은 Plan 모드 또는 `specify` 스킬로 `docs/specs/`에 스펙 작성
2. **승인** — 스펙 확인 후 구현
3. **실행** — Agent 모드, 필요 시 `Task` 서브에이전트 (`explore`, `shell`)
4. **검증** — `./gradlew test` 통과, 변경 범위 최소화

## Superpowers (Cursor 플러그인)

에이전트는 Superpowers process skills(`using-superpowers`, `brainstorming`, `systematic-debugging`, `verification-before-completion` 등)를 사용한다.

- **문서·스펙·Approved 계약**은 `.cursor/rules/harness-workflow.mdc` ⛔가 Superpowers보다 **우선**
- **기능 스펙 작성**은 플러그인이 아니라 `.cursor/skills/specify/`가 SSOT
- 스킬 트리거·우선순위: `.cursor/rules/superpowers-workflow.mdc` · [`.cursor/README.md`](.cursor/README.md)

## Commands

```bash
cp .env.example .env      # 최초 1회 — Auth env 등 채우기
docker compose up -d      # MySQL만 (로컬 DB)
./gradlew bootRun         # Spring 로컬 실행 (local 프로필, .env 자동 로드)
./gradlew test            # 테스트
./gradlew build           # 빌드

# 선택: Spring까지 Docker로 띄울 때
docker compose --profile app up -d --build
./scripts/verify-deploy.sh
```

## 금기사항

- `git push --force` (main/master)
- `rm -rf` 등 파괴적 shell 명령
- 테스트 없이 핵심 로직만 추가 (요청이 없는 한)
