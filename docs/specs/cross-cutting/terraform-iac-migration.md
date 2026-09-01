# Terraform 전면 IaC 전환

> 상태: Draft
> MVP: Out of scope (인프라 구조 정리 — Milestone `출시 이후`, `priority: could`)
> 관련 BR: N/A
> 관련 이슈: [#125](https://github.com/Central-MakeUs/TripFit-server/issues/125)

## 목표

지금 AWS 콘솔에서 손으로 만든 인프라(EC2 4대·보안 그룹·Elastic IP·Route 53)를 **Terraform 코드로 옮겨 저장소에서 관리**한다. 인프라 변경이 코드 리뷰·이력 추적을 거치게 만들고, 사람이 통째로 날려도 코드로 다시 세울 수 있게 한다.

## 배경

- 현재 인프라는 전부 **수동 생성**이다. 누가 언제 무엇을 왜 바꿨는지 남는 기록이 `deploy/README.md`·`docs/decisions/`의 서술뿐이고, 실제 AWS 상태와 문서가 이미 어긋나 있다(아래 "문서 vs 실제" 표).
- EC2 D(Redis) 추가처럼 **인스턴스가 하나 늘 때마다 같은 수작업**(서브넷·키페어·SG 규칙·태그)을 반복한다. `009`(EC2 C)·`010`(EC2 D) 두 결정 메모가 "A/B/C와 동일 패턴 재사용"이라고 적고 있는데, 그 "동일 패턴"이 코드가 아니라 문서와 기억에만 있다.
- 인스턴스 재생성 시 private IP가 바뀌어 `LOKI_HOST`·`REDIS_HOST` GitHub Secret과 `docker-compose.yml` 기본값을 **사람이 두 군데 맞춰줘야** 한다(`deploy/README.md`). IaC는 이 값을 output으로 뽑아낼 수 있는 출발점이다.
- 관련 문서: [`deploy/README.md`](../../../deploy/README.md)(배포 SSOT) · [`deploy/ec2-split-deployment.md`](../../../deploy/ec2-split-deployment.md) · [`docs/decisions/009-observability-logging.md`](../../decisions/009-observability-logging.md) · [`docs/decisions/010-redis-infra.md`](../../decisions/010-redis-infra.md)

### 사용자 확정 사항 (2026-08-27)

| 항목 | 결정 |
|------|------|
| 전환 방식 | **기존 리소스 1:1 import** — 아키텍처 불변, 리소스 재생성 없음 |
| 코드 위치 | 이 저장소 **`infra/terraform/`** |
| state 저장소 | **S3 + 네이티브 lockfile**(`use_lockfile = true`, Terraform 1.10+) — DynamoDB 락 테이블 없음 |
| Milestone · priority | **출시 이후** · **`priority: could`** |

## 현재 인프라 인벤토리 (2026-08-27 실측, `aws ec2/route53 describe-*`)

계정 `932924001445` · 리전 `ap-northeast-2` · **default VPC `vpc-09a38c427d4580601`(172.31.0.0/16)**

### EC2 인스턴스 (4대 — 전부 `subnet-047b1bf51101845fc`, ap-northeast-2c, `t3.micro`, `ami-0bc151a94289adb52`, 키페어 `TripFit-Keypair`)

| Name | Instance ID | Private IP | Public IP | 루트 볼륨 | 역할 |
|------|-------------|-----------|-----------|-----------|------|
| `TP-server` (A) | `i-0936ee54784b10fb0` | 172.31.41.238 | **3.36.246.189 (EIP)** | `vol-053b3491ad4b8670e` 20GB gp3 | Nginx + Spring Boot |
| `TP-db` (B) | `i-09984ef55eb912da7` | 172.31.37.134 | 43.200.181.198 (자동 할당) | `vol-0ff658c2bd1a58d59` 20GB gp3 | MySQL 8.0 |
| `TP-monitoring` (C) | `i-0d790b6ef7b73ba99` | 172.31.38.217 | **54.116.62.227 (EIP)** | `vol-0fa3fb0a262959e9e` 8GB gp3 | Loki + Grafana |
| `TP-redis` (D) | `i-06fb8540484834192` | 172.31.38.246 | 43.201.18.82 (자동 할당) | `vol-08a82ecc735157661` 8GB gp3 | Redis |

### 보안 그룹 (4개 + default)

| SG | ID | Inbound |
|----|----|---------|
| `TP-server` | `sg-0ecf6b74dde1673fa` | 22·80·443 ← `0.0.0.0/0` |
| `TP-db` | `sg-061c0c797931aa6b9` | 22 ← `0.0.0.0/0` · 3306 ← `sg-TP-server` |
| `TP-redis` | `sg-092690da2b75842e2` | 22 ← `0.0.0.0/0` · 6379 ← `sg-TP-server` |
| `TP-monitoring` | `sg-028d13674b3812eb1` | 22·80·443 ← `0.0.0.0/0` · 3100 ← `sg-TP-server`, `sg-TP-db` |
| `default` | `sg-01c7d217b8c9ad08c` | 자기 SG 전체 허용 (AWS 기본값 — 관리 대상 아님) |

### Elastic IP (2개)

| Allocation ID | IP | 연결 |
|---------------|-----|------|
| `eipalloc-0a3b95e745272e95f` | 3.36.246.189 | `TP-server` |
| `eipalloc-0747deaa466e11334` | 54.116.62.227 | `TP-monitoring` |

### Route 53 — `tripfit.online.` (`Z1013585288W0WQFYBR5W`, 6 레코드)

| 이름 | 타입 | TTL | 값 |
|------|------|-----|-----|
| `tripfit.online.` | A | 300 | `216.198.79.1` (Vercel) |
| `tripfit.online.` | TXT | 300 | `google-site-verification=...` |
| `api.tripfit.online.` | A | 300 | `3.36.246.189` (EC2 A EIP) |
| `grafana.tripfit.online.` | A | 300 | `54.116.62.227` (EC2 C EIP) |
| `tripfit.online.` | NS / SOA | 172800 / 900 | 존 생성 시 AWS 자동 — **import 대상 아님** |

### ⚠️ 문서 vs 실제 (STOP §1 — 2026-08-27 사용자 결정: 실제를 "정석"에 맞춰 하드닝)

| 문서 서술 | 실제 (2026-08-27 실측) | 판단 |
|-----------|------------------------|------|
| `ec2-split-deployment.md` §2.1: EC2 B는 **Private subnet**, DB에 **Public IP 사용하지 않음** | 4대 전부 default VPC의 **같은 public subnet**(`MapPublicIpOnLaunch=true`), `TP-db`에 자동 할당 public IP `43.200.181.198` 존재 | AWS는 **실행 중 인스턴스의 서브넷을 그 자리에서 바꾸는 기능이 없다**(ENI가 서브넷에 고정) — 진짜 private 서브넷 이전은 신규 인스턴스+데이터 마이그레이션이 필요해 "재생성 없음" 전제와 충돌 → **서브넷 이전은 Out of Scope**. 대신 **공인 IP 제거**(경량 하드닝, 아래 P2.5)로 정석 방향에 최대한 근접 |
| `decisions/010`: EC2 D는 "Public IP·Elastic IP 부여하지 않음" | EIP는 없는 게 맞지만 자동 할당 public IP `43.201.18.82` 존재 | **이번 스펙에서 하드닝 대상** — P2.5에서 제거 |
| `ec2-split-deployment.md` §2.1: VPC CIDR `10.0.0.0/16` | default VPC `172.31.0.0/16` | 문서에 "예:"로 적혀 있어 예시임이 드러남 — amend는 선택 |
| `ec2-split-deployment.md` §2.3: `sg-app` Outbound 3306만 / `sg-db` Outbound minimal | 실제 outbound 규칙 미확인 (default allow-all 추정) | **import 시 실측값으로 확정** |

**이번 스펙의 방침 (2026-08-27 사용자 확인 — "정석 방향으로 맞추는 게 좋아 보임"):** 서브넷 자체 이전은 AWS 제약상 재생성이 필요해 Out of Scope로 남기되, **`TP-db`·`TP-redis`의 자동 할당 공인 IP는 이번 스펙에서 제거**한다(P2.5, 경량 하드닝 — 인스턴스 재생성 없음, stop/start만). 서브넷 CIDR 문서 표기는 amend. SSH 22 `0.0.0.0/0`은 이번 결정과 별개 주제라 **계속 Out of Scope**(별도 이슈).

## 요구사항

### Must Have

**P0 — state 백엔드 부트스트랩**

- [ ] `infra/terraform/bootstrap/`에 state 전용 S3 버킷 정의 (`tripfit-tfstate-<account-id>`) — 버저닝 ON, SSE(AES256) ON, Public Access Block 4종 ON
- [ ] 부트스트랩은 로컬 state로 apply한 뒤 자기 자신을 원격 백엔드로 마이그레이션 (닭-달걀 문제 해소 절차를 README에 기록)
- [ ] 루트 `backend "s3"` 블록에 `use_lockfile = true` (DynamoDB 락 테이블 사용 안 함), `encrypt = true`, `key = "prod/terraform.tfstate"`

**P1 — 네트워크·DNS import (파괴 위험 낮음)**

- [ ] default VPC·서브넷·default SG는 **`data` 소스로 참조만** (AWS가 만든 리소스라 관리·삭제 대상 아님)
- [ ] 보안 그룹 4개(`TP-server`·`TP-db`·`TP-redis`·`TP-monitoring`) import — 규칙은 `aws_vpc_security_group_ingress_rule`/`egress_rule` 개별 리소스로 기술
- [ ] Elastic IP 2개(+ 인스턴스 연결) import
- [ ] Route 53 호스팅 존 + A·TXT 레코드 4개 import (NS·SOA 제외)

**P2 — EC2 인스턴스 import (파괴 위험 높음 — 가드레일 필수)**

- [ ] EC2 4대 import
- [ ] 4대 전부에 `lifecycle { prevent_destroy = true }` — 특히 `TP-db`는 재생성 = **MySQL 데이터 전손**
- [ ] `lifecycle { ignore_changes = [ami, user_data, tags["..."]] }` — AMI ID 드리프트가 인스턴스 **강제 재생성**으로 이어지지 않게 차단
- [ ] 루트 EBS는 `root_block_device`로 인스턴스에 포함 (별도 `aws_ebs_volume` 리소스로 만들지 않음)
- [ ] 키페어 `TripFit-Keypair`는 **`data` 소스로만 참조** — `aws_key_pair` import는 공개키 원문을 API가 돌려주지 않아 매번 diff가 뜬다

**P2.5 — 경량 네트워크 하드닝 (2026-08-27 추가, 인스턴스 재생성 없음)**

`TP-db`·`TP-redis`를 "정석"(문서가 원래 의도한 방향)에 최대한 근접시킨다. 서브넷은 그대로 두고 **불필요한 공인 IP만 제거**한다 — 이미 3306·6379는 SG가 `sg-app`에서만 허용해 실질적 노출은 없었지만, 공인 IP 자체가 남아 있으면 향후 SG 실수 한 번으로 바로 인터넷에 노출된다.

- [ ] `aws_subnet.main`(default 서브넷을 `data`가 아니라 관리 대상으로 import)에서 `map_public_ip_on_launch = false`로 설정 — 기존 인스턴스에는 영향 없음, **향후 신규 launch에만 적용**(비파괴적 속성 변경)
- [ ] **운영 런북** (`infra/terraform/README.md`에 기록, Terraform이 자동 실행하지 않는 수동 절차):
  1. 트래픽이 적은 시간대 선정 — **`TP-db` stop/start는 그 구간 동안 API 전체 장애**(MySQL 응답 불가). `TP-redis`는 `RedisTokenRevocationChecker`가 fail-open이라 즉시무효화만 일시 비활성화, API 자체는 유지(`decisions/010`)
  2. `aws ec2 stop-instances --instance-ids i-09984ef55eb912da7`(TP-db) → 상태 `stopped` 확인 → `start-instances` → `running` 확인. EBS(`vol-0ff658c2bd1a58d59`)는 인스턴스에 계속 연결된 상태라 **데이터 손실 없음**, private IP(`172.31.37.134`)도 동일 ENI라 유지됨
  3. `TP-redis`(`i-06fb8540484834192`)도 동일 절차
  4. 각각 재기동 후 `aws ec2 describe-instances`로 `PublicIpAddress` 필드가 사라졌는지 확인
- [ ] 검증: `docker logs tripfit-app`에서 DB/Redis 재연결 성공 로그 확인, `curl -fsSI https://api.tripfit.online/health` 정상
- [ ] `deploy/README.md`·`decisions/010`에 "공인 IP 없음(하드닝 완료, 2026-XX-XX)"로 amend

**P3 — 검증·CI·문서**

- [ ] **`terraform plan` 결과가 `No changes.`(0 add / 0 change / 0 destroy)** — 이 스펙의 핵심 수용 조건
- [ ] `.github/workflows/`에 PR용 `terraform fmt -check` + `validate` + `plan` 잡 추가, plan 결과를 PR 코멘트로 게시
- [ ] **`apply`는 CI에서 자동 실행하지 않는다** — 로컬 수동 실행만 (전환 초기 안전장치)
- [ ] `docs/decisions/012-terraform-iac.md` 작성 (배포 구조 결정 — `docs/decisions/README.md` 기준 대상)
- [ ] `deploy/README.md`·`deploy/ec2-split-deployment.md`·`decisions/010`의 "문서 vs 실제" 불일치 amend
- [ ] `infra/terraform/README.md` — 최초 셋업·plan/apply 절차·**절대 하지 말 것**(`terraform destroy`) 명시
- [ ] `docs/specs/README.md` `cross-cutting/` 표에 이 스펙 등록

### Nice to Have

- [ ] GitHub Actions OIDC + 읽기 전용 IAM 역할로 CI plan 실행 (장기 액세스 키 없이) — IAM 권한이 필요해 계정 관리자 작업 선행
- [ ] `tflint` / `checkov` 등 정적 검사 추가
- [ ] 인스턴스 정의를 `modules/ec2-node`로 공통화 (4대가 태그·SG만 다름)
- [ ] `output`으로 private IP를 노출해 `LOKI_HOST`·`REDIS_HOST` 수동 동기화 제거

### Out of Scope (이번 스펙에서 하지 않음)

- **관리형 서비스 전환** — EC2 B → RDS, ALB·ECS/Fargate 도입 (`ec2-split-deployment.md` §8의 다음 단계, 별도 결정 메모 필요)
- **완전한 private 서브넷 이전** — 신규 인스턴스+데이터 마이그레이션 필요(AWS는 실행 중 인스턴스의 서브넷 변경 불가), 별도 스펙·이슈로 분리
- **SSH 22의 `0.0.0.0/0` 제한** — 이번 "정석 정렬" 결정과 별개 주제, 별도 이슈 (관리자 IP 고정 또는 SSM Session Manager 전환 중 선택 필요)
- **앱 배포 자동화 대체** — Docker Compose + GitHub Actions 파이프라인은 그대로 유지, Terraform이 컨테이너를 배포하지 않는다
- **GitHub Secrets·앱 환경변수의 Terraform 관리** — 비밀값은 계속 GitHub Secrets가 SSOT
- **Vercel(프론트) 리소스** — 이 저장소 소관 아님 (`decisions/002`)
- **Grafana 대시보드·Loki 설정의 코드화** — `009` 후속 항목
- **여러 환경(dev/stg/prod) 분리** — 현재 운영 환경 1개뿐, 워크스페이스 도입은 필요해질 때

## API / 인터페이스

**API 없음** — 서버 API 계약·DTO·`ErrorCode` 변경 없음. 프론트 영향 없음(`Breaking-Change-Reason` 트레일러 대상 아님).

## 데이터 모델

**DB 스키마 변경 없음** — 엔티티·ERD 무관. Flyway 등 마이그레이션도 해당 없음(`harness-workflow.md` STOP §3).

Terraform state 파일이 새 "상태 저장소"로 추가된다:

```
s3://tripfit-tfstate-932924001445/
  prod/terraform.tfstate         # 루트 스택
  prod/terraform.tfstate.tflock  # 네이티브 lockfile (use_lockfile)
  bootstrap/terraform.tfstate    # 버킷 자신
```

## 디렉터리 구조 (제안)

```
infra/terraform/
├── README.md            # 셋업·plan/apply 절차·금기
├── bootstrap/           # state 버킷 (최초 1회)
│   └── main.tf
├── versions.tf          # terraform >= 1.10, aws provider ~> 6.0 핀
├── backend.tf           # S3 + use_lockfile
├── providers.tf         # region ap-northeast-2, default_tags
├── data.tf              # default VPC·subnet·key pair (참조 전용)
├── security-groups.tf   # SG 4개 + 규칙
├── compute.tf           # EC2 4대 + EIP
├── dns.tf               # Route 53 존 + 레코드
├── imports.tf           # import 블록 (전환 완료 후 삭제)
├── variables.tf
└── outputs.tf           # private IP 등
```

## 비즈니스 규칙

| BR | 적용 내용 | 구현 위치 |
|----|-----------|-----------|
| — | 해당 없음 (인프라 전용) | — |

## 검증 시나리오

### 정상

- [ ] `terraform init`이 S3 백엔드에 연결되고 state가 원격에 올라간다
- [ ] `terraform plan`이 **`No changes. Your infrastructure matches the configuration.`** 를 출력한다 (P3 핵심 조건)
- [ ] 두 세션에서 동시에 `terraform apply`를 시도하면 뒤쪽이 lockfile 때문에 대기·실패한다 (락 동작 확인)
- [ ] SG 규칙 하나를 코드에서 의도적으로 바꾼 뒤 `plan`이 그 한 줄만 변경으로 잡는다 → 되돌린다

### 엣지 · 실패

- [ ] `terraform plan`이 EC2 인스턴스를 **replace(destroy/create)** 하겠다고 하면 **즉시 중단** — `ami`·`user_data`·`subnet_id`·`availability_zone` 드리프트가 원인. `ignore_changes`로 해소되기 전엔 apply 금지
- [ ] `prevent_destroy` 덕분에 `terraform destroy`가 인스턴스에서 오류로 막힌다 (의도된 동작)
- [ ] state 버킷 버저닝이 켜져 있어, state를 잘못 덮어써도 이전 버전으로 복구된다
- [ ] AWS 자격증명이 없는 CI에서 `plan` 잡이 실패해도 `main` 배포 파이프라인(`ci-cd.yml`)은 영향받지 않는다

### 수동 / 통합

- [ ] import 후 **서비스 무중단 확인**: `curl -fsSI https://api.tripfit.online/health`, `https://grafana.tripfit.online` 응답, 앱→MySQL·Redis 연결 정상
- [ ] `aws ec2 describe-instances`로 인스턴스 ID 4개가 import 전후 동일한지 확인 (재생성 없음 증명)
- [ ] **P2.5 하드닝 검증**: `TP-db`·`TP-redis` stop/start 후 `describe-instances`에 `PublicIpAddress`가 없음 확인 · private IP·인스턴스 ID 불변 확인 · 앱 재연결(DB 쿼리, 로그인 후 토큰 갱신으로 Redis refresh token 저장·rotate 확인) 정상

## 완료 기준

- [ ] `terraform plan` = **0 add / 0 change / 0 destroy**
- [ ] state가 S3 원격 백엔드에 저장되고 로컬 `terraform.tfstate`는 `.gitignore` 처리
- [ ] EC2 4대에 `prevent_destroy` 적용 확인
- [ ] PR에서 `terraform fmt -check`·`validate`·`plan` 잡이 동작
- [ ] `docs/decisions/012-terraform-iac.md` 작성, `docs/decisions/README.md` 목록에 추가
- [ ] `TP-db`·`TP-redis` 공인 IP 제거 확인 (P2.5) + `deploy/README.md`·`decisions/010` amend
- [ ] "문서 vs 실제" 표의 나머지 amend 대상(`ec2-split-deployment.md` CIDR 예시 등) 반영
- [ ] `docs/specs/README.md`에 이 스펙 등록
- [ ] `./gradlew test` 통과 (인프라 전용 변경이라 회귀 없음 확인용)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| EC2 import 시 **강제 재생성** | 확정(가드레일) | 최대 위험. `TP-db` 재생성 = MySQL 전손. `prevent_destroy` + `ignore_changes` + plan 수동 검토로 방어 |
| AMI `ami-0bc151a94289adb52` 만료 | 리스크 | Ubuntu AMI는 주기적으로 deregister된다. 사라져도 **기존 인스턴스는 멀쩡**하지만 `data "aws_ami"`로 조회하면 plan이 깨진다 → AMI ID를 **하드코딩 + `ignore_changes`** 로 고정 |
| Terraform 실행 주체의 IAM 권한 | **[미정]** | 현재 `claude-infra` 사용자는 EC2·Route 53 read는 되지만 `iam:*`·`s3:ListAllMyBuckets`이 막혀 있다. state 버킷 생성·CI 역할 발급은 **계정 관리자 권한 필요** — 결정 필요자: 사용자(AWS 루트 계정 보유자) |
| `terraform destroy` 오조작 | 완화 | `prevent_destroy` + README 금기 명시 + CI에서 apply/destroy 미실행 |
| default VPC 의존 | 수용 | 1:1 import이므로 default VPC를 그대로 쓴다. 전용 VPC 신설은 Out of Scope |
| SSH 22 `0.0.0.0/0` | **별도 이슈** | 이번 "정석 정렬" 결정은 공인 IP 제거(P2.5)까지만 — SSH 제한은 관리자 IP 고정/SSM 전환 중 선택이 필요해 범위 밖 |
| **`TP-db` stop/start 다운타임** | **수용(운영 필요)** | P2.5 실행 구간 동안 API 전체가 DB 연결 불가로 장애 — 저트래픽 시간대 선정 필수. `TP-redis`는 fail-open이라 영향 작음(`decisions/010`) |
| Terraform vs OpenTofu | 확정 | 사용자 지정대로 **Terraform**(BUSL) 사용 |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-27 | 초안 — 사용자 결정(1:1 import · `infra/terraform/` · S3+native lockfile · 출시 이후/could) 반영, AWS 실측 인벤토리 포함 |
| 2026-08-27 | P2.5 추가 — 사용자가 "문서 vs 실제" 불일치를 실제 amend 대신 **정석(문서 의도) 방향으로 하드닝**하기로 결정. 서브넷 이전은 AWS 제약(재생성 필요)상 Out of Scope 유지, `TP-db`·`TP-redis` 공인 IP 제거(stop/start, 경량)만 이번 스펙에 포함 |
