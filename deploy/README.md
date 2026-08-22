# TripFit 배포 (`deploy/`)

Docker Compose 기반 배포 설정. **배포 운영 SSOT** — 절차·환경변수·검증 스크립트는 이 문서를 기준으로 합니다.

| 관련 문서 | 용도 |
|-----------|------|
| [`docs/decisions/002-domain-split-vercel-api.md`](../docs/decisions/002-domain-split-vercel-api.md) | 도메인 분리 확정 |
| [`docs/architecture.md`](../docs/architecture.md) | 프로필·ddl-auto·레이어 |
| [`ec2-split-deployment.md`](ec2-split-deployment.md) | VPC·SG·1→2 EC2 심화 |
| [`.claude/rules/deployment.md`](../.claude/rules/deployment.md) | 에이전트 배포 가드레일 |

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

상세 네트워크·SG: [`ec2-split-deployment.md`](ec2-split-deployment.md)

## 빠른 시작

### 로컬 (MySQL Docker + bootRun)

```bash
cp .env.example .env
docker compose up -d              # MySQL만
./gradlew bootRun                 # Spring (localhost:8080, .env 로드)
```

Spring까지 Docker로 검증할 때만:

```bash
docker compose --profile app up -d --build
./scripts/verify-deploy.sh
```

### EC2 B — MySQL

```bash
cd deploy/mysql
cp .env.example .env
docker compose up -d
```

### EC2 A — API + HTTPS (Route 53 `api.tripfit.online` 연결 후)

