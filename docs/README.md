# TripFit 문서 (`docs/`)

기획·아키텍처·스펙의 **단일 진실 공급원(SSOT)** 입니다.
코드·배포 설정과 충돌 시: **PRD > MVP 범위 컷 > 구현 코드** 순으로 해석하고, 불일치는 스펙 또는 이 README에 기록합니다.

에이전트 행동 규칙: [`.claude/rules/README.md`](../.claude/rules/README.md) · 하네스 코어 [`core-guardrails.md`](../.claude/rules/core-guardrails.md)(⛔ STOP) · [`core-workflow.md`](../.claude/rules/core-workflow.md)(트랙·게이트)

**"AI를 어떻게 활용했는가"에 대한 총정리 서술 문서:** [`harness-engineering.md`](harness-engineering.md) — 다층 SSOT·규칙·스킬·훅 구조와, 그 구조가 실제 인시던트를 겪으며 어떻게 보강됐는지까지 기록. **레이어별 실행 흐름 상세**는 [`harness/`](harness/README.md).

**"지금 이 순간 실제로 어떻게 동작하는가"를 쉬운 말로 담은 문서:** [`how-it-works.md`](how-it-works.md) — 스펙·ADR이 아니라 현재 동작 요약. 인증·세션 등 보안·아키텍처 성격 로직을 바꾸면 같은 턴에 갱신 (`.claude/rules/core-guardrails.md` STOP §6).

## 문서 유형 (작성 시 판단 기준)

**폴더는 역할로 나누고(아래 디렉터리 맵), 유형은 문서를 쓸 때의 판단 기준으로만 씁니다.** 유형을 먼저 정하면 어떤 구조로 쓸지가 거의 따라옵니다. 한 문서에 유형이 섞이면 길어지고 찾기 어려워지니 나눌지 검토하세요.

아래 "여기서는" 칸은 **감을 잡기 위한 예시**입니다. 어느 문서가 어떤 유형인지의 **전수 매핑과 작성 규칙 SSOT는 [`.claude/rules/doc-writing.md`](../.claude/rules/doc-writing.md)**이며, 두 곳에 같은 목록을 두면 한쪽만 고쳤을 때 어긋나므로 여기에는 옮겨 적지 않습니다.

| 유형 | 독자의 목적 | 여기서는 | 템플릿 |
|------|-------------|----------|--------|
| **학습** | 처음 접해서 전체 흐름을 알고 싶다 | (현재 없음 — 온보딩 문서 미작성) | [`templates/learning-doc.md`](templates/learning-doc.md) |
| **문제 해결** | 지금 막힌 것을 풀고 싶다 | (현재 `docs/`에 없음 — 배포 트러블슈팅이 `deploy/README.md`에 섞여 있음) | [`templates/how-to-doc.md`](templates/how-to-doc.md) |
| **참조** | 값·계약·목록을 정확히 확인하고 싶다 | `architecture/package-layout.md` · `architecture/erd.md` · `architecture/api-response.md` · `product/glossary.md` · `product/mvp.md` · `business-rules/` · `api/` | [`templates/reference-doc.md`](templates/reference-doc.md) |
| **설명** | 왜 이렇게 됐는지 이해하고 싶다 | `how-it-works.md` · `decisions/` · `product/prd.md` · `product/flows/` · `harness-engineering.md` · `harness/` | [`decisions/README.md`](decisions/README.md) ADR |

작업 산출물인 **스펙**(`specs/`)과 **감사**(`audits/`)는 위 4유형과 축이 달라 각자의 템플릿을 씁니다 — [`templates/README.md`](templates/README.md) 참고.

## 디렉터리 맵

```
docs/
├── README.md                 ← 이 파일 (인덱스)
├── how-it-works.md           ← 쉬운 말로 쓴 "지금 이렇게 동작합니다" (스펙 아님)
├── architecture.md           ← 레이어 구조 및 아키텍처 설계 원칙 (설명)
├── architecture/
│   ├── package-layout.md     ← 패키지 구조 및 공통 컴포넌트 참조 SSOT
│   ├── erd.md
│   └── api-response.md       ← REST JSON envelope (확정)
├── templates/                ← 문서 유형별 템플릿 — 목록: templates/README.md
├── product/                  ← 기획 SSOT
│   ├── release-milestones.md   ← 릴리즈 Milestone(MVP 출시/출시 이후)·priority 운영 SSOT
│   ├── mvp.md · platform.md · prd.md · glossary.md
│   ├── design/ · business-rules/ · flows/
├── specs/                    ← 기능 스펙 — 목록 SSOT: specs/README.md
├── decisions/                ← 아키텍처 결정 — 목록: decisions/README.md
└── audits/                   ← 감사 기록 (코드 감사 + 문서 품질 감사) — 목록: audits/README.md
```

