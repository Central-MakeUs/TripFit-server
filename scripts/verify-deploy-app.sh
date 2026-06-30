#!/usr/bin/env bash
# EC2 A (split deploy) — app container + optional remote MySQL ping
# Usage: cd deploy/app && source .env && ../../scripts/verify-deploy-app.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${DEPLOY_DIR:-$ROOT_DIR/deploy/app}"

if [[ -f "$DEPLOY_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  set -a
  source "$DEPLOY_DIR/.env"
  set +a
elif [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  set -a
  source "$ROOT_DIR/.env"
  set +a
fi

APP_PORT="${APP_PORT:-8080}"
MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
VERIFY_FLYWAY="${VERIFY_FLYWAY:-false}"

failures=0

log() {
  printf '[verify-deploy-app] %s\n' "$*"
}

check_container_running() {
  local name="$1"
  if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
    log "FAIL container not running: $name"
    failures=$((failures + 1))
    return 1
  fi
  log "OK container running: $name"
}

check_app_health() {
  if curl -fsS "http://localhost:${APP_PORT}/actuator/health/readiness" >/dev/null; then
    log "OK app readiness endpoint"
  else
    log "FAIL app readiness (http://localhost:${APP_PORT}/actuator/health/readiness)"
    failures=$((failures + 1))
  fi
}

check_remote_mysql() {
  if [[ -z "$MYSQL_HOST" || "$MYSQL_HOST" == "mysql" ]]; then
    log "SKIP remote MySQL (MYSQL_HOST not set or local compose service name)"
    return 0
  fi

  if ! command -v mysql >/dev/null 2>&1; then
    log "SKIP remote MySQL client not installed"
    return 0
  fi

  local user="${SPRING_DATASOURCE_USERNAME:-}"
  local pass="${SPRING_DATASOURCE_PASSWORD:-}"
  if [[ -z "$user" || -z "$pass" ]]; then
    log "SKIP remote MySQL (SPRING_DATASOURCE_* not set)"
    return 0
  fi

  if mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$user" -p"$pass" -Nse "SELECT 1;" >/dev/null 2>&1; then
    log "OK remote MySQL: ${MYSQL_HOST}:${MYSQL_PORT}"
  else
    log "FAIL remote MySQL: ${MYSQL_HOST}:${MYSQL_PORT}"
    failures=$((failures + 1))
  fi
}

check_app_logs() {
  if docker logs tripfit-app 2>&1 | rg -qi "error executing ddl|schema-validation|flyway.*failed|application run failed|unsupported database|communications link failure"; then
    log "FAIL suspicious errors in app logs"
    failures=$((failures + 1))
  else
    log "OK no critical errors in app logs"
  fi
}

log "starting app-only verification (VERIFY_FLYWAY=${VERIFY_FLYWAY})"
check_container_running tripfit-app
check_app_health
check_remote_mysql
check_app_logs

if [[ "$VERIFY_FLYWAY" == "true" ]]; then
  log "WARN VERIFY_FLYWAY=true on split deploy — use verify-deploy.sh on DB host or manual flyway check"
fi

if [[ "$failures" -gt 0 ]]; then
  log "verification failed ($failures issue(s))"
  exit 1
fi

log "verification passed"
exit 0
