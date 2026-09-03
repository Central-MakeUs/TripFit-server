# Prometheus 모니터링 구축

> 상태: Draft
> MVP: Out of scope (Milestone `출시 이후` · `priority: could`)
> 관련 BR: N/A

애플리케이션 메트릭 수집을 위해 Spring Boot Actuator의 Prometheus 엔드포인트를 열고, 인터넷 비노출 보안을 유지하면서 EC2 C(모니터링)에서 메트릭을 수집할 스크래핑 통신 경로를 결정하는 스펙이다.

## 목표

Grafana 한 곳에서 로그(Loki)와 메트릭(Prometheus)을 함께 보게 만들어, JVM·API 지연·DB 커넥션 상태를 SSH 없이 확인할 수 있게 한다.

## 배경

EC2 C(`TP-monitoring`)에서 이미 운영 중인 **Loki + Grafana** 스택(`deploy/monitoring/docker-compose.yml`, `docs/decisions/009-observability-logging.md`)에 Prometheus를 추가해, 로그에 더해 **애플리케이션·시스템 메트릭**(JVM 힙·GC, HTTP 요청 수·latency, HikariCP 커넥션 풀 등)을 수집하고 Grafana 한 곳에서 함께 조회한다.

## 현재 상태 (2026-09-03 실측)

| 항목 | 실제 |
|------|------|
| Loki + Grafana | EC2 C에서 운영 중 — `grafana.tripfit.online`, datasource provisioning은 `grafana-provisioning/datasources/loki.yaml` |
| 앱 의존성 | `spring-boot-starter-actuator`만 있음 — **`micrometer-registry-prometheus` 없음** → `/actuator/prometheus` 엔드포인트가 생성되지 않는다 |
| actuator 노출 설정 | `application.yml`의 `management.endpoints.web.exposure.include: health` — `prometheus` 미노출 |
| 앱 포트 바인딩 | `deploy/app/docker-compose.yml`: `"127.0.0.1:${APP_PORT:-8080}:8080"` — 루프백 전용이라 EC2 C에서 직접 접근 불가 |
| nginx | `deploy/nginx/snippets/proxy-api.conf`: `location /actuator/ { return 404; }` — 외부 노출 의도적으로 차단 |

즉 **스크래핑 경로가 두 겹으로 막혀 있고, 그걸 뚫는 방식이 이 스펙의 핵심 결정 사항**이다.

### 의존성 좌표 확인 결과 (2026-09-03, `researcher` 조사)

| 항목 | 확인 내용 |
|------|-----------|
| 좌표 | `io.micrometer:micrometer-registry-prometheus` — **3.x와 동일**, 버전 명시 불필요(Boot BOM이 관리) |
| 자동설정 존재 여부 | `spring-boot-micrometer-metrics-4.1.0.jar`에 `PrometheusMetricsExportAutoConfiguration`이 이미 있고 등록돼 있다. 다만 `@ConditionalOnClass`가 `PrometheusMeterRegistry`를 요구해, registry 의존성이 없으면 자동설정이 통째로 꺼진다 |
| 엔드포인트 id | `PrometheusScrapeEndpoint`의 `@WebEndpoint(id="prometheus")` — 노출 목록에 쓸 이름은 `prometheus` |
| **3.x와 달라진 점** | 자동설정 클래스가 `spring-boot-actuator-autoconfigure`에서 **별도 모듈 `spring-boot-micrometer-metrics`로 이동**했다. 그 클래스를 코드에서 직접 import하는 3.x 예제는 경로가 깨진다 — 이번 작업 범위(의존성 + yml)에서는 직접 참조할 일이 없어 영향 없음 |

의존성과 노출 설정은 **둘 다** 필요하다. 하나만 해서는 엔드포인트가 404다.

## 스크래핑 경로 — **(a) 확정** (2026-09-03 사용자 승인)

어느 안이든 **인터넷 비노출 유지**가 전제이며, 보안 그룹 인바운드 규칙 추가가 따라온다.

| 안 | 내용 | 판정 |
|----|------|------|
| **(a)** | nginx 스니펫에 `/actuator/prometheus`만 EC2 C private IP `allow` + `deny all` | **채택** — 앱 포트는 루프백(`127.0.0.1:8080`) 그대로 유지되어 공격 표면이 늘지 않고, 변경 범위가 nginx 설정 한 곳으로 끝난다 |
| (b) | 앱 포트를 private IP에 바인딩 + SG로 EC2 C 출발지만 허용 | 미채택 — 앱 포트 자체를 사설망에 여는 방식이라, SG 규칙 하나만 잘못돼도 8080이 노출된다. (a)보다 실수 비용이 크다 |
| (c) | Prometheus를 EC2 A에 함께 배치 | 미채택 — `decisions/009`가 EC2 A 메모리 부족(908MB 중 97MB 여유)을 이유로 이미 배제한 방향 |

