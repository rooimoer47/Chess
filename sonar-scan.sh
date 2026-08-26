#!/bin/bash
# Scan the project with the local SonarQube container and write the findings out
# as a triage list (.sonar-report/findings.md + findings.json).
#
#   ./sonar-scan.sh                       start the server if needed, scan, report
#   ./sonar-scan.sh --report-only         rebuild the report from the last analysis
#   ./sonar-scan.sh --dismiss KEY falsepositive|accept "why"
#   ./sonar-scan.sh --safe HOTSPOT_KEY "why"
#   ./sonar-scan.sh --backup [FILE]       pg_dump the server's database
#   ./sonar-scan.sh --export-triage       save dismissals + profile to sonar/
#   ./sonar-scan.sh --replay-triage [-n]  re-apply them to a rebuilt server
#
# The two dismiss forms record a triage decision on the server, so the finding
# stays out of every later report instead of being re-argued each scan.
#
# The server runs from docker-compose.sonar.yml on its own PostgreSQL. Upgrading
# is a matter of bumping the image there and letting SonarQube migrate the
# schema on restart — take a --backup first regardless. The durable record of
# triage decisions lives in sonar/triage.json, which is tracked in git.
#
# Overridable via the environment: SONAR_URL, SONAR_CONTAINER, SONAR_DB_CONTAINER,
# SONAR_TOKEN, SONAR_ADMIN, SONAR_ADMIN_PASSWORD.
set -euo pipefail

cd "$(dirname "$0")"

SONAR_URL="${SONAR_URL:-http://localhost:9000}"
SONAR_CONTAINER="${SONAR_CONTAINER:-chess-sonarqube-1}"
SONAR_DB_CONTAINER="${SONAR_DB_CONTAINER:-chess-sonar-db-1}"
# The image version lives in the compose file, not here — that is the one
# place the stack is defined.
SONAR_COMPOSE="${SONAR_COMPOSE:-docker-compose.sonar.yml}"
SONAR_ADMIN="${SONAR_ADMIN:-admin}"
# SonarQube 26.8 enforces 12+ chars with mixed case and a symbol; the older
# admin1234 no longer satisfies the policy.
SONAR_ADMIN_PASSWORD="${SONAR_ADMIN_PASSWORD:-Admin1234chess!}"
PROJECT_KEY="pvt.phgg.chess:chess"
TOKEN_FILE=".sonar-token"
# Deliberately not under target/ — the scan runs `mvn clean`, which would wipe
# the previous report out from under a triage session in progress.
OUT_DIR=".sonar-report"

MODE=scan
case "${1:-}" in
  --report-only) MODE=report ;;
  --dismiss)     MODE=dismiss ;;
  --safe)        MODE=safe ;;
  --backup)      MODE=backup ;;
  --export-triage) MODE=export ;;
  --replay-triage) MODE=replay ;;
  -h|--help)     awk 'NR>1 && /^#/ {print; next} NR>1 {exit}' "$0"; exit 0 ;;
  "")            ;;
  *)             echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
esac

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

# --- server ----------------------------------------------------------------
# The container holds its analysis history, so always reuse it rather than
# creating a fresh one.
server_status() {
  curl -sf --max-time 5 "$SONAR_URL/api/system/status" 2>/dev/null \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])' 2>/dev/null || echo DOWN
}

ensure_server() {
  if [ "$(server_status)" = "UP" ]; then
    log "SonarQube already up at $SONAR_URL"
    return
  fi
  # SonarQube needs its database, so bring the whole stack up rather than the
  # one container; compose is a no-op for whatever is already running.
  log "Bringing up the SonarQube stack ($SONAR_COMPOSE)"
  docker compose -f "$SONAR_COMPOSE" up -d >/dev/null 2>&1
  # It serves STARTING until Elasticsearch and any schema migration finish.
  for _ in $(seq 1 60); do
    [ "$(server_status)" = "UP" ] && log "SonarQube is up" && return
    sleep 5
  done
  echo "SonarQube did not come up within 5 minutes" >&2
  exit 1
}

