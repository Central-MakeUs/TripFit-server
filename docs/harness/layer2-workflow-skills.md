# Layer 2 — Workflow Enforcement (승인 게이트가 있는 반복 워크플로)

> 분류: **skill** (`.claude/skills/*/SKILL.md`) · 강제 수단: 절차 + 사용자 승인 게이트 · 대응 다이어그램: "Layer 2: refactor-audit 플로우"

## 1. 기본 사항

### 이 레이어가 나타내는 것

에이전트가 매번 임의의 방식으로 작업하지 않도록, 반복되는 작업 유형마다 **단계와 승인 지점을 고정한** 절차입니다. 공통 명제는 하나 — **LLM의 자기 보고를 신뢰하지 않는다.** "안 바꿨습니다"가 아니라 `oasdiff` diff가 0인지, "테스트 통과했습니다"가 아니라 `./gradlew test`의 실제 종료 코드를 요구합니다.

### 파일 위치와 분류

| 스킬 | 파일 | 트리거 | 산출물 | 승인 게이트 |
|---|---|---|---|---|
| `specify` | [`.claude/skills/specify/SKILL.md`](../../.claude/skills/specify/SKILL.md) + `references/spec-template.md` | 새 API·엔티티·인증 흐름, DB 스키마 변경, 3파일+ 리팩터 | `docs/specs/{domain}/{feature}.md` | **있음** (Approved 전 구현 금지) |
| `refactor-audit` | [`.claude/skills/refactor-audit/SKILL.md`](../../.claude/skills/refactor-audit/SKILL.md) + `references/audit-checklist.md`·`audit-template.md` | 기존 코드 아키텍처 감사·무손실 리팩터 | `docs/audits/{domain}/audit.md` · `refactor-log.md` | **있음** (감사·구현 각각) |
| `verify` | [`.claude/skills/verify/SKILL.md`](../../.claude/skills/verify/SKILL.md) | "완료/통과" 선언 직전 | 없음(검증 절차) | 없음 |
| `defer-followup` | [`.claude/skills/defer-followup/SKILL.md`](../../.claude/skills/defer-followup/SKILL.md) | 「다른 이슈로 빼」 | Draft 스펙 + 스펙 amend + (확인 후) 이슈 | **있음** (이슈 생성 전) |
| `debug-bug` | [`.claude/skills/debug-bug/SKILL.md`](../../.claude/skills/debug-bug/SKILL.md) | 버그 리포트·테스트 실패 | 없음(조사 절차) | 없음 |

**로딩 메커니즘:** 스킬은 `name` + `description` 한 줄만 항상 컨텍스트에 노출되고, 에이전트가 그 작업을 인식해 호출할 때 **SKILL.md 전문이 로드**됩니다. 규칙(Layer 1)이 "항상 켜져 있는 배경"이라면 스킬은 "필요할 때 꺼내 읽는 절차서"입니다.

> `debug-bug`가 규칙이 아니라 스킬인 이유: 트리거가 "버그 리포트를 받았는가"라는 **상황**이라 `paths:`(파일 경로 기반) 스코프로는 표현할 수 없었습니다. always-load 규칙에 넣으면 매 세션 토큰을 먹으므로 스킬로 옮겼습니다(2026-08-11).

## 2. 언제 발동하고, 어떤 흐름을 타는가 — `refactor-audit` 기준

다이어그램이 그리는 건 `refactor-audit`입니다. 이게 승인 게이트가 가장 많고 검증이 가장 엄격하기 때문입니다.

### 절대 원칙 (SKILL.md에 명시)

1. API Contract 100% 동일 (Request/Response, HTTP Status, ErrorCode, Endpoint, Swagger)
2. 비즈니스 로직 변경 금지 — 내부 구현만 개선
3. YAGNI — 불필요한 추상화·과도한 AOP 분리 금지
4. 성능 악화 금지
5. **`harness-workflow.md` ⛔ STOP이 이 스킬보다 항상 우선**

### 단계별 흐름과 읽는 파일

```
[0] 도메인 1개 선택          ← 여러 도메인 동시 진행 금지
     읽는 것: SKILL.md "도메인" 표 (auth / user / user-schedule /
              trip / notification / cross-cutting)

[1] Audit — 읽기 전용 서브에이전트
     Agent 툴(subagent_type: Explore 또는 general-purpose)로 새 컨텍스트 생성
     서브에이전트 프롬프트에 반드시 포함:
       · 대상 패키지 경로
       · references/audit-checklist.md 의 15개 점검 항목 전체
       · A/B/C/D 분류 기준
       · references/audit-template.md 포맷
       · "코드 수정 금지 — 읽기·분석만"
     산출: docs/audits/{domain}/audit.md

[2] 승인 게이트  ← 사람
     audit.md의 A/B 항목만 사용자에게 요약 보고
     C/D는 애초에 이번 라운드 구현 대상이 아님 (거부가 아니라 범위 밖)
     승인 안 받은 항목은 손대지 않음

[3] Implement — 승인된 A/B만
     우선순위 Critical → High → Medium → Low
     구현 중 API 계약·ErrorCode·엔드포인트를 건드리게 되면
       → 원칙 위반 신호 → 즉시 중단하고 사용자에게 질문

[4] Verify — 무손실을 기계적으로 증명 (3종 전부 통과해야 함)
     ./gradlew test --tests "...OpenApiSpecExportTest"   # 현재 스펙 export
     oasdiff breaking docs/api/openapi.json build/openapi/openapi.json
     ./gradlew test                                       # ArchitectureTest 포함
     ⚠ 기준이 "breaking 없음"이 아니라 diff 자체가 0
     하나라도 실패 → "일단 커밋" 금지 → 원인 분석 → 재수정 → 재검증

[5] Report
     docs/audits/{domain}/refactor-log.md 에 append (Changelog 스타일)
       실행 날짜 · 반영한 A/B 목록 · 변경 파일/라인 수 · 검증 결과
       · 남겨둔 C/D 항목과 그 이유
     사용자에게 짧게 요약 (plain-language-reporting.md 적용)

[6] 다음 도메인 — 사용자 승인 없이 자동 진행 금지
```

