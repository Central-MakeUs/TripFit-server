# Prometheus 수치 수집 · 기준값 알림

> 상태: **Approved** (2026-10-19 전면 재작성·승인 — `#126`)
> MVP: Out of scope (Milestone `출시 이후` · `priority: could`)
> 관련 BR: 해당 없음

앱·DB 서버의 CPU·메모리와 DB 커넥션 풀 대기 수치를 Prometheus로 모으고, 기준값을 일정 시간 넘으면 Grafana가 알림을 보내게 만드는 스펙이다. Prometheus는 새 서버 EC2 E에 둔다. 같은 구성을 로컬 부하 테스트 환경에도 띄워, 알림이 실제로 울리는지 먼저 검증한다.

이 스펙은 "수치를 모으고 기준을 넘으면 알린다"까지다. 알림을 받아 원인 리포트를 쓰는 부분은 [`incident-ai-report.md`](incident-ai-report.md)(장애 알림 AI 원인 리포터)가 다룬다.

## 목표

서버 자원이나 DB 커넥션 풀이 한계에 가까워지면, 사람이 대시보드를 보고 있지 않아도 알림을 받는다.

## 배경

- 운영 서버는 4대다: **A** 앱(`TP-server`) · **B** MySQL(`TP-db`) · **C** Loki+Grafana(`TP-monitoring`) · **D** Redis(`TP-redis`). 이 스펙이 **E**(`TP-metrics`)를 새로 띄운다.
- 지금 Grafana(C)에 연결된 데이터는 Loki 로그뿐이다. CPU·메모리 같은 수치는 어디에도 모이지 않는다.
- 2026-08-26부터 도메인별 에러 로그 알림 4개가 Discord로 간다. 이 규칙들은 운영 Grafana에 API로 직접 만들었고 저장소에는 없다([`009`](../../decisions/009-observability-logging.md) 후속 작업 참고).
- 이 스펙의 2026-09-03 판은 Prometheus를 C에 두는 계획이었다. C는 t3.micro(1GB)에 nginx·certbot·Loki·Grafana가 이미 떠 있어 메모리가 빠듯하다. 그래서 Prometheus 전용 서버 E를 새로 띄우기로 바꿨다(2026-10-19 사용자 결정).

### 의존성 확인 결과 (2026-09-03 `researcher` 조사, 2026-10-19 로컬 jar 재확인)

| 항목 | 확인 내용 |
|------|-----------|
| 좌표 | `io.micrometer:micrometer-registry-prometheus` — 버전은 Spring Boot BOM(Bill of Materials, 의존성 버전 묶음)이 관리한다 |
| 자동설정 | `spring-boot-micrometer-metrics-4.1.0.jar`에 `PrometheusMetricsExportAutoConfiguration`이 있다. `PrometheusMeterRegistry` 클래스가 있어야 켜지므로 registry 의존성이 없으면 엔드포인트도 없다 |
| 엔드포인트 id | `prometheus` — 노출 목록에 이 이름을 넣는다 |
| 3.x와 다른 점 | 자동설정이 별도 모듈 `spring-boot-micrometer-metrics`로 옮겨졌다. 이번 작업은 의존성과 yml만 바꾸므로 영향이 없다 |

## 변경 범위 (2026-09-03 판 대비)

### ADDED

- 새 서버 E(t3.micro)와 `deploy/metrics/` 구성
- A·B의 node_exporter(호스트 CPU·메모리 수집기)
- Grafana 기준값 알림 규칙과 연락처(파일로 관리)
- 로컬 검증 환경(`loadtest/`의 `observability` 프로필)과 트래픽 폭주 시나리오 S4

### MODIFIED

- Prometheus 위치: C → E
- 스크래핑 경로 "nginx 허용 목록" 안의 허용 출발지: C private IP → E private IP
- 알림: Out of Scope → 기준값 알림을 범위에 넣음 (Alertmanager가 아니라 Grafana 알림 기능을 씀)

### REMOVED

- C의 `deploy/monitoring/docker-compose.yml`에 Prometheus 컨테이너를 추가하는 계획 항목 — 구현된 적이 없는 계획이라 삭제할 코드는 없다

## 요구사항

### Must Have

**앱**

- [ ] `build.gradle`에 `io.micrometer:micrometer-registry-prometheus` 추가 (버전 생략)
- [ ] `application.yml`·`application-loadtest.yml`의 `management.endpoints.web.exposure.include`에 `prometheus` 추가

**운영 인프라**

