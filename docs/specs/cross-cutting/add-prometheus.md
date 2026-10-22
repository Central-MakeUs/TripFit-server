# Prometheus 수치 수집 · 임계점 감시

> 상태: **Approved** (2026-10-19 전면 재작성·승인 — `#126`)
> MVP: Out of scope (Milestone `출시 이후` · `priority: could`)
> 관련 BR: 해당 없음

`#126`은 "앱·DB의 CPU·메모리가 임계점을 넘으면, AI가 원인을 분석한 리포트를 Discord로 받는다"는 기능이다. 이 문서는 그중 **수치를 모으고(Prometheus) 임계점을 넘었는지 판단하는(Grafana 감시 규칙)** 부분과, 기능 전체의 구현 순서를 다룬다. 리포트를 쓰고 보내는 부분은 [`incident-ai-report.md`](incident-ai-report.md)(AI 원인 리포터)가 다룬다.

## 목표

서버 자원이나 DB 커넥션 풀이 한계에 가까워지면, 사람이 대시보드를 보고 있지 않아도 AI 원인 리포터가 깨어나 리포트를 보낸다.

## 전체 그림

### 부품 3개

기능은 세 부품이 차례로 이어져 동작한다.

```
① 수치 기록              ② 임계점 감시                     ③ 리포트 작성·전송
Prometheus (E)     →     Grafana 감시 규칙 (C)       →     AI 원인 리포터 (E)  →  Discord
"앱 CPU 지금 82%"        "70% 넘은 지 1분 → 리포터 호출"     "원인은 ○○ 코드로 보임"
```

괄호 안은 서버다 — C는 기존 Loki+Grafana 서버, E는 이번에 새로 띄우는 서버(아래 "배경").

| 부품 | 하는 일 | 하지 않는 일 | 담당 문서 |
|------|---------|--------------|-----------|
| ① Prometheus | 15초마다 수치를 가져와 기록한다 | 위험한지 판단하지 않는다 | 이 문서 |
| ② Grafana 감시 규칙 | 1분마다 수치를 보고, 임계점을 넘은 상태가 지속 시간 동안 이어지면 발생 시 리포터를 **한 번** 부른다. 수치가 내려가면 리포터에 해제를 한 번 더 전한다 | 이번에 추가하는 임계점 규칙 5개는 **Discord에 직접 보내지 않는다** (예외는 아래) | 이 문서 |
| ③ AI 원인 리포터 | 수치·로그·코드를 모아 Gemini로 분석하고 Discord에 리포트를 보낸다 | 임계점을 판단하지 않는다 | [`incident-ai-report.md`](incident-ai-report.md) |

- 장애 한 건에 Discord에는 **발생 리포트 하나 + 해제 한 줄**이 간다. AI 분석이 실패하면 리포트 대신 수치만 담은 메시지가 간다([`incident-ai-report.md`](incident-ai-report.md) "보내기").
- Grafana가 Discord로 **직접** 보내는 경로는 두 가지뿐이다.
  - `리포터 중단` 규칙 — 리포터가 멈추면 ③이 없으므로 기존 Discord 연락처로 직접 보낸다(아래 "감시 규칙" 표의 6번째 규칙).
  - 기존 에러 로그 알림 4개 — 이번 기능과 무관하게 지금처럼 직접 보낸다.
- ②를 Grafana에 맡긴 이유: Grafana가 이미 C에서 돌고 있고, 지속 시간·한 번만 부르기·해제 판단 기능이 있어 새로 만들 것이 없다(2026-10-19 사용자 결정).

### 구현 순서 — PR 2개

운영을 먼저 올린다(2026-10-19 사용자 결정). 운영 서버에는 일부러 부하를 걸지 않는다 — 임계점을 넘기려면 운영 서비스를 10분 넘게 느리게 만들어야 하고, 1주 관찰 기간의 평소 수치도 오염되기 때문이다. 부하를 거는 검증은 로컬 복제 환경에서 한다.

