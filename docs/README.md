# TripFit 문서 (`docs/`)

기획·아키텍처·스펙의 **단일 진실 공급원(SSOT)** 입니다.  
코드·배포 설정과 충돌 시: **PRD > MVP 범위 컷 > 구현 코드** 순으로 해석하고, 불일치는 스펙 또는 이 README에 기록합니다.

에이전트 행동 규칙: [`.claude/rules/README.md`](../.claude/rules/README.md) · 하네스 코어 [`harness-workflow.md`](../.claude/rules/harness-workflow.md)

## 디렉터리 맵

```
docs/
├── README.md                 ← 이 파일 (인덱스)
├── architecture.md           ← 레이어·패키지·설정·DB 요약
├── architecture/
│   ├── erd.md
│   └── api-response.md       ← REST JSON envelope (확정)
├── product/                  ← 기획 SSOT
│   ├── development-wave.md   ← Wave 운영·판단·GitHub Backlog
│   ├── waves.md              ← Wave 1~4 요약표
│   ├── mvp.md · platform.md · prd.md · glossary.md
│   ├── design/ · business-rules/ · flows/ · templates/
├── specs/                    ← 기능 스펙 — 목록 SSOT: specs/README.md
└── decisions/                ← 아키텍처 결정 — 목록: decisions/README.md
```

배포 심화 문서(구 EC2 분리 가이드)는 [`deploy/ec2-split-deployment.md`](../deploy/ec2-split-deployment.md)로 이동 — 배포 관련 문서는 `deploy/` 한 디렉터리에 모음.

**상세 목록은 하위 README를 SSOT로 둔다** (이 파일에 스펙 전수를 중복하지 않음).

| 하위 인덱스 | 내용 |
|-------------|------|
| [`specs/README.md`](specs/README.md) | wave별 스펙·이슈 매핑·상태 |
| [`decisions/README.md`](decisions/README.md) | ADR 목록 |
| [`product/flows/README.md`](product/flows/README.md) | 사용자 플로우 |
| [`product/business-rules/README.md`](product/business-rules/README.md) | BR-* |

## 읽는 순서 (기능 구현 시)

`.claude/rules/harness-workflow.md`의 Before Coding 순서와 동일 — 두 목록이 따로 손으로 유지되지 않도록 여기서만 상세를 두고, harness는 이 순서를 그대로 참조한다.

1. `architecture.md` — 레이어·패키지 구조
2. `product/development-wave.md` — 활성 Wave·Must (Backlog `#29`~`#32`)
3. `product/waves.md` — 요약표
4. `product/platform.md` — 클라이언트·인증 전제
5. `decisions/002-domain-split-vercel-api.md` — 도메인 분리 확정
6. `product/mvp.md` — MVP In/Out
7. `product/prd.md` + `business-rules/` · `glossary.md`
8. `architecture/erd.md` + `architecture/api-response.md`
9. `specs/{feature}.md` — [`specs/README.md`](specs/README.md)에서 선택 (`.claude/skills/specify`)
10. 구현 후 `docs/`·이슈 동기화 (하네스 After Coding)

**Wave Must/Nice/Out·`[미정]`:** `.claude/rules/harness-wave.md`

## 런타임 vs 문서

| 항목 | 문서 (SSOT) | 실제 구현 |
|------|-------------|-----------|
| API JSON 계약 | `architecture/api-response.md` (**확정**) | `GlobalExceptionHandler`, DTO envelope |
| DB 스키마 | `architecture/erd.md` | JPA 엔티티 + Hibernate `ddl-auto` (Flyway 금지) |
| 설정·프로필 | `architecture.md` | `application-{profile}.yml` |
| 배포 절차 | [`../deploy/README.md`](../deploy/README.md) | `deploy/`, 루트 `docker-compose.yml` |
| VPC·SG 심화 | [`../deploy/ec2-split-deployment.md`](../deploy/ec2-split-deployment.md) | AWS 인프라 (참고) |

ERD는 **MySQL 8.0** 기준.

## 관련 경로

| 경로 | 용도 |
|------|------|
| [`AGENTS.md`](../AGENTS.md) | AI·개발자 프로젝트 지도 |
| [`deploy/README.md`](../deploy/README.md) | Docker·EC2 배포 |
| [`.dev/README.md`](../.dev/README.md) | 임시 작업 로그 (장기 문서는 여기로 이관) |
| [`.claude/rules/README.md`](../.claude/rules/README.md) | Claude Code AI 규칙·스킬 |

## 스펙 작성

큰 기능은 `docs/specs/{kebab-case}.md`에 작성합니다. 템플릿: `.claude/skills/specify/references/spec-template.md`
