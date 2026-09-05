# 하네스 재구성 — 3 트랙 × 4 게이트 + 리서치 게이트 구체화

> 상태: Implemented  
> MVP: Out of scope (제품 기능 아님 — 에이전트 하네스 개편)  
> 관련 BR: N/A

## 목표

새 기능·감사/리팩터·버그 세 갈래 작업이 **같은 게이트 4개(리서치·승인·검증·회고)를 공유하는 하나의 사이클**로 하네스를 재구성하고, 지금까지 절차가 없던 두 가지 — **외부 지식 조사(G1)**와 **문서 품질 검증(G3)** — 를 전용 서브에이전트와 체크리스트로 구체화한다.

## 배경

- 개편 직전 하네스는 always-load 규칙 5개 + path-scoped 규칙 7개 + 스킬 5개 + 훅 4개가 **조건별로 흩어져 트리거**됐다. 동작에는 문제가 없었지만 "하나의 사이클"로 읽히지 않아, `safe-refactor`이 사실상 별도 진입점으로 취급됐다.
- **사용자가 공유한 외부 하네스 사례**(다른 개발자가 구축한 0~11단계 선형 파이프라인 — 저장소 탐색 → 요구사항 분석 → 불변조건 정의 → 리서치 → 아키텍처 결정 기록 → CLAUDE.md 갱신 → 작업 분해 → 환경 검증 → 구현 루프 → 동시성·부하 테스트 → 독립 코드리뷰 → 문서화)를 검토한 결과, **선형 복제는 증분 개발에 낭비**다 — 매 작업마다 저장소 탐색·환경 검증을 반복하게 되는데, TripFit은 always-load 규칙이 이미 그 맥락을 상시 제공한다. 대신 그 사이클의 장점(단계가 하나로 이어져 보임, 리서치·문서갱신이 절차로 존재함)만 취해 **트랙 × 게이트** 구조로 재구성했다.
- 2026-09-03 세션에서 `core-workflow.md`의 당시 `Before Coding`·`After Coding` 절에 리서치·문서 갱신 점검을 **선반영**했으나(두 절은 이번 개편으로 게이트에 흡수돼 지금은 없다), 트랙·게이트 구조 정리와 리서치 절차 구체화는 미완이었다.
- 이 저장소는 **Spring Boot 4.1.0 / Java 21**을 쓴다. 4.x는 정식 출시(GA)된 지 얼마 되지 않아 웹 예제 대다수가 3.x 기준이고, `docs.spring.io`는 최신 stable이면 **버전 경로마저 버전 없는 URL로 리다이렉트**된다(2026-09-03 실측, 도착 페이지는 4.1.1) — URL로 버전을 고정할 수 없으므로 도착 페이지 버전을 확인하지 않으면 우리 버전과 다른 결론을 낳는다. 리서치 게이트가 필요한 실제 이유.
- **문서 품질 규칙의 빈칸 (2026-09-03 [toss/technical-writing](https://github.com/toss/technical-writing) 검토 후 확인)** — 현재 규칙 체계는 `core-reporting.md`(채팅·보고, 독자=비전공자)와 `java-comments.md`(코드 주석, 독자=서버 개발자)를 덮지만, **`docs/*.md` 본문 자체의 작성 규칙은 없다**. 문서 118개·17,027줄이 쌓인 시점에 이 빈칸을 메웠다.
  - 실측 진단: `####`(H4) 사용이 3곳(파일 2개)뿐이라 "한 페이지에 하나만" 원칙은 이미 지켜지고 있고, `glossary.md`가 용어 SSOT 역할을 하고 있다. 반면 **H1 바로 아래 개요가 없는 문서가 19개**이며 그중 18개가 `audits/`다 — 개별 문서가 아니라 `audit-template.md`를 고쳐야 하는 구조적 문제.
  - **라이선스 주의:** 해당 저장소는 CC BY-NC-SA 4.0이다. 원칙·체크리스트 아이디어는 참고하되 **문장을 그대로 옮기지 않는다** — 우리 문맥으로 재작성하고 출처만 링크한다.
- 관련 문서: [`.claude/rules/core-workflow.md`](../../../.claude/rules/core-workflow.md) · [`.claude/rules/core-tools.md`](../../../.claude/rules/core-tools.md) · [`.claude/rules/README.md`](../../../.claude/rules/README.md)

## 요구사항

### Must Have

- [x] `core-workflow.md`(당시 `harness-workflow.md`)에 **3 트랙 × 4 게이트** 구조 정의 — 기존 ⛔ STOP 6개 절은 **그대로 유지**(우선순위도 불변)
  - 트랙 A: 기능·API·DB (`specify`) / 트랙 B: 감사·무손실 리팩터 (`safe-refactor`) / 트랙 C: 버그·테스트 실패 (`debug`)
  - 게이트 G1 리서치 · G2 승인 · G3 검증 · G4 회고 — 세 트랙이 공유, 트랙별로 내용만 다름
- [x] **G1 리서치 게이트** 절차 구체화
  - 소스 우선순위: ① 로컬 실물(`build.gradle`·`./gradlew dependencies`·`~/.gradle/caches` 실제 jar) → ② 공식 문서(**버전 고정 필수**) → ③ 릴리즈 노트·마이그레이션 가이드 → ④ provider 공식 문서
  - 블로그·StackOverflow는 **근거 인용 금지**(힌트로만, ①②로 재확인)
  - TripFit 고유 함정 2개 명시: (a) 웹 예제 대다수가 Spring Boot 3.x 기준 (b) `docs.spring.io`는 **최신 stable이면 버전 경로마저 버전 없는 URL로 리다이렉트**되어 URL로 버전을 고정할 수 없으므로, 도착 페이지 상단 버전을 확인하고 우리 버전과 다르면 로컬 jar·BOM으로 교차 확인 (2026-09-03 `researcher` 스모크 테스트에서 초안의 "`/4.1/`로 고정하라"가 틀렸음이 실측으로 드러나 정정)
- [x] **`.claude/agents/researcher.md` 신규 생성** — 조사 전용 서브에이전트
  - `tools`: WebSearch, WebFetch, Read, Bash, Grep, Glob (**Edit/Write 없음** — 코드 수정 불가)
  - 고정 출력 포맷: `## 결론(3줄 이내)` / `## 우리 버전(4.1.0·Java 21) 적용 여부` / `## 근거(URL + 문서상 버전 + 확인일자)` / `## 3.x와 달라진 점`
  - 본문에 위 소스 우선순위를 그대로 포함 — 로컬 버전 확인을 **웹 조회보다 먼저** 하도록 강제
- [x] **서브에이전트 vs 인라인 판단 기준** 명시 — 2개 이상 문서를 비교해야 하면 `researcher`, 단일 페이지 확인이면 인라인 `WebFetch`(서브에이전트 오버헤드가 더 큼)
- [x] **`safe-refactor`을 B 트랙으로 편입** — 현재 G3(검증)만 `preflight` 스킬을 참조 중인 패턴을 **G1·G4까지 확장**해 별도 진입점이 아니라 사이클 안의 미니 사이클이 되게 한다
- [x] `specify`(A 트랙)·`debug`(C 트랙)에도 트랙 표기와 게이트 참조 추가
- [x] **G4 회고 게이트** 정의 — 문서 갱신 점검(`AGENTS.md`/`CLAUDE.md`/`.claude/rules/*.md`, **자동 갱신 금지·승인 후 반영**) + 후속 제안(`core-followup.md`) + 트랙별 로그 기록(B 트랙은 `refactor-log.md`)을 하나의 게이트로 통합

**G3 검증 게이트 — 문서 품질 (toss/technical-writing 흡수분)**

- [x] **`.claude/rules/doc-writing.md` 신설** — `paths: ["docs/**/*.md", ".claude/**/*.md"]`로 **path-scoped**(문서를 만질 때만 로드, always-load 금지 — 코드 작업 세션에서는 순수 토큰 낭비)
  - 3단계 체크리스트: ① 문서 유형 정하기(학습/문제 해결/참조/설명) ② 정보 구조(개요 필수·가치 먼저·제목 일관·예측 가능) ③ 문장(한 문장 한 생각·메타 담화 제거·용어 일관 — `glossary.md` 참조)
  - `core-reporting.md`(채팅·보고)·`java-comments.md`(코드 주석)와 **독자·적용 범위 경계를 명시** — "한 규칙 = 한 관심사" 유지
  - 원칙은 우리 문맥으로 재작성하고 출처만 링크 (CC BY-NC-SA)
- [x] **`.claude/agents/doc-reviewer.md` 신설** — 문서 diff만 보고 위 3단계로 판정하는 서브에이전트. 원 저장소처럼 봇 3개로 쪼개지 않고 **1개로 통합**(`researcher`와 동일 패턴, `code-review`가 코드에 하는 일을 문서에 함)
- [x] **G3에 문서 게이트 편입** — 트리거는 **새 문서 생성** 또는 **기존 문서 50줄 이상 변경**. 오타·한 줄 수정에는 돌리지 않는다. 문체는 exit code로 판정 불가하므로 **훅이 아니라 advisory 게이트**로만 둔다
- [x] **`audit-template.md`에 개요 1문장 필수화** — `audits/` 18개 문서가 H1 바로 아래 날짜·`## 범위`로 시작해 처음 여는 사람이 문서 정체를 알 수 없는 문제를 템플릿 한 곳에서 해결
- [x] **`docs/README.md`에 문서 유형 축 추가** — 역할별 폴더(product/specs/decisions/audits/harness) ↔ 문서 유형(학습·문제 해결·참조·설명) 매핑 한 줄. **폴더 재편이 아니라 작성 시 판단 기준으로만** 사용
- [x] **문서 유형별 템플릿 신설** — `docs/templates/`에 `learning-doc.md`·`how-to-doc.md`·`reference-doc.md` + `README.md`. 설명 유형은 `decisions/README.md`의 ADR 템플릿, 작업 산출물은 `spec-template.md`·`audit-template.md`를 그대로 쓰고 중복 생성하지 않는다
- [x] **`doc-writing.md`에 유형 판정 순서 + 유형별 필수 섹션 + `docs/` 전수 유형 매핑표** 추가 — 어느 문서가 어떤 유형인지 미리 지정해 판정이 사람마다 달라지지 않게 함
- [x] **`doc-reviewer`를 유형 인식 리뷰로 확장** — 판정한 유형의 필수 섹션 누락을 점검하고, `doc-writing.md` 매핑표와 다르게 읽히면 불일치를 지적
- [x] **`doc-writing.md`에 이 저장소 실제 사례 기반 Do/Don't 예시** 추가 (원문 복사 대신 우리 문서에서 발췌 — 라이선스 안전)
- [x] `core-tools.md`의 "작업 유형 → 도구" 표를 **트랙 × 게이트 표로 교체**
- [x] 문서 동기화 — `.claude/rules/README.md`(디렉터리 다이어그램 + Skills 표 + Agents 절 + 워크플로 한 줄), [`docs/harness-engineering.md`](../../harness-engineering.md)(§3 규칙 개수 · §4 트랙×게이트·서브에이전트 · §13 구성 요소 수), `docs/harness/README.md`("레이어와 사이클의 관계" 절 신설), `docs/harness/layer1-human-gate.md`(규칙 표·개수), `docs/harness/layer2-workflow-skills.md`(스킬 표에 사이클 위치 열·서브에이전트 문단·verify 7단계), `docs/harness/architecture-diagrams.md`(다이어그램 2·5를 트랙/게이트/에이전트 구조로 갱신). `layer3`(훅)·`layer4`(CI)는 이번 개편으로 바뀐 내용이 없어 그대로 둠

### Nice to Have

- [ ] ~~라이브러리 문서 전용 MCP(Context7 계열) 도입 검토~~ — **미도입 확정 (2026-09-03)**. 새 의존성을 들이는 결정인데 `researcher` 서브에이전트 + `WebFetch`로 G1이 이미 동작하는 것을 확인했다. 필요가 실제로 생기면 그때 재검토한다.
- [x] `decisions/` 템플릿에 **"결정 한 줄"을 H1 바로 아래 배치** (2026-09-03 사용자 승인 후 반영). 기존 ADR 11개는 소급 적용하지 않고 새로 쓰는 것부터 적용한다.
- [x] 기존 `audits/` 문서에 개요 소급 추가 — **17개 완료** (2026-09-03). `auth/audit.md`는 이미 상태 인용구가 있어 대상에서 제외됐다.

### Out of Scope (이번 스펙에서 하지 않음)

- 외부 사례의 0~11 선형 사이클을 그대로 복제하는 것
- 동시성·부하·장애 테스트 절차 신설 (위 외부 사례의 "동시성·부하 테스트" 단계 — 필요하면 별도 이슈)
- 실행 환경 검증 단계 신설 (위 외부 사례의 "환경 검증" 단계 — always-load 규칙이 제공하는 맥락으로 대체 가능하다고 판단)
- 훅(`hooks/*.sh`) 추가·변경 — 이번 개편은 규칙·스킬·에이전트 문서 계층만 건드린다
- `.claude/rules/`의 ⛔ STOP 6개 절 내용 변경
- **`docs/`를 문서 유형 폴더(tutorials/how-tos/explanations/reference)로 재편** — 현재 역할별 구조(product/specs/decisions/audits/harness)가 정상 동작 중이라 갈아엎기 비용이 이득보다 크다. 유형은 폴더가 아니라 **작성 시 판단 기준**으로만 쓴다
- **toss/technical-writing의 문장·프롬프트 원문 복사** — CC BY-NC-SA 4.0 라이선스 전염 방지

## API / 인터페이스

**API 없음** — 에이전트 하네스 문서·설정 변경이라 런타임 계약에 영향이 없다. `oasdiff` diff는 0이어야 한다.

## 데이터 모델

해당 없음 (엔티티·스키마 변경 없음).

## 비즈니스 규칙

해당 없음 (BR-* 무관).

## 검증 시나리오

아래 체크박스는 **새 세션에서 실제로 돌려봐야 확인되는 항목**이라 미체크로 둔다. 이번 구현 시점에 기계적으로 확인한 내용은 아래 "검증 결과" 절에 있다.

### 정상

- [ ] 새 세션에서 기능 요청 시 A 트랙으로 분류되고 G1~G4가 순서대로 인식된다
- [ ] `safe-refactor` 실행 시 G1(리서치)·G4(회고)가 실제로 걸린다 — 현재는 G3만 공유
- [ ] `researcher` 서브에이전트가 웹 조회 **전에** `build.gradle` 버전을 확인하고, 고정 출력 포맷으로 결과를 반환한다
- [ ] 버그 리포트 요청 시 C 트랙(`debug`)으로 분류된다
- [ ] 새 문서를 만들거나 기존 문서를 50줄 이상 고치면 G3에서 `doc-reviewer`가 돌고, 개요 누락·용어 혼용·제목 스타일 불일치를 지적한다
- [ ] `doc-writing.md`가 `docs/**/*.md`를 열 때만 로드되고, Java 코드만 만지는 세션에서는 로드되지 않는다

### 엣지 · 실패

- [ ] 단일 문서 1페이지 확인이면 서브에이전트를 띄우지 않고 인라인 `WebFetch`를 쓴다
- [ ] 블로그·StackOverflow 내용을 근거로 인용하지 않는다 (힌트로만 사용 후 공식 문서로 재확인)
- [ ] 도착 페이지의 버전 표시를 확인하지 않은 채 `docs.spring.io` 문서만 읽고 결론 내지 않는다
- [ ] 오타·한 줄 수정에는 `doc-reviewer`를 돌리지 않는다 (트리거 기준 준수)
- [ ] toss 문서의 문장을 그대로 복사해 규칙 파일에 넣지 않는다 (CC BY-NC-SA)
- [ ] ⛔ STOP 절이 트랙·게이트보다 계속 우선한다 (문서 충돌 시 게이트 진행보다 중단이 우선)

### 수동 / 통합

- [ ] 규칙·스킬 개수 변화에 따른 **4곳 동기화**(`rules/README.md` 다이어그램·표, `harness-engineering.md`, `docs/harness/layer1~2`) 완료 — `rules/README.md` 유지보수 체크리스트 기준
- [ ] 문서 간 상대경로 링크가 깨지지 않는다

## 완료 기준

- [x] 위 Must Have 전 항목 반영
- [x] `./gradlew test` 통과 (문서·설정만 바뀌므로 결과 불변이어야 함)
- [x] API 계약 무변경 — `git status` 기준 `.java`·`src/main/resources` 변경 **0건**이라 OpenAPI 스펙이 바뀔 수 없음을 근거로 확인(`oasdiff` 실행 대신 변경 부재로 증명)
- [x] 기존 ⛔ STOP 6개 절·훅 4개 동작 불변
- [x] 문서 링크 깨짐 없음
- [x] `doc-writing.md`가 `paths:` 스코프로만 로드됨을 확인 (always-load 규칙 개수 불변)
- [ ] `audit-template.md` 개요 항목 반영 후, 새로 생성되는 `audits/` 문서가 H1 아래 개요로 시작함

## 검증 결과 (2026-09-03 구현 시점)

**실제로 확인한 것**

| 항목 | 결과 |
|------|------|
| `./gradlew test` (구현 전/후 2회) | 두 번 다 통과 (exit 0) |
| `.java`·`src/main/resources` 변경 | 0건 — API 계약 무변경 |
| `.claude/hooks/`·`settings.json` 변경 | 0건 — 훅 4개 불변 |
| ⛔ STOP §1~§6 | diff가 80번째 줄부터 시작 — STOP 절(1~79줄) 무수정 확인 |
| 상대경로 링크 (`.claude/**`·`docs/**`) | 깨진 링크 0건 |
| always-load 규칙 개수 | 5개로 불변 (`doc-writing.md`는 `paths:` frontmatter 확인) |
| `researcher` 지침 | 동일 지침을 서브에이전트에 적용해 실행 — 로컬 `build.gradle` 우선 확인 → 고정 출력 포맷 준수 확인. **지침의 `/4.1/` 서술 오류를 이 테스트가 잡아내 정정** |

**이 세션에서 확인하지 못한 것**

`.claude/agents/*.md`는 만든 직후 **같은 세션에서 곧바로 에이전트 타입으로 등록됐다**(처음 호출했을 때는 `Agent type 'researcher' not found`가 났으나, 잠시 뒤 `researcher`·`doc-reviewer` 모두 사용 가능해졌다). 다만 등록 시점이 파일 생성과 정확히 언제 동기화되는지는 확인하지 못했으므로, **새 에이전트를 만든 직후 호출이 실패하면 잠시 뒤 재시도하거나 새 세션에서 확인**한다.

지침 검증은 두 에이전트 모두 실제로 실행해 확인했다(`researcher`는 등록 전이라 동일 프롬프트를 범용 서브에이전트로 대리 실행, `doc-reviewer`는 이번 변경분 6개 파일을 실제 리뷰). 아래는 **실사용을 거쳐야 확인되는 항목**이다.

- 트랙 자동 분류(A/B/C)가 새 세션에서 실제로 인식되는지
- `doc-writing.md`가 `docs/**/*.md` 접근 시에만 로드되는지 (frontmatter는 확인, 실제 로딩 동작은 미확인)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 코어 규칙 파일 비대화 | 2026-09-04 `#128`에서 STOP(`core-guardrails.md`)과 게이트(`core-workflow.md`)로 1차 분리 | 권장 ~120줄을 넘기면 게이트별 추가 분리 검토 — 형제 규칙 분리 패턴과 동일 |
| priority · Milestone | [미정] | `core-scope.md` ⛔에 따라 **에이전트가 단정 금지** — 사용자 확정 필요 |
| 라이브러리 문서 MCP 도입 | [미정] | 새 의존성 결정이라 별도 승인 필요 |
| `researcher` 모델 선택 | [미정] | 조사·요약 작업이라 sonnet으로 비용 절감 가능하나, 정확도 우선이면 상위 모델 — 사용 후 재평가 |
| `decisions/` 템플릿 "한 줄 결정" 최상단 배치 | [미정] | ADR 관례를 바꾸는 변경 — Nice to Have로 두고 **사용자 승인 후에만** 진행 |
| 문서 게이트 트리거 임계값(50줄) | [미정] | 운영해보고 과하거나 느슨하면 조정 |
| CC BY-NC-SA 라이선스 | 확정 | 원칙·체크리스트 아이디어만 참고, 문장은 우리 문맥으로 재작성하고 출처 링크 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-03 | 초안 — 외부 하네스 사례(0~11 선형 사이클) 검토 후 트랙×게이트 구조로 재해석. `core-workflow.md`에 리서치·문서갱신 점검은 선반영 완료 |
| 2026-09-03 | 후속 정리 — 스펙 상단 메타를 인용구로 통일(frontmatter 폐지), `how-it-works.md` 유형을 학습→설명 정정, `decisions/` 템플릿에 "결정 한 줄" 추가, 스펙 29개에서 폐지된 구 릴리즈 축 메타 줄 제거, 문서 감사 보고서의 사실 오류 2건 정정 |
| 2026-09-03 | toss 활용 확대 — `docs/templates/` 3종 신설, `doc-writing.md`에 유형 판정 순서·필수 섹션·`docs/` 전수 매핑표·실사례 Do/Don't 추가, `doc-reviewer` 유형 인식화 |
| 2026-09-03 | [toss/technical-writing](https://github.com/toss/technical-writing) 검토 결과 흡수 — G3에 문서 품질 게이트(`doc-writing.md` 규칙 + `doc-reviewer` 에이전트 + `audit-template.md` 개요 필수화) 추가. 별도 이슈 분리 대신 #127에 통합(사용자 결정) |