**최초 1회 수동 부트스트랩 전용** — TLS 발급까지 끝내고 나면 이후 배포는 `main` push → CI/CD가 GitHub Secrets만으로 처리한다 (아래 [환경 변수](#환경-변수) 절). `.env`는 이 최초 설정과 수동 재배포(`ec2-deploy-app.sh` 단독 실행) 때만 쓴다.

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

### GitHub Actions Secrets (전체 — push 시 자동 주입, EC2 SSH·`.env` 수정 불필요)

**`main` push → CI/CD deploy** 단계에서 아래 Secrets를 SSH 세션 env로 받아 EC2에서 `export` 후 `docker compose up -d`를 실행한다.
**한 번 등록해두면 이후 배포는 GitHub Secrets만 갱신하면 되고, EC2에 SSH로 들어가 `deploy/app/.env`를 만들거나 수정할 필요가 없다.**

| Secret | 필수 | 설명 |
|--------|------|------|
| `MYSQL_HOST` | ✅ | EC2 B private IP |
| `MYSQL_PORT` | | 기본 3306 |
| `MYSQL_DATABASE` | ✅ | DB 이름 (`tripfit`) |
| `SPRING_DATASOURCE_USERNAME` | ✅ | DB 계정 |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | DB 비밀번호 |
| `SPRING_PROFILES_ACTIVE` | | 기본 `dev` |
| `APP_PORT` | | Spring 컨테이너 바인딩 포트, 기본 8080 |
| `NGINX_HTTP_PORT` / `NGINX_HTTPS_PORT` | | 기본 80 / 443 |
| `CERTBOT_DOMAIN` | | 기본 `api.tripfit.online` |
| `JWT_SECRET` | ✅ | Access JWT 서명 키 (256bit+ random) |
| `JWT_ACCESS_EXPIRATION` | | 기본 7200초(2h) |
| `JWT_REFRESH_EXPIRATION_DAYS` | | 기본 30일 |
| `GOOGLE_CLIENT_ID` | | Google 로그인 web client ID (`aud` 검증 · authorization code 교환) |
| `GOOGLE_CLIENT_SECRET` | | Google 로그인 web client secret (authorization code 교환) |
| `GOOGLE_CLIENT_ID_IOS` | | Google iOS client ID |
| `GOOGLE_CLIENT_ID_ANDROID` | | Google Android client ID |
| `GOOGLE_CALENDAR_CLIENT_ID` | | Google Calendar 연동 전용 client ID — 로그인과 분리(`docs/specs/google-calendar-client-id-separation.md`), Calendar FE 착수 전까지는 미등록 상태 |
| `GOOGLE_CALENDAR_CLIENT_SECRET` | | 위 Calendar 전용 client의 secret — authorization code·refresh token 교환 |
| `SOCIAL_TOKEN_AES_KEY` | ✅ (Calendar 연동 시) | Base64 인코딩 32바이트 AES-256 키 — 없으면 연동 API 호출 시 500 |
| `APPLE_BUNDLE_ID` | | Apple App ID(Bundle ID, 예: `com.tripfit.app`) — iOS 네이티브 앱 로그인 `aud` 검증·토큰교환/revoke `client_id` |
| `APPLE_SERVICE_ID` | | Apple Services ID — 모바일 브라우저 로그인 경로 `aud` 검증·토큰교환/revoke `client_id` (`docs/specs/apple-oauth-multi-audience.md`) |
| `FIREBASE_CREDENTIALS_BASE64` | ✅ (알림 연동 시) | Firebase 서비스 계정 JSON 전체를 base64 인코딩한 값 (`docs/specs/notification.md` D4) — 파일을 컨테이너에 올리지 않고 env로만 전달 |

등록 위치: GitHub repo → **Settings → Secrets and variables → Actions**

기존 SSH·이미지 Secrets(`EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_DEPLOY_PATH`, `GHCR_PAT`, `GHCR_USERNAME`)와 함께 사용한다.
`deploy/app/docker-compose.yml`의 `app.environment`가 위 변수를 Spring Boot 컨테이너로 넘긴다.

`MYSQL_HOST`·`MYSQL_DATABASE`·`SPRING_DATASOURCE_*`·`JWT_SECRET`·`SOCIAL_TOKEN_AES_KEY`(Calendar 연동 시)는 **필수** — 미등록 상태로 push하면 CI/CD 배포 단계가 즉시 실패한다(fail-fast). 나머지는 비어 있으면 `docker-compose.yml`에 박힌 기본값을 그대로 쓴다.

`FRONTEND_IMAGE` **사용하지 않음** — 프론트는 Vercel.

**`deploy/app/.env`는 이제 필수 아님** — CI/CD 자동 배포는 위 Secrets만으로 완결된다. 다만 `scripts/ec2-deploy-app.sh`로 **수동** 배포하거나 로컬에서 직접 `docker compose`를 띄울 때는 여전히 `.env` 또는 `export`로 값을 넘겨야 한다 (`deploy/app/.env.example` 참고). `CERTBOT_EMAIL`은 최초 1회 `init-letsencrypt.sh`/`setup-api-https.sh` 수동 실행 시에만 필요해 CI/CD 화이트리스트에는 포함하지 않았다.

로컬: 루트 `.env` 또는 `deploy/app/.env.example` 참고. 상세 스펙: `docs/specs/auth-social-login.md`

### 스토어 제출 전 OAuth 콘솔 설정 체크리스트

위 `GOOGLE_CLIENT_ID`류는 env에 client id 문자열만 등록하면 되지만, **각 소셜 로그인 콘솔(Google Cloud Console 등) 쪽 설정은 별도로 채워야** 실제 로그인이 동작한다. 코드·검증 로직(`GoogleTokenVerifier` 등)은 이미 완료된 상태 — 아래는 콘솔에서 값만 등록하면 되는 항목.

| 항목 | 콘솔 | 채울 수 있는 시점 |
|------|------|-------------------|
| 승인된 자바스크립트 원본 | Google Cloud Console (`GOOGLE_CLIENT_ID` Web 타입) | 프론트 최종 도메인 확정 후 |
| 승인된 리다이렉션 URI | Google Cloud Console (`GOOGLE_CLIENT_ID` Web 타입) | 프론트 콜백 라우트 확정 후 (환경 B, `docs/product/platform.md`) |
| App Store ID (선택 필드) | Google Cloud Console (`GOOGLE_CLIENT_ID_IOS`) | 앱이 App Store에 실제 게시된 후 |
| Apple/Android 로그인 동일 설정값 | Apple Developer / Google Play Console | 각 콘솔 요구 시점에 맞춰 |

미등록 상태로는 `redirect_uri_mismatch` 등으로 로그인 자체가 실패하므로 **스토어 심사 제출 전 반드시 확인** — 추적: [#62](https://github.com/Central-MakeUs/TripFit-server/issues/62)

## CI/CD

`.github/workflows/ci-cd.yml` — `main` push → GHCR push → EC2 A deploy (app + nginx + certbot)
deploy 시 **위 GitHub Secrets 전부가 app 컨테이너에 자동 주입**된다 — EC2 쪽 `.env` 유무와 무관하게 동작한다.

## 검증 스크립트

| 스크립트 | 사용 시점 |
|----------|-----------|
| `scripts/verify-deploy.sh` | 로컬 compose |
| `scripts/setup-api-https.sh` | **배포 + LE 발급 일괄** (권장) |
| `scripts/ec2-deploy-app.sh` | pull + up (임시 self-signed) |
| `scripts/init-letsencrypt.sh` | LE 최초 발급 (`api.tripfit.online`) |
| `scripts/renew-letsencrypt.sh` | 갱신 + nginx reload (cron) |
| `scripts/verify-deploy-app.sh` | EC2 A 검증 (`VERIFY_TLS=true`) |
