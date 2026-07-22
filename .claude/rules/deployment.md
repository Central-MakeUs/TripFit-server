---
paths:
  - "**/application*.yml"
  - "**/docker-compose.yml"
  - "**/Dockerfile"
  - "**/domain/**"
  - "deploy/**"
---

# Deployment Rules

**SSOT — 중복 작성 금지, 링크만 참조:**

| 주제 | 문서 |
|------|------|
| 배포 절차·환경변수·검증 스크립트 | [`deploy/README.md`](../../deploy/README.md) |
| 프로필·ddl-auto·레이어 | [`docs/architecture.md`](../../docs/architecture.md) |
| VPC·SG·1→2 EC2 마이그레이션 | [`deploy/ec2-split-deployment.md`](../../deploy/ec2-split-deployment.md) |

## 도메인 (확정)

- **프론트** `tripfit.online` → Vercel (이 repo에 frontend Docker **없음**)
- **API** `api.tripfit.online` → EC2 `deploy/app/` (Nginx + Certbot + app)
- `docs/decisions/002-domain-split-vercel-api.md` — Agent 재확인·`FRONTEND_IMAGE` 추가 **금지**

## 스키마

- **Flyway / Liquibase / SQL 마이그레이션 미사용·작성 금지.** Hibernate `ddl-auto`만 — 프로필별 값은 `architecture.md` SSOT.
- **상용 보존 데이터 없음(dev).** 스키마 변경 시 엔티티를 최신 형태로만 두고 DB는 리셋(`docker compose down -v` 등). 구 스키마 호환·데이터 보존 마이그레이션 코드 금지. 상세: `harness-workflow.md` ⛔ DB 스키마 절.
- prod `update`: 기동 시 엔티티 기준으로 스키마 자동 반영. 스키마 변경은 local/dev에서 검증 후 배포(필요 시 volume 재생성).

## MySQL / JPA 주의

- `globally_quoted_identifiers: true` **사용 금지** (TEXT quoting 등과 조합 시 DDL 실패 유발)
- 스키마 drift 원인은 보통 **단일 설정이 아니라** TEXT quoting + 예약어(`user`, `rank`) + dialect + naming strategy **조합**
- 예약어 컬럼: `@Column(name = "...")` (`rank` → `recommendation_rank`)
- 테이블명: **`users`** (구 `user` — MySQL 예약어 회피). Java 엔티티는 `User`

## 검증

- 로컬: `./scripts/verify-deploy.sh`
- EC2 A: `./scripts/verify-deploy-app.sh`
- CI: `.github/workflows/ci-cd.yml`
- `.env`·Secrets 커밋 금지. `deploy/app/.env.example`는 placeholder만.
- 스키마 실험 중 DB 리셋: `docker compose down -v` (운영 데이터 있을 때 **금지**, 훅으로 차단)
