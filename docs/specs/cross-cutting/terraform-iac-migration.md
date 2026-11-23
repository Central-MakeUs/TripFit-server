# 신규 Lightsail Terraform 구축

> 상태: Approved · 2026-11-23 · #125
> MVP: 출시 이후 · priority: could (이슈 분류 유지)
> 관련 BR 및 API 변경: 해당 없음

## 목표

승인된 [서버 이전 #140](https://github.com/Central-MakeUs/TripFit-server/issues/140)에 필요한 단일 서버를 새 계정에서 재현한다. 2026-08-27의 기존 EC2 가져오기 계획은 Git 및 이슈 이력에 남긴다.

## 변경 범위

- ADDED: 서울 Linux 2GB 공인 IPv4 번들, 고정 IPv4 연결, 관리자 IPv4 한 개에 제한된 SSH, 80·443 방화벽, 공개키, Docker 설치.
- MODIFIED: 계정별 새 state를 사용한다. S3 버저닝·암호화·공개 차단·네이티브 잠금을 유지한다. 서버와 버킷의 생명주기는 분리한다.
- REMOVED: 이전 계정의 EC2·보안 그룹·Elastic IP·Route 53 가져오기 및 재시작.

## 요구사항

### Must Have

- [ ] 계정과 리전, 번들 가격, Free 플랜과 크레딧, API 권한을 확인한다. 승인 후 저장된 계획을 수동으로 적용한다.
- [ ] 계정 불일치와 전체 공개 SSH를 차단한다. S3 state와 잠금은 새 계정으로 제한한다. state의 암호화 사본을 계정 밖에 보관한다.
- [ ] `terraform fmt -check`, 백엔드 없이 `validate`, 계정 보호 및 포트 검증을 통과한다. 적용 후 `plan`에 변경이 없어야 한다.
- [ ] 주소 출력과 재구축 절차를 #140에서 사용한다. 앱 비밀, DB 내용, DNS, GitHub Secrets는 Terraform 관리 대상에서 제외한다.

### Nice to Have

- 현재 계정의 인증이나 DNS 없이 로컬 provider 모의 검증으로 설정 변경을 확인한다.

## API 및 데이터 모델

앱 API, 인증 계약, DB 스키마와 제품 비즈니스 규칙의 변경은 없다. Terraform 입력은 계정 ID, 환경, 가용 영역, 관리자 IPv4와 SSH 공개키다. 출력은 고정 IPv4와 SSH 사용자다. state와 잠금은 계정별 S3 버킷에 저장한다.

## 검증 시나리오

- 정상: 빈 계정의 승인된 plan은 서버·키·방화벽·고정 IP와 state 버킷을 생성한다.
- 실패: 계정이 다르면 provider와 백엔드에서 차단하고, SSH에 전체 IPv4를 지정하면 입력 검증이 실패한다.
- 통합: 적용 후 서버 초기화와 SSH를 확인한다. 같은 입력으로 다시 실행한 plan은 리소스 변경이 없어야 한다.

## 범위 밖

이전 계정 가져오기, DNS 변경, 앱 배포, GitHub Secrets 및 MySQL 데이터 관리는 #125의 Terraform 적용에서 제외한다.

## 완료 기준

구문·포트·계정 방어 검증을 마치고 생성 전 plan을 제시한다. 실제 적용 후 초기화 완료, 고정 IP와 방화벽, 변경 없는 재계획을 확인한다. 앱 통합 검증은 #140에서 수행한다.

## 비용과 적용 전 실측

추가 비용은 서버 기본료와 별도로 S3 저장·요청, 전송량, 세금 및 DNS 비용을 확인한다. 유료 플랜 전환이나 서울 번들 제공 여부 불일치 시 운영 적용을 보류한다. 실제 계정 ID·관리자 IPv4·공개키는 운영 입력이다.

근거: [AWS Lightsail 번들](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-bundles.html), [AWS Free 플랜](https://aws.amazon.com/free/compute/lightsail-vs-ec2/), [S3 백엔드](https://developer.hashicorp.com/terraform/language/backend/s3), [Lightsail 공개키 가져오기](https://docs.aws.amazon.com/lightsail/2016-11-28/api-reference/API_ImportKeyPair.html).

## 로컬 검증 결과

Terraform 1.15.9 및 AWS provider 6.73.0에서 fmt와 양쪽 루트의 validate를 통과했다. 모의 테스트에서 2GB·IPv4·웹/관리 포트와 Lightsail의 RSA 공개키 조건을 검증했다. 로컬 STS가 다른 계정 ID를 반환했을 때 양쪽 provider가 실제 plan을 중단했다. 운영 계정에서의 백엔드 접근 및 적용 후 무변경 plan은 남아 있다.

## 변경 이력

- 2026-11-23: 이전 EC2 가져오기 초안을 신규 Lightsail 생성으로 개정했다.
