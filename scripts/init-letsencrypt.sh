#!/usr/bin/env bash
# Let's Encrypt — api.tripfit.online (webroot 방식)
#
# 사전 조건:
#   - Route 53 A: api.tripfit.online → 이 EC2 Elastic IP
#   - EC2 SG Inbound 80, 443
#   - docker compose 로 nginx·app 기동 가능
#
# 사용:
#   export CERTBOT_EMAIL=codus5068@naver.com
#   ./scripts/init-letsencrypt.sh
#
# 테스트(스테이징 CA):
#   CERTBOT_STAGING=1 CERTBOT_EMAIL=... ./scripts/init-letsencrypt.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${DEPLOY_DIR:-$ROOT_DIR/deploy/app}"
PRIMARY_DOMAIN="${CERTBOT_DOMAIN:-api.tripfit.online}"
EMAIL="${CERTBOT_EMAIL:?CERTBOT_EMAIL is required}"
STAGING="${CERTBOT_STAGING:-0}"
RSA_KEY_SIZE="${CERTBOT_RSA_KEY_SIZE:-4096}"

log() {
  printf '[init-letsencrypt] %s\n' "$*"
}

cd "$DEPLOY_DIR"

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

: "${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME is required (export before running)}"
: "${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD is required (export before running)}"
export SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD

if [[ "$STAGING" != "0" ]]; then
  STAGING_ARG="--staging"
  log "STAGING mode (fake LE certs)"
else
  STAGING_ARG=""
fi

if command -v dig >/dev/null 2>&1; then
  resolved="$(dig +short "${PRIMARY_DOMAIN}" A 2>/dev/null | head -1 || true)"
  if [[ -n "$resolved" ]]; then
    log "DNS ${PRIMARY_DOMAIN} → ${resolved}"
  else
    log "WARN: ${PRIMARY_DOMAIN} A record not found — ACME may fail"
  fi
fi

log "[1/6] DH params"
docker compose run --rm --entrypoint sh certbot -c '
  if [ ! -f /etc/letsencrypt/ssl-dhparams.pem ]; then
    curl -fsS https://raw.githubusercontent.com/certbot/certbot/master/certbot/certbot/ssl-dhparams.pem \
      -o /etc/letsencrypt/ssl-dhparams.pem \
      || openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048
  fi
'

log "[2/6] dummy certificate (nginx 443 기동용)"
path="/etc/letsencrypt/live/${PRIMARY_DOMAIN}"
docker compose run --rm --entrypoint sh certbot -c "
  mkdir -p '${path}' &&
  openssl req -x509 -nodes -newkey rsa:${RSA_KEY_SIZE} -days 1 \
    -keyout '${path}/privkey.pem' \
    -out '${path}/fullchain.pem' \
    -subj '/CN=${PRIMARY_DOMAIN}'
"

log "[3/6] ensure stack is up (app must stay healthy for nginx)"
docker compose up -d
if docker inspect tripfit-nginx >/dev/null 2>&1; then
  docker exec tripfit-nginx nginx -s reload
fi

log "[4/6] remove dummy certificate"
docker compose run --rm --entrypoint sh certbot -c "
  rm -Rf /etc/letsencrypt/live/${PRIMARY_DOMAIN} \
    /etc/letsencrypt/archive/${PRIMARY_DOMAIN} \
    /etc/letsencrypt/renewal/${PRIMARY_DOMAIN}.conf
"

log "[5/6] certbot certonly — ${PRIMARY_DOMAIN}"
# shellcheck disable=SC2086
docker compose run --rm --entrypoint sh certbot -c "
  certbot certonly --webroot -w /var/www/certbot \
    ${STAGING_ARG} \
    -d ${PRIMARY_DOMAIN} \
    --email ${EMAIL} \
    --rsa-key-size ${RSA_KEY_SIZE} \
    --agree-tos \
    --no-eff-email \
    --force-renewal
"

log "[6/6] nginx reload"
docker exec tripfit-nginx nginx -s reload

log "OK — verify:"
log "  curl -fsSI https://${PRIMARY_DOMAIN}/health"
log "  curl -fsSI https://${PRIMARY_DOMAIN}/actuator/health   # expect 404"
log "cron renew: 0 3 * * * cd ${DEPLOY_DIR} && ${ROOT_DIR}/scripts/renew-letsencrypt.sh"