# The database holds everything; the SonarQube data volume is now just an
# Elasticsearch index, which the server rebuilds on its own.
backup_data() {
  local out="${1:-sonarqube-backup-$(date +%Y%m%d-%H%M%S).sql.gz}"
  docker inspect "$SONAR_DB_CONTAINER" >/dev/null 2>&1 || {
    echo "No $SONAR_DB_CONTAINER container to back up" >&2; exit 1; }

  log "Dumping the sonarqube database to $out"
  docker exec "$SONAR_DB_CONTAINER" pg_dump -U sonarqube sonarqube | gzip > "$out"
  log "Backup written to $out ($(du -h "$out" | cut -f1))"
}

# --- token -----------------------------------------------------------------
# Tokens are shown once at creation, so cache it. The file is gitignored.
ensure_token() {
  if [ -n "${SONAR_TOKEN:-}" ]; then
    return
  fi
  if [ -s "$TOKEN_FILE" ]; then
    SONAR_TOKEN="$(cat "$TOKEN_FILE")"
    # A token that has been revoked server-side still sits in the file.
    if curl -sf -u "$SONAR_TOKEN:" "$SONAR_URL/api/authentication/validate" \
        | grep -q '"valid":true'; then
      return
    fi
    log "Cached token is no longer valid, generating a new one"
  fi
  SONAR_TOKEN="$(curl -sf -u "$SONAR_ADMIN:$SONAR_ADMIN_PASSWORD" \
    -X POST "$SONAR_URL/api/user_tokens/generate" \
    -d "name=chess-scan-$(date +%s)" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')"
  printf '%s' "$SONAR_TOKEN" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  log "Generated a new scan token (cached in $TOKEN_FILE)"
}

# --- scan ------------------------------------------------------------------
run_scan() {
  # Sonar reads the Java bytecode, not just the sources, so classes have to
  # exist. test-compile is enough — the tests themselves need not run.
  log "Compiling"
  mvn -q clean test-compile

  log "Analysing"
  mvn -q sonar:sonar -Dsonar.token="$SONAR_TOKEN" -Dsonar.host.url="$SONAR_URL"
}

# The scanner uploads a report that the server processes asynchronously; the
# issues API still serves the previous analysis until that task finishes.
wait_for_ce() {
  local task_id
  task_id="$(grep '^ceTaskId=' target/sonar/report-task.txt | cut -d= -f2)"
  log "Waiting for the server to process the report ($task_id)"
  for _ in $(seq 1 90); do
    local status
    status="$(curl -sf -u "$SONAR_TOKEN:" "$SONAR_URL/api/ce/task?id=$task_id" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)["task"]["status"])')"
    case "$status" in
      SUCCESS) return ;;
      FAILED|CANCELED) echo "Report processing $status" >&2; exit 1 ;;
    esac
    sleep 2
  done
  echo "Report processing did not finish within 3 minutes" >&2
  exit 1
}

# --- report ----------------------------------------------------------------
fetch_json() {
  # $1 = api path with query string, $2 = output file
  curl -sf -u "$SONAR_TOKEN:" "$SONAR_URL/$1" -o "$2"
}

build_report() {
  mkdir -p "$OUT_DIR"
  log "Fetching findings"

  # Issues are paginated; 500 is the server's maximum page size.
  local page=1
  : > "$OUT_DIR/issues-pages.jsonl"
  while :; do
    fetch_json "api/issues/search?components=$PROJECT_KEY&resolved=false&ps=500&p=$page&additionalFields=rules" \
      "$OUT_DIR/.page.json"
    cat "$OUT_DIR/.page.json" >> "$OUT_DIR/issues-pages.jsonl"
    echo >> "$OUT_DIR/issues-pages.jsonl"
    local total fetched
    total=$(python3 -c 'import sys,json; print(json.load(open(sys.argv[1]))["total"])' "$OUT_DIR/.page.json")
    fetched=$((page * 500))
    [ "$fetched" -ge "$total" ] && break
    page=$((page + 1))
  done
  rm -f "$OUT_DIR/.page.json"

  fetch_json "api/hotspots/search?projectKey=$PROJECT_KEY&status=TO_REVIEW&ps=500" \
    "$OUT_DIR/hotspots.json"

  SONAR_URL="$SONAR_URL" PROJECT_KEY="$PROJECT_KEY" OUT_DIR="$OUT_DIR" \
    python3 sonar_report.py

  rm -f "$OUT_DIR/issues-pages.jsonl" "$OUT_DIR/hotspots.json"
}

