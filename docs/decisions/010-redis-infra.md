# 010 — Redis 인프라 (신규 EC2 D)

- **상태:** 확정
- **날짜:** 2026-08-08
- **관련:** [`004-auth-token-rotation.md`](004-auth-token-rotation.md), [`docs/specs/auth/auth-token-rotation.md`](../specs/auth/auth-token-rotation.md), Issue **#4**

## ⚠️ 2026-09-15 amend — Redis 용도 변경 (블랙리스트 폐기)

**"EC2 D를 Redis 전용으로 둔다"는 결정 자체는 유효하다.** 다만 아래 본문이 도입 근거로 든 용도(access token `jti` 블랙리스트)는 [`auth-refresh-redis-cookie.md`](../specs/auth/auth-refresh-redis-cookie.md)로 **폐기**됐다 — access token은 이제 블랙리스트 없이 자체 TTL(15분)로만 만료된다.

**현재 Redis가 실제로 담는 것** (키 목록 SSOT: [`erd.md`](../architecture/erd.md) Redis 절):

- **refresh token** — `auth:refresh:{active,family,revoked,user}:*` 4종. MySQL `refresh_token` 테이블을 대체한 SSOT
- **공휴일 캐시** — `holiday:kr:{year}` ([`011-holiday-data-source.md`](011-holiday-data-source.md))

"App을 수평 확장해도 공유돼야 하는 상태"라는 **도입 논리는 그대로 유효**하다(refresh token은 오히려 블랙리스트보다 더 강하게 공유가 필요하다). 다만 아래 "트레이드오프" 절의 fail-open 논거는 더 이상 성립하지 않는다 — refresh 저장소가 Redis이므로 Redis 장애 시 토큰 갱신이 실패한다(fail-open으로 열화되지 않음). 상세는 새 스펙의 SPOF 절 참고.

## 맥락

> 아래는 2026-08-08 결정 당시의 기록이다. 용도 서술은 위 amend로 대체됐다.

`#4`(RTR + 액세스 토큰 즉시 무효화)를 시작으로 이 저장소에 처음 Redis가 필요해졌다. Redis가 쓰이는 용도(로그아웃 시 access token jti 블랙리스트 — `#35` 정원 hold도 한때 후보였으나 2026-08-19 `#114`로 DB 비관적 락으로 대체·삭제됨)는 전부 "App 인스턴스가 여러 대로 늘어나도 공유돼야 하는 상태"라, App 프로세스 안에 내장하면 App을 수평 확장하는 순간 각 인스턴스가 자기 것만 보게 돼 목적이 무너진다. 어디에·어떻게 올릴지 결정이 필요했다.

## 결정

**신규 EC2 D**를 Redis 전용으로 프로비저닝한다. 기존 EC2 B(MySQL)와 동일한 논리를 따른다 — App(EC2 A)만 사설 IP로 접근하는 내부 인프라이지, Grafana(EC2 C)처럼 외부에 공개할 이유가 없다.

- **네트워크:** A/B/C와 동일 VPC·서브넷(Private)·키페어 재사용. Public IP·Elastic IP 부여하지 않음(App만 사설 IP로 접근)
- **보안 그룹:** Inbound `6379`는 `sg-app`에서만 허용(`sg-db`가 `sg-app`에만 3306을 여는 것과 동일 패턴). `0.0.0.0/0` 오픈 금지
- **인스턴스 크기:** `t3.micro` — EC2 C 도입 시(`009`) 산정한 것과 동일 사이즈. 토큰 블랙리스트·향후 카운터·락 키 정도는 데이터량이 작아 충분
- **운영 방식:** 기존 A/B/C와 동일하게 Docker Compose로 self-managed — `deploy/redis/docker-compose.yml`

## 고려한 대안

| 대안 | 장점 | 단점 |
|------|------|------|
| A. App(EC2 A)에 컨테이너로 동거 | 신규 인스턴스 불필요, 네트워크 홉 없음(최저 지연) | App을 수평 확장하는 순간 인스턴스마다 별도 Redis를 갖게 돼 "공유 상태"라는 도입 목적 자체가 깨짐 |
| B. EC2 B(MySQL)에 동거 | 신규 인스턴스 불필요, 이미 Private subnet이라 보안 요건은 충족 | DB와 캐시/락 관심사가 한 인스턴스에 섞임 — 이 저장소가 A/B/C를 나눠온 "장애 격리·역할별 분리" 원칙과 상충. MySQL 재시작·점검이 Redis에도 영향 |
| C. AWS ElastiCache(managed) | 백업·장애조치를 AWS가 대신 처리, 운영 부담 적음 | EC2보다 비용이 높고, VPC·보안그룹 설정이 EC2 인스턴스 추가와는 다른 별도 서비스 체계라 이 저장소의 기존 관례(전부 self-managed EC2 Docker)에서 벗어남. `decisions/004`에서 원래 `[미정]`으로 남겨뒀던 대안 |
| **D. 신규 EC2 D (택함)** | A/B/C와 동일한 검증된 패턴 재사용(네트워크·SG·배포 스크립트 관례 그대로), 앱 확장과 무관하게 항상 공유 상태 보장 | EC2 인스턴스 1대 추가 비용 |

## 트레이드오프 · 후속 리스크

- **비용:** EC2 인스턴스 1대 추가 (약 월 $7.5, t3.micro 온디맨드 기준 — `009`와 동일 규모)
- **새 실패 지점:** App의 인증 경로(매 요청)가 Redis 가용성에 의존하게 됨 — `#4`는 Blacklist 전략을 채택해 Redis 장애 시 fail-open(조회 실패 시 통과)으로 완화하기로 함(`004` 참고)
- **단일 인스턴스 SPOF:** 지금은 Redis도 이중화 없이 EC2 1대 — App(A)도 아직 단일 인스턴스라 전체 아키텍처의 일관된 현재 단계. 향후 App을 여러 대로 늘리는 시점에 Redis Sentinel/Cluster 또는 ElastiCache 전환을 재검토
- **모니터링:** 기존 EC2 C(Loki)로 Redis 컨테이너 로그도 수집 대상에 포함할지 배포 시 확인

## 후속 작업

- [x] EC2 D 프로비저닝 (t3.micro, A/B/C와 동일 VPC·서브넷·키페어) — `TP-redis`(`i-06fb8540484834192`), private IP `172.31.38.246`, 2026-08-10
- [x] `TP-redis` 보안 그룹 생성 — Inbound 6379 ← `sg-app`(`TP-server`)만, 22(SSH)는 기존 A/B/C와 동일하게 오픈. 외부(App 아닌 곳) 접근 차단·App→Redis 접근 성공 실측 확인 완료
- [x] `deploy/redis/docker-compose.yml` 작성 (Redis 공식 이미지, `requirepass` 설정, `--save ""` + `--appendonly no`로 영속화 비활성 — TTL 데이터라 불필요 확정)
- [x] `deploy/README.md`·`deploy/ec2-split-deployment.md`에 EC2 D 반영 (`009` EC2 C 추가 때와 동일 절차)
- [x] `deploy/app/.env.example`·`docker-compose.yml`에 `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` 반영, GitHub Secrets 등록 완료(2026-08-10)
- [x] EC2 C(Loki) 로그 수집 대상에 Redis 컨테이너 포함 — 포함하기로 결정, `deploy/redis/docker-compose.yml`에 동일 loki logging driver 적용