(a)는 기존 nginx 차단 규칙(`location /actuator/ { return 404; }`)보다 **더 구체적인 경로 블록을 추가**하는 형태다. nginx는 정확 일치·정규식 우선순위 규칙을 따르므로, `location = /actuator/prometheus`를 별도로 두고 그 안에서만 EC2 C를 허용한다. 나머지 `/actuator/*`는 404가 그대로 유지된다.

## 요구사항

### Must Have

- [ ] `build.gradle`에 `io.micrometer:micrometer-registry-prometheus` **의존성 추가** — **버전은 생략한다**(Spring Boot BOM이 관리, 4.1.0 기준 Micrometer 1.17.0)
- [ ] `application.yml`의 `management.endpoints.web.exposure.include`에 `prometheus` 추가 (`health`와 병기)
- [ ] 스크래핑 경로 (a)/(b)/(c) 결정 후 구성 — nginx 스니펫 또는 포트 바인딩 + SG 인바운드, 인터넷 비노출 검증 포함
- [ ] `deploy/monitoring/docker-compose.yml`(**EC2 C**)에 Prometheus 컨테이너 추가 — 루트 `docker-compose.yml`은 로컬 MySQL용이라 대상 아님
- [ ] Prometheus scrape 설정 파일 추가 (EC2 A 타깃, `loki-config.yaml`과 동일한 마운트 패턴)
- [ ] Grafana datasource provisioning에 `prometheus.yaml` 추가 (`loki.yaml`과 동일 패턴)
- [ ] 핵심 대시보드 구성 — JVM 힙·GC, HTTP 요청 수·latency, HikariCP 커넥션 풀
- [ ] `deploy/README.md` 갱신 — 신규 컨테이너·포트·env·SG 규칙, private IP 관리 패턴(`LOKI_HOST` 절과 동일 방식)

### Out of Scope (이번 스펙에서 하지 않음)

- Alertmanager 구축 (우선 대시보드까지만)
- 분산 트레이싱(Zipkin, Tempo)
- SSH(22) `0.0.0.0/0` 정리 — `#125`에서도 범위 밖으로 분리된 별개 주제

## 검증 시나리오

### 정상

- [ ] EC2 C의 Prometheus가 EC2 A의 `/actuator/prometheus`를 정상 스크랩한다 (Targets 화면 `UP`)
- [ ] Grafana에서 Prometheus datasource로 JVM 힙·HTTP latency·HikariCP 지표가 조회된다

### 엣지 · 실패

- [ ] 인터넷에서 `https://api.tripfit.online/actuator/prometheus` 호출 시 **404 또는 403** — 외부 노출 0을 실제로 확인한다
- [ ] `/actuator/env`·`/actuator/heapdump` 등 나머지 엔드포인트는 EC2 C에서도 접근되지 않는다
- [ ] Prometheus 컨테이너가 죽어도 앱은 정상 동작한다 (스크랩 대상일 뿐 의존성이 아님)

## 완료 기준

- [ ] 위 Must Have 전 항목 반영
- [ ] `./gradlew test` 통과
- [ ] 위 검증 시나리오의 정상·엣지 항목 전부 확인
- [ ] EC2 C 메모리 사용량을 배포 후 확인해 여유가 남는지 기록 (아래 리스크)

## 리스크

- **EC2 C 메모리** — t3.micro(1GB)에 nginx·certbot·Loki·Grafana가 이미 떠 있다. Prometheus와 시계열 저장까지 얹으면 부족할 수 있어 retention·scrape interval을 보수적으로 잡거나 t3.small 승격을 검토해야 한다 (`decisions/009`에 동일 리스크 기록됨).
- **actuator 노출 범위** — `/actuator/prometheus`만 열고 `env`·`heapdump` 등 나머지는 계속 차단돼야 한다.

## 관련

- 이슈: `#126` · 관련 `#125`(Terraform IaC 전환 — SG 규칙이 추가되면 import 대상에 포함)
- 결정: `docs/decisions/009-observability-logging.md`

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-09-03 | 실제 인프라 실측 반영 — Micrometer 의존성 부재, actuator 미노출, 스크래핑 경로 이중 차단(루프백 바인딩 + nginx 404)을 명시하고 경로 결정 항목 추가 |
| (이전) | 초안 |
