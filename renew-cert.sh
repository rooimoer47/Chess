#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Stopping stack for certificate renewal..."
docker compose down

echo "==> Renewing certificate..."
docker compose --profile certbot run --rm -p 80:80 certbot renew --quiet

echo "==> Restarting stack..."
docker compose up -d

echo "==> Certificate renewed."
