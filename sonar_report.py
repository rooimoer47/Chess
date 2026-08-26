"""Turn a SonarQube analysis into a triage list.

Called by sonar-scan.sh once the server has finished processing an analysis;
reads the raw API responses it dropped in OUT_DIR and writes findings.md
(a checklist to work through) and findings.json (the same data, for tooling).
"""

import json
import os
import urllib.parse
from collections import defaultdict
from datetime import datetime, timezone

SONAR_URL = os.environ["SONAR_URL"]
PROJECT_KEY = os.environ["PROJECT_KEY"]
OUT_DIR = os.environ["OUT_DIR"]

SEVERITY_ORDER = ["BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO"]


def severity_rank(sev):
    return SEVERITY_ORDER.index(sev) if sev in SEVERITY_ORDER else len(SEVERITY_ORDER)


def load_issues():
    """Read the paged api/issues/search responses, one JSON object per line."""
    issues, rule_names = [], {}
    with open(f"{OUT_DIR}/issues-pages.jsonl") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            page = json.loads(line)
            issues.extend(page["issues"])
            for rule in page.get("rules", []):
                rule_names[rule["key"]] = rule["name"]
    return issues, rule_names


def relative_path(component):
    """"proj:key:src/main/Foo.java" -> "src/main/Foo.java"."""
    return component.split(":")[-1]


def issue_link(key):
    return (
        f"{SONAR_URL}/project/issues?id={urllib.parse.quote(PROJECT_KEY)}"
        f"&issues={key}&open={key}"
    )


def rule_link(rule):
    return f"{SONAR_URL}/coding_rules?open={urllib.parse.quote(rule)}&rule_key={urllib.parse.quote(rule)}"


def location(issue):
    line = issue.get("line")
    path = relative_path(issue["component"])
    return f"{path}:{line}" if line else path


def main():
    issues, rule_names = load_issues()
    hotspots = json.load(open(f"{OUT_DIR}/hotspots.json"))["hotspots"]

    bugs = [i for i in issues if i.get("type") == "BUG"]
    vulns = [i for i in issues if i.get("type") == "VULNERABILITY"]
    smells = [i for i in issues if i.get("type") == "CODE_SMELL"]

    by_rule = defaultdict(list)
    for issue in smells:
        by_rule[issue["rule"]].append(issue)
    # Worst severity first, then the rules with the most hits — the cheapest
    # triage decisions ("is this rule worth honouring at all?") come first.
    rule_groups = sorted(
        by_rule.items(),
        key=lambda kv: (severity_rank(kv[1][0]["severity"]), -len(kv[1]), kv[0]),
    )

    out = []
    w = out.append

    w(f"# SonarQube findings — {PROJECT_KEY}")
    w("")
    w(f"Generated {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')} · "
      f"{len(issues)} open issues, {len(hotspots)} security hotspots to review")
    w("")
    w(f"[Dashboard]({SONAR_URL}/dashboard?id={urllib.parse.quote(PROJECT_KEY)})")
    w("")
    w("Each box is a *candidate* task — Sonar's rules are generic, so decide "
      "whether the concern is real for this code before acting on it.")
    w("")

    def issue_lines(items, show_rule):
        for issue in sorted(items, key=lambda i: (severity_rank(i["severity"]), location(i))):
            prefix = f"`{issue['rule']}` " if show_rule else ""
            w(f"- [ ] {prefix}**{issue['severity']}** `{location(issue)}` — "
              f"{issue['message']} ([open]({issue_link(issue['key'])}))")
        w("")

    if bugs:
        w(f"## Bugs ({len(bugs)})")
        w("")
        issue_lines(bugs, show_rule=True)

    if vulns:
        w(f"## Vulnerabilities ({len(vulns)})")
        w("")
        issue_lines(vulns, show_rule=True)

    if hotspots:
        w(f"## Security hotspots ({len(hotspots)})")
        w("")
        w("Hotspots are not defects — each one asks whether the surrounding "
          "usage is safe. Reviewing means answering that question.")
        w("")
        for h in sorted(hotspots, key=lambda h: (h["ruleKey"], relative_path(h["component"]))):
            line = h.get("line")
            path = relative_path(h["component"])
            where = f"{path}:{line}" if line else path
            w(f"- [ ] `{h['ruleKey']}` **{h.get('vulnerabilityProbability', '')}** "
              f"`{where}` — {h['message']}")
        w("")

    if rule_groups:
        w(f"## Code smells ({len(smells)})")
        w("")
        w("Grouped by rule: one judgement about the rule usually settles every "
          "hit under it.")
        w("")
        for rule, items in rule_groups:
            name = rule_names.get(rule, "")
            w(f"### {rule} — {name} ({len(items)}, {items[0]['severity']})")
            w("")
            w(f"[Rule description]({rule_link(rule)})")
            w("")
            for issue in sorted(items, key=lambda i: location(i)):
                w(f"- [ ] `{location(issue)}` — {issue['message']} "
                  f"([open]({issue_link(issue['key'])}))")
            w("")

    with open(f"{OUT_DIR}/findings.md", "w") as fh:
        fh.write("\n".join(out))

    machine = {
        "project": PROJECT_KEY,
        "generated": datetime.now(timezone.utc).isoformat(),
        "issues": [
            {
                "key": i["key"],
                "rule": i["rule"],
                "ruleName": rule_names.get(i["rule"], ""),
                "severity": i["severity"],
                "type": i.get("type"),
                "path": relative_path(i["component"]),
                "line": i.get("line"),
                "message": i["message"],
                "effort": i.get("effort"),
                "url": issue_link(i["key"]),
            }
            for i in sorted(issues, key=lambda i: (severity_rank(i["severity"]), i["rule"]))
        ],
        "hotspots": [
            {
                "key": h["key"],
                "rule": h["ruleKey"],
                "probability": h.get("vulnerabilityProbability"),
                "path": relative_path(h["component"]),
                "line": h.get("line"),
                "message": h["message"],
            }
            for h in hotspots
        ],
    }
    with open(f"{OUT_DIR}/findings.json", "w") as fh:
        json.dump(machine, fh, indent=2)

    print(f"{len(bugs)} bugs, {len(vulns)} vulnerabilities, {len(smells)} code smells "
          f"across {len(rule_groups)} rules, {len(hotspots)} hotspots")


main()
