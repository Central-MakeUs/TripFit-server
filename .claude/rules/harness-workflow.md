# Harness Workflow

이 저장소에서 작업할 때 **무엇을 하기 전에 멈춰야 하는지(⛔ STOP 6개)**와 **어떤 순서로 일하는지(트랙 1개 선택 → 게이트 4개 공통 통과)**를 정의한다. 작업 성격에 따라 트랙 A(기능)·B(감사·리팩터)·C(버그) 중 하나를 고르고, 트랙이 달라도 G1 리서치 → G2 승인 → G3 검증 → G4 회고는 모두 통과한다.

**형제 규칙 (내용 분리 SSOT):** `harness-milestone.md` · `harness-follow-up.md` · `workflow-tools.md`
Java·ErrorCode 상세: `spring-boot-java.md` · Git: `.github/CONTRIBUTING.md`

## ⛔ STOP (모든 규칙보다 우선)

구현·기본값 변경·커밋 전 확인. **위반 시 중단·사용자 질문.**

### 1. 문서·구현 정합

1. **문서·스펙·결정 vs 구현** — `docs/specs/`(Approved), `docs/decisions/`(확정), `docs/product/`, `docs/architecture.md`, `deploy/README.md`와 다른 TTL·API 계약·enum·한도·env 이름·패키지 경로를 **조용히 맞추지 않는다**.
2. **문서 vs 문서** — PRD vs 스펙, 스펙 vs 와이어프레임, decisions vs architecture, 스펙 vs `.env.example` 등 **서로 다른 값·용어·경로**면 한쪽을 임의 선택하지 말고 충돌을 짧게 목록화해 확인 요청.
3. **임의 개선 금지** — "더 나은 기본값", "업계 일반값", "코드가 간단해짐"만으로 문서와 다른 수치·구조를 바꾸지 않는다. 변경 시 **문서 amend 또는 사용자 명시 승인**.
4. **불확실하면 질문** — 가정을 숨기지 않는다. 질문 없이 진행한 선택은 **잘못된 작업**.
5. **구현 상태 보고 전 코드 우선 확인** — 스펙 Must Have 체크박스(`[ ]`/`[x]`)·"미구현"·"대기 중"·"`#n` 선행 필요" 같은 서술은 기능이 merge된 뒤 스펙 동기화가 누락되면 stale해진다. 사용자·프론트에게 "이 API·enum·이벤트가 아직 구현 안 됨/트리거 안 됨"이라고 **부정적으로 단정**해 답하기 전에, 관련 Controller·Service(이벤트 발행부)·테스트를 직접 grep/Read로 확인한다. `docs/specs/*.md` 문구 하나만 근거로 구현 여부를 판단하지 않는다 — 필요하면 `gh issue view`로 실제 이슈 상태도 함께 확인.
6. **"Swagger에 있다"는 소스 어노테이션이 아니라 실제 생성 문서로 확인** — DTO·enum에 `@Schema`가 있다고 해서 Swagger에 실제로 노출된다고 단정하지 않는다. `@ApiResponse`에서 제네릭 wrapper(`SuccessResponse<T>`)를 `schema = @Schema(implementation = SuccessResponse.class)`처럼 raw 타입으로 지정하면 springdoc이 실제 `data` 타입(리스트·필드·enum)을 못 읽어 스키마가 통째로 사라진다(`useReturnTypeSchema = true` 필요 — `spring-boot-java.md` 참고, `NotificationController` 사고 사례). "프론트가 필요한 값이 Swagger에 이미 있다"고 답하기 전에 로컬 `/v3/api-docs`, 배포 서버 `/v3/api-docs`, 또는 `docs/api/openapi.json`을 실제로 열어 해당 스키마·enum이 진짜 노출되는지 확인한다.

**절대 금지:** 문서와 다른 access/refresh TTL, API 필드, 에러 코드, env 키를 임의 구현·커밋. **에러 코드·`@TripActivity`·권한 어노테이션을 “다음 커밋에” 미루기.** **스펙 문서만 보고 "미구현"이라고 사용자에게 보고 — 코드 미확인 상태로 구현 상태 단정.** **`@Schema` 존재만 보고 "Swagger에 이미 노출된다"고 보고 — 실제 생성된 OpenAPI 문서 미확인.**

