---
name: safe-refactor
description: 기존 도메인 코드를 아키텍처 감사 후 API 계약·비즈니스 로직을 100% 유지하며 무손실 리팩토링한다. 도메인 1개씩 순차 진행하며 감사·구현 각 단계마다 사용자 승인이 필요할 때 사용.
---

# Refactor Audit Workflow

새 기능 개발이 아니라 **기존 코드 품질 개선**이 목적일 때 쓰는 스킬. `specify`가 "구현 전 스펙"을 강제하듯, 이 스킬은 "감사 → 승인 → 구현 → 기계적 검증" 순서를 강제한다.

**이 스킬은 `core-workflow.md` 사이클의 B 트랙이다.** 아래 6단계는 트랙 공통 게이트와 이렇게 대응한다 — 게이트의 내용 자체는 `core-workflow.md`가 SSOT이며 여기서 중복 정의하지 않는다.

| 이 스킬 | 공통 게이트 |
|---------|-------------|
| 1. Audit | 진입 + **G1 리서치**(외부 라이브러리·Spring 기능 판단이 필요하면 `researcher`) |
| 2. 승인 | **G2 승인** — A/B 항목 확정 |
| 3. Implement | 구현 — 코딩 중 지킬 것 |
| 4. Verify | **G3 검증** — `preflight` 스킬 + `oasdiff` diff 0 |
| 5. Report | **G4 회고** — `refactor-log.md` append + 문서 갱신 점검 |
| 6. 다음 도메인 | 다음 사이클 진입 (승인 필요) |

점검 항목·A/B/C/D 분류 체계는 [`references/audit-checklist.md`](references/audit-checklist.md)·[`references/audit-template.md`](references/audit-template.md)가 SSOT. 이 스킬은 그걸 한 번에 실행하는 대신 **도메인 단위로 쪼개고 매 단계 승인 게이트를 둔다.**

## 절대 원칙 (변경 불가)

1. **API Contract 100% 동일** — Request/Response Body, HTTP Status, ErrorCode, Endpoint, Swagger 스펙
2. **비즈니스 로직 변경 금지** — 내부 구현만 개선
3. **YAGNI** — 필요 없는 추상화 금지, 과도한 AOP/Utility 분리 금지
4. **성능 악화 금지**
5. **`core-guardrails.md` ⛔ STOP이 이 스킬보다 항상 우선** — 특히 레거시 즉시 삭제(§4), ErrorCode·AOP same-turn(§2)는 이 스킬에서도 그대로 적용. 단, 이 스킬은 원칙상 ErrorCode·엔드포인트·계약을 안 건드리는 게 정상이므로, 리팩토링 중 그걸 건드리게 되면 **범위 위반 신호** — 멈추고 사용자에게 질문

## When to Use

- 사용자가 "아키텍처 감사", "리팩토링 감사·정리" 등을 요청하거나 `safe-refactor {domain}` 형태로 도메인을 지정할 때
- 새 API·기능 추가가 아니라 기존 코드 내부 품질(중복·Dead Code·Legacy·Spring 관례·성능·구조) 개선이 목적일 때

## 도메인 (docs/specs/ 폴더·패키지와 1:1 대응)

| 도메인 | 대응 패키지 |
|--------|-------------|
| `auth` | `auth` |
| `user` | `user` (+`user/googlecalendar`) |
| `user-schedule` | `user/schedule` |
| `trip` | `trip` (recommendation 포함, flat) |
| `notification` | `notification` |
| `cross-cutting` | `common` — 도메인 무관 |

**한 번에 한 도메인만 진행한다.** 여러 도메인을 동시에 감사·구현하지 않는다. 순서는 사용자가 지정하지 않으면 위 표 순서(auth부터)로 제안하되, 확정은 사용자에게 묻는다.

## Steps

### 1. Audit — 읽기 전용, 신선한 서브에이전트

- `Agent` 툴(`subagent_type: Explore` 또는 `general-purpose`, 읽기 전용)로 **새 서브에이전트**를 띄워 해당 도메인 패키지만 스캔한다.
  - **왜 서브에이전트인가:** 방금 짠 코드를 같은 대화에서 스스로 평가하면 자기 판단을 재확인하는 self-grading 편향이 생기기 쉽다 — `code-review`/`simplify` 스킬이 서브에이전트 컨텍스트를 쓰는 것과 동일한 이유(`core-tools.md`). 이 도메인은 아직 이번 세션에서 건드리지 않았더라도, 신선한 컨텍스트가 dead code·중복을 더 냉정하게 찾는다.
  - 프롬프트에 반드시 포함: 대상 패키지 경로, `references/audit-checklist.md` 15개 점검 항목 전체, A/B/C/D 분류 기준, `references/audit-template.md` 포맷, "코드 수정 금지 — 읽기·분석만".
