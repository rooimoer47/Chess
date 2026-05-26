#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
source .env

echo "==> Generating nginx config for ${CHESS_DOMAIN}..."
envsubst '${CHESS_DOMAIN}' < nginx/nginx.conf.template > nginx/nginx.conf

echo "==> Stopping stack (frees port 80 for certificate issuance)..."
docker compose down 2>/dev/null || true

echo "==> Issuing/renewing TLS certificate for ${CHESS_DOMAIN}..."
docker compose --profile certbot run --rm -p 80:80 certbot certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --no-eff-email \
  --keep-until-expiring \
  -d "${CHESS_DOMAIN}" \
  --email "${CHESS_ADMIN_EMAIL}"

echo "==> Building and starting stack..."
docker compose up -d --build

echo "==> Done. Visit https://${CHESS_DOMAIN}"
