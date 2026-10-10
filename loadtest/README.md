# 외부 API 장애 격리 부하 테스트

외부 API(FCM·카카오·Google 토큰 서버)가 멈추거나 느려질 때, 그 API를 쓰지 않는 요청이 얼마나 영향을 받는지 재는 장치다. 이 문서를 따라 하면 시나리오 3개(S1~S3)를 로컬에서 그대로 재현할 수 있다. 설계와 측정 결과는 [`external-api-bulkhead.md`](../docs/specs/cross-cutting/external-api-bulkhead.md)에 있다.

도구 두 가지를 Docker 이미지로 쓴다. **k6**는 시나리오 스크립트대로 요청을 보내고 응답 시간을 집계하는 부하 테스트 도구이고, **WireMock**은 미리 정한 응답을 정한 시간만큼 늦게 돌려주는 가짜 HTTP 서버다.

## 구성

앱은 운영 코드에 부하 테스트용 설정만 더해 띄운다. 외부 서비스는 아래처럼 바꾼다.

| 외부 서비스 | 부하 테스트에서 | 이유 |
|-------------|-----------------|------|
| 카카오 `user/me` | WireMock (`slow-` 토큰은 4초 지연, `fast-` 토큰은 즉시) | 설정 키 `tripfit.oauth.kakao-user-me-url`로 주소를 바꿀 수 있다 |
| Google 토큰 엔드포인트 | WireMock (4초 지연) | 설정 키 `tripfit.oauth.google-token-url` |
| Google ID 토큰 검증 | 테스트 소스의 대역 (토큰 문자열을 사용자 ID로 사용) | Google 서명 키로만 검증할 수 있다 |
| FCM(Firebase Cloud Messaging) | 테스트 소스의 대역 (`LOADTEST_FCM_DELAY_MS`만큼 늦게 응답) | FCM 주소는 SDK 안에 고정돼 있다 |
| MySQL·Redis | Docker (3307·6380 포트) | 로컬 개발 DB(3306)와 겹치지 않게 |

설정 키는 운영 코드에 있고 기본값은 실제 주소다. 값을 WireMock으로 바꾸는 파일(`src/test/resources/application-loadtest.yml`)과 대역(`src/test/java/.../loadtest/`)은 테스트 소스에만 있어 운영 빌드에 들어가지 않는다.

앱은 18080 포트로 뜬다. 뜰 때마다 DB를 새로 만들고 시드 데이터(여행방 20개, 방마다 멤버 9명과 기기 토큰)를 넣은 뒤 액세스 토큰을 `loadtest/data/seed.json`에 쓴다.

## 시나리오

시나리오마다 외부 장애 하나를 만들고, 그 외부 API를 쓰지 않는 요청의 응답 시간을 잰다.

| 파일 | 만드는 장애 | 동시에 거는 부하 | 영향을 재는 요청 |
|------|-------------|------------------|------------------|
| `k6/s1-fcm-hang.js` | FCM 대역이 120초 동안 응답하지 않음 | 여행 정보 수정 초당 20건(건당 멤버 9명에게 알림) | 여행방 목록 조회 초당 300건 |
| `k6/s2-kakao-slow.js` | 카카오 `user/me` 4초 지연 | 카카오 로그인 VU 300개 | 여행방 목록 조회 초당 300건 |
| `k6/s3-google-exchange-slow.js` | Google 토큰 엔드포인트 4초 지연 | — | Google 로그인 VU 10개의 응답 시간 |

VU는 k6의 가상 사용자로, 응답을 받는 대로 다음 요청을 보낸다. S1·S2는 1초마다 `/actuator/metrics`의 Hikari(DB 커넥션 풀) 지표를 읽어 `db_connections_active`(사용 중 커넥션)·`db_connections_pending`(커넥션을 기다리는 요청)으로 요약에 남긴다.

## 사전 준비

- Docker
- JDK 21 (Gradle wrapper가 쓴다)

k6와 WireMock은 Docker 이미지로 받으므로 따로 설치하지 않는다. 아래 명령은 모두 **저장소 루트에서** 실행한다.

## 실행 순서

1. 인프라를 띄운다.

   ```bash
   docker compose -f loadtest/docker-compose.yml up -d mysql redis wiremock
   ```

