# 하네스 레이어별 상세 (`docs/harness/`)

[`docs/harness-engineering.md`](../harness-engineering.md)가 **"왜 이렇게 만들었나"의 서술형 총정리**라면, 이 폴더는 각 레이어가 **"언제 발동해서 어떤 파일의 어떤 부분을 읽고 어떻게 동작하는가"**를 실행 흐름 단위로 푼 문서입니다. 발표·면접에서 한 레이어를 깊게 파고들 때 이 파일들을 봅니다.

최종 포트폴리오 다이어그램인 [`architecture-diagrams.md`](architecture-diagrams.md)의 **"관심사 분리(Separation of Concerns)"** 계층과 파일별 대응 관계는 다음과 같습니다.

| 문서 | 포트폴리오 계층 | 분류 | 강제 수단 |
|---|---|---|---|
| [`layer1-human-gate.md`](layer1-human-gate.md) | 1. Probabilistic / 2. Human Decision | **rule** (`.claude/rules/`) | 프롬프트 (소프트) |
| [`layer2-workflow-skills.md`](layer2-workflow-skills.md) | 1. Probabilistic / 2. Human Decision | **skill** (`.claude/skills/`) | 절차 + 승인 게이트 |
| [`layer3-deterministic-hooks.md`](layer3-deterministic-hooks.md) | 3. Deterministic Layer | **hook** (`.claude/hooks/`) | **shell exit code** (하드) |
| [`layer4-api-contract-safety.md`](layer4-api-contract-safety.md) | 4. Mechanical Verification | **CI + script** (`.github/workflows/`) | 저장소 밖 독립 3중 검증 |

## 한 장으로 보는 관심사 분리와 통제력 스펙트럼

```text
확률적 (약한 통제) ←──────────────────────────────────────────────→ 결정론적 (강한 통제)
Probabilistic Layer   Human Decision Layer  Deterministic Layer   Mechanical Verification
규칙 & 스킬 (L1, L2)  Human Gate (L1, L2)   로컬 훅 (L3)          CI 파이프라인 (L4)
"AI가 맥락을 따름"    "사람이 예외를 판단"  "AI 판단과 무관하게   "AI가 관여할 수
                                            기계가 강제 차단"     없는 곳에서 교차 검증"
```

이 스펙트럼 자체가 설계 결과입니다 — 전부 강하게 만들지 않고, **확률적인 AI의 판단(Probabilistic) 위에 결정론적인 기계의 통제(Deterministic)를 결합**하여 통제 비용과 안전성의 균형을 맞췄습니다.

## AI-native 엔지니어로서 강조할 우선순위 (레이어 종합)

각 문서의 마지막 절에 레이어 내부 우선순위가 있고, 여기는 **시스템 전체를 가로질러 본 포트폴리오 어필 순위**입니다.

### 1순위 — Deterministic Layer (L3): agent-type → command-type 훅 전환

**왜 1순위인가:** "AI로 만들었다가 실패해서 결정론적 스크립트로 바꿨다"는 서사는 AI를 실제로 굴려본 사람만 가질 수 있습니다. LLM 기반 훅이 "절대 막지 마라"는 명시적 지시에도 커밋을 차단한 사고를 겪고, *advisory는 판단이 아니라 불변식이므로 LLM에 맡기면 안 된다*는 결론에 도달한 과정이 [`warn-breaking-change.sh`](../../.claude/hooks/warn-breaking-change.sh) 상단 주석에 코드로 남아 있습니다.

**차별점:** 대부분의 "AI 활용" 사례는 AI를 더 많이 쓰는 방향입니다. 이건 **AI를 덜 쓰기로 한 판단**이고, 그 경계를 비용 기준으로 그었습니다.

### 2순위 — Mechanical Verification (L4): oasdiff 한계를 알고 3중 감지를 직접 구현

**왜 2순위인가:** 도구를 붙이는 건 누구나 합니다. `oasdiff`가 스키마 diff만 본다는 한계를 **실제 사고(#64: optional 필드인데 로직만 조건부 필수화)**로 확인하고, 스키마 밖에 커밋 트레일러(ErrorCode)·@ApiResponse를 얹은 **3-Tier Detection**을 구축한 게 핵심입니다. #75(신규/변경 오분류)를 `-` 줄과 교차해 고친 것도 "알림이 틀리면 팀이 알림을 무시한다"는 이해에서 나온 투자입니다.

**차별점:** 단순 CI 구축이 아니라, AI가 생성한 코드로 인해 Frontend 계약이 깨지는 것을 막기 위해 **자동화의 입력이 되도록 문서 컨벤션(`@ApiResponse`의 `NAME — 설명` 형식)을 설계**한 것 — 컨벤션이 곧 파싱 가능한 인터페이스가 됐습니다.

### 3순위 — Probabilistic Layer (L2): self-grading 편향의 구조적 차단

**왜 3순위인가:** 방금 짠 코드를 같은 컨텍스트에서 리뷰하면 자기 판단을 재확인하게 된다는 인지적 한계를, 프롬프트("객관적으로 봐줘")가 아니라 **아키텍처**(강제로 새 서브에이전트 컨텍스트)로 풀었습니다. 검증 기준도 "breaking 없음"이 아니라 **"diff 자체가 0"**으로 못 박아 LLM이 해석할 여지를 없앴습니다.

**감점 요인:** 결국 에이전트가 "절차를 따르기로 선택"해야 작동합니다. Deterministic Layer보다 한 단계 약합니다.

### 4순위 — Probabilistic Layer (L1): path-scoped 규칙 로딩

**왜 4순위인가:** "규칙 파일을 잘 썼다"는 프롬프트 엔지니어링에 가깝고 누구나 보여줄 수 있습니다. 여기서 그나마 차별화되는 건 **컨텍스트 예산 설계**입니다 — always-load 5개 + path-scoped 7개로 나눠 Java를 안 건드리는 세션에는 Spring 컨벤션을 아예 싣지 않습니다.

**함께 말할 것:** 문서 SSOT를 만들면서 동시에 **그 SSOT가 썩는다는 걸 전제**하고 STOP §1.5·§1.6("문서 말고 코드/생성물을 확인하라")을 넣은 점. 실제로 2026-08-28에 Redis ADR이 stale해 잘못된 답변을 했다가 그 절차로 복구하고 19개 문서를 정정했습니다.

## 이 폴더를 안 만들어도 되는 것

- **`docs/` 구조 자체를 별도 레이어로 강조하지 않습니다.** 폴더 정리가 잘 됐다는 건 AI-native의 증거가 아닙니다(문서 잘 쓰는 팀은 AI 없이도 많습니다). 이 저장소에서 `docs/`가 의미 있는 건 *에이전트가 매 턴 읽고 어긋나면 멈추는 제어면(Human Gate)*이기 때문이고, 그 역할은 Layer 1 문서의 STOP 표가 이미 표현합니다.

## 유지보수

이 폴더의 문서는 `.claude/` 구조가 바뀌면 stale해집니다. 훅·규칙·스킬 개수가 바뀌면 [`.claude/rules/README.md`](../../.claude/rules/README.md)(구조 SSOT)를 먼저 고치고, 이 폴더와 [`harness-engineering.md`](../harness-engineering.md)를 뒤따라 갱신하세요. **내용이 어긋나면 `.claude/rules/README.md`가 맞습니다.**
