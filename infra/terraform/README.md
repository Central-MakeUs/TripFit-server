# TripFit Lightsail 인프라

#125는 새 계정에 서버를 만들고, #140은 출력된 IPv4에 앱을 배포한다. 아래에서는 서울 리전, Linux 2GB·공인 IPv4 번들(`small_3_0`), Git 저장소 밖에 보관하는 EC2/Lightsail 관리자 RSA SSH 키를 사용한다. Lightsail의 공개키 가져오기는 [RSA 형식](https://docs.aws.amazon.com/lightsail/2016-11-28/api-reference/API_ImportKeyPair.html)을 사용한다. 번들 기본료 목표는 월 $12다.

## 읽기 전용 확인

새 계정 자격 증명으로 AWS CLI 프로필을 준비한다. 기존 계정의 기본 프로필과 분리한다. 아래 ID는 가입 사용자 이름이 아닌 **12자리 AWS 계정 ID**다.

```bash
export AWS_PROFILE=tripfit-new
aws sts get-caller-identity
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws freetier get-account-plan-state --region us-east-1
aws lightsail get-regions --include-availability-zones --region ap-northeast-2
aws lightsail get-bundles --region ap-northeast-2 \
  --query 'bundles[?bundleId==`small_3_0`].[bundleId,price,ramSizeInGb,isActive]'
aws lightsail get-blueprints --region ap-northeast-2 \
  --query 'blueprints[?blueprintId==`ubuntu_24_04`].[blueprintId,isActive]'
```

결제 콘솔에서도 Free 플랜과 크레딧을 확인한다. 콘솔과 CLI의 계정 ID가 같아야 한다. 서울에서 번들·이미지가 제공되지 않거나 유료 플랜 전환이 요구되면 중단한다. 비용표에는 번들 기본료, S3 객체 저장·요청, 필요시 DNS·전송량·세금을 따로 적는다. 새 리소스 생성 전 실제 plan에 교체·삭제가 없는지 확인하고 사용자의 승인을 받는다.

## 처음 생성하기

명령은 모두 이 폴더(`infra/terraform/`)에서 실행한다. Terraform 1.10 이상을 사용한다. 각 계정·환경의 입력과 아티팩트는 별도 안전한 폴더에 둔다. 예를 들어 `production.tfvars`에 `account_id`, `environment = "prod"`, 확인한 `availability_zone`, `admin_ipv4_cidr`(관리자 공인 IPv4 + `/32`), `admin_public_key`를 지정한다. 새 키가 필요하면 관리자 PC에서 `ssh-keygen -t rsa -b 3072 -f ~/.ssh/tripfit-lightsail`로 생성하고 `.pub` 파일만 사용한다. 배포 전에 개인키를 `ssh-add ~/.ssh/tripfit-lightsail`로 관리자 PC의 SSH 에이전트에 등록한다. AWS 자격 증명과 SSH 개인키를 변수 파일로 전달하지 않는다.

1. 최초 검토용 계획은 백엔드 설정을 복사하기 전 두 루트에서 각각 생성한다. `terraform -chdir=bootstrap init` 후 `terraform -chdir=bootstrap plan -var="account_id=$ACCOUNT_ID" -out=bootstrap.tfplan`을 실행한다. 서버 루트에서도 `terraform init` 후 `terraform plan -var-file=production.tfvars -out=preview.tfplan`을 실행한다. 빈 로컬 state를 쓰므로 두 계획 모두 AWS 리소스를 변경하지 않는다.
2. 승인 후 `terraform -chdir=bootstrap apply bootstrap.tfplan`으로 S3 버킷을 만든다. `backend.tf.example`을 `bootstrap/backend.local.tf`와 서버 루트 `backend.local.tf`에 복사한다.
3. 현재 폴더의 `bootstrap.s3.hcl`와 `production.s3.hcl`에 `bucket = "tripfit-tfstate-<계정 ID>"`, `region = "ap-northeast-2"`, `allowed_account_ids = ["<계정 ID>"]`를 적는다. `key`는 버킷 루트에서 `bootstrap/terraform.tfstate`, 서버 루트에서 `prod/terraform.tfstate`처럼 서로 다르게 둔다. 입력의 환경 이름을 바꾸면 서버 state 경로도 바꾼다.
4. 현재 폴더에서 `terraform -chdir=bootstrap init -migrate-state -backend-config="$PWD/bootstrap.s3.hcl"`로 state를 옮긴다. 서버 루트는 `terraform init -reconfigure -backend-config=production.s3.hcl`로 연결한다. `terraform plan -var-file=production.tfvars -out=production.tfplan`을 실행한다. 최초 검토한 서버 계획과 비교해 변경이 일치하면 `terraform apply production.tfplan`을 실행한다.
5. 두 루트에서 다시 `plan`을 실행해 변경이 없는지 확인한다. `terraform output -raw public_ipv4`를 #140에 넘긴다. SSH로 접속한 뒤 `cloud-init status --wait`와 `docker compose version`을 확인한다. Docker 그룹 가입은 새 SSH 연결부터 적용된다.

Lightsail은 자체 `/bin/sh` 스크립트 뒤에 사용자 초기화 코드를 붙인다. `docker-init.sh`는 POSIX 셸 문법으로 작성한다. 새 서버는 생성 시점의 스크립트를 사용한다. 기존 서버에서 초기화 스크립트를 수정하거나 실패를 복구할 때는 관리자 키로 호스트를 확인한 후 `ssh -o StrictHostKeyChecking=yes ubuntu@"$SERVER_IP" 'sudo /bin/sh -s' < docker-init.sh`를 실행한다. 새 SSH 연결에서 `docker info`, `docker compose version`, `systemctl is-active docker cron`을 확인한다. AWS가 생성 후 user data 변경을 지원하지 않아 Terraform은 기존 서버의 해당 속성 변경을 무시한다.

서버와 버킷에 삭제 방지가 걸려 있다. 계획에 교체가 나타나면 데이터·인증서·고정 IP에 미치는 영향을 검토한 뒤 별도로 결정한다. 주소를 바꾸거나 서버를 정리할 때는 연결이 끊긴 고정 IP와 별도 비용 자원이 남았는지 확인한다.

## state 사본과 재구축

적용 직후 각각의 루트에서 `terraform state pull`을 실행하고, 결과를 파이프로 GPG 등으로 암호화해 관리자 PC에 저장한다. state 백업은 기존 AWS 리소스와 코드의 연결을 복구할 때 사용한다. **다른 계정에 새로 만들 때는 새 입력과 빈 state를 사용한다.** 앱 비밀과 MySQL 백업은 [앱 배포 절차](https://github.com/Central-MakeUs/TripFit-server/blob/main/deploy/README.md)에서 관리한다.

`terraform fmt -check -recursive`, 각 루트의 `init -backend=false`·`validate`, 서버 루트의 `terraform test`는 로컬에서 실행할 수 있다. 서버 테스트는 모의 AWS provider를 사용한다.
