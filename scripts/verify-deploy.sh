#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  source .env
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
MYSQL_DATABASE="${MYSQL_DATABASE:-tripfit}"
APP_PORT="${APP_PORT:-8080}"

EXPECTED_TABLES=(
  users
  refresh_token
  trip
  trip_member
  regular_schedule
  personal_schedule
  recommendation
)

failures=0

log() {
  printf '[verify-deploy] %s\n' "$*"
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
    log "FAIL app readiness endpoint (http://localhost:${APP_PORT}/actuator/health/readiness)"
    failures=$((failures + 1))
  fi
}

check_tables() {
  local tables
  tables="$(docker exec tripfit-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -Nse \
    "SELECT table_name FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' ORDER BY table_name;" 2>/dev/null || true)"

  for table in "${EXPECTED_TABLES[@]}"; do
    if grep -qx "$table" <<<"$tables"; then
      log "OK table exists: $table"
    else
      log "FAIL missing table: $table"
      failures=$((failures + 1))
    fi
  done
}

check_foreign_keys() {
  local fk_count
  fk_count="$(docker exec tripfit-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -Nse \
    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='${MYSQL_DATABASE}' AND constraint_type='FOREIGN KEY';" 2>/dev/null || echo 0)"
  if [[ "$fk_count" -ge 6 ]]; then
    log "OK foreign keys: $fk_count"
  else
    log "FAIL foreign keys (expected >= 6, got: $fk_count)"
    failures=$((failures + 1))
  fi
}

check_app_logs() {
  # grep -E — rg는 EC2·CI에 없을 수 있고, 없으면 이 검사가 조용히 통과해버림(exit 127을 if가 false로 삼킴)
  if docker logs tripfit-app 2>&1 | grep -Eqi "error executing ddl|schema-validation|application run failed|unsupported database|doesn't have a default value"; then
    log "FAIL suspicious errors found in app logs"
    failures=$((failures + 1))
  else
    log "OK no schema errors in app logs"
  fi
}

log "starting deployment verification"
check_container_running tripfit-mysql
check_container_running tripfit-app
check_app_health
check_tables
check_foreign_keys
check_app_logs

if [[ "$failures" -gt 0 ]]; then
  log "verification failed ($failures issue(s))"
  exit 1
fi

log "verification passed"
exit 0
