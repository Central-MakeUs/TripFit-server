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

if docker compose run --rm --entrypoint certbot certbot renew \
  --webroot -w /var/www/certbot --quiet; then
  # certbot 컨테이너가 12h 루프에서 먼저 갱신하면 이 스크립트 실행 전후 인증서가 같아 보여 reload를 건너뛰게 된다.
  # 그러면 nginx가 메모리에 올린 구 인증서를 만료까지 계속 내보내므로(2026-09-29 api 만료 사고) 매번 무조건 reload한다.
  docker exec tripfit-nginx nginx -s reload
  log "nginx reloaded"
  log "OK"
else
  log "FAIL renew"
  exit 1
fi
