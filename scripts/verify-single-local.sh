#!/usr/bin/env bash
# 후보 환경의 가용성·포트 격리·자원 설정을 확인하며 실측값은 별도 기록한다.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.local-state/single/.env"
# 셸에 남아 있는 운영 자격 증명이 Compose 값을 덮어쓰지 않도록 분리한다.
DOCKER_ENV=(env -i "PATH=$PATH" "HOME=$HOME")
for docker_var in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG DOCKER_TLS_VERIFY DOCKER_CERT_PATH DOCKER_API_VERSION; do
  if [[ -n "${!docker_var:-}" ]]; then
    DOCKER_ENV+=("$docker_var=${!docker_var}")
  fi
done
compose() {
  "${DOCKER_ENV[@]}" docker compose --project-name tripfit-single-local --env-file "$ENV_FILE" \
    -f "$ROOT_DIR/deploy/single/docker-compose.yml" "$@"
}
[[ -f "$ENV_FILE" ]] || { echo 'Run ./scripts/single-local.sh up first'; exit 1; }
# 이 파일에서는 검증용으로 생성한 로컬 포트 값만 읽는다.
APP_PORT="$(sed -n 's/^APP_PORT=//p' "$ENV_FILE")"
GATEWAY_PORT="$(sed -n 's/^NGINX_HTTP_PORT=//p' "$ENV_FILE")"
curl -fsS "http://127.0.0.1:${APP_PORT}/actuator/health/readiness" >/dev/null
curl -fsS "http://127.0.0.1:${GATEWAY_PORT}/health" >/dev/null
curl -fsS "http://127.0.0.1:${GATEWAY_PORT}/v3/api-docs" >/dev/null
[[ "$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:${GATEWAY_PORT}/actuator/health")" == 404 ]]
[[ "$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:${APP_PORT}/actuator/prometheus")" != 200 ]]
[[ "$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:${GATEWAY_PORT}/api/v1/auth/me")" == 401 ]]
compose exec -T mysql sh -ec 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h 127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE" -Nse "SELECT 1"' >/dev/null
compose exec -T redis sh -ec 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping' | grep -qx PONG
for service in mysql redis; do
  id="$(compose ps -q "$service")"
  [[ "$(docker inspect "$id" --format '{{len .HostConfig.PortBindings}}')" == 0 ]]
done
for id in $(compose ps -q); do
  [[ "$(docker inspect "$id" --format '{{.HostConfig.LogConfig.Type}}')" == json-file ]]
  [[ "$(docker inspect "$id" --format '{{index .HostConfig.LogConfig.Config "max-size"}}')" == 10m ]]
  [[ "$(docker inspect "$id" --format '{{index .HostConfig.LogConfig.Config "max-file"}}')" == 3 ]]
  [[ "$(docker inspect "$id" --format '{{.HostConfig.Memory}}')" -gt 0 ]]
done
compose ps
echo 'OK: readiness, gateway, OpenAPI, auth boundary, DB, Redis, private ports, log rotation, memory limits'
