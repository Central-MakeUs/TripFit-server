# Deployment Environment Reference

TripFit 백엔드 배포 인프라의 도메인 DNS 매핑, EC2 4대 서버 구성, 그리고 애플리케이션 실행에 필요한 환경 변수(GitHub Actions Secrets) 전체 목록을 정의한 참조(SSOT) 문서다. 인프라 프로비저닝 및 환경 변수 추가·수정 시 이 문서를 기준으로 삼는다. 실제 배포 및 검증 절차는 [`deploy/README.md`](README.md)를 참고한다.

## 언제 이 문서를 보는가

- 새로운 환경 변수(GitHub Secret)를 추가하거나 기존 키 값을 갱신할 때
- EC2 인스턴스(A/B/C/D) 역할, 사설/공인 IP, 포트 및 보안 그룹(SG) 규칙을 점검할 때
- 프론트엔드(Vercel)와 백엔드(EC2) 간의 도메인 및 DNS 매핑을 확인할 때

## 도메인 및 DNS 구조

| 도메인 | 호스팅 | 용도 및 위치 |
|---|---|---|
| `tripfit.online` | **Vercel** (React/Next.js) | 프론트엔드 서비스 (EC2에 프론트엔드 컨테이너 없음) |
| `api.tripfit.online` | **EC2 A** (Nginx + Spring Boot) | 백엔드 API 서버 (`deploy/app/`, `deploy/nginx/`) |
| `grafana.tripfit.online` | **EC2 C** (Nginx + Grafana) | 모니터링 대시보드 (`deploy/monitoring/`, 운영 전용) |

### Route 53 레코드

- `tripfit.online` → Vercel DNS (CNAME/A, Vercel 대시보드 안내 준수)
- `api.tripfit.online` → EC2 A Elastic IP (A 레코드)
- `grafana.tripfit.online` → EC2 C Elastic IP (A 레코드)

### 프론트엔드 연동 환경 변수 (Vercel)

```env
NEXT_PUBLIC_API_BASE_URL=https://api.tripfit.online
```

상세 결정 배경: [`docs/decisions/002-domain-split-vercel-api.md`](../docs/decisions/002-domain-split-vercel-api.md)

## 서버 인프라 매핑 (EC2 A~D)

| 경로 | 서버 | 사설 IP / 포트 | 역할 및 스택 |
|---|---|---|---|
| [`app/`](app/) | **EC2 A** | `api.tripfit.online` / :80, :443, :8080 | Nginx 리버스 프록시 + Certbot(TLS) + Spring Boot API |
| [`mysql/`](mysql/) | **EC2 B** | :3306 | MySQL 8.0 데이터베이스 전용 인스턴스 |
| [`monitoring/`](monitoring/) | **EC2 C** | `172.31.38.217` / :3100, :3000 | Loki(로그 수집) + Grafana(대시보드) |
| [`redis/`](redis/) | **EC2 D** | `172.31.38.246` / :6379 | Redis (refresh token 저장소 + 공휴일 캐시, `sg-app` 허용) |
| [`../docker-compose.yml`](../docker-compose.yml) | **로컬** | localhost:8080, :3306 | 로컬 개발 환경 (App build + MySQL) |

상세 네트워크 및 보안 그룹: [`ec2-split-deployment.md`](ec2-split-deployment.md)

## 환경 변수 (GitHub Actions Secrets)

### GitHub Actions Secrets 등록 위치

등록 위치: GitHub repo → **Settings → Secrets and variables → Actions**

**`main` push → CI/CD deploy** 단계에서 아래 Secrets를 SSH 세션 env로 받아 EC2에서 `export` 후 `docker compose up -d`를 실행한다.
**한 번 등록해두면 이후 배포는 GitHub Secrets만 갱신하면 되고, EC2에 SSH로 들어가 `deploy/app/.env`를 만들거나 수정할 필요가 없다.**
`deploy/app/docker-compose.yml`의 `app.environment`가 아래 변수를 Spring Boot 컨테이너로 넘긴다.

### 배포 인프라 및 CI/CD 전용 Secrets

기존 SSH·이미지 Secrets(`EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_DEPLOY_PATH`, `GHCR_PAT`, `GHCR_USERNAME`)는 CI/CD가 EC2 A에 접속하고 GHCR에서 도커 이미지를 pull받는 데 필수적으로 사용된다.

