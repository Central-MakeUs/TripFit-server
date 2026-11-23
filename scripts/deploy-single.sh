#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 2 || ! $1 =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ || ! $2 =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: $0 <Lightsail IPv4> <40-character release commit>" >&2
  exit 2
fi

address=$1
commit=$2
cd "$(git rev-parse --show-toplevel)"
git cat-file -e "${commit}^{commit}"
# Verify the release layout before streaming anything to the server.
git cat-file -e "$commit:scripts/single-release.sh"
git cat-file -e "$commit:deploy/single/compose.production.yml"

git archive "$commit" deploy/single deploy/nginx/nginx.conf deploy/nginx/snippets scripts/single-release.sh |
  ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ForwardAgent=no "ubuntu@$address" \
    "umask 077; set -eu; mkdir -p ~/tripfit/releases; d=\$(mktemp -d ~/tripfit/releases/$commit.XXXXXXXX); tar -xf - -C \"\$d\"; bash \"\$d/scripts/single-release.sh\" deploy $commit"
