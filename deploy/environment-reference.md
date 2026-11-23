# 단일 서버 배포 환경 변수

이 문서는 #140 운영 Compose가 읽는 구성과 배포 자격 증명의 위치를 설명한다.

| 항목 | 위치와 값 |
|---|---|
| 프론트 | Vercel의 `tripfit.online`과 `NEXT_PUBLIC_API_BASE_URL=https://api.tripfit.online` |
| 앱 호스트 | Terraform의 Lightsail 고정 IPv4. 관리자 PC가 수동 배포할 때 지정 |
| MySQL | 내부 서비스명 `mysql`. `MYSQL_ROOT_PASSWORD`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, 선택 `MYSQL_DATABASE` |
| Redis | 내부 서비스명 `redis`. `REDIS_PASSWORD` |
| 토큰 | `JWT_SECRET` 필수. 현재 계약의 `JWT_ACCESS_EXPIRATION`·`JWT_REFRESH_EXPIRATION_DAYS` 값 유지 |
| 쿠키 | 운영 오버레이에서 `COOKIE_DOMAIN=tripfit.online`, `COOKIE_SECURE=true` |
| 외부 연동 | 기존 Google, Google Calendar, Apple, Kakao, Firebase, 공휴일 및 토큰 암호화 설정 중 사용하는 키 |
| 이미지 | GitHub Actions가 커밋 SHA 태그를 발행하고 배포 스크립트가 실제 digest를 고정 |

서버의 비밀 파일은 `~/tripfit/shared/.env` 한 곳에 둔다. [환경 변수 예시](single/.env.example)와 [공통 Compose](single/docker-compose.yml)가 전체 키의 기준이다. 예시의 로컬 이미지 주소·포트는 운영에서 배포 스크립트와 운영 Compose가 결정한다. 기존 GitHub Secret에서 값을 다시 내려받을 수 없다면 관리자 비밀 저장소나 공급자 콘솔에서 복구한다. 새 자격 증명은 외부 연동이 필요한 값만 교체한다.

DB 루트·앱 자격 증명을 바꿀 때는 초기 환경 파일 수정만으로 기존 볼륨의 계정 비밀번호가 변경되지 않는다는 점에 유의한다. 개인키·토큰·비밀번호는 Git이나 Terraform에 넣지 않는다.