| Secret 키 | 필수 | 설명 |
|---|---|---|
| `EC2_HOST` | ✅ | EC2 A Elastic IP (배포 대상 호스트) |
| `EC2_USER` | ✅ | EC2 SSH 계정명 (기본 `ubuntu`) |
| `EC2_SSH_KEY` | ✅ | EC2 SSH 접속용 Private Key 원문 |
| `EC2_DEPLOY_PATH` | ✅ | EC2 내 배포 디렉터리 경로 |
| `GHCR_USERNAME` | ✅ | GHCR 도커 이미지 pull용 GitHub 계정명 |
| `GHCR_PAT` | ✅ | GHCR 도커 이미지 pull 권한 Personal Access Token |

### 애플리케이션 런타임 Secrets

| Secret 키 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `MYSQL_HOST` | ✅ | - | EC2 B private IP |
| `MYSQL_PORT` | | 3306 | MySQL 포트 |
| `MYSQL_DATABASE` | ✅ | tripfit | DB 이름 |
| `SPRING_DATASOURCE_USERNAME` | ✅ | - | DB 계정명 |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | - | DB 비밀번호 |
| `SPRING_PROFILES_ACTIVE` | | dev | 활성 스프링 프로필 (유일한 배포 환경: `dev`) |
| `APP_PORT` | | 8080 | Spring 컨테이너 바인딩 포트 |
| `NGINX_HTTP_PORT` | | 80 | HTTP 포트 |
| `NGINX_HTTPS_PORT` | | 443 | HTTPS 포트 |
| `CERTBOT_DOMAIN` | | api.tripfit.online | TLS 인증서 도메인 |
| `LOKI_HOST` | | 172.31.38.217 | EC2 C(모니터링) private IP (fail-safe 기본값 보유) |
| `REDIS_HOST` | | 172.31.38.246 | EC2 D(Redis) private IP (fail-safe 기본값 보유) |
| `REDIS_PORT` | | 6379 | Redis 포트 |
| `REDIS_PASSWORD` | ✅ | - | Redis `requirepass` 인증 암호 |
| `JWT_SECRET` | ✅ | - | Access JWT 서명 키 (256bit+ random) |
| `JWT_ACCESS_EXPIRATION` | | 900 | Access 토큰 만료 시간 (15분, 단위: 초) |
| `JWT_REFRESH_EXPIRATION_DAYS` | | 30 | Refresh 토큰 만료 일수 (단위: 일) |
| `GOOGLE_CLIENT_ID` | | - | Google 로그인 Web Client ID (`aud` 검증용) |
| `GOOGLE_CLIENT_SECRET` | | - | Google 로그인 Web Client Secret |
| `GOOGLE_CLIENT_ID_IOS` | | - | Google 로그인 iOS Client ID |
| `GOOGLE_CLIENT_ID_ANDROID` | | - | Google 로그인 Android Client ID |
| `GOOGLE_CALENDAR_CLIENT_ID` | | - | Google Calendar 연동 전용 Client ID (로그인과 분리) |
| `GOOGLE_CALENDAR_CLIENT_SECRET` | | - | Google Calendar 연동 전용 Secret |
| `SOCIAL_TOKEN_AES_KEY` | ✅ (Calendar 시) | - | Base64 인코딩 32바이트 AES-256 키 |
| `APPLE_BUNDLE_ID` | | com.tripfit.app | iOS 네이티브 앱 로그인 `aud` 검증용 App ID |
| `APPLE_SERVICE_ID` | | - | 모바일 브라우저 로그인 경로 `aud` 검증용 Services ID |
| `APPLE_TEAM_ID` | ✅ (Apple 시) | - | Apple Developer Team ID (JWT `iss`) |
| `APPLE_KEY_ID` | ✅ (Apple 시) | - | Sign in with Apple `.p8` Key ID (JWT `kid`) |
| `APPLE_PRIVATE_KEY` | ✅ (Apple 시) | - | Sign in with Apple `.p8` 키 원문 (ES256 서명용) |
| `KAKAO_ADMIN_KEY` | ✅ (Kakao 시) | - | Kakao Developers Admin Key (탈퇴 시 unlink 호출용) |
| `FIREBASE_CREDENTIALS_BASE64` | ✅ (알림 시) | - | Firebase 서비스 계정 JSON 전체 Base64 인코딩 값 |
| `HOLIDAY_API_SERVICE_KEY` | ✅ (공휴일 시) | - | 특일 정보 API 인증키 (**Decoding 키**, 비어 있으면 공휴일 동기화만 skip) |

