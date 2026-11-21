# 저비용 단일 서버 재구축

> 상태: Approved (2026-11-23, 로컬 검증 및 운영 전환)
>
> MVP: In scope (사용자 지정: MVP 출시 · priority: must, 제품 API 범위 변경 없음)
>
> 관련 BR: 해당 없음

## 목표

기존 데이터 복원 없이 앱·MySQL·Redis를 서버 한 대에 모아 월 운영비와 재구축 작업을 줄인다. 이 문서는 재구축 계획이며 현재 운영 상태를 설명하지 않는다. 로컬 구현은 #140에서 진행한다.

## 배경

- 2026-11-23 사용자 확정: 기존 데이터 복원 불필요. Prometheus·Grafana·Loki·AI 장애 리포터를 이번 재구축에서 제외한다.
- 이전 배포는 역할별 EC2 5대로 구성했다. [기존 배포 이력](../../../deploy/ec2-split-deployment.md)에 구조를 남겼다.
- 새 계정 Free 플랜이 준비됐다. 서버 생성 및 공개 전환은 적용 단계에서 진행한다.
- 기준 문서: [배포 환경](../../../deploy/environment-reference.md), [배포 절차](../../../deploy/README.md), [아키텍처](../../architecture.md), [클라이언트 플랫폼](../../product/platform.md), [MVP](../../product/mvp.md).
- 신규 서버 Terraform 구성은 #125에 맡긴다.

## 작업 순서