| 순서 | 무엇을 | 어디서 | 선행 |
|------|--------|--------|------|
| **PR 1 — 운영 구축** | 부품 ①②③을 만들고 운영에 배포. 배포 전에 단위 테스트와 로컬 가짜 알림 점검(부하 없음)을 마친다 | `main`에서 브랜치 | 없음 |
| PR 1 배포 후 1주 | 운영 평소 수치를 관찰하고, 사용자가 서버별 임계점을 정해 이 문서를 고친다 | 운영 | PR 1 배포 |
| **PR 2 — 부하 검증** | 로컬 복제 환경에서 트래픽 폭주(S4)로 감시 규칙이 울리는지, 리포트가 맞는지 10회 측정 | 두 선행이 머지된 뒤 `main`에서 브랜치 | PR 1 머지(감시 규칙 파일·리포터를 그대로 쓴다) + `#134` 머지(`loadtest/`가 `#134`에만 있다). 1주 관찰과는 병행할 수 있다 |

이 문서의 Must Have·검증·완료 항목에는 `PR 1`·`PR 2`·`1주 관찰` 중 하나를 붙인다. PR 1은 한 PR이지만 완료 기준이 두 문서에 나뉘어 있다 — 이 문서와 [`incident-ai-report.md`](incident-ai-report.md)의 PR 1 항목을 **모두** 만족해야 PR 1이 끝난다.

## 배경

- 이 기능 전 운영 서버는 4대였다: **A** 앱(`TP-server`) · **B** MySQL(`TP-db`) · **C** Loki+Grafana(`TP-monitoring`) · **D** Redis(`TP-redis`). 이 기능이 **E**(`TP-metrics`, t3.micro)를 2026-10-19에 새로 띄웠다(주소·ID는 [`deploy/environment-reference.md`](../../../deploy/environment-reference.md)).
- 지금 Grafana(C)에 연결된 데이터는 Loki 로그뿐이다. CPU·메모리 같은 수치는 어디에도 모이지 않는다.
- 2026-08-26부터 도메인별 에러 로그 알림 4개가 Grafana에서 Discord로 직접 간다. 이 규칙들은 운영 Grafana에 API로 직접 만들었고 저장소에는 없다([`009`](../../decisions/009-observability-logging.md) 후속 작업 참고). 이번 기능은 이 4개를 건드리지 않는다.
- 이 문서의 2026-09-03 판은 Prometheus를 C에 두는 계획이었다. C는 t3.micro(1GB)에 nginx·certbot·Loki·Grafana가 이미 떠 있어 메모리가 빠듯하다. 그래서 Prometheus 전용 서버 E를 새로 띄우기로 바꿨다(2026-10-19 사용자 결정).

### 의존성 확인 결과 (2026-09-03 `researcher` 조사, 2026-10-19 로컬 jar 재확인)

| 항목 | 확인 내용 |
|------|-----------|
| 좌표 | `io.micrometer:micrometer-registry-prometheus` — 버전은 Spring Boot BOM(Bill of Materials, 의존성 버전 묶음)이 관리한다 |
| 자동설정 | `spring-boot-micrometer-metrics-4.1.0.jar`에 `PrometheusMetricsExportAutoConfiguration`이 있다. `PrometheusMeterRegistry` 클래스가 있어야 켜지므로 registry 의존성이 없으면 엔드포인트도 없다 |
| 엔드포인트 id | `prometheus` — 노출 목록에 이 이름을 넣는다 |
| 3.x와 다른 점 | 자동설정이 별도 모듈 `spring-boot-micrometer-metrics`로 옮겨졌다. 이번 작업은 의존성과 yml만 바꾸므로 영향이 없다 |

## 변경 범위

2026-09-03 판 대비 바뀐 것이다.

### ADDED

- 새 서버 E와 `deploy/metrics/` 구성
- A·B의 node_exporter(호스트 CPU·메모리 수집기)
- Grafana 감시 규칙 6개(임계점 5개 + 리포터 중단 1개)와 연락처(파일로 관리)
- 로컬 복제 환경(`loadtest/`의 `observability` 프로필)과 트래픽 폭주 시나리오 S4 — PR 2