# --- portable triage state -------------------------------------------------
# The server's database is the only place dismissals live, and the embedded H2
# one cannot be upgraded — so keep a copy in the repo that survives a rebuild.
SONAR_PROFILE="${SONAR_PROFILE:-Chess}"

export_triage() {
  mkdir -p sonar
  log "Exporting the $SONAR_PROFILE quality profile"
  curl -sf -u "$SONAR_TOKEN:" \
    "$SONAR_URL/api/qualityprofiles/backup?language=java&qualityProfile=$SONAR_PROFILE" \
    -o sonar/quality-profile-java.xml
  log "Exporting triage decisions"
  SONAR_URL="$SONAR_URL" SONAR_TOKEN="$SONAR_TOKEN" PROJECT_KEY="$PROJECT_KEY" \
    python3 sonar_triage.py export
}

replay_triage() {
  if [ -f sonar/quality-profile-java.xml ]; then
    log "Restoring the $SONAR_PROFILE quality profile"
    curl -sf -u "$SONAR_TOKEN:" -X POST "$SONAR_URL/api/qualityprofiles/restore" \
      -F "backup=@sonar/quality-profile-java.xml" -o /dev/null
    curl -sf -u "$SONAR_TOKEN:" -X POST "$SONAR_URL/api/qualityprofiles/add_project" \
      --data-urlencode "language=java" \
      --data-urlencode "qualityProfile=$SONAR_PROFILE" \
      --data-urlencode "project=$PROJECT_KEY" -o /dev/null || true
  fi
  log "Replaying triage decisions"
  SONAR_URL="$SONAR_URL" SONAR_TOKEN="$SONAR_TOKEN" PROJECT_KEY="$PROJECT_KEY" \
    python3 sonar_triage.py replay "${1:-}"
}

# --- triage decisions ------------------------------------------------------
# Recording the verdict on the server is what keeps the report shrinking: a
# dismissed finding never comes back, however often the project is rescanned.
dismiss_issue() {
  local key="$1" transition="$2" reason="${3:-}"
  case "$transition" in
    falsepositive|accept) ;;
    *) echo "Transition must be falsepositive or accept, got: $transition" >&2; exit 2 ;;
  esac
  [ -n "$reason" ] && curl -sf -u "$SONAR_TOKEN:" -X POST \
    "$SONAR_URL/api/issues/add_comment" \
    --data-urlencode "issue=$key" --data-urlencode "text=$reason" -o /dev/null
  curl -sf -u "$SONAR_TOKEN:" -X POST "$SONAR_URL/api/issues/do_transition" \
    --data-urlencode "issue=$key" --data-urlencode "transition=$transition" -o /dev/null
  log "Marked $key as $transition"
}

mark_hotspot_safe() {
  local key="$1" reason="${2:-}"
  curl -sf -u "$SONAR_TOKEN:" -X POST "$SONAR_URL/api/hotspots/change_status" \
    --data-urlencode "hotspot=$key" --data-urlencode "status=REVIEWED" \
    --data-urlencode "resolution=SAFE" --data-urlencode "comment=$reason" -o /dev/null
  log "Marked hotspot $key reviewed/safe"
}

# --- main ------------------------------------------------------------------
# Backup is the one action that must work with the server down.
if [ "$MODE" = backup ]; then
  backup_data "${2:-}"
  exit 0
fi

ensure_server
ensure_token

case "$MODE" in
  dismiss)
    [ $# -ge 3 ] || { echo "Usage: $0 --dismiss KEY falsepositive|accept \"why\"" >&2; exit 2; }
    dismiss_issue "$2" "$3" "${4:-}"
    exit 0
    ;;
  safe)
    [ $# -ge 2 ] || { echo "Usage: $0 --safe HOTSPOT_KEY \"why\"" >&2; exit 2; }
    mark_hotspot_safe "$2" "${3:-}"
    exit 0
    ;;
  export)
    export_triage
    exit 0
    ;;
  replay)
    replay_triage "${2:-}"
    exit 0
    ;;
  scan)
    run_scan
    wait_for_ce
    ;;
esac

build_report

log "Report written to $OUT_DIR/findings.md"
echo "Dashboard: $SONAR_URL/dashboard?id=$(K="$PROJECT_KEY" python3 -c 'import urllib.parse,os; print(urllib.parse.quote(os.environ["K"]))')"
