# TripFit 배포 (`deploy/`)

Docker Compose 기반 배포 설정. **역할별로 분리**되어 있습니다.

## 구조

| 경로 | 서버 | 설명 |
|------|------|------|
| [`app/`](app/) | EC2 A | Spring Boot — **GHCR 이미지 pull** (빌드 없음) |
| [`mysql/`](mysql/) | EC2 B | MySQL 8.0 전용 |
| [`../docker-compose.yml`](../docker-compose.yml) | 로컬 | App build + MySQL 한 번에 (개발용) |

상세 네트워크·SG 설계: [`docs/architecture/ec2-split-deployment.md`](../docs/architecture/ec2-split-deployment.md)

## 빠른 시작

### 로컬 (단일 호스트)

```bash
cp .env.example .env
docker compose up -d --build
./scripts/verify-deploy.sh
```

### EC2 B — MySQL

```bash
cd deploy/mysql
cp .env.example .env    # MYSQL_ROOT_PASSWORD 변경
docker compose up -d
```

### EC2 A — App

```bash
cd deploy/app
cp .env.example .env    # MYSQL_HOST = EC2 B private IP, GHCR_IMAGE 설정
export SPRING_DATASOURCE_USERNAME=...
export SPRING_DATASOURCE_PASSWORD=...
# private package: export GHCR_PAT=... GHCR_USERNAME=...
../../scripts/ec2-deploy-app.sh
../../scripts/verify-deploy-app.sh
```

## 환경 변수

| 변수 | app/.env | mysql/.env | Git |
|------|----------|------------|-----|
| `MYSQL_HOST` | ✅ EC2 B IP | — | example만 |
| `MYSQL_ROOT_PASSWORD` | — | ✅ | example만 |
| `SPRING_DATASOURCE_*` | ✅ (또는 export) | — | example만 |
| `GHCR_IMAGE` | ✅ | — | example만 |
| 실제 `.env` | 서버에만 | 서버에만 | **커밋 금지** |

DB 계정은 root 대신 `tripfit_app` 등 least-privilege 권장 (가이드 문서 SQL 참고).

## CI/CD

`.github/workflows/ci-cd.yml`:

- PR / `main` → `./gradlew test`
- `main` push → Docker build → GHCR push → EC2 A SSH deploy

필요 GitHub Secrets: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_DEPLOY_PATH`, `GHCR_PAT`, `GHCR_USERNAME`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

## 프로필·스키마 단계

| 단계 | `SPRING_PROFILES_ACTIVE` | ddl-auto | Flyway |
|------|--------------------------|----------|--------|
| 1 (현재) | dev | update | off |
| 3 (운영) | prod | validate | on |

자세한 규칙: `.cursor/rules/deployment.mdc`, `docs/architecture.md`

## 검증 스크립트

| 스크립트 | 사용 시점 |
|----------|-----------|
| `scripts/verify-deploy.sh` | 루트 `docker-compose` (app + mysql 컨테이너) |
| `scripts/verify-deploy-app.sh` | EC2 A 분리 (원격 MySQL) |
| `scripts/ec2-deploy-app.sh` | EC2 A pull + up + readiness 대기 |