### MODIFIED

- Prometheus 위치: C → E
- 스크래핑 경로 "nginx 허용 목록" 안의 허용 출발지: C private IP → E private IP
- 임계점 감시: Out of Scope → 범위에 넣음 (Alertmanager가 아니라 Grafana 감시 규칙을 씀)

### REMOVED

- C의 `deploy/monitoring/docker-compose.yml`에 Prometheus 컨테이너를 추가하는 계획 항목 — 구현된 적이 없는 계획이라 삭제할 코드는 없다

## 요구사항

### Must Have

**앱 (PR 1)**

- [ ] `build.gradle`에 `io.micrometer:micrometer-registry-prometheus` 추가 (버전 생략)
- [ ] `application.yml`의 `management.endpoints.web.exposure.include`에 `prometheus` 추가

**운영 인프라 (PR 1)**

- [ ] E 생성 — A~D와 같은 VPC·서브넷·키페어, git 체크아웃 기반(`~/TripFit-server`), Elastic IP 할당(CI가 SSH로 접속하는 고정 주소). 생성 직전에 사용자 확인
- [ ] `deploy/metrics/docker-compose.yml` — Prometheus, 보관 15일·최대 2GB(`--storage.tsdb.retention.time=15d`, `--storage.tsdb.retention.size=2GB`). AI 원인 리포터는 E에서 빌드하지 않고 CI가 GHCR에 올린 이미지를 받는다
- [ ] `deploy/metrics/prometheus.yml` + 운영 기록 규칙 파일 — 대상 주소는 env로 주입
- [ ] A·B에 node_exporter 컨테이너 추가 (`deploy/app/`, `deploy/mysql/` compose)
- [ ] nginx `location = /actuator/prometheus` — E private IP만 허용
- [ ] 아래 "접근 경로와 보안 그룹" 표의 보안 그룹(SG) 규칙 전부 — AI 원인 리포터가 쓰는 경로 포함
- [ ] C를 git 체크아웃 기반으로 전환 — 지금은 `~/monitoring`에 파일을 직접 복사해 둔 상태라 provisioning 파일을 배포할 길이 없다

**Grafana 감시 규칙 (PR 1 · 운영·로컬 공통 파일)**

- [ ] `grafana-provisioning/datasources/prometheus.yaml` — 주소는 env
- [ ] `grafana-provisioning/alerting/tripfit-watch.yaml` — 아래 "감시 규칙" 표의 규칙 6개와 리포터 연락처. 규칙마다 연락처를 직접 지정한다(임계점 규칙 5개 → AI 원인 리포터 웹훅, `리포터 중단` → 기존 `tripfit-discord-alerts`). 지속 시간·평가 주기·환경 이름은 env로 받고, 임계점은 환경별 Prometheus 기록 규칙(`tripfit:threshold`)에 둔다 — 로컬·운영이 Grafana 파일 하나를 같이 쓴다
- [ ] C의 Grafana에 기능 스위치 `GF_FEATURE_TOGGLES_ENABLE=alertingSimplifiedRouting` 추가 — 규칙별 연락처 지정에 필요하다. 기존 알림 경로(policy tree)는 건드리지 않는다
- [ ] 기존 에러 로그 알림 4개와 연락처 `tripfit-discord-alerts`는 건드리지 않는다
- [ ] Prometheus 수집 대상에 리포터 `/metrics` 추가 (`리포터 중단` 규칙이 쓴다)

**배포 전 로컬 점검 환경 (PR 1)**

- [ ] `deploy/metrics/docker-compose.local.yml` — Prometheus·Loki·Grafana(위 provisioning 파일 그대로)·AI 원인 리포터를 로컬에 띄운다. 앱·부하는 없어도 되고, `deploy/metrics/local/send-fake-alert.sh`로 가짜 알림을 리포터에 보내 점검한다. 점검 항목은 [`incident-ai-report.md`](incident-ai-report.md) "배포 전 로컬 점검"

