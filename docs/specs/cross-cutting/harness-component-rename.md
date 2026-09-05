# 하네스 구성요소 개명·분리

> 상태: Draft  
> MVP: Out of scope (제품 기능 아님 — 에이전트 하네스 정리)  
> 관련 BR: N/A

## 목표

하네스 구성요소(규칙·스킬·훅·에이전트)의 이름을 **파일명만 보고 역할을 알 수 있게** 바꾸고, 한 파일이 여러 관심사를 담고 있던 3곳을 쪼갠다. 이름에 `core-`/`tripfit-` 구분이 생기면서, 다음 단계인 "하네스를 다른 저장소로 복제"할 때 **무엇을 가져가고 무엇을 남길지가 파일명으로 자명해진다.**

## 배경

- 사용자가 이 하네스를 다른 프로젝트에서도 쓰려고 **별도 저장소로 복제**할 계획이다(2026-09-04 결정). 복제는 이 작업 다음 단계이며, 이번 스펙은 **복제 전에 TripFit 안에서 이름·구조를 먼저 정리**하는 단계다.
- 현재 이름의 문제는 세 가지다.
  - **동어반복** — `debug`("버그를 디버그"), `defer`(followup이 군더더기)
  - **내부 은어** — `harness-*` 접두사. 이 팀 밖에서는 통하지 않는다
  - **길이** — `spring-reviewer` 29자
- 이름만이 아니라 **한 파일이 여러 일을 하는 것**도 "직관적이지 않음"의 실체다. 실측:
  - `harness-workflow.md` — 195줄 / 20.9KB. always-load 규칙 중 최대이며, "하지 말 것"(⛔ STOP 6개)과 "어떤 순서로 할 것"(3트랙×4게이트)이 한 파일에 있다
  - `harness-milestone.md` — 70줄. 일반 원칙(우선순위 라벨을 에이전트가 임의 판단하지 않는다)과 TripFit 고유 사실(Release Gate·희망기간·조회윈도우·이슈번호)이 섞여 있다
- **파급 범위 실측 (2026-09-04)** — `harness-workflow` 127회/63개 파일, `spring-boot-java` 93회/43개, `safe-refactor` 47회/21개. `.claude/`·`docs/` 밖에서도 `AGENTS.md`·`CLAUDE.md`·`.github/CONTRIBUTING.md`·`deploy/environment-reference.md`·`scripts/notify-api-breaking-change.sh`·`.claude/settings.json`이 이름을 참조한다.
- **개명에서 제외한 이름 2개 (실측 근거)**
  - `verify` — md 파일 내 100회 중 스킬 참조는 33회뿐이고, `.md` 밖으로는 `AppleTokenVerifier` 등 **Java 소스·테스트 60여 개**와 `scripts/verify-deploy.sh`에 "검증하다"라는 일반 단어로 쓰인다. 개명 자체는 진행하되 **일괄 치환은 금지**하고 패턴을 좁힌다.
  - `specify` → `spec`은 **하지 않는다.** `docs/specs/` 경로 참조가 228회라 개명 후 "spec 스킬"·"docs/specs"·"스펙"이 문서에서 비슷하게 읽혀, 직관성을 높이려다 낮추게 된다. `specify`는 이미 짧고 뜻이 통해 개명 사유(동어반복·은어·길이)에 해당하지 않는다.