### 2. ErrorCode · AOP/Interceptor — 같은 턴 즉시 갱신

API·BR 실패 케이스·권한 게이트·`last_activity_at` touch를 **추가·변경하면 같은 PR·같은 턴**에 끝낸다. “나중에” 금지.

| 변경 | 같은 턴에 필수 |
|------|----------------|
| 새 실패 분기·HTTP/`code` | `{Domain\|Feature}ErrorCode` + `TripFitException` throw + **스펙 에러 표** + `@Schema` |
| L1 touch ([`trip-last-activity-at.md`](../../docs/specs/trip/trip-last-activity-at.md)) | public 유스케이스 `@TripActivity` (create는 엔티티 초기값) |
| 멤버/방장 전용 API | `@TripMemberOnly` / `@TripOwnerOnly` + Interceptor 계약 유지 |
| 폐기된 `code`·게이트 | enum·throw·스펙·Swagger **삭제** (아래 레거시 절) |

**금지:** throw/`code`만 넣고 enum·스펙 미갱신 · touch인데 `@TripActivity` 누락(또는 수동 `touchLastActivity` 재도입) · Draft 전용 코드를 Approved 전 enum에 미리 넣기 · Filter/Interceptor에서 envelope와 다른 ad-hoc JSON

SSOT: [`docs/architecture/api-response.md`](../../docs/architecture/api-response.md) · `spring-boot-java.md` ErrorCode·AOP 절

### 3. DB 스키마 — 마이그레이션 금지 (상용 보존 데이터 없음)

1. Flyway / Liquibase / `V*__*.sql` / 데이터 보존 마이그레이션 **작성·커밋 금지**.
2. 스키마 SSOT = **JPA 엔티티(최신 하나)** + Hibernate `ddl-auto` (`docs/architecture.md`).
3. 로컬·dev DB **폐기·재생성** 허용 (`docker compose down -v` 등). orphan·구 스키마 호환 레이어 금지.
4. “나중에 Flyway V2” 식 예정 코드/파일·주석 추가 금지. prod 보존이 필요해지면 **그때** decisions + 마이그레이션 별도 결정.

### 4. 레거시·정책 불일치 코드 제거 (호환 레이어 금지 · **같은 변경에서 즉시**)

현행 Approved 스펙·BR·decisions·구현 계약과 다른 코드·경로·문서를 “호환용”으로 남기지 않는다.
**dev·상용 보존 데이터 없음** → 듀얼 패스·구 클라/DB 호환·orphan 유지 **불필요·금지**.

1. **삭제 대상:** 폐기 API/path · 정책상 폐기 enum/`ErrorCode`/필드 · 구 상수·검증(예: 대체된 730일 A1) · 대체된 Repository 메서드·벌크 쿼리 · 구 스키마 매핑 · 호환 if · `@Deprecated` 방치 · stale `@Schema`/메시지 · “나중에 지울” TODO · 스펙·OpenAPI의 **현행 계약으로 적힌** 구 수치·구 API명
2. **시점 (필수):** 정책 amend·경로 교체 구현과 **같은 커밋/같은 PR/같은 턴**. “다음 커밋에 정리”·“일단 새 경로만 추가” **금지**
3. **교체 = 구경로 삭제:** 새 구현을 넣으면 **호출되지 않는 구 메서드·상수·테스트 assert·문서 ‘현행’ 문구**를 같은 변경에서 제거. “요청 밖 dead code라서 언급만”으로 **넘기지 않는다** — 이번 변경이 대체한 코드는 **요청 범위**
4. **하지 않음:** 구 클라/DB 호환 어댑터 · orphan 컬럼 · deprecated 방치 · soft/hard·live/snapshot 등 **정책상 폐기된 이중 분기**
5. **예외 — 진짜 요청 밖:** 이번 정책과 **무관한** 기존 dead code만 언급. **정책 불일치·이번 교체 잔존은 이 절이 우선 → 삭제**
6. **이력 문서:** 스펙 Changelog·과거 체크리스트의 “당시 A1=730” 등은 OK. **‘현행 코드/계약’** 으로 적힌 구 값은 §1·본 절로 **즉시 amend**