배포 심화 문서(구 EC2 분리 가이드)는 [`deploy/ec2-split-deployment.md`](../deploy/ec2-split-deployment.md)로 이동 — 배포 관련 문서는 `deploy/` 한 디렉터리에 모음.

**상세 목록은 하위 README를 SSOT로 둔다** (이 파일에 스펙 전수를 중복하지 않음).

| 하위 인덱스 | 내용 |
|-------------|------|
| [`specs/README.md`](specs/README.md) | 도메인별 스펙·이슈 매핑·상태 |
| [`decisions/README.md`](decisions/README.md) | ADR 목록 |
| [`product/flows/README.md`](product/flows/README.md) | 사용자 플로우 (제품 정책·시나리오) |
| [`product/business-rules/README.md`](product/business-rules/README.md) | BR-* |
| [`templates/README.md`](templates/README.md) | 문서 유형별 템플릿 |
| [`audits/README.md`](audits/README.md) | 감사 기록 — 도메인별 코드 감사 · 저장소 전체 문서 품질 감사 |

## 읽는 순서 (기능 구현 시)

`.claude/rules/core-workflow.md`의 "진입 — 트랙 분류" 순서와 동일 — 두 목록이 따로 손으로 유지되지 않도록 여기서만 상세를 두고, harness는 이 순서를 그대로 참조한다.

1. `architecture.md` — 레이어·패키지 구조
2. `product/release-milestones.md` — 활성 Milestone(`MVP 출시`/`출시 이후`)·Must
3. `product/platform.md` — 클라이언트·인증 전제
4. `decisions/002-domain-split-vercel-api.md` — 도메인 분리 확정
5. `product/mvp.md` — MVP In/Out
6. `product/prd.md` + `business-rules/` · `glossary.md`
7. `architecture/erd.md` + `architecture/api-response.md`
8. `specs/{feature}.md` — [`specs/README.md`](specs/README.md)에서 선택 (`.claude/skills/specify`)
9. 구현 후 `docs/`·이슈 동기화 (하네스 G3 검증 · G4 회고 게이트)

**priority: must/could·`[미정]`:** `.claude/rules/core-scope.md`

## 런타임 vs 문서

| 항목 | 문서 (SSOT) | 실제 구현 |
|------|-------------|-----------|
| API JSON 계약 | `architecture/api-response.md` (**확정**) | `GlobalExceptionHandler`, DTO envelope |
| DB 스키마 | `architecture/erd.md` | JPA 엔티티 + Hibernate `ddl-auto` (Flyway 금지) |
| 패키지 레이아웃 | `architecture/package-layout.md` | 도메인별 패키지 디렉터리 |
| 설정·프로필 | `architecture.md` | `application-{profile}.yml` |
| 배포 절차 | [`../deploy/README.md`](../deploy/README.md) | `deploy/`, 루트 `docker-compose.yml` |
| 환경 변수·인프라 | [`../deploy/environment-reference.md`](../deploy/environment-reference.md) | GitHub Secrets, EC2 인프라 |
| VPC·SG 심화 | [`../deploy/ec2-split-deployment.md`](../deploy/ec2-split-deployment.md) | AWS 인프라 (참고) |

ERD는 **MySQL 8.0** 기준.

## 관련 경로

| 경로 | 용도 |
|------|------|
| [`AGENTS.md`](../AGENTS.md) | AI·개발자 프로젝트 지도 |
| [`harness-engineering.md`](harness-engineering.md) | AI 하네스 엔지니어링 총정리 (구조·인시던트·수치) |
| [`deploy/README.md`](../deploy/README.md) | Docker·EC2 배포 |
| [`.dev/README.md`](../.dev/README.md) | 임시 작업 로그 (장기 문서는 여기로 이관) |
| [`.claude/rules/README.md`](../.claude/rules/README.md) | Claude Code AI 규칙·스킬 |

## 스펙 작성

큰 기능은 `docs/specs/{domain}/{kebab-case}.md`에 작성합니다(도메인 폴더: `specs/README.md` 매핑 표). 템플릿: `.claude/skills/specify/references/spec-template.md`
