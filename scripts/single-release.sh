#!/usr/bin/env bash
set -euo pipefail
umask 077

release_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
root="$HOME/tripfit"
shared="$root/shared"
mkdir -p "$shared"/{letsencrypt,acme,tls-enabled}
# Nginx serves ACME tokens as an unprivileged worker inside the bind mount.
chmod 755 "$shared/acme"
exec 9>"$shared/operation.lock"
flock -n 9 || { echo "A deployment or certificate operation is already running." >&2; exit 1; }

if [[ ! -r "$shared/.env" || $(stat -c '%a' "$shared/.env") != 600 ]]; then
  echo "Create $shared/.env with permissions 0600 before deploying." >&2
  exit 1
fi

stack() {
  local directory=$1 image
  shift
  image=$(cat "$directory/.release-image") || return
  env -i PATH="$PATH" HOME="$HOME" DEPLOY_STATE_DIR="$shared" GHCR_IMAGE="$image" \
    docker compose --env-file "$shared/.env" --project-name tripfit-production \
    -f "$directory/deploy/single/docker-compose.yml" \
    -f "$directory/deploy/single/compose.production.yml" "$@"
}

activate() {
  local directory=$1
  stack "$directory" up -d --wait --wait-timeout 240 mysql redis app gateway &&
    stack "$directory" exec -T app curl -fsS http://localhost:8080/actuator/health/readiness >/dev/null &&
    stack "$directory" exec -T gateway wget -qO /dev/null http://127.0.0.1/health
}

case "${1:-}" in
  deploy)
    commit=${2:-}
    [[ $commit =~ ^[0-9a-f]{40}$ ]]
    [[ $release_dir == "$root/releases/$commit."* ]] || exit 2
    old=""
    if [[ -L "$root/current" ]]; then
      old=$(readlink -f "$root/current")
      [[ $old == "$root/releases/"* && -r "$old/.release-image" ]] || exit 2
    fi

    repository=ghcr.io/central-makeus/tripfit-server
    docker pull "$repository:$commit"
    digest=$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$repository:$commit" |
      awk -v prefix="$repository@sha256:" 'index($0, prefix) == 1 { print; exit }')
    [[ $digest =~ ^ghcr\.io/central-makeus/tripfit-server@sha256:[0-9a-f]{64}$ ]]
    printf '%s\n' "$digest" > "$release_dir/.release-image"
    stack "$release_dir" config --quiet
    if activate "$release_dir"; then
      # mv -T atomically switches the pointer without following the current symlink.
      pending="$root/.current.$$"
      if ln -s "$release_dir" "$pending" && mv -Tf "$pending" "$root/current"; then
        echo "Active release: $commit ($digest)"
        exit 0
      fi
      if [[ -L $pending ]]; then unlink "$pending"; fi
    fi

    if [[ -n $old ]]; then
      if activate "$old"; then
        echo "Deployment failed. Restored $old." >&2
      else
        echo "Deployment and rollback both failed. Inspect the stack on the host." >&2
      fi
    else
      stack "$release_dir" stop gateway app || true
      echo "Initial deployment failed. MySQL volume was preserved." >&2
    fi
    exit 1
    ;;
  dump)
    [[ $(readlink -f "$root/current") == "$release_dir" ]] || exit 2
    stack "$release_dir" exec -T mysql sh -ec '
      export MYSQL_PWD="$MYSQL_PASSWORD"
      exec mysqldump -u "$MYSQL_USER" --single-transaction --no-tablespaces "$MYSQL_DATABASE"
    '
    ;;
  tls|renew|renew-check)
    [[ $(readlink -f "$root/current") == "$release_dir" ]] || exit 2
    domain=api.tripfit.online
    marker="$shared/tls-enabled/https.conf"
    if [[ $1 == tls ]]; then
      email=${2:?Provide the certificate contact email.}
      stack "$release_dir" run --rm --no-deps certbot certonly --non-interactive --agree-tos \
        --email "$email" --webroot -w /var/www/certbot --cert-name "$domain" -d "$domain" --keep-until-expiring
      enabled_now=false
      if [[ ! -L $marker ]]; then
        ln -s /etc/nginx/tripfit/https.conf "$marker"
        enabled_now=true
      fi
      if ! stack "$release_dir" exec -T gateway nginx -t ||
         ! stack "$release_dir" exec -T gateway nginx -s reload; then
        if $enabled_now; then unlink "$marker"; fi
        exit 1
      fi
    else
      [[ -L $marker ]] || { echo "HTTPS has not been enabled." >&2; exit 1; }
      if [[ $1 == renew-check ]]; then
        stack "$release_dir" run --rm --no-deps certbot renew --dry-run
      else
        stack "$release_dir" run --rm --no-deps certbot renew --quiet
        stack "$release_dir" exec -T gateway nginx -t
        stack "$release_dir" exec -T gateway nginx -s reload
      fi
    fi
    ;;
  *)
    echo "Usage: $0 deploy <commit> | tls <email> | renew | renew-check | dump" >&2
    exit 2
    ;;
esac