- [ ] E 생성 — A~D와 같은 VPC·서브넷·키페어, git 체크아웃 기반(`~/TripFit-server`). 생성 전 사용자 확인
- [ ] `deploy/metrics/docker-compose.yml` — Prometheus, 보관 15일·최대 2GB(`--storage.tsdb.retention.time=15d`, `--storage.tsdb.retention.size=2GB`)
- [ ] `deploy/metrics/prometheus.yml` + 운영 기록 규칙 파일 — 대상 주소는 env로 주입
- [ ] A·B에 node_exporter 컨테이너 추가 (`deploy/app/`, `deploy/mysql/` compose)
- [ ] nginx `location = /actuator/prometheus` — E private IP만 허용
- [ ] 아래 "접근 경로와 보안 그룹" 표의 보안 그룹(SG) 규칙 전부 — AI 원인 리포터가 쓰는 경로 포함
- [ ] C를 git 체크아웃 기반으로 전환 — 지금은 `~/monitoring`에 파일을 직접 복사해 둔 상태라 provisioning 파일을 배포할 길이 없다

**Grafana (운영·로컬 공통 파일)**

- [ ] `grafana-provisioning/datasources/prometheus.yaml` — 주소는 env
- [ ] `grafana-provisioning/alerting/` — 아래 "알림 규칙" 표의 규칙과 연락처. 기준값·지속 시간·평가 주기·환경 이름은 env로 받아 같은 파일을 쓴다
- [ ] 기존 에러 로그 알림 4개와 연락처 `tripfit-discord-alerts`는 건드리지 않는다

**로컬 검증 환경 (`loadtest/`)**

- [ ] `observability` 프로필 — 앱 컨테이너(CPU 2 · 메모리 1GB, 운영 t3.micro와 비슷하게), MySQL 컨테이너 상한(CPU 2 · 메모리 1GB), Prometheus, cAdvisor(컨테이너 수치 수집기), Grafana 10.4.2(운영과 같은 버전)
- [ ] 기존 S1~S3의 `bootTestRun` 실행 방식은 그대로 둔다 — 새 프로필은 별도로 띄운다
- [ ] k6 시나리오 `s4-traffic-surge.js` — 여행방 조회 같은 평범한 API에 요청을 몰아 CPU·메모리·커넥션 풀을 기준값 위로 올린다
- [ ] `loadtest/README.md`에 S4와 `observability` 프로필 실행 절차 추가

### Nice to Have

- [ ] 핵심 대시보드 — JVM 힙·GC, HTTP 요청 수·지연, HikariCP, 서버별 CPU·메모리
- [ ] C·D·E의 node_exporter

### Out of Scope

- AI 원인 리포터 — [`incident-ai-report.md`](incident-ai-report.md)
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

### 수집·알림 흐름

수치는 E의 Prometheus가 A·B에서 가져가고, C의 Grafana가 그 수치로 알림 규칙을 평가한다.

```
[A] 앱 /actuator/prometheus (nginx: E만 허용) · node_exporter :9100 ─┐
[B] node_exporter :9100 ─────────────────────────────────────────────┤ 수집
                                                                     v
                                                     [E] Prometheus :9090
                                                                     │ 조회
                                                                     v
                              [C] Grafana ── 기준값 초과 ──> 연락처(contact point)
                                                             = AI 원인 리포터
```

서버별로 추가되는 것은 다음과 같다.

| 서버 | 추가되는 것 | 비고 |
|------|-------------|------|
| A (앱) | `micrometer-registry-prometheus` 의존성, node_exporter 컨테이너 | A는 메모리가 빠듯하다(아래 리스크) |
| B (MySQL) | node_exporter 컨테이너 | B에는 MySQL만 있어 호스트 수치가 곧 DB 수치다 |
| C (Grafana) | Prometheus datasource, 알림 규칙 파일 | git 체크아웃 기반으로 전환해 파일을 배포한다 |
| E (신규) | Prometheus (+ AI 원인 리포터) | t3.micro |

### 스크래핑 경로

앱의 `/actuator/prometheus`를 E에서 가져가는 방법으로 세 안을 비교했고, 2026-09-03에 사용자가 "nginx 허용 목록" 안을 승인했다. 2026-10-19에는 허용 출발지만 C에서 E로 바꿨다.

