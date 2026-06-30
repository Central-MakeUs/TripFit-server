# 002 — 도메인 분리: Vercel(프론트) + EC2(API)

> 상태: 확정 (2026-06-30)  
> Agent·배포 작업 시 **재질문 금지** — 아래 구조를 전제로 구현한다.

## 결정

| 도메인 | 역할 | 호스팅 | HTTPS |
|--------|------|--------|-------|
| `tripfit.online` | React/Next.js 웹 UI | **Vercel** | Vercel 자동 |
| `api.tripfit.online` | Spring Boot REST API | **EC2 A** (Nginx + app) | Let's Encrypt (Certbot) |

## 이유

- 프론트는 Vercel 배포 예정 — EC2에 `FRONTEND_IMAGE`·Next.js 컨테이너 **불필요**
- API만 EC2에서 운영하면 백엔드 배포·DB 연결이 단순해짐
- apex(`tripfit.online`)와 API 서브도메인 분리는 Vercel + 자체 API 조합의 일반 패턴

## DNS (Route 53)

```
tripfit.online      → Vercel (CNAME 또는 Vercel 안내 A)
api.tripfit.online  → EC2 A Elastic IP (A 레코드)
```

**주의:** `tripfit.online` A 레코드를 EC2 IP에 두면 Vercel 프론트와 충돌한다. 프론트 런칭 시 apex를 Vercel로 옮긴다.

## EC2 A 스택 (이 repo `deploy/app/`)

- `nginx` — `server_name api.tripfit.online`, 80/443
- `certbot` — `api.tripfit.online` 인증서
- `app` — Spring Boot (GHCR)
- **frontend 서비스 없음**

| `deploy/nginx/snippets/proxy-api.conf` | `app:8080` 프록시 |

## 프론트 (Vercel) 계약

```env
NEXT_PUBLIC_API_BASE_URL=https://api.tripfit.online
```

- API path: `/api/v1/...`
- CORS: Vercel origin(`https://tripfit.online`) 허용 필요 시 `config/`에 추가 (스펙·구현 시)

## 관련 파일

| 경로 | 내용 |
|------|------|
| `deploy/nginx/conf.d/` | `api.tripfit.online` 전용 |
| `deploy/app/docker-compose.yml` | nginx + certbot + app |
| `scripts/init-letsencrypt.sh` | `CERTBOT_DOMAIN=api.tripfit.online` 기본 |
| `docs/product/platform.md` | 플랫폼 맥락 |
| `.cursor/rules/deployment.mdc` | Agent 배포 규칙 |

## Agent 금지 사항

- EC2 compose에 `FRONTEND_IMAGE`·frontend 컨테이너 다시 추가하지 말 것
- Nginx `server_name`을 `tripfit.online`으로 API 서버에 설정하지 말 것 (Vercel 담당)
- 사용자에게 «Vercel vs EC2» 재확인 질문하지 말 것