### 5. API 계약 변경 — `Breaking-Change-Reason` 트레일러 (같은 커밋 필수)

프론트가 **조금이라도 대응해야 하는** API 계약 변경은 CI의 `oasdiff breaking` 판정(좁은 스키마 기준)을 기다리지 않고 **변경을 만드는 커밋 시점에 직접** 기록한다. "필드 하나 추가일 뿐"·"optional이라 breaking 아님"·"enum 값만 늘렸을 뿐"이라는 이유로 생략하지 않는다.

**대상 (하나라도 해당하면 필수):**

- 요청/응답 필드 **추가·삭제·이름변경·타입변경·필수화**(optional 추가 포함)
- enum 값 **추가·삭제·이름변경**
- `ErrorCode` **신규·변경·삭제**, HTTP 상태 변경
- 경로·HTTP 메서드 **변경·삭제**, 필드 의미(semantics)만 바뀌어 프론트 처리 로직이 달라지는 경우

**필수 조치:** 위 변경이 포함된 커밋 본문에 `Breaking-Change-Reason: <한 줄 사유>` 트레일러 추가. 형식·예시·Discord 알림 흐름: [`docs/api/README.md`](../../docs/api/README.md) "왜 변경했는가" 절.

**같은 턴 체크 (ErrorCode·AOP §2와 동일 패턴):** DTO·enum·`ErrorCode`·`@RequestMapping` 경로를 수정하는 파일을 커밋에 담기 **직전에** 이 절을 재확인한다. 커밋을 만든 뒤 사용자가 지적해서야, 또는 CI가 "⚠️ 사유 미기재"를 띄운 뒤에야 트레일러를 추가하는 흐름은 **금지** — 이미 늦은 대응이다.

**금지:** 트레일러 없이 커밋 · oasdiff `breaking` 카테고리(스키마 파괴적 변경)에만 해당한다고 임의로 좁혀 해석 · "나중에 CI 알림 뜨면 추가" 미루기.

### 6. 보안·아키텍처 성격 로직 변경 — `docs/how-it-works.md` 같은 턴 갱신

인증·세션·토큰 저장 방식, 결제, 개인정보 저장·암호화, 그 외 "이게 바뀐 걸 사용자가 한참 뒤에야 알면 곤란한" 성격의 로직을 바꾸면, 그 작업과 **같은 턴**에 [`docs/how-it-works.md`](../../docs/how-it-works.md)의 해당 절도 쉬운 말로 고친다(없던 주제면 새 절 추가). "나중에 정리"·"스펙에 이미 적었으니 됐다" 금지 — ErrorCode·AOP(§2)와 동일한 패턴.

**대상(예):** 토큰·세션 저장 위치·전달 방식, 비밀번호·시크릿 처리, 결제 흐름, 대량 개인정보 접근·삭제 로직.

**금지:** `docs/specs/`에만 적어두고 `how-it-works.md`는 방치 · "사소한 변경"이라며 임의로 갱신 생략 · 기술 용어를 그대로 옮겨적기(사용자가 다른 문서 없이도 읽히게 — `plain-language-reporting.md` 준수).

## 사이클 — 3 트랙 × 4 게이트

작업 성격에 따라 **트랙**을 고르고, 트랙이 달라도 **게이트 4개는 공통**으로 통과한다.

```
진입(트랙 분류) → G1 리서치 → G2 승인 → 구현 → G3 검증 → G4 회고
                    │
                    ├─ A 트랙: 기능·API·DB        → specify
                    ├─ B 트랙: 감사·무손실 리팩터  → refactor-audit
                    └─ C 트랙: 버그·테스트 실패    → debug-bug
```