- 산출물: `docs/audits/{domain}/audit.md` (신규 작성, **코드는 건드리지 않음**)

### 2. 승인

- `audit.md`의 A/B 항목을 사용자에게 요약 보고하고, 실제로 진행할 항목을 확인받는다 (`AskUserQuestion` 또는 채팅).
- 승인받지 않은 항목은 손대지 않는다. C/D는 애초에 이번 라운드에서 구현 대상이 아니다.

### 3. Implement

- 승인된 A/B 항목만, 우선순위(Critical → High → Medium → Low) 순으로 구현한다.
- `core-guardrails.md` "구현 — 코딩 중 지킬 것" 절 전체 준수 — 요청 범위만, drive-by 리팩터 금지, 레거시는 같은 변경에서 삭제.
- 구현 중 API 계약·ErrorCode·엔드포인트를 건드리게 되면 **즉시 멈추고 사용자에게 확인** (원칙 위반 신호).

### 4. Verify — 무손실을 기계적으로 증명

LLM의 "안 바꿨다"는 자기 보고를 신뢰하지 않고, 아래를 전부 통과해야 다음 단계로 간다.

```bash
# 1) 현재 코드 기준 OpenAPI 스펙 export
./gradlew test --tests "com.tripfit.tripfit.common.config.OpenApiSpecExportTest"

# 2) main 스냅샷과 diff — 이 스킬 기준은 "breaking 없음"이 아니라 "diff 자체가 0"
oasdiff breaking docs/api/openapi.json build/openapi/openapi.json

# 3) 전체 테스트 (ArchitectureTest 포함)
./gradlew test
```

- oasdiff에 어떤 diff라도 나오면 실패로 간주하고 원인을 되돌린다 (`docs/api/README.md` "oasdiff가 못 보는 변경" 절 참고 — 스키마 diff는 기본값·문자열 포맷·computed field 트리거·헤더·순수 비즈니스 로직 변화는 못 잡으므로, 그 부분은 기존 테스트 스위트 통과 여부로 대체 검증한다).
- Must Have급 변경(3파일 이상)이면 `code-review` 또는 `simplify` 스킬로 최종 게이트를 한 번 더 돌리는 걸 권장한다.
- 하나라도 실패하면 "일단 커밋"하지 않고 원인 분석 → 재수정 → 재검증.

### 5. Report

- `docs/audits/{domain}/refactor-log.md`에 append (Changelog 스타일):
  - 실행 날짜, 반영한 A/B 항목 목록, 변경 파일·라인 수, 검증 결과(`./gradlew test`, oasdiff diff 0 확인), 남겨둔 C/D 항목과 이유
  - **H1 바로 아래에는 이 로그가 무엇인지 설명하는 개요 한 문단을 유지한다** (`doc-writing.md` — 날짜 섹션부터 시작하지 않는다)
- `audit.md`·`refactor-log.md`를 새로 만들었거나 50줄 이상 고쳤으면 **`doc-reviewer`** 서브에이전트로 확인 (G3 문서 품질 게이트).
- 사용자에게 짧게 요약 보고 — 설명 문체는 `core-reporting.md`를 따른다.

### 6. 다음 도메인

- 사용자 승인 없이 다음 도메인으로 자동 진행하지 않는다.

## 브랜치·이슈·커밋 (현재 단계 — 로컬 실험, 2026-08-03 사용자 결정)

- **지금은 GitHub 이슈 트래킹 없이 로컬에서만 진행한다.** `CONTRIBUTING.md`의 `{type}/{issue-number}-{description}` 브랜치 규칙은 이 실험 단계에서는 강제하지 않는다.
- 다만 실제로 PR을 올릴 단계가 되면 이슈 번호가 다시 필요하다 — 그 시점이 오면 사용자에게 상기한다.
- 커밋은 `AGENTS.md` 공통 규칙대로 **사용자가 명시적으로 요청할 때만** 진행한다.

## 금지

- 여러 도메인 동시 감사·구현
- 승인 없는 A/B 항목 구현, 또는 감사(audit.md 작성)와 같은 턴에 바로 구현 (승인 단계 생략)
- API·ErrorCode·엔드포인트를 "개선"이라는 이유로 변경
- `oasdiff` diff 확인·전체 테스트 통과 전 "완료" 보고
- C/D 항목을 이유 없이 나열만 하고 넘어감 (`references/audit-template.md` 원칙: 반드시 이유 포함)
