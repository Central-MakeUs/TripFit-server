#!/usr/bin/env bash
# Let's Encrypt 갱신 + nginx reload (cron 권장)
#
#   0 3 * * * cd /path/to/TripFit-server/deploy/app && /path/to/TripFit-server/scripts/renew-letsencrypt.sh >> /var/log/tripfit-certbot-renew.log 2>&1
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${DEPLOY_DIR:-$ROOT_DIR/deploy/app}"
PRIMARY_DOMAIN="${CERTBOT_DOMAIN:-api.tripfit.online}"

log() {
  printf '[renew-letsencrypt] %s\n' "$*"
}

cd "$DEPLOY_DIR"

before="$(docker compose run --rm --entrypoint cat certbot "/etc/letsencrypt/live/${PRIMARY_DOMAIN}/fullchain.pem" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null || true)"

if docker compose run --rm --entrypoint certbot certbot renew \
  --webroot -w /var/www/certbot --quiet; then
  after="$(docker compose run --rm --entrypoint cat certbot "/etc/letsencrypt/live/${PRIMARY_DOMAIN}/fullchain.pem" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null || true)"
  if [[ "$before" != "$after" ]]; then
    log "certificate renewed — reloading nginx"
    docker exec tripfit-nginx nginx -s reload
  else
    log "no renewal needed"
  fi
  log "OK"
else
  log "FAIL renew"
  exit 1
fi
