# AI 원인 리포터 이미지. 빌드 컨텍스트는 저장소 루트다(루트의 Gradle wrapper를 같이 쓴다).
#   docker build -f incident-reporter/Dockerfile -t tripfit-incident-reporter .
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY incident-reporter incident-reporter

RUN chmod +x gradlew && ./gradlew -p incident-reporter installDist --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

# 의심 코드를 짚기 위해 분석 직전에 저장소 사본을 git pull 한다.
# 홈 디렉터리가 있어야 git이 설정을 읽고 쓸 수 있다. /source는 저장소 사본을 담을 볼륨 자리로, 처음 붙는
# 빈 볼륨이 이 소유권을 물려받아 reporter 사용자가 clone할 수 있다.
RUN apk add --no-cache git \
    && addgroup -S reporter && adduser -S -h /home/reporter reporter -G reporter \
    && mkdir -p /source && chown reporter:reporter /source \
    && git config --system --add safe.directory '*'

WORKDIR /app
COPY --from=builder /build/incident-reporter/build/install/incident-reporter /app

USER reporter:reporter

# 서버 E(t3.micro)에서 Prometheus와 함께 돌도록 힙 상한을 둔다. 값은 배포 후 실측해 조정한다(스펙 리스크 표).
ENV JAVA_OPTS="-Xmx96m -XX:MaxMetaspaceSize=64m -XX:+UseSerialGC"

EXPOSE 9095

ENTRYPOINT ["/app/bin/incident-reporter"]
