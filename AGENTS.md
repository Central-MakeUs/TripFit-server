# TripFit Server

TripFit 백엔드 API 서버. AI 에이전트가 작업할 때 참고하는 프로젝트 지도입니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.0
- Gradle (wrapper 포함)
- JUnit 5 (테스트)

## Conventions

- 패키지: `com.tripfit.tripfit`
- DB/API 네이밍은 기능 추가 시 `docs/architecture.md` 기준으로 통일
- 범위 밖 리팩터링·포맷 변경 금지 — 요청된 작업만 수정
- 커밋은 사용자가 명시적으로 요청할 때만
- 비밀값(`.env`, API 키)은 코드·커밋에 포함하지 않음

## Important Paths

| 경로 | 용도 |
|------|------|
| `src/main/java/com/tripfit/tripfit/` | 애플리케이션 진입점·도메인 코드 |
| `src/main/resources/` | `application.properties`, 정적 리소스 |
| `src/test/java/` | 단위·통합 테스트 |
| `docs/architecture.md` | 아키텍처·레이어 규칙 |
| `docs/specs/` | 기능 스펙 (구현 전 작성) |
| `.cursor/rules/` | 파일·상황별 세부 규칙 |
| `.cursor/skills/` | 반복 워크플로우 (specify 등) |

## Workflow

1. **계획** — 큰 기능은 Plan 모드 또는 `specify` 스킬로 `docs/specs/`에 스펙 작성
2. **승인** — 스펙 확인 후 구현
3. **실행** — Agent 모드, 필요 시 `Task` 서브에이전트 (`explore`, `shell`)
4. **검증** — `./gradlew test` 통과, 변경 범위 최소화

## Commands

```bash
./gradlew test          # 테스트
./gradlew bootRun       # 로컬 실행
./gradlew build         # 빌드
```

## 금기사항

- `git push --force` (main/master)
- `rm -rf` 등 파괴적 shell 명령
- 테스트 없이 핵심 로직만 추가 (요청이 없는 한)
