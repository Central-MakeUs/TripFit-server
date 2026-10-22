# TripFit 배포 (`deploy/`)

TripFit 서버의 로컬 개발 구동, EC2 5대 인프라 배포 절차, HTTPS 인증서 발급 및 운영 스크립트 실행법을 다루는 가이드다. 각 서버별 기동 순서, Let's Encrypt TLS 발급, CI/CD 자동 배포 흐름, 스토어 심사 전 OAuth 콘솔 등록 체크리스트를 설명한다. 상세 인프라 사양 및 환경 변수(GitHub Secrets) 전체 목록은 [`deploy/environment-reference.md`](environment-reference.md)를 참고한다.

| 관련 문서 | 용도 |
|-----------|------|
| [`environment-reference.md`](environment-reference.md) | **인프라 사양 및 환경 변수(Secrets) 전체 표 (SSOT)** |
| [`docs/decisions/002-domain-split-vercel-api.md`](../docs/decisions/002-domain-split-vercel-api.md) | 도메인 분리 확정 |
| [`docs/architecture.md`](../docs/architecture.md) | 프로필·ddl-auto·레이어 |
| [`ec2-split-deployment.md`](ec2-split-deployment.md) | VPC·SG·1→2 EC2 심화 |
| [`holiday-api-setup.md`](holiday-api-setup.md) | 공휴일 API 인증키 발급·등록·검증 |
| [`.claude/rules/deployment.md`](../.claude/rules/deployment.md) | 에이전트 배포 가드레일 |

역할별로 분리되어 있습니다.

## 도메인 구조 (확정)

| 도메인 | 호스팅 | 이 repo |
|--------|--------|---------|
| `tripfit.online` | **Vercel** (React/Next.js) | 프론트 저장소 — EC2에 frontend 컨테이너 **없음** |
| `api.tripfit.online` | **EC2 A** (Nginx + Spring Boot) | `deploy/app/`, `deploy/nginx/` |
| `grafana.tripfit.online` | **EC2 C** (Nginx + Grafana) | `deploy/monitoring/` — 운영용, 클라이언트 앱과 무관 |

**Route 53**