[#125](https://github.com/Central-MakeUs/TripFit-server/issues/125)에서 신규 Lightsail·고정 IP·방화벽·S3 state를 준비한다. [#140](https://github.com/Central-MakeUs/TripFit-server/issues/140)에서 로컬 후보를 운영용 Compose로 확장하고, 서버 생성과 비용 승인 이후 배포·DNS·HTTPS를 확인한다.

## 변경 범위

기존 [로깅 결정](../../decisions/009-observability-logging.md), [Redis 인프라 결정](../../decisions/010-redis-infra.md), [Prometheus 스펙](add-prometheus.md), [AI 리포터 스펙](incident-ai-report.md) 대비 제안이다. 모니터링 제외는 사용자 확정이며 Lightsail 2GB 후보와 월 기본료 $12 예산은 사용자 승인되었다. 자원 한도는 로컬 실측 후 판단한다. 실제 운영 전환 전에 해당 문서를 이번 방향으로 amend한다.

### ADDED

- Lightsail Linux/Unix 공인 IPv4 포함 2GB 서버 한 대를 우선 검증 후보로 둔다. 서울 리전의 실제 선택 가능 여부와 생성 화면 가격을 생성 전에 확인한다.
- 단일 Docker Compose에서 Nginx·Certbot·앱·MySQL 8.0·Redis를 실행한다. 프론트는 기존 Vercel에 유지한다. 공개 API 주소는 api.tripfit.online이다.
- 로그 파일 크기·개수 제한, 기동 상태 확인, 비용 알림과 재구축 절차를 남긴다.

### MODIFIED

- 서버 간 사설 IP 연결 → Compose 내부 서비스 이름으로 연결한다. MySQL·Redis 포트는 호스트에 공개하지 않는다.
- Loki 로그 전송 → Docker 로컬 로그로 전환한다. 로그만을 위한 별도 유료 서버는 만들지 않는다.
- 기존 DB·Redis 내용 복원 → 새 볼륨과 현재 엔티티로 시작한다. 사용자는 다시 가입·로그인한다. SQL 마이그레이션은 작성하지 않는다.
- SSH·배포 대상은 단일 서버로 변경한다. 이미지는 기존 GHCR에서 빌드·전달하며 서버에서 Gradle 빌드를 하지 않는다.

### REMOVED

- 새 환경의 Prometheus·Grafana·Loki·AI 장애 리포터·node_exporter 실행과 배포 작업.
- 관련 Loki logging driver 설정, 옛 사설 IP 기본값·접근 허용 규칙, 활성 배포에서 쓰지 않는 모니터링 Secrets 참조.
- 대체된 분리 배포 경로·스크립트·워크플로와 현행 안내. 과거 결정의 이력은 폐기 시점을 표시해 보존한다. k6 API 부하 시나리오는 유지한다.
- 앱의 Prometheus 전용 의존성·노출 설정·히스토그램 설정. readiness 확인을 위한 Actuator health는 유지한다.

## 요구사항

### Must Have

이 체크리스트는 계획의 수용 조건이며 GitHub `priority:` 판정을 대신하지 않는다.

- [ ] 단일 서버에서 현재 API·인증·쿠키·외부 서비스 연동 계약을 유지한다. Redis는 리프레시 토큰과 공휴일 캐시에 필요하므로 유지한다.
- [ ] Java 전체 프로세스 메모리와 MySQL·Redis·OS 여유를 포함해 2GB 후보의 실행 가능성을 확인한다. 현재 Dockerfile에는 JVM 메모리 상한이 없다.
- [ ] 컨테이너 한도·JVM heap·DB 버퍼 크기는 로컬 재현으로 정하고 근거를 기록한다. 임의로 TTL·동시성 계약·API 계약을 변경하지 않는다.
- [x] 로컬에서 MySQL을 먼저 초기화하고 전용 앱 계정을 설정한 뒤 앱을 기동한다. 기존 GitHub DB 비밀번호를 화면·로그에 출력하지 않는다.
- [x] 로컬에서 MySQL named volume을 사용하고 재배포에서 볼륨을 삭제하지 않는다. 초기화 허용은 기존 데이터에 관한 결정이다.
- [x] Redis 세션 소실 시 로그인 갱신 실패와 재로그인 필요를 로컬 HTTP로 검증했다. 장애 전반의 검증은 운영 단계에 남긴다.
- [ ] 외부 공개는 HTTPS 서비스와 필요한 HTTP 인증서 발급 경로로 제한한다. SSH는 관리자 출발지로 제한하고 관리자가 확인한 커밋을 자신의 IP에서 SSH로 배포한다.
- [ ] 기존 도메인의 등록기관·NS 변경 권한을 확인하고 DNS·HTTPS를 연결한다. 권한이 없으면 도메인 연결을 완료했다고 보고하지 않는다.
- [ ] 새 계정 Free 플랜과 크레딧 적용 여부는 생성 전에 확인한다. 서버 생성 직전 월 기본료·추가 과금 조건을 제시하고 사용자 승인을 받는다.
- [ ] 예산 알림은 지출 차단 장치가 아님을 안내한다. 중지와 삭제의 요금 차이, 디스크·IP·스냅샷 등 추가 과금 항목을 문서화한다.

### Nice to Have

- [ ] 향후 보존 데이터가 생기면 관리자 PC에 암호화한 MySQL 덤프를 보관하고 별도 DB에서 복원 검증한다.

### Out of Scope

- 기존 데이터·로그 복원, 기존 계정 폐쇄·리소스 삭제·미납 처리.
- RDS·ALB·NAT Gateway·별도 Redis/모니터링 서버·유료 지원 플랜.
- 기존 5대 인프라의 Terraform import 및 자동 DB 백업 저장소. 새 인프라와 state 백엔드는 #125에서 관리한다.

## API / 인터페이스

신규 API 없음. 기존 요청·응답·ErrorCode·OAuth 연동·JWT 수명·쿠키 계약은 유지한다. `dev` 프로필을 사용한다. `/actuator/health/readiness`는 배포 확인에 유지한다.

## 데이터 모델

엔티티·ERD 변경 없음. MySQL 8.0과 Hibernate `ddl-auto: update`로 빈 DB를 만든다. Redis 키·TTL·토큰 정책은 현행 [인증 스펙](../auth/auth-refresh-redis-cookie.md)을 따른다.

## 비즈니스 규칙

BR 변경 없음. 배포 축소로 제품 기능을 제거하지 않는다.

## 비용과 계정 준비

- 기준일: 2026-11-23. [AWS 공식 번들 표](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-bundles.html)의 Linux 공인 IPv4 포함 2GB는 월 최대 기본료 $12, 4GB는 $24다. 2GB 번들은 60GB 저장 공간을 포함한다.
- 세금·환율·전송량 초과·추가 스토리지/스냅샷·DNS/도메인 등은 별도다. 번들 기본료는 전체 청구액 상한이 아니다.
- 2GB는 검증 후보이며 충분하다고 측정한 상태가 아니다. 1GB 이하는 Java·MySQL 동시 실행의 여유가 확인되지 않아 우선안으로 삼지 않는다. 2GB 검증 실패 시 4GB로 자동 증설하지 않고 예산을 다시 확인한다.
- [Lightsail 결제 FAQ](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html)에 따르면 인스턴스를 중지해도 삭제 전까지 번들 요금이 발생한다. 삭제 뒤에도 별도 디스크·스냅샷 등은 따로 정리해야 한다.
- [Lightsail FAQ](https://aws.amazon.com/lightsail/faq/)에 따르면 인스턴스에 연결된 고정 IPv4는 추가 요금이 없고, 미연결 상태가 1시간을 넘으면 시간당 $0.005가 부과된다. [리전 안내](https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-regions-and-availability-zones-in-amazon-lightsail.html)에서 서울 지원을 확인했다.
- 계정은 계획 검토와 로컬 구성 작성 전에 필요하지 않다. 실제 서버 생성 단계에는 사용 가능한 AWS 계정이 필요하다. 새 계정 codus5068의 Free 플랜을 사용한다.
- [Free Tier FAQ](https://aws.amazon.com/free/free-tier-faqs/)는 기존 계정 보유 이력이 있는 고객의 신규 무료 플랜·크레딧 자격을 제한한다. 새 계정의 Free 플랜은 사용자 확인 상태이며, 실제 사용 가능 리전과 번들은 적용 전 확인한다. 기존 미납 문의는 별도로 계속한다.
- 계정 준비 시 사용자가 직접 가입·기존 비밀번호 입력·MFA 설정을 한다. 비밀번호·MFA 코드·카드 정보를 채팅에 적지 않는다. 배포용 접근은 필요한 권한만 사용하며 별도 승인 없이 영구 관리자 키를 만들지 않는다.

## 구현 순서와 검증

1. 계획·월 예산·단일 서버 장애 위험·기존 문서 amend 범위를 승인받는다. 이 단계는 리소스 생성 승인이 아니다.
2. 단일 Compose·초기 설정·검증 스크립트를 작성한다. 로컬에서 2GB 조건의 기동·메모리·재기동·인증 연동을 검증한다.
3. 계정 접근과 도메인 제어 권한을 확인한다. 결제 정보가 유효하고 대상 계정을 사용할 수 있는지 확인한다.
4. 생성 화면의 번들 가격을 제시하고 승인 후 서버 한 대만 만든다. 고정 IP 연결·방화벽·Docker·로그 제한을 설정한다.
5. 기존 저장소 Secrets를 유지하며 배포 주소·키·변경된 DB 설정만 갱신한다. GHCR·외부 OAuth·Firebase 키는 유효성을 확인해 재사용한다. GitHub Actions는 main에서 테스트와 API 계약 검사 작업이 모두 성공하면 SHA 이미지를 발행하고 관리자는 선택한 커밋을 수동 배포한다.
6. DNS·HTTPS·실제 클라이언트 로그인을 확인한다. 사용자 확인 후 운영 전환을 보고한다. 원래 AWS 계정의 리소스는 별도 승인 없이 삭제하지 않는다.

## 로컬 자원 검증 후보

자원 한도와 메모리 실측값은 [로컬 검증 문서](../../../deploy/single/README.md)의 자원 한도·검증 결과 표를 기준으로 한다. 후보는 앱·MySQL·Redis·Nginx 전체를 포함하며 실제 2GB 호스트의 OS·Docker 여유는 별도 확인한다.

## 영향 파일

### 이번 로컬 단계

- `deploy/single/`의 Compose·이미지·HTTP 게이트웨이·실행 문서.
- `scripts/single-local.sh`, `scripts/verify-single-local.sh`, `scripts/verify-single-functional.py`, 로컬 상태 경로의 Git·Docker 제외 설정.
- 이 스펙·인덱스와 기존 문서의 로컬 후보 안내 링크.

### 운영 전환 단계

구현 전 아래 목록을 기준으로 실제 변경 파일을 확정한다.

- `deploy/single/` 공통 Compose·운영 오버레이·환경 예시, TLS·수동 배포·검증 스크립트.
- `.github/workflows/ci-cd.yml`, 대체되는 `.github/workflows/deploy-observability.yml`, `build.gradle`, `src/main/resources/application.yml`.
- 대체된 분리 배포 디렉터리, 워크플로와 장애 리포터는 삭제한다. k6 API 부하 시나리오는 유지한다.
- `deploy/README.md`, `deploy/environment-reference.md`, `deploy/ec2-split-deployment.md`, `docs/architecture.md`, `docs/how-it-works.md`, 관련 ADR·스펙·인덱스.

## 검증 시나리오

### 정상

- [ ] 새 DB 초기화, 앱 readiness, HTTPS, Swagger, 로그인·갱신·로그아웃, 여행 방 생성·조회·참여를 확인한다.
- [ ] 기존 외부 API·푸시·캘린더 연동에 필요한 키가 유지되며 초기화 전 사용자 세션에 의존하지 않는다.

### 엣지 · 실패

- [ ] DB·Redis가 외부에서 접근되지 않고, 수집기·Loki 플러그인 없이 전체 컨테이너가 기동한다.
- [ ] 배포·호스트 재시작 후 MySQL 내용이 유지되고 Redis 재시작 후 동작이 계약과 일치한다.

### 수동 / 통합

- [ ] 2GB 조건에서 메모리 부족으로 인한 종료(OOM)와 반복 재시작이 없는지 기동·기능 호출·배치·재배포 중 최대 사용량을 검증하고 실제 서버에서도 재확인한다. 검증 결과 없이 사양을 확정하지 않는다.
- [ ] JVM 외 메모리·OS 여유·디스크·로그 증가를 확인한다. DNS 연결이 막히면 서버 내부 기동 확인과 외부 서비스 복구를 구분한다.

## 완료 기준

- [x] `./gradlew test`, `./gradlew build`와 단일 Compose 로컬 기동 검증 통과.
- [ ] 승인된 서버에서 배포·재배포·기능 확인을 완료하고 현재 구성의 문서·설정·Secrets 참조를 맞춘다.
- [ ] REMOVED 대상의 구 실행 경로를 같은 변경에서 정리하고 과거 결정의 대체 시점을 표시한다.
- [ ] 새 환경의 기본료·추가 과금 항목·알림·정리 절차를 사용자에게 설명한다.

## 리스크·미결정

확정된 범위와 승인·확인이 필요한 항목을 구분한다.

| 항목 | 상태 | 결정 필요자 |
|---|---|---|
| 데이터 복원 생략·모니터링 4종 제외 | 사용자 확정 | 사용자 |
| Lightsail 채택·2GB 월 기본료 $12 허용 | 사용자 승인 | 사용자 |
| 2GB 메모리 적합성·자원 한도 | 기동·재배포·재부팅·동시 조회 확인. 배치 실행 중 최대 사용량은 미측정 | 구현 담당자, 증설 비용은 사용자 |
| 새 계정 Free 플랜·서울 번들 | 사용자가 무료 플랜을 확인했고 서울 2GB 서버에 배포했다 | 구현 담당자 |
| 기존 도메인의 NS·레코드 변경 권한 | Spaceship으로 NS 위임 후 프론트와 API의 공개 HTTPS 응답 확인 | 사용자·도메인 관리자 |
| SSH 배포 | 관리자 IP에서 수동 배포 | 사용자 확정 |
| 단일 호스트 장애 시 앱·DB 동시 중단 | 제안의 위험 | 사용자 |
| 기존 미납액·기존 계정 과금 중단 | 이 계획으로 해결하지 않음 | 사용자·AWS 지원 |

## 변경 이력

| 날짜 | 변경 |
|---|---|
| 2026-11-24 | MODIFIED · 이미지 발행 조건에 API 계약 검사 작업 성공을 포함하도록 승인. |
| 2026-11-23 | 로컬 검증과 신규 Terraform·수동 배포 계획 승인 |

## 로컬 단계 검증 기록

2026-11-23: 테스트 543개 통과, build 통과. 로컬 HTTP·Redis 갱신·MySQL 재시작/재배포 보존과 환경 변수 격리 검증을 완료했다. 수치와 미검증 범위는 [검증 결과](../../../deploy/single/README.md#검증-결과)에 기록한다. 실제 AWS·DNS·HTTPS·외부 공급자 연동 전환은 완료하지 않았다.

2026-11-23 운영 코드 검증: Gradle 전체 테스트 543개와 빌드가 통과했다. 로컬 운영 오버레이에서 테스트 인증서로 HTTPS·ACME 경로, 내부 DB/Redis 포트, MySQL 덤프를 확인했다. 두 실제 OpenAPI 출력의 차이는 없었다. Ubuntu의 모의 Docker에서 실패한 릴리스가 직전 버전으로 복구되는 것을 확인했다. 공개 인증서와 2GB AWS 호스트 검증은 남아 있다.

## 2026-11-23 운영 복구 및 E2E 검증 결과

운영에 배포한 백엔드 커밋은 `cee44d8`이다. 배포와 재부팅, DNS 전환, 인증서 발급·시험 갱신 기록은 [서버 재구축 이슈 #140](https://github.com/Central-MakeUs/TripFit-server/issues/140)에 있다.

| 환경 | 확인한 결과 |
|---|---|
| 서울 Lightsail 2GB | 재배포와 호스트 재부팅 후 MySQL 검증 행이 유지됐다. 재부팅 후 API 명세를 동시에 4건씩 총 40회 조회했다. 앱·MySQL·Redis·Nginx가 모두 정상 상태였고 OOM 종료나 반복 재시작은 없었다. |
| 공개 웹·API | 홈페이지에서 온보딩을 거쳐 Google 로그인 화면까지 진입했다. HTTPS 상태 점검과 OpenAPI 공개 경로 32개, 일반 API의 HTTP→HTTPS 전환, 비로그인 요청 거부, 프론트 출처의 인증 포함 CORS 요청을 확인했다. |
| 로컬 브라우저·API | 테스트 세션으로 모바일 크기의 Chrome에서 여행방 목록·상세 조회, 새로고침 후 쿠키 갱신, 방 생성 완료 화면과 HTTP 201을 두 차례 확인했다. 별도 HTTP 테스트에서 방 활성화, 두 번째 사용자의 초대 참여, 앱 재생성 후 데이터 유지도 확인했다. |
| 백엔드 | 전체 543개 테스트와 빌드·서식 검사를 통과했다. 생성한 OpenAPI가 저장소 명세와 일치했다. 동시성 시나리오 6개를 각각 30회씩 세 차례 더 실행했고 실패는 재현되지 않았다. |
| 캘린더·푸시 | 전체 테스트 중 Google Calendar 33개와 알림 43개를 별도로 다시 실행해 모두 통과했다. Google·Firebase 응답을 모의했고, 실제 MySQL에서 바쁜 시간 저장 및 트랜잭션 커밋·롤백에 따른 알림 이력과 발송 요청을 검증했다. |

운영 계정으로 로그인한 뒤의 토큰 갱신·여행방 참여, 실제 Google 캘린더 일정 반영, 테스트 기기의 푸시 수신은 보류했다. 운영 배치 실행 중 메모리 최대 사용량도 후속 확인 대상으로 남긴다.
