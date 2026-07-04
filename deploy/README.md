# TripFit 배포 (`deploy/`)

Docker Compose 기반 배포 설정. **배포 운영 SSOT** — 절차·환경변수·검증 스크립트는 이 문서를 기준으로 합니다.

| 관련 문서 | 용도 |
|-----------|------|
| [`docs/decisions/002-domain-split-vercel-api.md`](../docs/decisions/002-domain-split-vercel-api.md) | 도메인 분리 확정 |
| [`docs/architecture.md`](../docs/architecture.md) | 프로필·ddl-auto·레이어 |
| [`docs/architecture/ec2-split-deployment.md`](../docs/architecture/ec2-split-deployment.md) | VPC·SG·1→2 EC2 심화 |
| [`.cursor/rules/deployment.mdc`](../.cursor/rules/deployment.mdc) | 에이전트 배포 가드레일 |

역할별로 분리되어 있습니다.

## 도메인 구조 (확정)

| 도메인 | 호스팅 | 이 repo |
|--------|--------|---------|
| `tripfit.online` | **Vercel** (React/Next.js) | 프론트 저장소 — EC2에 frontend 컨테이너 **없음** |
| `api.tripfit.online` | **EC2 A** (Nginx + Spring Boot) | `deploy/app/`, `deploy/nginx/` |

**Route 53**

- `tripfit.online` → Vercel DNS (CNAME/A, Vercel 대시보드 안내 따름)
- `api.tripfit.online` → EC2 A Elastic IP (A 레코드)

**프론트 환경 변수 (Vercel)**

```env
NEXT_PUBLIC_API_BASE_URL=https://api.tripfit.online
```

상세: [`docs/decisions/002-domain-split-vercel-api.md`](../docs/decisions/002-domain-split-vercel-api.md)

## 구조

| 경로 | 서버 | 설명 |
|------|------|------|
| [`app/`](app/) | EC2 A | Nginx(:80/443) + Certbot + Spring Boot API |
| [`nginx/`](nginx/) | EC2 A | `api.tripfit.online` 리버스 프록시 |
| [`mysql/`](mysql/) | EC2 B | MySQL 8.0 전용 |
| [`../docker-compose.yml`](../docker-compose.yml) | 로컬 | App build + MySQL (`--profile edge` 시 API Nginx) |

상세 네트워크·SG: [`docs/architecture/ec2-split-deployment.md`](../docs/architecture/ec2-split-deployment.md)

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
cp .env.example .env
docker compose up -d
```

### EC2 A — API + HTTPS (Route 53 `api.tripfit.online` 연결 후)

```bash
cd deploy/app
cp .env.example .env
# .env: MYSQL_HOST, GHCR_IMAGE
export SPRING_DATASOURCE_USERNAME=...
export SPRING_DATASOURCE_PASSWORD=...
export CERTBOT_EMAIL=codus5068@naver.com
# private GHCR: export GHCR_PAT=... GHCR_USERNAME=...

../../scripts/setup-api-https.sh
```

또는 단계별:

```bash
../../scripts/ec2-deploy-app.sh          # nginx + certbot + app (임시 self-signed)
../../scripts/init-letsencrypt.sh        # Let's Encrypt 실제 인증서
VERIFY_TLS=true ../../scripts/verify-deploy-app.sh
```

검증:

```bash
curl -fsSI https://api.tripfit.online/health
curl -fsSI https://api.tripfit.online/api/v1/...   # API 구현 후
```

**스택 구성** (`deploy/app/docker-compose.yml`)

| 서비스 | 역할 |
|--------|------|
| `nginx` | `api.tripfit.online` :80/:443 → `app:8080` |
| `certbot` | LE 발급·12h 갱신 시도 |
| `app` | Spring Boot (GHCR), `127.0.0.1:8080`만 바인딩 |

**cron 갱신** (갱신 시 nginx reload 포함):

```bash
0 3 * * * cd /path/to/TripFit-server/deploy/app && /path/to/TripFit-server/scripts/renew-letsencrypt.sh
```

### EC2 A — API (HTTPS 생략, dev만)

```bash
../../scripts/setup-api-https.sh --skip-tls
```

## 환경 변수

| 변수 | app/.env | 설명 |
|------|----------|------|
| `MYSQL_HOST` | ✅ | EC2 B private IP |
| `GHCR_IMAGE` | ✅ | Spring Boot 이미지 |
| `CERTBOT_DOMAIN` | ✅ (기본 `api.tripfit.online`) | LE 인증서 도메인 |
| `NGINX_HTTP_PORT` / `NGINX_HTTPS_PORT` | ✅ | 기본 80 / 443 |

`FRONTEND_IMAGE` **사용하지 않음** — 프론트는 Vercel.

## CI/CD

`.github/workflows/ci-cd.yml` — `main` push → GHCR push → EC2 A deploy (app + nginx + certbot)

## 검증 스크립트

| 스크립트 | 사용 시점 |
|----------|-----------|
| `scripts/verify-deploy.sh` | 로컬 compose |
| `scripts/setup-api-https.sh` | **배포 + LE 발급 일괄** (권장) |
| `scripts/ec2-deploy-app.sh` | pull + up (임시 self-signed) |
| `scripts/init-letsencrypt.sh` | LE 최초 발급 (`api.tripfit.online`) |
| `scripts/renew-letsencrypt.sh` | 갱신 + nginx reload (cron) |
| `scripts/verify-deploy-app.sh` | EC2 A 검증 (`VERIFY_TLS=true`) |