**CI 배포 (PR 1)**

- [ ] 별도 워크플로 `.github/workflows/deploy-observability.yml`의 `deploy-metrics`(E) 작업 — `incident-reporter/`·`deploy/metrics/`가 바뀐 main 반영 때 리포터 이미지를 빌드해 GHCR에 올리고, E에 SSH로 접속해 저장소를 main으로 맞춘 뒤 Secrets 값을 넣어 `docker compose up`
- [ ] 같은 워크플로의 `deploy-monitoring`(C) 작업 — `deploy/monitoring/`이 바뀐 main 반영 때 C에 SSH로 접속해 저장소를 main으로 맞춘 뒤 Secrets 값을 넣어 `docker compose up`
- [ ] 두 작업 모두 수동 실행(`workflow_dispatch`)을 둔다. 기존 `ci-cd.yml`(앱 테스트·배포)은 건드리지 않는다 — 같은 파일에 두면 수동 실행 때 기존 작업까지 돌기 때문이다(2026-10-19 사용자 결정)
- [ ] 새 비밀값은 아래 "GitHub Secrets" 표대로 GitHub Secrets로만 관리하고 서버 `.env`는 직접 고치지 않는다(`deployment.md` 규칙과 같음). B의 node_exporter는 비밀값이 없어 B에서 한 번 손으로 띄운다(B에는 CI 배포가 없다)

**로컬 복제 환경 (PR 2 · `loadtest/`)**

- [ ] `application-loadtest.yml`의 actuator 노출에 `prometheus` 추가
- [ ] `observability` 프로필 — 앱 컨테이너(CPU 2 · 메모리 1GB, 운영 t3.micro와 비슷하게), MySQL 컨테이너 상한(CPU 2 · 메모리 1GB), Prometheus, cAdvisor(컨테이너 수치 수집기), Grafana 10.4.2(운영과 같은 버전). 감시 규칙은 PR 1의 공통 파일을 그대로 쓴다
- [ ] 기존 S1~S3의 `bootTestRun` 실행 방식은 그대로 둔다 — 새 프로필은 별도로 띄운다
- [ ] k6 시나리오 `s4-traffic-surge.js` — 여행방 조회 같은 평범한 API에 요청을 몰아 CPU·메모리·커넥션 풀을 임계점 위로 올린다
- [ ] `loadtest/README.md`에 S4와 `observability` 프로필 실행 절차 추가

### Nice to Have (PR 1 이후 아무 때나)

- [ ] 핵심 대시보드 — JVM 힙·GC, HTTP 요청 수·지연, HikariCP, 서버별 CPU·메모리
- [ ] C·D·E의 node_exporter

### Out of Scope

- AI 원인 리포터 자체 — [`incident-ai-report.md`](incident-ai-report.md)
- 기존 에러 로그 알림 4개를 파일로 옮기는 일
- Alertmanager, 분산 트레이싱(Zipkin·Tempo)
- SSH(22) `0.0.0.0/0` 정리, Terraform import — `#125`가 E와 새 SG 규칙을 import 대상에 포함한다

## API / 인터페이스

클라이언트(앱·React) API 변경 없음. 서버 간 내부 경로는 아래 "접근 경로와 보안 그룹" 표에 있다.

## 데이터 모델

해당 없음 (DB 스키마 변경 없음).

## 비즈니스 규칙

해당 없음.

## 설계

### 서버별 구성

부품 ①②가 서버 A·B·C·E에 걸쳐 놓이는 모습은 다음과 같다.

```
[A] 앱 /actuator/prometheus (nginx: E만 허용) · node_exporter :9100 ─┐
[B] node_exporter :9100 ─────────────────────────────────────────────┤ ① 수집
                                                                     v
                                                     [E] Prometheus :9090
                                                                     │ 조회
                                                                     v
                              [C] Grafana 감시 규칙 ── ② 임계점 초과 ──> [E] AI 원인 리포터 :9095
```