2. 앱을 띄운다. 로그에 `loadtest seed ready`가 찍히면 준비된 것이다.

   S2·S3용 (FCM 대역은 100ms 뒤 정상 응답):

   ```bash
   ./gradlew bootTestRun
   ```

   S1용 (FCM 대역이 120초 동안 응답하지 않음):

   ```bash
   LOADTEST_FCM_DELAY_MS=120000 ./gradlew bootTestRun
   ```

3. 다른 터미널에서 시나리오를 실행한다. 요약은 `loadtest/results/`에 남는다.

   S1:

   ```bash
   docker compose -f loadtest/docker-compose.yml run --rm k6 run --summary-export /loadtest/results/s1.json /loadtest/k6/s1-fcm-hang.js
   ```

   S2:

   ```bash
   docker compose -f loadtest/docker-compose.yml run --rm k6 run --summary-export /loadtest/results/s2.json /loadtest/k6/s2-kakao-slow.js
   ```

   S3:

   ```bash
   docker compose -f loadtest/docker-compose.yml run --rm k6 run --summary-export /loadtest/results/s3.json /loadtest/k6/s3-google-exchange-slow.js
   ```

   S3를 초당 정해진 횟수로만 로그인하게 하려면 `LOGIN_RATE`를 준다. 교환이 따라가는지(credential이 저장되는지)를 현실적인 빈도로 볼 때 쓴다.

   ```bash
   docker compose -f loadtest/docker-compose.yml run --rm -e LOGIN_RATE=1 k6 run /loadtest/k6/s3-google-exchange-slow.js
   ```

4. 다음 시나리오 전에 앱을 다시 띄운다. S1은 멈춘 발송이 남아 다음 측정을 오염시키고, 모든 시나리오는 새 DB에서 시작해야 비교가 된다.

## 확인

- k6 출력 끝의 요약에 `{ scenario:read }`(S1·S2) 또는 `{ scenario:login }`(S3) 줄이 있고, `loadtest/results/`에 요약 JSON이 생기면 정상 실행이다.
- 비교할 지표는 `http_req_duration{scenario:read}`의 p95·p99, `dropped_iterations`(보내지 못한 요청), `db_connections_active`·`db_connections_pending`이다.
- 200이 아닌 응답은 k6가 `status=… error=…` 경고로 찍는다.

## 결과를 읽을 때 주의할 점

- **지연은 4초다.** 외부 호출의 응답 타임아웃이 5초라, 5초로 잡으면 "느리게 성공"이 아니라 타임아웃으로 끝난다.
- **목록 조회는 초당 요청 수를 고정한다.** VU가 쉬지 않고 요청하게 하면 노트북 CPU가 먼저 포화돼 전후 차이가 가려진다(첫 측정에서 확인). 요청을 보낼 VU가 모자라 보내지 못한 요청은 `dropped_iterations`로 따로 센다.
- **S2의 `status=0`(오류 1211)은 서버 문제가 아니다.** 오류 1211은 k6가 TCP 연결을 맺지 못하고 시간이 초과된 것이다. Tomcat은 503 응답에 `Connection: close`를 붙여 연결을 닫는다. 그래서 거절된 로그인이 매번 새 연결을 만들고, 그 연결을 Docker Desktop의 포트 전달이 다 받지 못한다. 서버 로그에는 남지 않는다.
- **S3의 기본 부하는 비현실적으로 크다.** 적용 후 로그인이 빨라져 VU 10개가 초당 수백 건을 로그인하므로, 대부분의 코드 교환이 건너뛰어진다. 교환이 따라가는지는 `LOGIN_RATE=1`로 본다.

## 적용 전 수치 재현

적용 전 수치는 부하 테스트 장치만 들어간 커밋(`#134` 브랜치의 첫 커밋)을 체크아웃해 같은 순서로 잰다. 장치는 외부 URL 설정 키와 이 폴더, 테스트 소스의 실행 설정뿐이라 측정 대상 동작은 바뀌지 않는다.

## 정리

```bash
docker compose -f loadtest/docker-compose.yml stop
```

## 다음에 읽을 것

- 측정 결과와 설계 결정: [`external-api-bulkhead.md`](../docs/specs/cross-cutting/external-api-bulkhead.md)
