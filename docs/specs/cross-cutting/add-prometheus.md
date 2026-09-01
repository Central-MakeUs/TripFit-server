---
name: add-prometheus
status: Draft
wave: 무관
implements: None
deferred: None
---

# Prometheus 모니터링 구축

## 개요
기존 Grafana + Loki 환경에 Prometheus를 추가하여 애플리케이션 메트릭(CPU, 메모리, DB 커넥션, HTTP 요청 등)을 수집하고 모니터링 가시성을 고도화한다.

## 완료 조건
- [ ] Spring Boot Actuator 및 Micrometer 설정 변경 (`/actuator/prometheus` 엔드포인트 노출)
- [ ] `docker-compose`에 Prometheus 컨테이너 추가
- [ ] Grafana에 Prometheus 데이터 소스 연동
- [ ] 핵심 대시보드(JVM 지표, API Latency 등) 구성 및 배포 스크립트 반영

## Out of Scope
- Alertmanager 구축 (우선 모니터링 대시보드까지만 구현)
- 분산 트레이싱(Zipkin, Tempo) 구축