| 서버 | 추가되는 것 | 비고 |
|------|-------------|------|
| A (앱) | `micrometer-registry-prometheus` 의존성, node_exporter 컨테이너 | A는 메모리가 빠듯하다(아래 리스크) |
| B (MySQL) | node_exporter 컨테이너 | B에는 MySQL만 있어 호스트 수치가 곧 DB 수치다 |
| C (Grafana) | Prometheus datasource, 감시 규칙 파일 | git 체크아웃 기반으로 전환해 파일을 배포한다 |
| E (신규) | Prometheus, AI 원인 리포터 | t3.micro |

### 스크래핑 경로

앱의 `/actuator/prometheus`를 E에서 가져가는 방법으로 세 안을 비교했고, 2026-09-03에 사용자가 "nginx 허용 목록" 안을 승인했다. 2026-10-19에는 허용 출발지만 C에서 E로 바꿨다.

| 안 | 내용 | 판정 |
|----|------|------|
| nginx 허용 목록 | nginx에 `location = /actuator/prometheus`를 따로 두고 E private IP만 `allow`, 나머지 `deny all` | **채택** — 앱 포트는 루프백(`127.0.0.1:8080`) 그대로라 공격 표면이 늘지 않는다 |
| 앱 포트 사설망 개방 | 앱 포트를 private IP에 바인딩하고 SG로 E만 허용 | 미채택 — SG 규칙 하나만 잘못돼도 8080이 열린다 |
| A에 Prometheus 배치 | Prometheus를 A에 함께 띄움 | 미채택 — A는 908MB 중 97MB만 여유다([`009`](../../decisions/009-observability-logging.md) 배경) |

기존 `location /actuator/ { return 404; }`는 그대로라 다른 actuator 엔드포인트는 계속 404다.

### 접근 경로와 보안 그룹

두 문서(이 문서와 AI 원인 리포터)가 쓰는 서버 간 경로 전부다. 인터넷에는 하나도 열지 않는다.

| 경로 | 호출하는 쪽 | 접근 제한 | 쓰는 부품 |
|------|-------------|-----------|-----------|
| A nginx `/actuator/prometheus` | E Prometheus | nginx `allow` E private IP + SG ← E | ① |
| A·B `:9100` (node_exporter) | E Prometheus | SG ← E | ① |
| E `:9090` (Prometheus) | C Grafana | SG ← C | ② |
| E `:9095` (AI 원인 리포터 `/alerts`) | C Grafana | SG ← C + 공유 토큰 | ②→③ |
| E `:9095` (AI 원인 리포터 `/metrics`) | E Prometheus | E 내부 | 리포터 중단 규칙 |
| C `:3100` (Loki) | E AI 원인 리포터 | SG ← E (2026-10-19 추가 — 그 전에는 A·B만 허용) | ③ |

### 환경별 차이를 흡수하는 방법

로컬과 운영은 DB 수치를 얻는 방법이 다르다. 운영은 B 호스트의 node_exporter, 로컬은 MySQL 컨테이너의 cAdvisor다. 그래서 Prometheus **기록 규칙**(recording rule)으로 환경마다 같은 이름의 수치를 만들고, 감시 규칙은 그 이름만 본다.

| 기록 규칙 이름 | 의미 | 운영 원천 | 로컬 원천 (PR 2) |
|----------------|------|-----------|------------------|
| `tripfit:app_cpu_usage:ratio` | 앱 프로세스 CPU 사용률 (0~1) | `process_cpu_usage` | 같음 |
| `tripfit:app_heap_usage:ratio` | JVM 힙 사용률 | `jvm_memory_used_bytes` / `jvm_memory_max_bytes` (heap) | 같음 |
| `tripfit:db_cpu_usage:ratio` | DB 서버 CPU 사용률 | B의 `node_cpu_seconds_total` | `mysql` 컨테이너의 `container_cpu_usage_seconds_total` ÷ CPU 상한 |
| `tripfit:db_memory_usage:ratio` | DB 서버 메모리 사용률 | B의 `1 - MemAvailable / MemTotal` | `mysql` 컨테이너 working set ÷ 메모리 상한 |
| `tripfit:db_pool_pending` | 커넥션을 기다리는 요청 수 | `hikaricp_connections_pending` | 같음 |

