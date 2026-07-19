# TripFit 문서 (`docs/`)

기획·아키텍처·스펙의 **단일 진실 공급원(SSOT)** 입니다.  
코드·배포 설정과 충돌 시: **PRD > MVP 범위 컷 > 구현 코드** 순으로 해석하고, 불일치는 스펙 또는 이 README에 기록합니다.

## 디렉터리 맵

```
docs/
├── README.md                 ← 이 파일
├── architecture.md           ← 레이어·패키지·설정·DB 요약
├── architecture/
│   ├── erd.md                ← 테이블·관계 정의 (MVP 6테이블)
│   ├── api-response.md       ← REST JSON envelope 초안 (프론트 합의 전)
│   └── ec2-split-deployment.md
├── product/
│   ├── waves.md              ← Wave 1~4 **요약표**
│   ├── development-wave.md   ← **Wave 운영·판단·GitHub SSOT**
│   ├── mvp.md                ← MVP In/Out 범위
│   ├── platform.md           ← React·스토어·API 전제 (Agent)
│   ├── prd.md                ← 제품 요구 원본
│   ├── glossary.md           ← 도메인 용어
│   ├── design/               ← Figma 와이어프레임 요약
│   ├── business-rules/       ← BR-* 규칙
│   └── flows/                ← 사용자 플로우
├── specs/                    ← 기능 스펙 (implement 전)
│   ├── auth-social-login.md              ← wave 1 소셜 로그인·JWT (Approved)
│   ├── user-onboarding.md                ← wave 1 이름·온보딩 (재진입 D-REENTRY-2 · #22 부분 확정)
│   ├── schedule-participation-onboarding.md ← wave 1 join 게이트 **Draft** (#22 D-JOIN-ENTRY·CLEAR·TRIP-FLOW 부분 확정)
│   ├── user-my-page.md                   ← wave 1 마이페이지 이름 수정 PATCH API
│   ├── schedule-unified.md               ← wave 2 정기/개별 2테이블 (Approved, #11)
│   ├── schedule-calendar-resolve.md      ← wave 2 달력 effective (Approved, A1=730일)
│   ├── trip-room-api.md                  ← wave 2 여행방·참여·홈 2뷰·Pin (#12 · D5 amend · submit→#22)
│   ├── trip-recommendation.md            ← wave 2 추천 4모드·확정 (Draft)
│   ├── auth-token-rotation.md            ← wave 4 RTR + Redis (Draft)
│   ├── auth-apple-server-notifications.md  ← Apple S2S webhook (스토어 제출 전)
│   └── user-profile-image-s3-mirror.md   ← wave 4 프로필 이미지 S3 미러링 B안 (Draft)
├── decisions/                ← 인프라·아키텍처 확정 (003 architecture guide, …, 007 user onboarding, …)
└── prompts/                  ← NotebookLM 01~04 + Cursor import 체크리스트
```

## 읽는 순서 (기능 구현 시)

1. `product/development-wave.md` — **Wave 운영·판단·Backlog** (활성 Wave·Must 확인)
2. `product/waves.md` — wave 요약표
3. `product/mvp.md` — 범위 확인
4. `product/platform.md` — 클라이언트(Vercel)·API(`api.tripfit.online`) 전제
5. `product/prd.md` + `business-rules/` — 상세 요구
6. `architecture.md` + `architecture/erd.md` + `architecture/api-response.md` — 레이어·스키마·API 계약
7. `specs/{feature}.md` — 이번 작업 스펙 (`.cursor/skills/specify`로 작성)
8. 구현 후 `docs/`와 코드 동기화

## 런타임 vs 문서

| 항목 | 문서 (SSOT) | 실제 구현 |
|------|-------------|-----------|
| API JSON 계약 | `architecture/api-response.md` (Draft) | 합의 후 `@RestControllerAdvice`, DTO envelope |
| DB 스키마 | `architecture/erd.md` | JPA 엔티티 + Hibernate `ddl-auto` |
| 설정·프로필 | `architecture.md` | `application-{profile}.yml` |
| 배포 절차 | `../deploy/README.md` | `deploy/`, 루트 `docker-compose.yml` |
| VPC·SG 심화 | `architecture/ec2-split-deployment.md` | AWS 인프라 (참고) |

기획 수정 반영: [`prompts/README.md`](prompts/README.md) — NotebookLM **01→02→03→(04)**, repo는 Cursor [`cursor-import-checklist.md`](prompts/cursor-import-checklist.md). ERD는 **MySQL 8.0** 기준.

## 관련 경로

| 경로 | 용도 |
|------|------|
| [`AGENTS.md`](../AGENTS.md) | AI·개발자 프로젝트 지도 |
| [`deploy/README.md`](../deploy/README.md) | Docker·EC2 배포 |
| [`.dev/README.md`](../.dev/README.md) | 임시 작업 로그 (장기 문서는 여기로 이관) |
| [`.cursor/README.md`](../.cursor/README.md) | Cursor AI 규칙·스킬 |

## 스펙 작성

큰 기능은 `docs/specs/{kebab-case}.md`에 작성합니다. 템플릿: `.cursor/skills/specify/references/spec-template.md`
