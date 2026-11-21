# Lightsail 인프라 구성 결과

11월 22일 커밋으로 추가한 Terraform 구성과 11월 23일 운영 검증 결과를 11월 24일에 정리했다. 앱 배포 담당자는 관리자 PC의 `infra/terraform/`에서 `terraform output -raw public_ipv4`로 배포할 주소를 확인할 수 있다. [이슈 #125](https://github.com/Central-MakeUs/TripFit-server/issues/125) · [PR #142](https://github.com/Central-MakeUs/TripFit-server/pull/142)

## 결정

새 계정의 서울 리전에 Linux 2GB Lightsail 한 대와 고정 IPv4를 생성했다. 공개 웹 포트는 80·443이고 SSH는 관리자 IPv4 하나에 허용한다. 서버와 상태 저장용 S3 버킷은 별도 Terraform 루트로 관리한다. [재구축 절차](../../infra/terraform/README.md)

## 당시 검증 결과

| 확인한 항목 | 결과 |
|---|---|
| 코드와 계정 제한 | 서식 검사와 두 루트의 `validate`, 모의 계획 3건을 통과했다. 대상과 다른 AWS 계정에서는 두 루트 모두 계획을 중단했다. |
| 운영 구성 | 저장한 계획을 적용하고 다시 계획했을 때 두 루트 모두 변경이 0건이었다. 재부팅 후 관리자 SSH와 Docker·Compose·cron 기동을 확인했다. 외부에서 DB·Redis 포트에 연결을 시도하면 시간 초과가 발생했다. |
| 상태 복구 | S3의 암호화, 버전 관리, 루트별 잠금 파일을 확인했다. 관리자 PC에서 두 상태 파일의 암호화 사본을 복호화해 원본과 대조했다. |

## 검증하지 못한 것과 남은 작업

2GB 번들의 월 기본료는 당시 확인한 12달러다. 이 기록에는 한 달치 실제 청구액이 없다. 요금과 크레딧은 결제 내역에서 확인한다. 운영 앱과 사용자 기능 검증은 [단일 서버 배포 결과](2026-11-24-single-server-rebuild.md)에 있다.