### Fail-Fast 및 기본값 운영 규칙

`MYSQL_HOST`·`MYSQL_DATABASE`·`SPRING_DATASOURCE_*`·`JWT_SECRET`·`SOCIAL_TOKEN_AES_KEY`(Calendar 연동 시)는 **필수** — 미등록 상태로 push하면 CI/CD 배포 단계가 즉시 실패한다(fail-fast). 나머지는 비어 있으면 `docker-compose.yml`에 박힌 기본값을 그대로 쓴다.

### `FRONTEND_IMAGE` 사용 금지 (프론트는 Vercel)

`FRONTEND_IMAGE` **사용하지 않음** — 프론트는 Vercel.  
프론트엔드는 Vercel(`tripfit.online`)에서 호스팅되므로 EC2 인프라에 frontend 컨테이너가 존재하지 않으며, `FRONTEND_IMAGE` 변수를 사용하거나 추가해서는 안 된다 (`.claude/rules/tripfit-release.md` 확정 사항).

### `.env` 파일 필수 여부 및 수동 배포

**`deploy/app/.env`는 이제 필수 아님** — CI/CD 자동 배포는 위 Secrets만으로 완결된다. 다만 `scripts/ec2-deploy-app.sh`로 **수동** 배포하거나 로컬에서 직접 `docker compose`를 띄울 때는 여전히 `.env` 또는 `export`로 값을 넘겨야 한다 (`deploy/app/.env.example` 참고). `CERTBOT_EMAIL`은 최초 1회 `init-letsencrypt.sh`/`setup-api-https.sh` 수동 실행 시에만 필요해 CI/CD 화이트리스트에는 포함하지 않았다.

### 특수 호스트 변수 관리 규칙

1. **`LOKI_HOST`**: EC2 A는 위 Secret으로 관리(값 `172.31.38.217` — TP-monitoring private IP). EC2 C를 재생성해 private IP가 바뀌면 **이 Secret만 갱신**하면 된다. `deploy/app/docker-compose.yml`·`deploy/mysql/docker-compose.yml`의 `${LOKI_HOST:-172.31.38.217}` 기본값은 Secret 미설정 시에도 컨테이너가 죽지 않게 하는 fail-safe 용도로 남겨뒀다 — IP가 실제로 바뀌면 이 기본값도 함께 갱신해 두 값이 계속 일치하도록 한다. **EC2 B(MySQL)는 CI/CD 대상이 아니라 이 Secret이 적용되지 않음** — B의 `deploy/mysql/.env`에 `LOKI_HOST`를 직접 수정해야 한다.
2. **`REDIS_HOST`**: 2026-08-10 EC2 D(`TP-redis`, `i-06fb8540484834192`) 프로비저닝 후 GitHub Secret 등록 완료(값 `172.31.38.246`). EC2 D를 재생성해 private IP가 바뀌면 **이 Secret과 `deploy/app/docker-compose.yml`의 `${REDIS_HOST:-172.31.38.246}` 기본값을 함께 갱신**한다(LOKI_HOST와 동일 패턴). Redis는 fail-open 설계(`RedisTokenRevocationChecker`)라 연결이 안 돼도 앱 자체는 죽지 않고 즉시무효화 기능만 비활성화된다.

## 관련 문서

- [`deploy/README.md`](README.md) — 배포 순서, HTTPS 발급 및 운영 절차 (문제 해결)
- [`deploy/ec2-split-deployment.md`](ec2-split-deployment.md) — VPC·서브넷·보안 그룹 심화 가이드
- [`deploy/holiday-api-setup.md`](holiday-api-setup.md) — 공휴일 API 인증키 발급 및 검증 절차
- [`docs/decisions/002-domain-split-vercel-api.md`](../docs/decisions/002-domain-split-vercel-api.md) — 도메인 분리 확정 ADR
- [`docs/decisions/010-redis-infra.md`](../docs/decisions/010-redis-infra.md) — Redis 인프라 도입 ADR