| 안 | 내용 | 판정 |
|----|------|------|
| nginx 허용 목록 | nginx에 `location = /actuator/prometheus`를 따로 두고 E private IP만 `allow`, 나머지 `deny all` | **채택** — 앱 포트는 루프백(`127.0.0.1:8080`) 그대로라 공격 표면이 늘지 않는다 |
| 앱 포트 사설망 개방 | 앱 포트를 private IP에 바인딩하고 SG로 E만 허용 | 미채택 — SG 규칙 하나만 잘못돼도 8080이 열린다 |
| A에 Prometheus 배치 | Prometheus를 A에 함께 띄움 | 미채택 — A는 908MB 중 97MB만 여유다([`009`](../../decisions/009-observability-logging.md) 배경) |

기존 `location /actuator/ { return 404; }`는 그대로라 다른 actuator 엔드포인트는 계속 404다.

### 접근 경로와 보안 그룹

두 스펙(이 문서와 AI 원인 리포터)이 쓰는 서버 간 경로 전부다. 인터넷에는 하나도 열지 않는다.

| 경로 | 호출하는 쪽 | 접근 제한 | 쓰는 스펙 |
|------|-------------|-----------|-----------|
| A nginx `/actuator/prometheus` | E Prometheus | nginx `allow` E private IP + SG ← E | 이 문서 |
| A·B `:9100` (node_exporter) | E Prometheus | SG ← E | 이 문서 |
| E `:9090` (Prometheus) | C Grafana | SG ← C | 이 문서 |
| E `:9095` (AI 원인 리포터 `/alerts`) | C Grafana | SG ← C + 공유 토큰 | AI 원인 리포터 |
| C `:3100` (Loki) | E AI 원인 리포터 | SG ← E (지금은 A·B만 허용) | AI 원인 리포터 |

### 환경별 차이를 흡수하는 방법

로컬과 운영은 DB 수치를 얻는 방법이 다르다. 운영은 B 호스트의 node_exporter, 로컬은 MySQL 컨테이너의 cAdvisor다. 그래서 Prometheus **기록 규칙**(recording rule)으로 환경마다 같은 이름의 수치를 만들고, Grafana 알림 규칙은 그 이름만 본다.

| 기록 규칙 이름 | 의미 | 운영 원천 | 로컬 원천 |
|----------------|------|-----------|-----------|
| `tripfit:app_cpu_usage:ratio` | 앱 프로세스 CPU 사용률 (0~1) | `process_cpu_usage` | 같음 |
| `tripfit:app_heap_usage:ratio` | JVM 힙 사용률 | `jvm_memory_used_bytes` / `jvm_memory_max_bytes` (heap) | 같음 |
| `tripfit:db_cpu_usage:ratio` | DB 서버 CPU 사용률 | B의 `node_cpu_seconds_total` | `mysql` 컨테이너의 `container_cpu_usage_seconds_total` ÷ CPU 상한 |
| `tripfit:db_memory_usage:ratio` | DB 서버 메모리 사용률 | B의 `1 - MemAvailable / MemTotal` | `mysql` 컨테이너 working set ÷ 메모리 상한 |
| `tripfit:db_pool_pending` | 커넥션을 기다리는 요청 수 | `hikaricp_connections_pending` | 같음 |

Prometheus 설정 파일은 환경별로 따로 둔다(`deploy/metrics/`·`loadtest/observability/`). 운영 private IP는 저장소에 적지 않고 `LOKI_HOST`와 같은 방식으로 env로 넣는다.

### 알림 규칙

규칙 5개의 기준값과 지속 시간은 이 표가 SSOT다. 다른 절은 이 표를 가리킨다.

| 규칙 | 대상 수치 | 로컬 | 운영 (관찰 기간 1주) | 운영 (관찰 후) |
|------|-----------|------|----------------------|----------------|
| 앱 CPU | `tripfit:app_cpu_usage:ratio` | > 0.7, 1분 지속 | > 0.9, 10분 지속 | [미정] |
| 앱 메모리 | `tripfit:app_heap_usage:ratio` | > 0.7, 1분 | > 0.9, 10분 | [미정] |
| DB CPU | `tripfit:db_cpu_usage:ratio` | > 0.7, 1분 | > 0.9, 10분 | [미정] |
| DB 메모리 | `tripfit:db_memory_usage:ratio` | > 0.7, 1분 | > 0.9, 10분 | [미정] |
| DB 커넥션 풀 대기 | `tripfit:db_pool_pending` | > 0, 1분 | > 0, 5분 | 같음 |