Prometheus 설정 파일은 환경별로 따로 둔다 — 운영 `deploy/metrics/`(PR 1), 배포 전 점검 `deploy/metrics/` 로컬 compose(PR 1, 수집 대상 없이 리포터만), 부하 검증 `loadtest/observability/`(PR 2). 운영 private IP는 저장소에 적지 않고 `LOKI_HOST`와 같은 방식으로 env로 넣는다.

### GitHub Secrets

E·C 배포에 쓰는 비밀값이다. 값은 사용자가 직접 등록한다.

| 구분 | Secret | 쓰는 곳 |
|------|--------|---------|
| 새로 등록 | `EC2_E_HOST` | E 고정 IP — CI SSH 접속 |
| 새로 등록 | `EC2_C_HOST` | C 고정 IP — CI SSH 접속 |
| 새로 등록 | `APP_PRIVATE_HOST` | A private IP — Prometheus의 앱·A 수집 대상 |
| 새로 등록 | `PROMETHEUS_HOST` | E private IP — C Grafana의 Prometheus·리포터 주소 |
| 새로 등록 | `REPORTER_TOKEN` | C Grafana → E 리포터 공유 토큰 |
| 새로 등록 | `GEMINI_API_KEY` · `GEMINI_MODEL` | E 리포터 |
| 새로 등록 | `GRAFANA_ADMIN_PASSWORD` | C Grafana 관리자 비밀번호(지금 C `.env`의 값을 그대로 옮김) |
| 재사용 | `EC2_SSH_KEY` · `EC2_USER` | CI SSH 접속 |
| 재사용 | `GHCR_PAT` · `GHCR_USERNAME` | E에서 리포터 이미지 받기 |
| 재사용 | `MYSQL_HOST` | B private IP — Prometheus의 DB 수집 대상 |
| 재사용 | `LOKI_HOST` | C private IP — 리포터의 로그 조회 |
| 재사용 | `DISCORD_WEBHOOK_URL` | 리포터가 보낼 기존 알림 채널 |

### 감시 규칙

Grafana에서는 "알림 규칙(alert rule)"이라고 부른다. 각 규칙은 "기록 규칙 수치 > 임계점(`tripfit:threshold{rule=…}`)"을 비교한다. Grafana가 계산식을 환경 변수로 치환하지 않아서, 임계점은 환경별 Prometheus 파일(운영 `deploy/metrics/prometheus/rules/tripfit.prod.yml`)에 숫자로 둔다. 앱·DB가 꺼져 수치가 사라지면 지속 시간 뒤 "수치 없음" 발생으로 리포터를 부른다 — 지속 시간이 배포 시간(약 2분)보다 길어 배포 중에는 울리지 않는다. 이 기능의 임계점 규칙은 Discord로 보내지 않고 리포터를 부르기만 하므로 "감시 규칙"이라 쓴다(예외: 6번째 `리포터 중단`). 규칙 6개의 임계점과 지속 시간은 이 표가 SSOT다.

| 규칙 | 대상 수치 | 로컬 (PR 2) | 운영 첫 1주 | 운영 1주 뒤 |
|------|-----------|-------------|------------------------|--------------|
| 앱 CPU | `tripfit:app_cpu_usage:ratio` | > 0.7, 1분 지속 | > 0.9, 10분 지속 | [미정] |
| 앱 메모리 | `tripfit:app_heap_usage:ratio` | > 0.7, 1분 | > 0.9, 10분 | [미정] |
| DB CPU | `tripfit:db_cpu_usage:ratio` | > 0.7, 1분 | > 0.9, 10분 | [미정] |
| DB 메모리 | `tripfit:db_memory_usage:ratio` | > 0.7, 1분 | > 0.9, 10분 | [미정] |
| DB 커넥션 풀 대기 | `tripfit:db_pool_pending` | > 0, 1분 | > 0, 5분 | 같음 |
| 리포터 중단 | 리포터 `up` | == 0, 2분 | == 0, 2분 | 같음 |

