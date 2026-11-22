# 기존 EC2 분리 배포 이력

이 문서는 단일 Lightsail 구성으로 대체하는 분리 배포의 이력이다. 운영 전환 방법은 [단일 서버 배포](README.md)를 따른다.

| 서버 | 역할 |
|---|---|
| EC2 A | Nginx·Certbot·Spring Boot API |
| EC2 B | MySQL 8.0 |
| EC2 C | Loki·Grafana |
| EC2 D | Redis |
| EC2 E | Prometheus·AI 장애 리포터 |

당시 앱은 사설 주소로 DB와 Redis에 연결했다. 구성의 상세 변경 이력은 [2026-11-23 전환 이전 문서](https://github.com/Central-MakeUs/TripFit-server/blob/e2a2ee7/deploy/ec2-split-deployment.md)에 보존되어 있다.
