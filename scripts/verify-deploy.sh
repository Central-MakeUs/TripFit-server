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
# dev/local(1단계): false — prod(3단계): true
VERIFY_FLYWAY="${VERIFY_FLYWAY:-false}"

EXPECTED_TABLES=(
  user
  user_condition
  trip
  trip_member
  member_schedule
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

check_flyway() {
  if [[ "$VERIFY_FLYWAY" != "true" ]]; then
    log "SKIP flyway check (dev phase; VERIFY_FLYWAY=true for prod)"
    return 0
  fi

  local version
  version="$(docker exec tripfit-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -Nse \
    "SELECT version FROM ${MYSQL_DATABASE}.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || true)"
  if [[ "$version" == "1" ]]; then
    log "OK flyway schema version: $version"
  else
    log "FAIL flyway schema version (expected 1, got: ${version:-none})"
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
  if docker logs tripfit-app 2>&1 | rg -qi "error executing ddl|schema-validation|flyway.*failed|application run failed|unsupported database"; then
    log "FAIL suspicious errors found in app logs"
    failures=$((failures + 1))
  else
    log "OK no schema/flyway errors in app logs"
  fi
}

log "starting deployment verification (VERIFY_FLYWAY=${VERIFY_FLYWAY})"
check_container_running tripfit-mysql
check_container_running tripfit-app
check_app_health
check_flyway
check_tables
check_foreign_keys
check_app_logs

if [[ "$failures" -gt 0 ]]; then
  log "verification failed ($failures issue(s))"
  exit 1
fi

log "verification passed"
exit 0