- 1~5번 규칙은 AI 원인 리포터를 부르고, 6번 `리포터 중단`만 기존 Discord 연락처 `tripfit-discord-alerts`로 직접 보낸다.

- 운영 A는 평소에도 메모리 사용률이 70%를 넘는다(908MB 중 97MB 여유, [`009`](../../decisions/009-observability-logging.md) 배경). 70%를 바로 걸면 장애가 없어도 리포터가 불린다.
- 그래서 운영은 첫 1주를 "운영 첫 1주" 열의 값으로 켜 두고 평소 값을 모은다. 1주 뒤 사용자가 서버별 임계점을 정하고 이 표를 고친다(2026-10-19 사용자 결정).
- 규칙마다 라벨 `env`·`resource`(`cpu`/`memory`/`db_pool`)·`target`(`app`/`db`)을 붙인다. AI 원인 리포터가 이 라벨로 무엇을 조사할지 정한다.

## 검증 시나리오

### PR 1 — 운영 (부하 없음)

- [ ] E Prometheus의 Targets 화면에서 앱·A·B·리포터가 모두 `UP`
- [ ] 운영 Grafana에서 기록 규칙 5개의 값이 나오고, 감시 규칙 6개가 평소에는 정상(`Normal`)이다
- [ ] Grafana → AI 원인 리포터 → Discord 경로 확인 — 항목은 [`incident-ai-report.md`](incident-ai-report.md) "운영 (PR 1 · 배포 직후)"
- [ ] 운영의 CPU·메모리 규칙 4개가 "감시 규칙" 표의 "운영 첫 1주" 값으로 배포된다
- [ ] 인터넷에서 `https://api.tripfit.online/actuator/prometheus` → 404 또는 403
- [ ] `/actuator/env`·`/actuator/heapdump`는 E에서도 접근되지 않는다
- [ ] 인터넷에서 A·B의 9100, E의 9090·9095에 접속되지 않는다
- [ ] Prometheus가 멈춰도 앱은 정상 동작한다

### PR 2 — 로컬 부하 검증

- [ ] S4 실행 중 감시 규칙이 "감시 규칙" 표의 로컬 지속 시간 뒤 발생(`Firing`)이 되어 리포터를 부르고, 부하를 멈추면 해제(`Normal`)로 돌아온다

## 완료 기준

**PR 1**

- [ ] Must Have의 "앱"·"운영 인프라"·"Grafana 감시 규칙"·"배포 전 로컬 점검 환경" 전 항목 반영
- [ ] `./gradlew test` 통과, `./gradlew build` 성공
- [ ] 검증 시나리오 "PR 1" 전부 확인
- [ ] 배포 후 A·E 메모리 사용량 기록 (아래 리스크)
- [ ] `deploy/README.md`·`deploy/environment-reference.md`·`deploy/ec2-split-deployment.md`에 E·포트·env·SG 반영
- [ ] `docs/decisions/009-observability-logging.md` 후속 작업의 "호스트 리소스 지표 수집"·"EC2 C git 체크아웃 전환" 항목 갱신
- [ ] `docs/how-it-works.md`에 "서버 수치 수집과 임계점 감시" 절 추가 — actuator 노출 경로가 늘어나는 변경이라 STOP §6 대상
- [ ] `REMOVED` 항목: 삭제할 코드 없음 확인 (계획 철회만)

**1주 관찰 (PR 1 배포 후)**

- [ ] 사용자가 서버별 운영 임계점을 정하고 "감시 규칙" 표의 [미정]을 채운다