**⛔ STOP은 모든 게이트보다 우선한다** — 게이트를 진행하는 것보다 **중단·질문이 먼저**다.

## 진입 — 트랙 분류 (시작 전 30초)

1. `docs/product/release-milestones.md` 활성 Milestone(MVP 출시/출시 이후)·Must
2. GitHub 이슈 — 범위·완료 기준 확인/생성 (**브랜치용 `#n` 확정**, 새 이슈 생성은 G2 ⚠️ 절 확인 필수)
3. 트랙 판정

| 트랙 | 언제 | 진입 |
|------|------|------|
| **A. 기능** | 새 기능·API·엔티티·정책 변경 | DB·인증·3파일+ → `specify` → `docs/specs/` → **승인 후** 구현 · 그 외 → `AGENTS.md` + 관련 `docs/product/` 확인 후 바로 구현 |
| **B. 감사·리팩터** | 기존 코드 품질 개선 (API 계약·비즈니스 로직 불변) | `refactor-audit` — 도메인 1개씩, 매 단계 승인 |
| **C. 버그** | 버그 리포트·`./gradlew test` 실패 | `debug-bug` — 재현 → 원인 분리 → 최소 수정 |

4. 문서 확인 순서: `AGENTS.md` → `docs/architecture.md` → `docs/product/release-milestones.md` → `docs/product/platform.md` → `docs/decisions/002-domain-split-vercel-api.md` → `docs/product/mvp.md`

**스펙 신호(A 트랙):** DB 스키마, 3파일+, BR-*, 프로필/배포, **인증·푸시·딥링크·결제** 등 클라 연동 API

**Milestone·priority / `[미정]` / 일정 용어:** `harness-milestone.md` (단정 금지 · 도메인 축 "Wave"와 중앙 트래커는 폐지됨 — 2026-08-19·2026-08-26)

## G1. 리서치 게이트 — 외부 지식 확인

외부 라이브러리·SDK·API(Spring Boot 버전 차이, 소셜 로그인 provider 스펙 등)에 의존하거나 처음 다루는 프레임워크 기능이면 **구현 전에** 확인한다. 학습 데이터가 stale하거나 실제 설치된 버전과 다를 수 있다. 이미 아는 내부 코드·컨벤션 확인은 위 진입 4번으로 충분하며 이 게이트 대상이 아니다.

**소스 우선순위 (위에서 답이 나오면 멈춤):** ① 로컬 실물(`build.gradle`·`./gradlew dependencies`·실제 jar) → ② 공식 문서(**버전 고정 필수**) → ③ 릴리즈 노트·마이그레이션 가이드 → ④ provider 공식 문서. **블로그·StackOverflow는 근거로 인용 금지** — 힌트로만 쓰고 ①②로 재확인한다.

⚠️ **이 저장소 고유 함정:** Spring Boot **4.1.0** — 웹 예제 대다수가 3.x 기준이라 그대로 쓰면 깨진다. `docs.spring.io`는 **최신 stable이면 버전 경로가 버전 없는 URL로 리다이렉트되므로 URL로 버전을 고정할 수 없다**(2026-09-03 실측). 도착 페이지 상단의 버전 표시를 확인하고, 우리 버전과 다르면 **로컬 jar·BOM 실물로 교차 확인**한다.

**도구 선택:** 2개 이상 문서를 비교해야 하면 **`researcher` 서브에이전트**([`.claude/agents/researcher.md`](../agents/researcher.md) — 소스 우선순위·출력 포맷 SSOT), 단일 페이지 확인이면 인라인 `WebFetch`(서브에이전트 오버헤드가 더 큼).

## G2. 승인 게이트 — 사람이 끊는 지점

- **트랙별 승인 대상:** A = 스펙(`docs/specs/`) · B = `audit.md`의 A/B 항목 · C = 원인 가설과 수정 범위
- 가정 명시 · 해석이 여러 개면 질문 · 더 단순한 방법 있으면 말하기
- 모호·문서 충돌 → **STOP §1** (구현 시작 금지)
- 변경 파일 목록 확정 (drive-by 리팩터 금지)
- 다단계: `1. [단계] → verify: [확인]`

