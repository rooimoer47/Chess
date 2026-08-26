#!/bin/bash
set -a
source .env
set +a

# This script runs the backend directly on the host, not inside the
# docker-compose network, so it can't resolve the "db" hostname that .env
# uses for production/Docker — talk to the published port on localhost
# instead. (Requires `docker compose -f docker-compose.local.yml up db -d`.)
export DB_URL="${DB_URL//db:5432/localhost:5432}"

# Logback defaults to /logs, which is writable inside the Docker container
# (mounted from ./logs) but requires root on a bare host. Point it at the
# repo-local logs/ directory instead when running outside Docker.
export LOG_PATH="${LOG_PATH:-$PWD/logs}"
mkdir -p "$LOG_PATH"

# docker-compose.local.yml now runs the container as the host user, so it no
# longer leaves a root-owned chess.log in the bind-mounted ./logs. This clears
# one left behind by an older run. Safe: directory write access is all that's
# needed to unlink a file you don't own, and it only touches this one file.
rm -f "$LOG_PATH/chess.log"

mvn spring-boot:run -Dspring-boot.run.mainClass=pvt.phgg.chess.ChessApplication