- `tripfit.online` → Vercel DNS (CNAME/A, Vercel 대시보드 안내 따름)
- `api.tripfit.online` → EC2 A Elastic IP (A 레코드)
- `grafana.tripfit.online` → EC2 C Elastic IP (A 레코드)

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
| [`monitoring/`](monitoring/) | EC2 C | Loki(:3100) + Grafana(:3000) — A/B 컨테이너 로그 수집·조회 |
| [`redis/`](redis/) | EC2 D | Redis(:6379) — refresh token 저장소 + 공휴일 캐시, `sg-app`에서만 접근 |
| [`metrics/`](metrics/) | EC2 E | Prometheus(:9090) + AI 원인 리포터(:9095) — 앱·DB 수치 수집, 임계점 초과 시 AI 리포트를 Discord로 (#126) |
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

B에는 CI 배포가 없다. 호스트 수치 수집기(node_exporter, #126)는 비밀값이 없어서 B의 저장소(`~/TripFit-server`)를 main으로 맞춘 뒤 한 번만 띄운다. 9100은 보안 그룹에서 EC2 E만 허용한다.

```bash
cd ~/TripFit-server/deploy/mysql && git pull --ff-only && docker compose up -d node-exporter
```

### EC2 D — Redis (`docs/decisions/010-redis-infra.md`)

프로비저닝 완료(2026-08-10) — `TP-redis`(`i-06fb8540484834192`), A/B/C와 동일 VPC·서브넷·키페어, t3.micro. Private IP `172.31.38.246`, 보안 그룹 `TP-redis`가 6379를 `sg-app`(TP-server)에서만 허용. Elastic IP 없음 — App만 사설 IP로 접근하고 외부 도메인이 필요 없는 내부 인프라라 EC2 A/C와 다름.

```bash
cd deploy/redis
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

**cron 갱신** (갱신 확인 후 매번 nginx reload — `certbot` 컨테이너가 먼저 갱신해도 반영되도록):

```bash
# 최초 1회 — ubuntu 사용자는 /var/log에 파일을 만들 수 없어, 없으면 리다이렉트 단계에서 cron이 매번 실패한다
sudo touch /var/log/tripfit-certbot-renew.log && sudo chown ubuntu:ubuntu /var/log/tripfit-certbot-renew.log
```

```bash
0 3 * * * cd /home/ubuntu/TripFit-server/deploy/app && /home/ubuntu/TripFit-server/scripts/renew-letsencrypt.sh >> /var/log/tripfit-certbot-renew.log 2>&1
```

**nginx 설정 반영:** `nginx.conf`는 파일 단위 bind mount라 `git pull`로 바뀌어도 실행 중인 컨테이너는 구 파일을 본다. CI 배포는 새 컨테이너로 `nginx -t` 검증 후 `tripfit-nginx`를 재시작한다. 수동으로 설정을 바꿨다면 reload가 아니라 `docker restart tripfit-nginx`를 쓴다.

### EC2 A — API (HTTPS 생략, dev만)

```bash
../../scripts/setup-api-https.sh --skip-tls
```

### EC2 C — 모니터링 (Loki + Grafana, `https://grafana.tripfit.online`)

결정 근거: [`docs/decisions/009-observability-logging.md`](../docs/decisions/009-observability-logging.md) (Issue #77). A/B와 같은 VPC·서브넷·키페어 재사용, 별도 t3.micro. Elastic IP 보유(EC2 A와 동일 이유 — Grafana URL·SSH가 인스턴스 재시작에 흔들리지 않도록).

**배포는 CI가 한다** — `.github/workflows/deploy-observability.yml`의 `deploy-monitoring` 작업이 `deploy/monitoring/`이 바뀐 main 반영 때(또는 Actions 화면의 수동 실행으로) C에 SSH로 접속해 `~/TripFit-server`를 main으로 맞추고, GitHub Secrets(`GRAFANA_ADMIN_PASSWORD`·`PROMETHEUS_HOST`·`REPORTER_TOKEN`)를 환경 변수로 넣어 `docker compose up -d`를 한다. C에서 손으로 `docker compose up`을 하지 않는다 — Secrets 값 없이 띄우면 Grafana가 Prometheus·리포터 주소 없이 재생성된다.

**git 체크아웃 전환 (2026-10-19 준비, 첫 CI 배포 때 완료)** — C는 원래 `~/monitoring`에 파일을 직접 복사해 돌렸다. 2026-10-19에 `~/monitoring`의 파일이 main과 모두 같음을 확인하고 `~/TripFit-server`에 저장소를 받아 두었다. compose 프로젝트 이름이 두 폴더 모두 `monitoring`이라 첫 CI 배포가 같은 볼륨(대시보드·알림·로그·인증서)을 그대로 이어받는다. 첫 CI 배포가 성공하면 아래 두 가지를 한 번 한다.

```bash
crontab -e   # 아래 한 줄로 바꾼다 — DEPLOY_DIR와 스크립트 경로가 저장소를 가리키게
```

```bash
0 3 * * * DEPLOY_DIR=/home/ubuntu/TripFit-server/deploy/monitoring CERTBOT_DOMAIN=grafana.tripfit.online /home/ubuntu/TripFit-server/scripts/renew-letsencrypt.sh >> /var/log/tripfit-certbot-renew.log 2>&1
```

- 옛 `~/monitoring`·`~/scripts`는 확인용으로 남겨 두었다가, 전환 뒤 갱신 cron이 한 번 성공하면 지운다. 전환이 끝나면 이 "git 체크아웃 전환" 문단도 현행 설명으로 정리한다.
- 갱신 cron은 `docker compose run certbot`만 하므로 Secrets 값이 필요 없다. 그래서 compose의 Secrets 값은 "없으면 빈 값"으로 두고, 빈 값 배포는 CI가 배포 전에 막는다.

**HTTPS (EC2 A `deploy/app`와 동일 패턴 — nginx+certbot, Let's Encrypt)** — C를 처음 만들 때(2026-08-01) 쓴 최초 설치 절차다. 지금 C는 인증서 발급이 끝나 다시 할 일이 없다. C를 새로 만들어 다시 발급해야 할 때만, 아래 `docker compose up -d` 대신 CI 수동 실행(`deploy-observability.yml`, target `monitoring`)으로 스택을 띄운 뒤 인증서 발급 스크립트만 실행한다.

```bash
export DEPLOY_DIR=$(pwd)/deploy/monitoring
export CERTBOT_DOMAIN=grafana.tripfit.online
../../scripts/ensure-ssl-certs.sh   # 임시 self-signed (nginx 443 기동용)
docker compose up -d
CERTBOT_EMAIL=codus5068@naver.com ../../scripts/init-letsencrypt.sh
```

- **스택**: `nginx`(:80/:443) → `grafana`(내부 `127.0.0.1:3000`만 바인딩, 공개 노출 없음), `certbot`(발급·갱신), `loki`(:3100, A/B가 push하는 대상 — 직접 브라우저 조회 대상 아님).
- Route 53: `grafana.tripfit.online` A → EC2 C Elastic IP. SG는 22/80/443만 공개하고, 3100은 아래 "SG 3100" 줄을 따른다.
- cron 갱신은 A와 같은 스크립트를 쓰고 도메인만 다르다(로그 파일 사전 생성도 A와 같음). 현행 cron 줄은 위 "git 체크아웃 전환"을 따른다.
- **A/B 쪽 최초 1회 필수 작업** — Loki가 뜨기 전에 A·B에서 각각 실행해야 `docker compose up -d`가 `driver: loki`로 성공한다:
  ```bash
  docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions
  ```
  (이미 `deploy/app/docker-compose.yml`, `deploy/mysql/docker-compose.yml` 상단 주석에도 동일 안내가 있음.)
- 로그 보존: `deploy/monitoring/loki-config.yaml`의 `retention_period`(확정 3일, 디스크 6.7GB 제약) + compactor가 자동 삭제 — 별도 cron 불필요.
- **프론트 공유 계정**: IP 제한 대신 Grafana Viewer role 읽기 전용 계정을 발급했다(재택·카페 등 IP가 매번 바뀌는 프론트 환경에 IP 화이트리스트는 맞지 않음). 계정 추가 발급은 admin으로 로그인 후 **Administration → Users** 또는:
  ```bash
  curl -u admin:$GRAFANA_ADMIN_PASSWORD -X POST https://grafana.tripfit.online/api/admin/users \
    -H 'Content-Type: application/json' \
    -d '{"name":"<이름>","login":"<아이디>","email":"<아이디>@tripfit.local","password":"<비밀번호>"}'
  # 기본 org role이 Viewer라 별도 설정 없이도 읽기 전용이지만, 명시적으로 확인하려면:
  curl -u admin:$GRAFANA_ADMIN_PASSWORD -X PATCH https://grafana.tripfit.online/api/org/users/<id> \
    -H 'Content-Type: application/json' -d '{"role":"Viewer"}'
  ```
- 기본 대시보드 "TripFit 서비스 로그 개요"(`uid: tripfit-overview`)가 홈 대시보드로 설정되어 있다 — 에러/경고 건수, nginx 4xx/5xx, app 에러·경고·전체 로그 스트림, nginx 오류 응답 로그. Explore 탭에서 직접 LogQL 조회도 가능.
- **#126 임계점 감시:** `grafana-provisioning/datasources/prometheus.yaml`(EC2 E Prometheus)과 `grafana-provisioning/alerting/tripfit-watch.yaml`(감시 규칙 6개·리포터 연락처)을 파일로 올린다. 규칙별 연락처 지정에 기능 스위치 `GF_FEATURE_TOGGLES_ENABLE=alertingSimplifiedRouting`이 필요하다. 기존 에러 로그 알림 4개는 운영 Grafana에 API로 만든 그대로다. 상세: [`add-prometheus.md`](../docs/specs/cross-cutting/add-prometheus.md)
- SG 3100은 A·B와 EC2 E(AI 원인 리포터의 로그 조회)에서만 허용한다.

### EC2 E — 수치 수집 + AI 원인 리포터 (Issue #126)

스펙: [`add-prometheus.md`](../docs/specs/cross-cutting/add-prometheus.md) · [`incident-ai-report.md`](../docs/specs/cross-cutting/incident-ai-report.md). 프로비저닝 2026-10-19 — `TP-metrics`(`i-0b2ffeefdb3fe200b`), A~D와 같은 VPC·서브넷·키페어(`TripFit-Keypair`), t3.micro, 디스크 16GB gp3, swap 1GB. Elastic IP `3.36.154.187`(CI SSH 접속용), private IP `172.31.43.92`.

- **스택** (`deploy/metrics/docker-compose.yml`): `prometheus`(:9090, 보관 15일·최대 2GB) — A의 앱(`/actuator/prometheus`)·A/B의 node_exporter(:9100)·리포터를 수집한다. `incident-reporter`(:9095) — C의 Grafana 감시 규칙이 부르면 수치·로그·코드를 모아 Gemini로 분석해 Discord로 보낸다. 이미지는 CI가 GHCR에 올린 것을 받는다.
- **보안 그룹 `TP-metrics`**: 22(SSH, CI 배포), 9090·9095는 `TP-monitoring`(C)에서만. E에서 나가는 수집은 A·B의 9100(각 SG에서 E만 허용)과 A nginx 80(`location = /actuator/prometheus`가 E private IP만 `allow`)으로 간다.
- **배포는 CI가 한다** — `deploy-observability.yml`의 `deploy-metrics` 작업이 `incident-reporter/`·`deploy/metrics/`가 바뀐 main 반영 때(또는 수동 실행으로) 리포터 테스트 → 이미지 빌드·GHCR 업로드 → E에 SSH → `~/TripFit-server`를 main으로 맞춤 → Secrets를 환경 변수로 넣어 `docker compose up -d` → Prometheus·리포터 응답 확인까지 한다. 서버 `.env`는 만들지 않는다.
- **최초 설정** (2026-10-19 완료, EC2 user data): Docker·compose·git 설치, `ubuntu`를 `docker` 그룹에 추가, swap 1GB, `~/TripFit-server` 저장소 받기, 운영자 SSH 공개키 추가. 다시 만들 때는 같은 순서를 따른다.
- **리포터 저장소 사본**: 리포터는 의심 코드를 짚으려고 컨테이너 볼륨(`reporter_source`)에 저장소를 따로 받아 두고, 분석 직전마다 `git pull --ff-only`로 main에 맞춘다. 처음 분석할 때 자동으로 받는다.
- **정상 동작 확인** (E에서): `curl -fsS localhost:9090/-/ready`(Prometheus) · `curl -fsS localhost:9095/metrics`(리포터) · Grafana Explore에서 Prometheus datasource로 `up` 조회 시 `tripfit-app`·`node-app`·`node-db`·`incident-reporter`가 1. CI 배포 작업도 앞의 두 개를 확인하고 끝난다.
- **배포 전 로컬 점검**: `deploy/metrics/docker-compose.local.yml` — Prometheus·Loki·Grafana(운영과 같은 감시 규칙 파일)·리포터를 로컬에 띄우고 `./local/send-fake-alert.sh`로 가짜 알림을 보낸다(부하 없음). 값은 `local/.env`(커밋 제외)에 넣는다.

## 환경 변수 관리

애플리케이션 실행 및 배포에 필요한 GitHub Actions Secrets 32개 전체 목록과 기본값, 필수 여부는 [`deploy/environment-reference.md`](environment-reference.md)에서 관리합니다.

### 주요 관리 원칙

- **자동 주입**: `main` push → CI/CD deploy 단계에서 GitHub Secrets를 SSH 세션 환경 변수로 주입받아 EC2에서 `export` 후 `docker compose up -d`를 실행합니다. 한 번 등록해두면 이후 배포는 GitHub Secrets만 갱신하면 되며, EC2에 SSH로 접속해 `deploy/app/.env`를 생성하거나 수정할 필요가 없습니다.
- **Fail-Fast 필수 변수**: 필수로 지정된 Secret이 미등록인 채로 push되면 CI/CD 배포 단계가 즉시 실패합니다. **어떤 변수가 필수인지는 [`environment-reference.md`](environment-reference.md)의 표가 SSOT** — 목록을 이 문서에 옮겨 적지 않습니다(양쪽에 두면 한쪽만 고쳤을 때 어긋납니다).
- **Fail-Safe 호스트 변수**: 모니터링(EC2 C)·Redis(EC2 D) 호스트 변수는 Secret 미설정 시에도 compose 기본값으로 컨테이너가 정상 기동되도록 처리돼 있습니다. 사설 IP가 바뀌면 GitHub Secret과 compose 기본값을 **함께** 갱신해야 합니다 — 변수명·현재 기본값은 [`environment-reference.md`](environment-reference.md) 참조.
- **`FRONTEND_IMAGE` 사용 금지**: `FRONTEND_IMAGE` **사용하지 않음** — 프론트는 Vercel. EC2에 프론트엔드 컨테이너가 없으므로 변수를 사용하거나 추가하지 않습니다.
- **수동 배포 및 로컬**: `scripts/ec2-deploy-app.sh` 수동 실행이나 로컬 docker compose 시에는 `.env` 또는 `export`로 값을 전달합니다 (`deploy/app/.env.example` 및 루트 `.env` 참고).
- **전체 환경 변수 표 및 상세 설명**: [`deploy/environment-reference.md`](environment-reference.md) 참조

### 스토어 제출 전 OAuth 콘솔 설정 체크리스트

위 `GOOGLE_CLIENT_ID`류·`KAKAO_ADMIN_KEY`는 env(GitHub Secrets)에 값만 등록하면 되지만, **각 소셜 로그인 콘솔(Google Cloud Console·Apple Developer·Kakao Developers 등) 쪽 설정은 별도로 채워야** 실제 로그인이 동작한다. 코드·검증 로직(`GoogleTokenVerifier`·`AppleTokenVerifier`·`KakaoTokenVerifier`·`OAuthProperties`)은 이미 완료된 상태 — 아래는 **콘솔 담당자가 콘솔 화면에서 직접 등록**하면 되는 항목뿐이다.

미등록 상태로는 `redirect_uri_mismatch`·`DEVELOPER_ERROR` 등으로 로그인 자체가 실패하므로 **스토어 심사 제출 전 반드시 확인**. (과거 GitHub Issue `#62`로 추적하던 체크리스트였으나 2026-08-02부로 이슈를 닫고 이 문서를 SSOT로 통합했다 — 아래가 최신 상태.)

**담당 구분:**

| 담당 | 필요한 것 |
|------|-----------|
| 백엔드 | 코드 변경 없음 — 각 콘솔에 실제 값 입력만(계정 접근 권한 필요) |
| 프론트 | 최종 서비스 도메인·콜백 라우트(리다이렉션 URI로 등록될 경로) 확정해서 전달 — Google/Kakao Redirect URI·네이티브 앱 키·패키지명은 이미 코드 확인으로 전달 완료 |
| 빌드 환경 담당자 | Android 디버그·릴리즈 keystore의 SHA-1(Google)·키 해시(Kakao) 추출 — 리포에 keystore가 없어 콘솔 담당자가 대신할 수 없음 |
| Apple/Google 계정 담당자 | App Store ID는 App Store Connect 앱 등록 완료 후에만 발급 — 게시 담당자가 값 전달 |

#### Google Cloud Console

기존 로그인용 GCP 프로젝트의 **APIs & Services → Credentials**에서 아래 3개 Client(이미 발급돼 있음)를 각각 연다.

| Client 타입 | 대응 env | 지금 등록할 것 | 시점 |
|---|---|---|---|
| **Web application** | `GOOGLE_CLIENT_ID` | 승인된 자바스크립트 원본(`https://tripfit.online`) + 승인된 리다이렉션 URI(프론트 콜백 경로) | 프론트 최종 도메인·콜백 라우트 확정 후 |
| **Android** | `GOOGLE_CLIENT_ID_ANDROID` | 패키지명 `com.tripfit.app` + **SHA-1 인증서 지문**(디버그·릴리즈 각각) | SHA-1 확보 즉시 |
| **iOS** | `GOOGLE_CLIENT_ID_IOS` | 번들 ID `com.tripfit.app` + App Store ID(선택 필드) | 앱 App Store 게시 후 |

**SHA-1 확보 방법** — 리포에 keystore가 없어 콘솔 담당자가 직접 뽑을 수 없다. 빌드 환경(Android Studio/CI) 담당자에게 아래 명령 실행을 요청:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

릴리즈 keystore는 별도 보유자가 동일 명령으로 추출(Google Play App Signing을 쓰면 Play Console의 "앱 무결성 → 앱 서명" 메뉴에서도 확인 가능).

**값 일치 확인**: `apps/app`(네이티브 앱)의 `GOOGLE_WEB_CLIENT_ID`, `apps/web`의 `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, 백엔드 `GOOGLE_CLIENT_ID` 세 값이 전부 같은 Web Client ID를 가리켜야 한다 — 하나라도 다르면 그 경로만 `aud` mismatch로 로그인이 실패한다.

Calendar 전용 Client(`GOOGLE_CALENDAR_CLIENT_ID`) 발급은 지금 안 해도 된다 — Calendar FE 착수 시점에 [`google-calendar-client-id-separation.md`](../docs/specs/user/google-calendar-client-id-separation.md) "GCP 콘솔 가이드" 절 참고.

#### Kakao Developers Console

앱 대시보드(`developers.kakao.com` → 내 애플리케이션 → 해당 앱)에서:

| 메뉴 | 등록할 것 |
|---|---|
| 앱 설정 → 플랫폼 | Android 패키지명 `com.tripfit.app` + **키 해시**(아래 명령), iOS 번들 ID `com.tripfit.app`, 네이티브 앱 키가 `6a686bacf5c3efba84a1cc8d0e655cd5`와 일치하는지 확인 |
| 카카오 로그인 → 활성화 설정 | ON |
| 카카오 로그인 → Redirect URI | `https://tripfit.online/auth/kakao/callback`(운영) + `http://localhost:3000/auth/kakao/callback`(로컬) — 환경 B(모바일 웹/데스크톱 브라우저) JS SDK 리다이렉트 플로우용 |
| 카카오 로그인 → 동의항목 | 닉네임·프로필 사진 활성화(선택 동의로 충분), 이메일은 필요 시 활성화 — 꺼져 있으면 백엔드는 해당 필드를 `null`로 받음(로그인 자체는 안 깨짐) |
| 보안 → Admin Key | 이미 발급 완료 상태 — `KAKAO_ADMIN_KEY` GitHub Secret에 등록돼 있는지만 재확인 |

**키 해시 확보 방법** (Google SHA-1과 같은 keystore 필요):

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | openssl sha1 -binary | openssl base64
```

릴리즈용은 릴리즈 keystore로 동일 명령을 실행해 별도 등록.

## CI/CD

`.github/workflows/ci-cd.yml` — `main` push → GHCR push → EC2 A deploy (app + nginx + certbot)
deploy 시 **위 GitHub Secrets 전부가 app 컨테이너에 자동 주입**된다 — EC2 쪽 `.env` 유무와 무관하게 동작한다.
**EC2 C(모니터링)·EC2 E(수치·리포터)는 `.github/workflows/deploy-observability.yml`** — 관련 파일이 바뀐 main 반영 때만 돌고, Actions 화면에서 수동 실행(all·metrics·monitoring)도 할 수 있다. 앱 배포(`ci-cd.yml`)와 분리해 둔 이유는 수동 실행 때 앱 테스트·배포까지 돌지 않게 하기 위해서다.
**EC2 B(MySQL)·EC2 D(Redis)는 파이프라인 밖** — 최초 설정·재배포 모두 수동(`deploy/mysql/`, `deploy/redis/` 절 참고).

## 검증 스크립트

| 스크립트 | 사용 시점 |
|----------|-----------|
| `scripts/verify-deploy.sh` | 로컬 compose |
| `scripts/setup-api-https.sh` | **배포 + LE 발급 일괄** (권장) |
| `scripts/ec2-deploy-app.sh` | pull + up (임시 self-signed) |
| `scripts/init-letsencrypt.sh` | LE 최초 발급 (`api.tripfit.online`) |
| `scripts/renew-letsencrypt.sh` | 갱신 + nginx reload (cron) |
| `scripts/verify-deploy-app.sh` | EC2 A 검증 (`VERIFY_TLS=true`) |
