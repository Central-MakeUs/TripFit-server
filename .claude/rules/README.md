# TripFit 프로젝트 규칙

Codex는 작업을 시작할 때 아래 공통 규칙을 읽고 변경 대상에 맞는 상세 규칙을 함께 적용한다. 저장소의 도메인 규칙과 Approved 계약이 우선한다.

| 작업 | 읽을 규칙 |
|---|---|
| 모든 작업 | [중단·확인 조건](core-guardrails.md), [범위·우선순위](core-scope.md), [개발 절차](core-workflow.md), [릴리즈·일정 용어](tripfit-release.md), [후속 제안](core-followup.md), [결과 보고](core-reporting.md) |
| Java·Spring·JPA | [도메인 레이어와 트랜잭션](spring-boot-java.md), [주석](java-comments.md), [리뷰 기준](spring-review.md) |
| API·인증·모바일 연동 | [OpenAPI](openapi-conventions.md), [클라이언트 전제](client-platform.md) |
| 테스트 | [테스트 작성·검증](testing.md) |
| 배포·인프라 | [배포 규칙](deployment.md) |
| 문서·이슈·PR | [문서 작성](doc-writing.md), [Git 규칙](../../.github/CONTRIBUTING.md) |

스킬 호출과 승인 순서는 [AGENTS.md](../../AGENTS.md)를 따른다. TripFit의 Git 훅은 커밋 때 DB 마이그레이션 파일을 검사하고, Java를 포맷하고, API 계약 변경 사유를 확인한다.

## 유지보수 체크리스트

- [ ] 클라이언트·스토어 전제 변경 시 `docs/product/platform.md` + `client-platform.md` 동기화
- [ ] 새 도메인 enum·상태 추가 시 `docs/product/glossary.md` 동기화
- [ ] ddl-auto·프로필 변경 시 `docs/architecture.md` + `deployment.md` 동기화
- [ ] 우선순위·`[미정]` 규칙 변경 → `core-scope.md`만 · 이 저장소 고유 릴리즈 사실 → `tripfit-release.md`만 (`core-*`에 중복 금지)
- [ ] 후속·Defer·ERD 제안 규칙 변경 → `core-followup.md`만
- [ ] 반복되는 코드 리뷰 코멘트 → 해당 `rules/*.md`에 한 줄 규칙으로 승격
- [ ] 레이어·PK 등 구조 규칙 변경 시 `src/test/java/com/tripfit/tripfit/architecture/ArchitectureTest.java`(ArchUnit) 반영 검토 — 일부 규칙은 prose가 아니라 `./gradlew test`가 실제로 검증함
