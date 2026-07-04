# TripFit Server

TripFit 백엔드 API 서버. AI 에이전트가 작업할 때 참고하는 프로젝트 지도입니다.

## How We Build

기획·검증 기준을 먼저 고정하고, 그에 맞춰 구현합니다. **기획**은 `docs/product/`, **기능 설계**는 `docs/specs/`, **아키텍처 선택**(필요할 때만)은 `docs/decisions/`에 둡니다. 작업 크기(S/M/T)에 따라 스펙·이슈 생략 여부를 나눕니다 (`harness-workflow` 규칙). 구현 후 `./gradlew test`와 PR·CI로 검증합니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.0
- Gradle (wrapper 포함)
- MySQL 8.0 (런타임) / H2 (test)
- JUnit 5 (테스트)
- Docker + GHCR (배포)

## Conventions

- 패키지: `com.tripfit.tripfit`
- DB/API 네이밍은 기능 추가 시 `docs/architecture.md` 기준으로 통일
- 범위 밖 리팩터링·포맷 변경 금지 — 요청된 작업만 수정
- 커밋은 사용자가 명시적으로 요청할 때만
- 비밀값(`.env`, API 키)은 코드·커밋에 포함하지 않음

## Important Paths

| 경로 | 용도 |
|------|------|
| `src/main/java/com/tripfit/tripfit/` | 애플리케이션·도메인 코드 |
| `src/main/resources/` | `application.yml`, 프로필별 `application-{profile}.yml` |
| `src/test/java/` | 단위·통합 테스트 |
| [`docs/README.md`](docs/README.md) | **문서 SSOT** — 기획·아키텍처·스펙 인덱스 |
| [`deploy/README.md`](deploy/README.md) | **배포 SSOT** — Docker·EC2·검증 스크립트 |
| [`.cursor/README.md`](.cursor/README.md) | Cursor 규칙·스킬·훅 |
| [`.github/README.md`](.github/README.md) | **Git SSOT** — 브랜치·커밋·PR·PN 리뷰 |

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

## Commands

```bash
./gradlew test          # 테스트
./gradlew bootRun       # 로컬 실행 (local 프로필)
./gradlew build         # 빌드
docker compose up -d --build              # 로컬 Docker
./scripts/verify-deploy.sh                # 로컬 배포 검증
```

## 금기사항

- `git push --force` (main/master)
- `rm -rf` 등 파괴적 shell 명령
- 테스트 없이 핵심 로직만 추가 (요청이 없는 한)
