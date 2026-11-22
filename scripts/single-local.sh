#!/usr/bin/env bash
# 독립된 Compose 프로젝트와 임시 자격 증명으로 단일 서버 후보를 검증한다.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
ENV_FILE="$ROOT_DIR/.local-state/single/.env"
PROJECT=tripfit-single-local
COMPOSE_FILE="$ROOT_DIR/deploy/single/docker-compose.yml"

prepare_env() {
  mkdir -p build "$ROOT_DIR/.local-state/single"
  chmod 700 "$ROOT_DIR/.local-state/single"
  if [[ ! -f "$ENV_FILE" ]]; then
    python3 - "$ENV_FILE" <<'PY'
import os, secrets, sys
path = sys.argv[1]
values = {
    'GHCR_IMAGE': 'tripfit-single-local:verify',
    'MYSQL_ROOT_PASSWORD': secrets.token_hex(24),
    'MYSQL_DATABASE': 'tripfit',
    'SPRING_DATASOURCE_USERNAME': 'tripfit_app',
    'SPRING_DATASOURCE_PASSWORD': secrets.token_hex(24),
    'REDIS_PASSWORD': secrets.token_hex(24),
    'JWT_SECRET': secrets.token_hex(32),
    'SOCIAL_TOKEN_AES_KEY': '',
    'APP_PORT': '18081', 'NGINX_HTTP_PORT': '18080',
    'COOKIE_DOMAIN': 'localhost', 'COOKIE_SECURE': 'true',
}
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as f:
    f.write(''.join(f'{k}={v}\n' for k, v in values.items()))
PY
  fi
}
# 셸에 남아 있는 운영 자격 증명이 Compose 값을 덮어쓰지 않도록 분리한다.
DOCKER_ENV=(env -i "PATH=$PATH" "HOME=$HOME")
for docker_var in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG DOCKER_TLS_VERIFY DOCKER_CERT_PATH DOCKER_API_VERSION; do
  if [[ -n "${!docker_var:-}" ]]; then
    DOCKER_ENV+=("$docker_var=${!docker_var}")
  fi
done
compose() {
  "${DOCKER_ENV[@]}" docker compose --project-name "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

case "${1:-help}" in
  up)
    prepare_env
    ./gradlew bootJar --no-daemon
    mkdir -p build/single-image
    python3 - <<'PY'
from pathlib import Path
import shutil
jars = [p for p in Path('build/libs').glob('*.jar') if not p.name.endswith('-plain.jar')]
if len(jars) != 1:
    raise SystemExit('Expected exactly one bootJar in build/libs')
shutil.copy2(jars[0], 'build/single-image/app.jar')
PY
    docker build -t tripfit-single-local:verify -f deploy/single/Dockerfile.local build/single-image
    compose config --quiet
    compose up -d --wait --wait-timeout 300
    "$ROOT_DIR/scripts/verify-single-local.sh"
    ;;
  verify) "$ROOT_DIR/scripts/verify-single-local.sh" ;;
  functional) python3 "$ROOT_DIR/scripts/verify-single-functional.py" ;;
  stats)
    compose ps
    ids="$(compose ps -q)"
    [[ -n "$ids" ]] || { echo "No running containers in $PROJECT"; exit 1; }
    docker stats --no-stream $ids
    ;;
  restart)
    compose restart
    compose up -d --wait --wait-timeout 300
    "$ROOT_DIR/scripts/verify-single-local.sh"
    ;;
  down)
    # DB 볼륨은 삭제하지 않는다.
    compose down
    ;;
  *)
    echo 'Usage: ./scripts/single-local.sh {up|verify|functional|stats|restart|down}'
    ;;
esac