- **다음 단계에 영향을 주는 G1 리서치 결과 (2026-09-04 확인)** — 이번 스펙 범위는 아니지만 이름 설계에 반영했다.
  - 플러그인은 `rules/` 디렉터리와 프로젝트형 `CLAUDE.md`를 **지원하지 않는다**: "Plugins do NOT support a `rules/` directory or project-style CLAUDE.md files." ([plugins-reference](https://code.claude.com/docs/en/plugins-reference), 2026-09-04 확인)
  - 대신 `SessionStart` 훅의 stdout이 컨텍스트로 주입된다: "The exceptions are `UserPromptSubmit`, `UserPromptExpansion`, `SessionStart`, and `PostModelSwitch`, where Claude Code adds plain-text stdout as context." ([hooks](https://code.claude.com/docs/en/hooks), 2026-09-04 확인)
  - 즉 규칙을 플러그인으로 옮기려면 별도 장치가 필요하다. **플러그인화 여부는 미결정**이며 이번 스펙은 그 결정과 무관하게 성립한다.
- 관련 문서: [`.claude/rules/README.md`](../../../.claude/rules/README.md) · [`docs/harness-engineering.md`](../../harness-engineering.md) · [`harness-track-gate-restructure.md`](harness-track-gate-restructure.md)

## 요구사항

### Must Have

**A. 개명 — 규칙 (`.claude/rules/`)**

- [ ] `harness-workflow.md` → **`core-guardrails.md`** + **`core-workflow.md`** (B-1 분리와 동시)
- [ ] `harness-milestone.md` → **`core-scope.md`** + **`tripfit-release.md`** (B-2 분리와 동시)
- [ ] `core-followup.md` → **`core-followup.md`**
- [ ] `core-tools.md` → **`core-tools.md`**
- [ ] `core-reporting.md` → **`core-reporting.md`**
- [ ] path-scoped 규칙 7개(`spring-boot-java`·`openapi-conventions`·`java-comments`·`testing`·`doc-writing`·`client-platform`·`deployment`)는 **이름 유지** — 이미 "언제 적용되는지"가 이름에 드러남

**B. 분리**

- [ ] **B-1. `harness-workflow.md`(195줄)을 둘로**
  - `core-guardrails.md` — ⛔ STOP §1~§6 + "금지(요약)" 표. **하지 말 것**
  - `core-workflow.md` — 진입(트랙 분류) · G1~G4 게이트 · 구현 절. **어떤 순서로 할 것**
  - STOP 절의 내용·번호·우선순위는 **변경하지 않는다**(이번 작업은 이동·개명일 뿐 정책 변경이 아님)
  - `core-workflow.md` 최상단에 "⛔ `core-guardrails.md`가 이 문서보다 우선" 한 줄 유지
- [ ] **B-2. `harness-milestone.md`(70줄)을 둘로**
  - `core-scope.md` — 일반 원칙만: 우선순위 라벨(must/could) 임의 판단 금지 · 미확정 항목 임의 확정 금지
  - `tripfit-release.md` — TripFit 고유: 🚨 Release Gate(앱 심사) · 릴리즈 축 · 일정 용어표(희망 기간·조회 윈도우·C1) — 폐지 이력 2건은 아래 F·G에서 `release-milestones.md`로 이관
- [ ] **B-3. `harness-workflow.md` 말미의 "도메인·배포 (확정 — 재질문 금지)" 절을 `tripfit-release.md`로 이동** — `tripfit.online`/`api.tripfit.online` 호스팅 사실은 워크플로가 아니라 프로젝트 고유 사실이다
- [ ] 분리 후 always-load 규칙은 5개 → **7개**가 된다(guardrails·workflow·scope·followup·tools·reporting·tripfit-release). **토큰 총량은 거의 그대로**이며(내용 이동일 뿐), 늘어난 것은 파일 헤더뿐이다

**C. 개명 — 스킬 (`.claude/skills/`)**

- [ ] `verify/` → **`preflight/`** (디렉터리 + `SKILL.md`의 `name:` frontmatter)
- [ ] `safe-refactor/` → **`safe-refactor/`** (`references/` 2개 동반 이동)
- [ ] `defer/` → **`defer/`**
- [ ] `debug/` → **`debug/`**
- [ ] `specify/`·`retro/` — **이름 유지**

**D. 개명 — 훅 (`.claude/hooks/`) · 에이전트 (`.claude/agents/`)**

- [ ] 훅 접두사 규칙 도입: 차단은 `deny-`, 경고는 `warn-`, 자동 실행은 `auto-`
  - `deny-dangerous-bash.sh` → **`deny-dangerous-bash.sh`**
  - `deny-db-migration.sh` → **`deny-db-migration.sh`**
  - `auto-format-java.sh` → **`auto-auto-format-java.sh`**
  - `warn-breaking-change.sh` — 이름 유지(이미 규칙에 맞음)
- [ ] `.claude/settings.json`의 훅 경로 3개 갱신
- [ ] `spring-reviewer.md` → **`spring-reviewer.md`** (파일명 + `name:` frontmatter)
- [ ] `researcher.md`·`doc-reviewer.md` — 이름 유지

**E. 참조 동기화**

- [ ] `.claude/rules/README.md` — 디렉터리 다이어그램 + Always-load 표 + Path-scoped 표 + Skills 표 + Agents 표 + Hooks 표 (**한 파일 안에서 표와 다이어그램이 따로 노는 사고 이력 있음** — 둘 다 확인)
- [ ] `.claude/settings.json` — 훅 경로
- [ ] `AGENTS.md` · `CLAUDE.md` — 규칙·스킬·훅 이름
- [ ] `docs/harness-engineering.md` — §3·§4·§5·§13 표
- [ ] `docs/harness/layer1-human-gate.md`(규칙) · `layer2-workflow-skills.md`(스킬·에이전트) · `layer3-deterministic-hooks.md`(훅) · `architecture-diagrams.md`(다이어그램)
- [ ] `.github/CONTRIBUTING.md` · `deploy/environment-reference.md` · `scripts/notify-api-breaking-change.sh`
- [ ] `docs/audits/`·`docs/specs/`의 기존 문서 — **경로 링크만** 갱신하고 서술 내용은 이력이므로 건드리지 않는다
- [ ] `docs/specs/README.md` — `cross-cutting/` 표에 이 스펙 행 추가 (개수 8 → 9)

**F. always-load 다이어트 (2026-09-04 사용자 승인으로 범위 추가)**

- [x] `tripfit-release.md`의 **이력 2건을 `docs/product/release-milestones.md`로 이관** — 규칙 파일에는 "지금 뭘 하라"만 남기고 "과거에 뭐가 폐지됐다"는 기록은 두지 않는다
  - Wave 도메인 축 폐지 서술 → 삭제. `release-milestones.md` §0·부록에 **더 자세한 원본이 이미 있어** 규칙 쪽이 축약 중복이었다. 지금 쓰는 "3개 질문" 표는 남긴다
  - `[미정]` 전용 트래커(`#2`) 폐지 이력 → `release-milestones.md` 부록에 **신규 추가**(거기 없던 내용이라 이관 필요). 운영 규칙("폐지된 `#2`에 새 `[미정]` 추가 금지")은 `core-scope.md`로 흡수
- [x] 일정 용어 표(희망기간·조회윈도우·C1)는 **옮기지 않는다** — `glossary.md`에 `C1 윈도우` 정의가 없어 이관 시 정의가 유실되고, `glossary.md`는 규칙이 아니라 자동 로드되지 않아 trip·schedule 작업 중 에이전트가 정의를 모른 채 코딩할 위험이 생긴다
- [x] Release Gate·도메인·배포 절도 **남긴다** — 트리거가 파일 접근이 아니라 대화("릴리즈 상태를 논의할 때")라 `paths:`로 대체 불가

**측정 결과 (정직한 기록):** always-load 총량 25,635자 → 25,467자로 **0.7% 감소**에 그쳤다. 사전 추정은 5%였으나, 이관하면서 남은 절에 안내 문장을 새로 써야 해 실제 절감은 훨씬 작았다. **토큰 절감은 이 작업의 근거가 되지 못한다** — 실익은 (a) 규칙 파일에서 이력이 빠져 읽기 쉬워진 것, (b) 하네스를 다른 저장소로 복제할 때 `tripfit-release.md`가 걷어내기 쉬워진 것 두 가지다.

**G. 폐지된 릴리즈 축 용어 전면 제거 (2026-09-04 사용자 요청으로 범위 추가)**

2026-08-26에 폐지된 구 도메인 축 용어가 저장소 전체에 잔존해 있었다. 폐지 사실을 반복 설명하는 문장이 규칙·스펙·스크립트에 흩어져, 읽는 사람이 매번 "이건 지금 쓰는 건가"를 판단해야 했다.

- [x] `scripts/github-bootstrap.sh` — 구 라벨 4개·구 마일스톤 8줄 제거. GitHub에서 라벨은 이미 삭제됐고 마일스톤은 전부 Close라 **실행이 끝난 dead code**였다(STOP §4). `bash -n` 통과
- [x] `glossary.md`·`core-scope.md`·`tripfit-release.md`·`core-workflow.md`·`defer/SKILL.md`·`specs/README.md`·`trip-recommendation-algorithm.md`·`trip-thumbnail-image.md`·`trip-recommendation.md`·`harness-track-gate-restructure.md` — 폐지 안내 문구·괄호·stale 참조 제거 또는 중립 표현으로 교체
- [x] `docs/audits/doc-writing-audit.md` **파일 삭제** — 감사 대상이 "구 용어 잔존 488회"였고 문서 스스로 "전부 해소됐다"고 기록한 **완료된 감사**다. 인바운드 참조 3곳(`docs/audits/README.md`·`harness-retro.md`·`doc-writing.md` 매핑표)도 함께 정리
- [x] `docs/product/release-milestones.md` — §0(긴 폐지 서사 + 변경 이력 표)을 "축은 3개 질문뿐" 표로 압축하고, 폐지 내역은 부록 목록으로만 남김. 26회 → 0회
- [x] `docs/harness-engineering.md` — 구 축 관련 과거 사고 사례 2행 제거

**결과:** 저장소 전체 잔존 3건. 이번 스펙 본문 2건(개명 대상을 설명하는 서술)과 `docs/audits/user-schedule/audit-round2.md` 1건(지금은 없어진 Java 주석을 **인용**한 문장 — 고치면 인용이 거짓이 되므로 유지)뿐이다. **`src/**/*.java`에는 원래 한 건도 없었다.**

### Nice to Have

- [ ] `.claude/rules/README.md`에 "작명 규칙" 절 신설 — 스킬=짧은 동사, 규칙=적용 시점이 드러나는 명사구, 훅=`deny-`/`warn-`/`auto-` 접두사. 다음에 구성요소를 추가할 때 같은 규칙을 따르게 한다

### Out of Scope (이번 스펙에서 하지 않음)

- **플러그인화** — `plugin.json`·`marketplace.json`·`hooks.json` 작성. 할지 여부 자체가 미결정
- **별도 저장소로 복제** — 이번 작업 다음 단계. 자바·스프링 관련 항목까지 **전부** 복사하는 것으로 결정됐으며, 이번 스펙은 그 복사의 사전 정리다
- **어댑터(`harness-map.md`) 도입 · `docs/` 경로를 역할 이름으로 치환** — 이식성을 위한 디커플링. 복제 후 새 저장소에서 판단
- **STOP 절·게이트의 내용 변경** — 이번은 이동·개명만. 정책은 그대로
- **`specify`·`retro`·`researcher`·`doc-reviewer`·`warn-breaking-change` 개명** — 위 배경 참조
- **path-scoped 규칙 7개 개명**

## API / 인터페이스

**API 없음** — 에이전트 하네스 파일 정리이며 애플리케이션 코드·엔드포인트·DTO를 건드리지 않는다. 따라서 `Breaking-Change-Reason:` 트레일러 대상이 아니다(STOP §5).

## 데이터 모델

**해당 없음** — 엔티티·스키마 변경 없음.

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 (예정) |
|----|-----------|------------------|
| N/A | 제품 도메인 규칙과 무관 | — |

## 실행 규칙 (치환 안전 장치)

기계적 일괄 치환이 코드를 깨뜨릴 수 있어, 아래를 지킨다.

1. **파일 확장자 한정** — `.md`·`.json`·`.sh`만 치환 대상. **`.java`는 절대 건드리지 않는다** (`verify`가 Java 60여 파일에 일반 단어로 존재)
2. **`verify` → `preflight`는 정확한 패턴만** — `skills/verify`, `` `verify` ``, `verify 스킬`, `/verify`. `verify-deploy.sh`·`verify-deploy-app.sh`·"검증"의 뜻으로 쓰인 산문은 **제외**
3. **고유 토큰은 일괄 치환 허용** — `harness-workflow`·`harness-milestone`·`core-followup`·`core-tools`·`core-reporting`·`safe-refactor`·`defer`·`debug`·`deny-dangerous-bash`·`deny-db-migration`·`auto-format-java`·`spring-reviewer`는 다른 뜻으로 쓰이지 않는다
4. **파일 이동은 `git mv`** — 이력 보존
5. **치환 후 링크 검증** — 상대 링크가 실제 파일을 가리키는지 확인(아래 검증 시나리오)

## 검증 시나리오

### 정상

- [ ] `.claude/rules/`·`skills/`·`hooks/`·`agents/`에 구 이름 파일이 하나도 남아있지 않다
- [ ] 새 세션을 열었을 때 always-load 규칙 7개가 모두 로드된다
- [ ] `/preflight`·`/safe-refactor`·`/defer`·`/debug`·`/specify`·`/retro` 6개 스킬이 모두 호출된다
- [ ] `spring-reviewer` 서브에이전트가 호출된다

### 엣지 · 실패

- [ ] `src/**/*.java`에 diff가 **0줄**이다 (`git diff --stat -- 'src/**/*.java'`)
- [ ] `scripts/verify-deploy.sh`·`verify-deploy-app.sh`가 이름·내용 모두 그대로다
- [ ] 문서 내 상대 링크 중 깨진 것이 없다 — 모든 `.claude/...md`·`docs/...md` 링크의 대상 파일이 실제로 존재
- [ ] 구 이름(`harness-workflow` 등)이 **폐지 사실을 설명하는 문맥을 제외하고** 저장소에 남아있지 않다

### 수동 / 통합

- [ ] 훅 3개 동작 확인 — 위험 명령 차단(exit 2) · 마이그레이션 파일 차단(exit 2) · Java 저장 시 자동 포맷
- [ ] `.claude/settings.json`의 훅 경로가 실제 파일과 일치

## 완료 기준

- [ ] `./gradlew test` 통과 (하네스 변경이 코드에 영향을 주지 않았음을 확인)
- [ ] 위 검증 시나리오 전 항목 통과
- [ ] E항목(참조 동기화) 8곳 전부 갱신
- [ ] `doc-reviewer` 서브에이전트 리뷰 통과 — 50줄 이상 고친 문서가 다수 발생하므로 G3 대상
- [ ] `REMOVED` 항목 실제 삭제 확인 — 구 파일명이 남지 않았는지 (STOP §4)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| `verify` 치환 오탐 | 확정 (완화책 있음) | 실행 규칙 §1·§2로 통제. 검증 시나리오에서 Java diff 0줄을 기계적으로 확인 |
| always-load 파일 5개 → 7개 | 확정 | 내용 이동이라 토큰 총량 영향 미미. 가독성을 위한 의도된 트레이드오프 |
| `docs/harness/`·`harness-engineering.md` stale화 | 확정 (이번에 갱신) | 발표·질의용 서술 문서라 하네스가 바뀌면 반드시 어긋난다. 복제 단계에서 새 저장소로 이전할지는 그때 결정 |
| 플러그인화 여부 | **[미정]** — 사용자 결정 | 규칙 배달 방식(SessionStart 주입 vs 동기화)과 함께 복제 후 판단 |
| 복제 대상 저장소 | **[미정]** — 사용자 결정 | 새 하네스 전용 저장소로 만들기로 했으나 경로·이름 미정 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-04 | 초안 |
| 2026-09-04 | G항목(폐지된 릴리즈 축 용어 전면 제거) 범위 추가 — 사용자 요청 |
| 2026-09-04 | F항목(always-load 다이어트) 범위 추가 — 사용자 승인. 절감 실측 0.7%로 사전 추정(5%)과 달랐음을 기록 |