**브랜치:** `main`에서 `{type}/{issue-number}-{description}` — 이슈 번호 생략 금지. SSOT: [`.github/CONTRIBUTING.md`](../../.github/CONTRIBUTING.md)

**⚠️ 새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인** (2026-08-04 사용자 결정, 2026-08-05 PR까지 확장)

- **규칙:** `gh issue create` · 새 작업 브랜치 분기(`git checkout -b` 등) · `gh pr create`는 사용자가 이미 명시적으로 요청한 경우가 아니면 **실행 전 채팅으로 먼저 묻는다.** "이슈 번호 없는 브랜치 금지"(CONTRIBUTING) 같은 형식 규칙과 별개로, **만들지 여부 자체**를 임의 판단하지 않는다.
- **적용 지점:** `defer-followup` 스킬의 `gh issue create` 단계, G4의 PR 생성 단계 등 이슈·브랜치·PR을 만들 수 있는 **모든** 지점.
- **승인 범위:** **커밋 승인이 PR 승인을 포함하지 않는다.** 구현·커밋까지 요청받았어도 "PR까지 올려줘"를 별도로 확인받지 않았다면 PR 생성 전에 다시 묻는다.

## 구현 — 코딩 중 지킬 것

- 요청 범위만 · 인접 “개선”·깨지지 않은 리팩터 금지
- 요청 밖 기능·단일 사용 추상화·불필요 설정 금지
- STOP 재확인 — 스펙과 다른 수치·계약·env 금지
- **레거시** — 경로·상수·검증·API를 바꾸면 **구 구현·미사용 메서드·구 assert·‘현행’ 문서 문구를 같은 변경에서 삭제** (STOP §4). “나중에” 금지
- **ErrorCode·AOP** — 실패·touch·권한 변경 시 **같은 턴** (STOP §2)
- 기존 스타일 유지. 내 변경으로 생긴 unused만 정리
- 패키지·Entity·DTO·enum: `spring-boot-java.md` · JWT `@Operation`: `openapi-conventions.md` · **메서드 역할 `//` 주석**: `java-comments.md` (public 유스케이스 생략 금지)
- 핵심 로직 변경 시 `./gradlew test`
- 변경한 모든 줄은 사용자 요청에 직접 연결

## G3. 검증 게이트 — "완료" 보고 전

절차 SSOT: [`verify` 스킬](../skills/verify/SKILL.md). 자기 보고를 믿지 않고 **기계적으로** 확인한다.

- 변경 요약 + `./gradlew test` — 실행 없이 "통과했을 것"이라고 보고 금지
- 스펙·이슈 완료 기준을 **실제 코드**와 대조 (STOP §1.5 — 문서 문구만 보고 단정 금지)
- **API 추가·변경:** `docs/` 동기화 + 관련 GitHub 이슈 (`gh issue view` → `gh issue edit`) + STOP §5 대상이면 커밋에 `Breaking-Change-Reason:` 트레일러 포함 확인 + `oasdiff`로 의도한 diff만 있는지 확인
- **보안·아키텍처 성격 변경:** STOP §6 대상(토큰·세션·결제·개인정보 저장 방식 등)이면 `docs/how-it-works.md` 해당 절 갱신 확인
- **레거시 재점검:** 이번 변경이 대체한 구 경로·상수·문서 ‘현행’ 문구가 남았는지 확인 후 **삭제/amend**. 요청 밖·정책 무관 dead code만 언급. **정책 불일치·교체 잔존 → STOP §4 삭제**
- **문서 품질:** 새 문서를 만들었거나 기존 문서를 **50줄 이상** 고쳤으면 **`doc-reviewer` 서브에이전트**([`.claude/agents/doc-reviewer.md`](../agents/doc-reviewer.md), 기준 SSOT: `doc-writing.md`). 오타·한 줄 수정은 대상 아님 — 문체는 exit code로 판정할 수 없어 **advisory**(훅 아님)
- **규모 게이트:** Must Have급(3파일+·API·DB)이면 `code-review` 또는 `simplify`를 서브에이전트 컨텍스트에서 한 번 더 — self-grading 편향 회피

