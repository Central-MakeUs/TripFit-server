# 단일 서버 로컬 검증

앱·MySQL·Redis·Nginx를 한 서버에 모으는 #140의 로컬 검증 구성이다. 같은 Compose를 사용하는 운영 오버레이의 실행 방법은 [배포 안내](../README.md)를 참고한다. [재구축 스펙](../../docs/specs/cross-cutting/low-cost-server-rebuild.md)에 따라 월 기본료 $12의 Lightsail 2GB 후보를 검토하며, AWS 서버 생성이나 현재 운영 배포 전환은 수행하지 않는다.

## 준비

- Java 21, Python 3, Docker Desktop과 Docker Compose v2가 필요하다.
- 호스트의 18080·18081 포트가 비어 있어야 한다.
- 초기 실행에는 Gradle 의존성과 Docker 이미지를 내려받는 네트워크 연결이 필요하다.
- 외부 서비스의 실제 키나 기존 운영 `.env`를 사용하지 않는다. 프로젝트 이름은 `tripfit-single-local`로 고정되어 기존 개발 컨테이너와 분리된다.

## 실행

저장소 루트에서 실행한다.

```bash
./scripts/single-local.sh up
./scripts/single-local.sh verify
./scripts/single-local.sh stats
python3 scripts/verify-single-functional.py
./scripts/single-local.sh down
```

`up`은 bootJar를 빌드한 뒤 Java 실행 환경(JRE) 이미지를 만든다. MySQL의 전용 앱 계정과 Redis 인증을 설정하고 상태 확인이 성공할 때까지 기다린다. 로컬 비밀값은 `.local-state/single/.env`에 처음 한 번 자동 생성하며 권한은 0600이다. 이 파일과 빌드 결과는 Git에 포함하지 않는다. Compose에는 Docker 연결에 필요한 환경만 전달하므로 셸에 남아 있는 운영 비밀값이 로컬 값을 덮어쓰지 않는다.

- 게이트웨이: `http://127.0.0.1:18080`
- OpenAPI: `http://127.0.0.1:18080/v3/api-docs`
- 앱 readiness: `http://127.0.0.1:18081/actuator/health/readiness`
- MySQL·Redis: Compose 내부 연결만 허용한다. 호스트에 DB 포트를 게시하지 않는다.

`down`은 컨테이너와 네트워크만 종료하고 MySQL 볼륨을 유지한다. 환경 파일은 Gradle의 `clean` 대상인 `build/` 밖에 둔다. 환경 파일을 지우면 기존 볼륨의 비밀번호와 불일치할 수 있으므로 볼륨을 유지하는 동안 환경 파일도 유지한다. Redis는 현행과 같은 비영속 방식으로 재시작 후 세션이 사라진다.

## 검증 범위

`verify-single-local.sh`는 실제 응답과 컨테이너 설정을 확인한다.

- 앱 readiness·Nginx health·OpenAPI 응답.
- 게이트웨이의 Actuator 차단, Prometheus endpoint 미노출, 미인증 사용자 조회 차단.
- 앱 DB 계정의 SQL 조회, Redis 인증 접속, DB·Redis 호스트 포트 비공개.
- Docker `json-file` 로그 회전과 컨테이너 메모리 한도.

`verify-single-functional.py`는 이 로컬 프로젝트에만 테스트 사용자를 넣는다. 운영 서버를 대상으로 사용하지 않는다.

- 테스트 사용자와 JSON Web Token(JWT)으로 사용자 조회·여행 방 생성·활성화·참여·조회.
- Redis의 테스트 세션에서 실제 HTTP 갱신과 Secure·HttpOnly·SameSite=Lax 쿠키 확인.
- 네 개씩 소량 동시 조회를 총 40회 실행한다. 성능 부하 시험을 대체하지 않는다.
- 전체 재시작과 앱 컨테이너 재생성 후 MySQL 여행 방 유지.
- Redis 재시작 후 이전 refresh token 거부와 로그아웃 처리.
- 컨테이너 메모리 한도·Linux 자원 그룹(cgroup) 피크·메모리 부족(OOM) 종료와 예상하지 않은 재시작 여부.

결과는 `build/single-local-functional.json`에 남는다. 기능 검증마다 임의 식별자의 테스트 사용자 두 명과 여행 방 한 개가 로컬 DB에 추가된다. 기존 데이터 삭제나 인증 우회용 서버 API를 추가하지 않는다. 실제 OAuth 로그인 자체는 검증하지 않는다.

## 자원 한도

다음 값은 로컬에서 검증한 후보이며 운영 부하 적합성은 아직 미확정이다.

| 서비스 | 컨테이너 한도 | 주요 설정 |
|---|---:|---|
| 앱 | 768MiB | Java heap 384MiB, metaspace 192MiB, code cache·direct memory 각 64MiB |
| MySQL | 512MiB | InnoDB buffer pool 128MiB, Performance Schema 비활성, max_connections=200 유지 |
| Redis | 96MiB | maxmemory 64MiB, noeviction, 인증 사용, 현행 비영속 방식 유지 |
| Nginx | 32MiB | 기존 요청 제한·Actuator 차단 snippet 재사용 |
| 합계 | 1408MiB | OS·Docker·운영 TLS 작업의 메모리는 별도 |

각 서비스는 `json-file` 로그를 파일당 10MB·최대 세 개로 제한한다. Redis 저장 한도에 도달하면 임의로 세션을 퇴출하지 않고 쓰기 오류가 발생할 수 있으므로 실제 운영 부하 검증이 필요하다. 컨테이너 OOM 시에는 서비스를 재시작할 수 있으며 로그와 상태를 확인해야 한다.

## 검증 결과

2026-11-23 로컬 실행 결과다. 전체 호스트는 Docker Desktop ARM64·메모리 약 8GB이며, 컨테이너별로 위 한도를 적용했다.

| 항목 | 결과 |
|---|---|
| `./gradlew test bootJar --no-daemon` | 테스트 543개, 실패·오류·스킵 0개 |
| 단일 Compose 기동·기본 검증 | 4개 서비스 healthy, 포트 격리·로그 회전 확인 |
| 실제 HTTP 기능·갱신·재시작·앱 재생성 | 통과 |
| 앱 기동·기능 호출·재기동 검증의 최대 cgroup 피크 | 약 428MiB / 한도 768MiB |
| MySQL 초기화부터 기능 호출까지 cgroup 피크 | 약 280MiB / 한도 512MiB |
| Redis·Nginx 같은 구간의 개별 피크 | 약 24MiB·20MiB |
| OOM 종료·예상하지 않은 재시작 | 관찰한 구간에서 없음 |

피크에는 cgroup이 집계한 메모리가 포함된다. 서비스별 피크의 단순 합은 동시 최대 사용량이 아니며 `docker stats`의 현재 working set과도 다르다. 실제 Lightsail 2GB 호스트의 OS·CPU·배포 중 메모리 여유, 정기 배치와 장시간 트래픽은 검증하지 않았다. 이 결과만으로 2GB 운영 적합성을 확정하지 않는다.

## 운영 확인

AWS에서의 메모리·디스크, 재배포, 실계정 외부 연동·DNS·HTTPS는 [운영 검증](../README.md#운영-검증과-비용-확인)을 따른다. 로컬 검증 프로젝트는 운영 프로젝트와 다른 이름과 볼륨을 쓴다.
