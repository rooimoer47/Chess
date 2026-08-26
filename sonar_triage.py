"""Export and replay the triage decisions held on a SonarQube server.

A dismissal is a judgement someone made about this code, but SonarQube keeps it
only in its own database — so rebuilding the server (or moving off the embedded
H2 one, which cannot be upgraded) would throw all of them away. Export writes
them to sonar/triage.json, which is tracked in git; replay re-applies them to a
freshly scanned project.

  python3 sonar_triage.py export
  python3 sonar_triage.py replay [--dry-run]

Reads SONAR_URL, SONAR_TOKEN, PROJECT_KEY from the environment.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from base64 import b64encode
from datetime import datetime, timezone

SONAR_URL = os.environ["SONAR_URL"].rstrip("/")
SONAR_TOKEN = os.environ["SONAR_TOKEN"]
PROJECT_KEY = os.environ["PROJECT_KEY"]

TRIAGE_FILE = "sonar/triage.json"

# A token authenticates as the username with an empty password.
AUTH = b64encode(f"{SONAR_TOKEN}:".encode()).decode()

# api/issues/search reports these; do_transition expects the second form.
TRANSITION_FOR = {
    "WONTFIX": "accept",
    "ACCEPTED": "accept",
    "FALSE-POSITIVE": "falsepositive",
}


def call(path, data=None):
    url = f"{SONAR_URL}/{path}"
    body = urllib.parse.urlencode(data).encode() if data else None
    req = urllib.request.Request(url, data=body)
    req.add_header("Authorization", f"Basic {AUTH}")
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.exit(f"{path} failed: HTTP {e.code} {e.read().decode()[:200]}")


def paged(path, key, **params):
    """Walk a paginated search endpoint and yield every item under `key`."""
    page = 1
    while True:
        q = urllib.parse.urlencode({**params, "ps": 500, "p": page})
        payload = call(f"{path}?{q}")
        items = payload.get(key, [])
        yield from items
        total = payload.get("total") or payload.get("paging", {}).get("total", 0)
        if page * 500 >= total or not items:
            return
        page += 1


def relative_path(component):
    return component.split(":")[-1]


def first_comment(item):
    comments = item.get("comments") or []
    return comments[0].get("markdown", "") if comments else ""


def do_export():
    issues = []
    # Filter client-side rather than with `resolutions=`: SonarQube renamed
    # WONTFIX to ACCEPTED, and each version rejects the other name outright.
    for i in paged("api/issues/search", "issues",
                   components=PROJECT_KEY, resolved="true",
                   additionalFields="comments"):
        if i.get("resolution") not in TRANSITION_FOR:
            continue  # FIXED / REMOVED — the code moved on, nothing to replay
        issues.append({
            "rule": i["rule"],
            "path": relative_path(i["component"]),
            "line": i.get("line"),
            "message": i["message"],
            "resolution": i["resolution"],
            "reason": first_comment(i),
        })

    hotspots = []
    for h in paged("api/hotspots/search", "hotspots",
                   projectKey=PROJECT_KEY, status="REVIEWED"):
        # The search response omits comments; only show returns them.
        detail = call(f"api/hotspots/show?hotspot={urllib.parse.quote(h['key'])}")
        comments = detail.get("comment") or []
        hotspots.append({
            "rule": h["ruleKey"],
            "path": relative_path(h["component"]),
            "line": h.get("line"),
            "message": h["message"],
            "resolution": h.get("resolution", "SAFE"),
            "reason": comments[0].get("markdown", "") if comments else "",
        })

    os.makedirs(os.path.dirname(TRIAGE_FILE), exist_ok=True)
    with open(TRIAGE_FILE, "w") as fh:
        json.dump({
            "project": PROJECT_KEY,
            "exported": datetime.now(timezone.utc).isoformat(),
            "issues": issues,
            "hotspots": hotspots,
        }, fh, indent=2)
    print(f"Exported {len(issues)} dismissals and {len(hotspots)} hotspot "
          f"reviews to {TRIAGE_FILE}")


def index_open(items, rule_key, line_key="line"):
    """Index open findings for matching, by exact line and by message.

    Line numbers drift as the file around them is edited, so an exact
    rule+path+line hit is preferred and rule+path+message is the fallback.
    """
    by_line, by_message = {}, {}
    for it in items:
        path = relative_path(it["component"])
        by_line[(it[rule_key], path, it.get("line"))] = it
        by_message.setdefault((it[rule_key], path, it["message"]), it)
    return by_line, by_message


def match(entry, by_line, by_message):
    key = (entry["rule"], entry["path"], entry["line"])
    if key in by_line:
        return by_line[key], "line"
    key = (entry["rule"], entry["path"], entry["message"])
    if key in by_message:
        return by_message[key], "message"
    return None, None


def do_replay(dry_run):
    if not os.path.exists(TRIAGE_FILE):
        sys.exit(f"No {TRIAGE_FILE} to replay — run export first.")
    saved = json.load(open(TRIAGE_FILE))

    open_issues = list(paged("api/issues/search", "issues",
                             components=PROJECT_KEY, resolved="false"))
    by_line, by_message = index_open(open_issues, "rule")

    applied = skipped = 0
    for entry in saved["issues"]:
        found, how = match(entry, by_line, by_message)
        if not found:
            # Usually means the code was changed so the finding no longer
            # fires — nothing to dismiss, and worth knowing about.
            print(f"  no longer raised: {entry['rule']} {entry['path']}:{entry['line']}")
            skipped += 1
            continue
        transition = TRANSITION_FOR.get(entry["resolution"])
        if not transition:
            print(f"  unknown resolution {entry['resolution']}, skipping")
            skipped += 1
            continue
        print(f"  {transition:14s} {entry['rule']:22s} {entry['path']}:{found.get('line')} (by {how})")
        if not dry_run:
            if entry["reason"]:
                call("api/issues/add_comment",
                     {"issue": found["key"], "text": entry["reason"]})
            call("api/issues/do_transition",
                 {"issue": found["key"], "transition": transition})
        applied += 1

    hot_open = list(paged("api/hotspots/search", "hotspots",
                          projectKey=PROJECT_KEY, status="TO_REVIEW"))
    h_by_line, h_by_message = index_open(hot_open, "ruleKey")

    h_applied = h_skipped = 0
    for entry in saved["hotspots"]:
        found, how = match(entry, h_by_line, h_by_message)
        if not found:
            # SonarQube reclassifies rules between versions — java:S2077 moved
            # from Security Hotspot to Vulnerability in 26.8. A review recorded
            # as "safe" is the same judgement as "false positive" on an issue,
            # so honour it rather than making someone decide twice.
            issue, how = match(entry, by_line, by_message)
            if issue:
                print(f"  falsepositive  {entry['rule']:22s} {entry['path']}:{issue.get('line')} "
                      f"(by {how}; now an issue, not a hotspot)")
                if not dry_run:
                    if entry["reason"]:
                        call("api/issues/add_comment",
                             {"issue": issue["key"], "text": entry["reason"]})
                    call("api/issues/do_transition",
                         {"issue": issue["key"], "transition": "falsepositive"})
                h_applied += 1
                continue
            print(f"  no longer raised: {entry['rule']} {entry['path']}:{entry['line']}")
            h_skipped += 1
            continue
        print(f"  {entry['resolution']:14s} {entry['rule']:22s} {entry['path']}:{found.get('line')} (by {how})")
        if not dry_run:
            call("api/hotspots/change_status", {
                "hotspot": found["key"], "status": "REVIEWED",
                "resolution": entry["resolution"], "comment": entry["reason"],
            })
        h_applied += 1

    verb = "would apply" if dry_run else "applied"
    print(f"{verb} {applied} dismissals ({skipped} not raised) and "
          f"{h_applied} hotspot reviews ({h_skipped} not raised)")


if len(sys.argv) < 2 or sys.argv[1] not in ("export", "replay"):
    sys.exit(__doc__)
if sys.argv[1] == "export":
    do_export()
else:
    do_replay("--dry-run" in sys.argv)
