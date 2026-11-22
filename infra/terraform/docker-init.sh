#!/bin/sh
# Lightsail appends this script to a /bin/sh wrapper.
set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2 cron util-linux
usermod -aG docker ubuntu
systemctl enable --now docker cron
docker compose version