**PR 2**

- [ ] Must Have의 "로컬 복제 환경" 전 항목 반영
- [ ] 검증 시나리오 "PR 2" 확인

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 운영 CPU·메모리 임계점 | [미정] | PR 1 배포 후 1주 관찰 → 사용자가 서버별 값 확정 |
| A 메모리 | 확인 필요 | node_exporter는 약 20MB지만 A는 이미 swap을 쓴다. PR 1 배포 직후 메모리를 재고, 부족하면 사용자에게 보고 |
| cAdvisor가 Docker Desktop(macOS)에서 MySQL 컨테이너 수치를 내보내는지 | 확인 필요 | PR 2 시작 때 확인. 안 되면 대안을 사용자에게 묻는다 |
| Grafana provisioning 파일의 env 치환 | 확정 | 2026-10-19 Grafana 10.4.2 로컬 실측 — `for`·`interval`·라벨·연락처 설정은 치환되고 계산식(`data.model`)은 치환되지 않는다. 그래서 임계점을 Prometheus 기록 규칙에 둔다(사용자 결정) |
| 규칙별 연락처 지정 | 확정 | 2026-10-19 Grafana 10.4.2 로컬 실측 — 기능 스위치 `alertingSimplifiedRouting`가 꺼져 있으면 규칙에 적은 연락처가 무시되고 기본 연락처로 간다. 운영 C에 스위치를 켠다(사용자 결정) |
| `리포터 중단` 알림 문구 | 확인 필요 | 운영 `tripfit-discord-alerts`에는 도메인 에러 알림용 맞춤 양식이 걸려 있어 이 알림 문구가 어색할 수 있다. PR 1 배포 때 테스트 전송으로 확인한다 |
| E 비용 | 확정 | t3.micro 1대 추가 (월 약 $7.5, 009와 같은 기준) + 공개 IPv4(Elastic IP) 월 약 $3.6 |

## 관련

- 이슈: `#126` · `#125`(Terraform) · `#134`(PR 2 선행)
- 결정: [`009-observability-logging.md`](../../decisions/009-observability-logging.md)
- 부품 ③: [`incident-ai-report.md`](incident-ai-report.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-10-19 | 운영 비밀값 관리 결정 반영(사용자 승인) — E·C 비밀값을 GitHub Secrets로 관리하고 별도 워크플로 `deploy-observability.yml`에 CI 배포 작업(`deploy-metrics`·`deploy-monitoring`, 바뀐 파일만·수동 실행 가능) 추가, 리포터 이미지는 CI가 GHCR에 올림, E Elastic IP, Grafana 관리자 비밀번호도 Secrets로 이전, GitHub Secrets 표 추가 |
| 2026-10-19 | PR 1 구현 중 결정 반영(사용자 승인) — 임계점을 Prometheus 기록 규칙(`tripfit:threshold`)으로, Grafana 기능 스위치 `alertingSimplifiedRouting` 켜기, 수치 없음은 알림으로 처리, `리포터 중단` 알림 문구 확인 항목 추가 |
| 2026-10-19 | 구현 순서 확정·문서 정리 — PR 1(운영 구축, 배포 전 가짜 알림 점검) → 1주 관찰 → PR 2(`#134` 머지 후 로컬 부하 검증). 운영에는 부하를 걸지 않는다. "Grafana 알림"을 "감시 규칙"으로 바꿔 Discord에 직접 보내지 않음을 명시(사용자 결정) |
| 2026-10-19 | 전면 재작성 — Prometheus를 새 서버 E로 이동, 임계점 감시·로컬 검증 환경·기록 규칙·접근 경로 표 추가, AI 원인 리포터를 별도 스펙으로 분리 |
| 2026-09-03 | 실제 인프라 실측 반영 — Micrometer 의존성 부재, actuator 미노출, 스크래핑 경로 이중 차단을 명시하고 "nginx 허용 목록" 안 확정 |
| (이전) | 초안 |