## G4. 회고 게이트 — 남길 것 남기기

- **프로젝트 문서 갱신 점검 (매 작업 필수):** 이번 작업에서 새로 확정된 설계 결정·컨벤션·자주 틀리기 쉬운 함정이 있으면 `AGENTS.md`/`CLAUDE.md` 또는 해당 `.claude/rules/*.md` 갱신이 필요한지 검토한다. 필요하면 구체적 수정안을 사용자에게 제안한다 — **자동 갱신 금지, 승인 후 반영.** 아래 "같은 실수 2회+" 문턱과 별개로 **매 작업 종료 시** 확인 대상이며, 새로 배운 게 없으면 조용히 스킵한다(보고에 언급 불필요)
- 같은 실수 2회+ → `.claude/rules/` 추가 **제안** (자동 추가 금지)
- **트랙별 기록:** B 트랙은 `docs/audits/{domain}/refactor-log.md`에 반영 이력 append
- Entity·스키마 후 ERD 개선 → `harness-follow-up.md` 💡 ERD
- Must Have급 완료 / 사용자 요청 시 후속 제안 → `harness-follow-up.md`
- 「다른 이슈로」범위 미루기 → `harness-follow-up.md` ✅ Defer (**이슈만 만들고 끝내지 않음**)
- **PR 전:** `Closes #n`·PR 체크리스트를 구현·테스트와 대조 (`[x]`만 실제 완료). 수동·미구현·`[제안]`·현재 Milestone 밖은 체크 금지. **`gh pr create` 실행 전 사용자에게 먼저 확인** — G2 "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절
- 커밋·PR: CONTRIBUTING — `{Type}: {한글}`, base `main`, **Create a merge commit** (Squash 금지)
- **커밋 요청 시:** 주제별 **최대 3개** (구현/테스트/문서·하네스). 억지 분할 금지
- **PR merge 확인 후:** 작업 브랜치 삭제 (원격+로컬) — CONTRIBUTING Pull Request "merge 후" 절. merge 안 된 브랜치는 삭제 금지

## 금지 (요약)

- **사용자 확인 없이 새 이슈·새 브랜치·새 PR 생성** — G2 "새 이슈·새 브랜치·새 PR 생성은 항상 먼저 확인" 절
- 이슈 번호 없는 브랜치명 — CONTRIBUTING 위반
- **G1 없이 외부 라이브러리 동작을 추측으로 구현** · 블로그·StackOverflow를 근거로 인용
- **G3 문서 품질 게이트 생략** — 새 문서 생성·50줄+ 문서 변경인데 `doc-reviewer` 미실행
- 문서·스펙·결정과 충돌하는 값을 묻지 않고 구현·커밋 — STOP §1
- **교체 후 구 경로·상수·‘현행’ 문서 방치** — STOP §4 (dev에서 호환 레이어 불필요)
- **프론트 대응이 필요한 API 계약 변경에 `Breaking-Change-Reason` 트레일러 누락** — STOP §5 (optional 필드 추가·enum 값 추가도 대상)
- **보안·아키텍처 성격 로직을 바꾸고 `docs/how-it-works.md` 미갱신** — STOP §6 (사용자가 나중에야 알게 됨)
- `git push --force` (main/master), `rm -rf`, 운영 DB 파괴
- `.env`·API 키를 코드·커밋에 포함

## 도메인·배포 (확정 — 재질문 금지)

| 도메인 | 호스팅 | 이 repo |
|--------|--------|---------|
| `tripfit.online` | Vercel (프론트) | **없음** — `FRONTEND_IMAGE`·frontend 컨테이너 금지 |
| `api.tripfit.online` | EC2 Nginx + Spring Boot | `deploy/app/`, `deploy/nginx/` |

API: `https://api.tripfit.online` · SSOT: `docs/decisions/002-domain-split-vercel-api.md`, `deploy/README.md`
