#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

BACKEND_URL="http://localhost:8080/api/auth/me"
BACKEND_LOG="$(mktemp -t chess-e2e-backend.XXXXXX.log)"
BACKEND_PID=""
STARTED_BACKEND=0

cleanup() {
  if [ "$STARTED_BACKEND" = "1" ] && [ -n "$BACKEND_PID" ]; then
    echo "==> Stopping backend (pid $BACKEND_PID)…"
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
    # mvn spring-boot:run forks the actual JVM as a child process that
    # doesn't always die with its parent — clean up any survivor.
    pkill -f "spring-boot:run.*ChessApplication" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "==> Ensuring the database is up…"
docker compose -f docker-compose.local.yml up db -d

echo "==> Waiting for the database…"
until docker compose -f docker-compose.local.yml exec -T db pg_isready -U chess >/dev/null 2>&1; do
  sleep 1
done

if curl -s -o /dev/null "$BACKEND_URL"; then
  echo "==> Backend already running on :8080 — reusing it."
else
  echo "==> Starting backend…"
  ./run-backend.sh > "$BACKEND_LOG" 2>&1 &
  BACKEND_PID=$!
  STARTED_BACKEND=1

  echo "==> Waiting for backend to become ready…"
  ready=0
  for _ in $(seq 1 60); do
    if curl -s -o /dev/null "$BACKEND_URL"; then
      ready=1
      break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      break
    fi
    sleep 2
  done

  if [ "$ready" != "1" ]; then
    echo "==> Backend did not become ready — log follows:"
    cat "$BACKEND_LOG"
    exit 1
  fi
fi

echo "==> Running e2e tests…"
cd frontend
npm run e2e
