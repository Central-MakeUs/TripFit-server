# TripFit 단일 서버 배포

이 절차는 #140에서 복구하는 Lightsail 환경의 기준이다. [Terraform 작업 #125](https://github.com/Central-MakeUs/TripFit-server/issues/125)가 생성한 고정 IPv4를 사용한다. 과거 5대 구성은 [배포 이력](ec2-split-deployment.md)에 있다.

## 필요한 값

- 관리자 PC에서 지정된 SSH 키로 `ubuntu@<고정 IPv4>`에 접속한다. AWS 콘솔 등 신뢰할 경로에서 호스트 키를 확인해 known_hosts에 등록한다. `cloud-init status --wait`와 `docker compose version`을 확인한다. 운영 Compose는 Compose 2.24.4 이상에서 쓸 수 있다.
- 서버의 `~/tripfit/shared/.env`를 `umask 077` 상태에서 작성하고 권한을 0600으로 확인한다. [환경 변수 안내](environment-reference.md)를 기준으로 새 DB·Redis 비밀과 유효한 외부 키를 설정한다. 개인키와 환경 파일을 Git에 포함하지 않는다.
- 외부에 공개하지 않는 GHCR 이미지라면 서버에서 읽기 권한 전용 토큰으로 `docker login ghcr.io`를 실행한다. 토큰은 `--password-stdin`으로 입력하고 터미널 기록이나 파일에 남기지 않는다.

## 앱 배포하기

GitHub Actions는 테스트와 API 계약 검사 작업을 모두 통과한 `main` 커밋의 Linux x86_64 이미지를 SHA 태그로 발행한다. 발행 성공을 확인한 후 관리자 PC의 이 저장소에서 실행한다.

```bash
./scripts/deploy-single.sh <고정 IPv4> <40자리 커밋 SHA>
```

해당 커밋의 Compose·Nginx 설정을 전송하고 서버에서 이미지 digest를 고정한다. DB·Redis·앱·Nginx의 기동 및 readiness가 성공하면 `~/tripfit/current`를 갱신한다. 실패하면 직전 정상 릴리스로 돌아간다. 첫 배포에 실패하면 앱과 Nginx를 중단하고 DB 볼륨을 남긴다. 재배포는 동일한 Compose 프로젝트를 쓰므로 MySQL의 이름 있는 볼륨이 유지된다. 이미지를 당겨오는 동안 실패하면 실행 중인 배포는 손대지 않는다.

정상 버전과 이전 버전의 로컬 이미지·설정을 보관한다. 긴급하게 이전 버전을 적용할 때는 그 SHA로 같은 명령을 실행한다. 정리 전에는 현재와 직전 정상 릴리스에 필요한 이미지와 디렉터리를 식별한다. 컨테이너와 함께 DB 볼륨을 삭제하는 명령은 쓰지 않는다.

## DNS와 인증서 연결하기

1. 고정 IP의 HTTP `/health`와 서버 내부 앱의 `/actuator/health/readiness`를 확인한다. 운영 HTTP는 ACME와 상태 확인을 제공하고 나머지는 HTTPS로 보낸다.
2. 기존 DNS 관리 권한을 확인하고 `api.tripfit.online`의 A 레코드를 새 IP로 설정한다. 기존 네임서버가 응답하지 않으면 새 DNS에 Vercel과 검증용 TXT 등 필요한 레코드를 먼저 옮긴다. 레코드를 확인한 뒤 등록기관에서 NS를 전환한다. 기존 AAAA 또는 DNSSEC DS가 남아 새 경로와 충돌하지 않는지도 확인한다.
3. 공개 DNS에서 API 주소가 새 IP로 해석되면 서버에서 아래 명령으로 인증서를 발급한다. 인증서 갱신 검증까지 성공하면 크론을 등록한다.

```bash
~/tripfit/current/scripts/single-release.sh tls <관리 이메일>
~/tripfit/current/scripts/single-release.sh renew-check
crontab -e
```

크론에는 `23 3,15 * * * "$HOME/tripfit/current/scripts/single-release.sh" renew`를 넣는다. 갱신이 성공하면 Nginx가 인증서를 다시 읽는다. DNS 전환 전 레코드와 변경 시간을 남긴다. 문제가 생기면 원인을 DNS·TLS·앱으로 나눠 확인하고, DNS를 복원할 때는 이전 대상이 정상 응답하는지도 확인한다.

## 운영 검증과 비용 확인

- 외부에서 HTTPS `/health`, `/v3/api-docs`, 프론트 로그인·갱신·여행 방을 확인한다. Actuator, MySQL, Redis는 외부에서 접근할 수 없어야 한다. 준비 상태는 컨테이너 내부에서 확인한다.
- 실제 2GB 서버에서 재배포·재부팅·배치·동시 요청 후 DB 유지, 메모리 부족 종료, 재시작 횟수와 디스크 여유를 확인한다. 기존 액세스와 DB 데이터 기록을 모두 삭제하는 기동 명령은 사용하지 않는다.
- 비용 및 사용량에서 크레딧·실사용액을 확인한다. 월 $12는 기본료다. 서버를 중지해도 청구될 수 있다. 연결하지 않은 고정 IP, 스냅샷, S3 및 만료일도 확인한다. 운영 지속 여부는 2027년 4월 23일까지 검토하고 무료 플랜 만료일인 5월 23일 전에 백업과 이동 여부를 결정한다.

## MySQL 보존이 필요해지면

관리자 PC에서 SSH로 덤프를 스트리밍하고 PC에서 바로 암호화한다. 개인키가 있는 PC에서 복호화한 SQL을 별도의 MySQL 8.0 검증 DB에 입력하고 주요 행의 복원을 확인한다. 데이터 보존이 확인될 때까지 운영 DB에 복원 실험을 하지 않는다.

```bash
set -o pipefail
umask 077
ssh ubuntu@<고정 IPv4> '~/tripfit/current/scripts/single-release.sh dump' |
  gpg --encrypt --recipient <백업 수신자 키> --output tripfit-mysql.sql.gpg
```

자동 백업 저장소는 이번 전환 범위에서 제외한다. USB나 다른 접근 가능한 위치에 암호화 사본을 보관하고 복호화 키와 분리한다.
