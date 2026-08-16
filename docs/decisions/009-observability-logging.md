# 009 — 로깅·모니터링 인프라 (Loki + Grafana)

- **상태:** 확정
- **날짜:** 2026-08-01
- **관련:** Issue **#77**

## 맥락

현재 프로덕션 로그 확인은 SSH 접속 후 `docker logs`로 매번 수동 조회하는 방식뿐이다.

- 컨테이너가 재배포되면 이전 로그가 사라진다(보존 안 됨).
- 최근 사례(Google OAuth `redirect_uri` 실패)처럼, 실패가 `WARN` 로그로만 남고 API 응답은 200/204로 정상 반환되는 **조용한 실패**는 누군가 직접 로그를 뒤지지 않으면 발견할 방법이 없었다.
- 프론트와 협업 중 "테스트해봤는데 안 되는 것 같다" → 백엔드가 SSH로 재현·확인하는 패턴이 반복되고 있다. 프론트가 직접 로그를 조회할 방법이 없다.

## 결정

**Loki + Grafana**를 **신규 EC2 C**(t3.micro, App/DB와 별도 인스턴스)에 컨테이너로 추가한다.

- API EC2(Instance A)는 이미 908MB 중 97MB만 여유(스와핑 중)라 모니터링을 얹을 여유가 없다. 장애·부하 시 앱과 모니터링이 동시에 죽는 문제도 피한다.
- EC2 C는 A/B와 같은 VPC·서브넷·키페어(`TripFit-Keypair`)를 재사용해 네트워크·접근 방식을 통일한다.
- **Loki**: 로그 저장소. 인덱싱 없이 라벨 기반으로 동작해 가볍다.
- **Grafana**: 로그 검색(LogQL)·대시보드 UI. 프론트에게 읽기 전용 접근을 공유해 SSH 없이 직접 조회할 수 있게 한다.
- 수집은 A/B 각 컨테이너의 docker logging driver 전환(또는 Promtail 추가)으로 EC2 C의 Loki에 전송한다.

## 고려한 대안

| 대안 | 공수 | 장점 | 단점 |
|------|------|------|------|
| A. 현행 유지 (SSH + `docker logs`) | 없음 | 추가 인프라 없음 | 매번 수동 확인, 재배포 시 로그 유실, 프론트 공유 불가 |
| B. ELK (Elasticsearch + Logstash + Kibana) | 높음 — 2~3일+. 별도 리소스 증설(ES 힙 최소 2GB+), Logstash/Filebeat 파이프라인 구성, Kibana 인증 설정 | 강력한 검색·대시보드, 생태계가 넓음 | 현재 인프라(EC2 1대) 대비 오버스펙, 지속적 운영 부담(버전 업·디스크 관리) |
| C. Logback → Discord 웹훅 알림 | 매우 낮음 — 반나절 이내. Logback appender 클래스 + `logback-spring.xml` 설정 + env var | 실시간 알림, 코드 변경 최소 | 검색·기간 조회 불가(Discord 채팅 스크롤에 의존), 과거 로그 조회 안 됨 |
| **D. Loki + Grafana (택함)** | 중간 — 반나절~1일. 컨테이너 2개 추가, docker logging driver로 로그 수집 | ELK급 검색 경험을 가볍게 제공, 프론트 셀프서브 대시보드 공유 가능, 필요 시 Grafana Alerting으로 Discord 알림도 병행 가능 | 신규 컨테이너로 리소스 사용량 증가, 프론트 공유 시 접근 제어 설계 필요 |

## 트레이드오프 · 후속 리스크

- 리소스: EC2 C도 t3.micro(1GB)라 Loki+Grafana+OS 합산 사용량을 계속 모니터링 필요 — 로그량 증가 시 t3.small 등으로 업그레이드 검토.
- 비용: EC2 인스턴스 1대 추가 (약 월 $7.5, t3.micro 온디맨드 기준).
- 접근 제어: Grafana를 프론트와 공유하려면 인증(계정 발급) 또는 네트워크 제한(IP 화이트리스트)이 필요하다 — 퍼블릭 오픈 금지.
- 로그 보존 기간(retention): 디스크 용량과 함께 정책을 별도로 정해야 한다.
- 민감정보: 로그에 개인정보·시크릿이 남지 않는지 기존 로깅 코드 재점검 필요(현재도 원칙은 지키고 있으나, 대시보드 공유 전 재확인).
- 실시간 알림: 이번 결정은 검색·대시보드에 집중한다. Discord 실시간 알림(Grafana Alerting 연동)은 즉시 구현 범위가 아니라 후속 작업으로 남긴다.

## 후속 작업

- [x] EC2 C 프로비저닝 (t3.micro, A/B와 동일 VPC·서브넷·키페어) — `TP-monitoring`, 2026-08-01
- [x] `docker-compose.yml`(`deploy/monitoring/`)에 `loki`, `grafana` 서비스 추가
- [x] A/B 각 컨테이너의 docker logging driver를 Loki로 전환
- [ ] Grafana 기본 대시보드 구성 (앱 로그 검색용) — 현재는 Loki datasource만 프로비저닝, 대시보드 없음
- [ ] 프론트 공유용 접근 방법 결정 (읽기 전용 계정 vs IP 제한 등)
- [ ] 로그 보존 기간 정책 확정 (`loki-config.yaml` 현재 잠정 7일)
- [x] `deploy/README.md`·`deploy/ec2-split-deployment.md`에 EC2 C 및 운영 절차 반영
- [ ] (후속, 이번 범위 아님) Grafana Alerting → Discord 웹훅 실시간 에러 알림
- [ ] (후속, 이번 범위 아님) 호스트 리소스(RAM/디스크/swap) 지표 수집 — Loki는 로그 전용이라 `docker logs`에 안 찍히는 OOM·swap 상황은 별도 파이프라인(Prometheus+node_exporter 또는 dmesg/journalctl용 Promtail) 필요
- [ ] (후속, 이번 범위 아님) EC2 C Elastic IP — 현재 A만 EIP 보유, C는 동적 public IP(SSH·Grafana 접근용). private IP(Loki push 경로)는 이미 고정이라 기능상 필수는 아니지만, 인스턴스 stop/start 시 public IP가 바뀔 수 있어 프론트에 Grafana URL을 공유하기 전 검토 권장