- 운영 A는 평소에도 메모리 사용률이 70%를 넘는다(908MB 중 97MB 여유, [`009`](../../decisions/009-observability-logging.md) 배경). 70%를 바로 걸면 장애가 없어도 알림이 울린다.
- 그래서 운영의 첫 1주는 "관찰 기간" 열의 값으로 켜 두고 평소 값을 모은다. 1주 뒤 사용자가 서버별 기준을 정한다(2026-10-19 사용자 결정).
- 규칙마다 라벨 `env`·`resource`(`cpu`/`memory`/`db_pool`)·`target`(`app`/`db`)을 붙인다. AI 원인 리포터가 이 라벨로 무엇을 조사할지 정한다.
- 연락처는 AI 원인 리포터 웹훅이다. 리포터가 멈췄을 때 알림이 사라지지 않게 하는 생존 감시는 [`incident-ai-report.md`](incident-ai-report.md)의 "생존 감시"가 정한다.

## 검증 시나리오

### 정상

- [ ] E Prometheus의 Targets 화면에서 앱·A·B가 모두 `UP`
- [ ] Grafana에서 기록 규칙 5개가 로컬·운영 모두 값이 나온다
- [ ] 로컬 S4 실행 중 규칙이 "알림 규칙" 표의 로컬 지속 시간 뒤 발생(`Firing`)이 되고, 부하를 멈추면 해제(`Normal`)로 돌아온다

### 엣지 · 실패

- [ ] 인터넷에서 `https://api.tripfit.online/actuator/prometheus` → 404 또는 403
- [ ] `/actuator/env`·`/actuator/heapdump`는 E에서도 접근되지 않는다
- [ ] 인터넷에서 A·B의 9100, E의 9090·9095에 접속되지 않는다
- [ ] Prometheus가 멈춰도 앱은 정상 동작한다
- [ ] 운영의 CPU·메모리 규칙 4개는 "알림 규칙" 표의 관찰 기간 값으로 배포된다

## 완료 기준

- [ ] Must Have 전 항목 반영
- [ ] `./gradlew test` 통과, `./gradlew build` 성공
- [ ] 검증 시나리오 정상·엣지 전부 확인
- [ ] 배포 후 A·E 메모리 사용량 기록 (아래 리스크)
- [ ] `deploy/README.md`·`deploy/environment-reference.md`·`deploy/ec2-split-deployment.md`에 E·포트·env·SG 반영
- [ ] `docs/decisions/009-observability-logging.md` 후속 작업의 "호스트 리소스 지표 수집"·"EC2 C git 체크아웃 전환" 항목 갱신
- [ ] `docs/how-it-works.md`에 "서버 수치 수집과 알림" 절 추가 — actuator 노출 경로가 늘어나는 변경이라 STOP §6 대상
- [ ] `REMOVED` 항목: 삭제할 코드 없음 확인 (계획 철회만)

## 리스크·미결정

| 항목 | 상태 | 비고 |
|------|------|------|
| 운영 CPU·메모리 기준값 | [미정] | 1주 관찰 후 사용자가 서버별 값 확정. 그전까지는 "알림 규칙" 표의 관찰 기간 값 |
| A 메모리 | 확인 필요 | node_exporter는 약 20MB지만 A는 이미 swap을 쓴다. 배포 직후 메모리를 재고, 부족하면 사용자에게 보고 |
| cAdvisor가 Docker Desktop(macOS)에서 MySQL 컨테이너 수치를 내보내는지 | 확인 필요 | 구현 첫 단계에서 확인. 안 되면 대안을 사용자에게 묻는다 |
| Grafana provisioning 파일의 env 치환 | 확인 필요 | 구현 시 Grafana 10.4 문서로 확인 |
| E 비용 | 확정 | t3.micro 1대 추가 (월 약 $7.5, 009와 같은 기준) |

## 관련

- 이슈: `#126` · `#125`(Terraform)
- 결정: [`009-observability-logging.md`](../../decisions/009-observability-logging.md)
- 다음 단계: [`incident-ai-report.md`](incident-ai-report.md)

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-10-19 | 전면 재작성 — Prometheus를 새 서버 E로 이동, 기준값 알림·로컬 검증 환경·기록 규칙·접근 경로 표 추가, AI 원인 리포터를 별도 스펙으로 분리 |
| 2026-09-03 | 실제 인프라 실측 반영 — Micrometer 의존성 부재, actuator 미노출, 스크래핑 경로 이중 차단을 명시하고 "nginx 허용 목록" 안 확정 |
| (이전) | 초안 |