### 왜 1단계가 서브에이전트인가

방금 짠 코드를 같은 대화에서 스스로 평가하면 자기 판단을 재확인하는 **self-grading 편향**이 생깁니다. `code-review`/`simplify` 스킬이 별도 컨텍스트를 쓰는 것과 같은 이유입니다. SKILL.md는 여기서 한 발 더 나가서, **이번 세션에서 안 건드린 도메인이라도** 신선한 컨텍스트를 쓰라고 명시합니다 — 같은 대화 맥락 자체가 편향원이라고 보기 때문입니다.

### `verify`가 SSOT인 지점

`refactor-audit`의 4단계는 검증 절차를 자체 정의하지 않고 `verify` 스킬을 참조합니다(중복 정의 방지). `verify`의 6단계:

1. `./gradlew test` 실제 실행
2. 스펙·이슈 체크리스트를 **실제 코드**와 대조 (STOP §1.5)
3. API 계약 변경 시 트레일러 필요 여부 재확인 + `oasdiff`로 의도한 diff만 있는지
4. 레거시 재점검 (STOP §4)
5. Must Have급이면 `code-review`/`simplify`를 서브에이전트로 한 번 더
6. 실제 통과한 항목만 `[x]`, 실패·미검증은 숨기지 않고 그대로 보고

## 3. 실제 사례 — `auth` 도메인 (2026-08-04)

`refactor-audit`을 처음 전체 사이클 적용한 사례입니다.

- 신선한 서브에이전트로 `auth` 패키지 읽기 전용 감사 → [`docs/audits/auth/audit.md`](../audits/auth/audit.md)
- A/B 9건을 사용자에게 요약 보고 → 승인
- 구현 → `./gradlew test` 통과
- 기록 → [`docs/audits/auth/refactor-log.md`](../audits/auth/refactor-log.md)
- **미완 부분도 그대로 기록:** `oasdiff` 검증은 당시 로컬 샌드박스의 Docker 제약으로 보류

대표적 개선 1건 (사용자 보고문에서 발췌한 표현):

> 카카오/구글/애플 서버에 로그인 확인을 받는 동안(느려질 수 있음) 우리 DB 접속 자리를 계속 붙잡고 있었어요. 카카오가 느려지면 로그인과 상관없는 다른 기능까지 다 같이 느려질 위험이 있었는데, 이제 확인부터 먼저 받고 DB 저장은 그다음에 짧게 하도록 순서를 바꿔서 그 위험을 줄였어요.

→ 외부 API 호출을 `@Transactional` 밖으로 빼는 변경. 현재는 [`spring-boot-java.md`](../../.claude/rules/spring-boot-java.md) "ACID / 트랜잭션 경계 — Atomicity" 절에 규칙으로 승격돼 있습니다.

## 4. AI-native 관점에서의 강조 포인트

| 순위 | 강조할 것 | 근거 |
|---|---|---|
| 1 | **컨텍스트 격리로 self-grading 편향을 구조적으로 차단** | 감사 1단계를 강제로 별도 서브에이전트에 위임. "AI가 자기 결과를 평가하면 안 된다"는 인지적 한계를 알고 아키텍처로 푼 것 — 프롬프트로 "객관적으로 봐줘"라고 부탁하는 것과 질적으로 다름 |
| 2 | **검증 기준이 "breaking 없음"이 아니라 "diff 0"** | 무손실 리팩터의 정의를 도구 출력으로 환원. LLM이 "계약 안 바꿨다"고 말할 여지 자체를 없앰 |
| 3 | **미검증 항목을 숨기지 않는 것을 규칙화** | `verify` 6단계 + auth 사례의 "oasdiff 보류" 기록. 완료율을 부풀리지 않는 게 오히려 신뢰 신호 |
| 4 | 승인 게이트 (A/B만 구현, C/D는 범위 밖) | 흔한 패턴이라 단독으로는 약함 |

**면접 활용 팁:** 이 레이어는 [Layer 3](layer3-deterministic-hooks.md)보다 한 단계 약합니다 — 결국 에이전트가 "절차를 따르기로 선택"해야 작동하기 때문입니다. 그래서 "절차로 되는 것(Layer 2)과 절차로 안 되는 것(Layer 3)을 어떻게 나눴는가"를 함께 말하는 게 가장 강한 구성입니다.
