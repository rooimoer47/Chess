---
name: sonar-scan
description: Run the local SonarQube analysis over this project and turn the findings into a triaged task list — validating each finding against the actual code, dismissing the ones that don't apply, and fixing the ones that do. Use when asked to run Sonar, do a SonarQube scan, check static-analysis or code-quality findings, or work through the Sonar backlog.
---

# SonarQube scan and triage

`./sonar-scan.sh` handles the mechanics: it brings up the SonarQube stack if
needed, compiles, analyses both the Java and the frontend TypeScript, waits for
the server to finish processing, and writes `.sonar-report/findings.md`
(a checklist) and `findings.json` (the same data, easier to script against).

The script is the easy half. The work is deciding which findings are real.

## Workflow

1. **Scan.** `./sonar-scan.sh` (or `--report-only` to re-read the last analysis
   without re-scanning — much faster when you are mid-triage).

2. **Triage in priority order**, not in report order:
   - **Bugs** first — Sonar's bug rules have a low false-positive rate, but they
     do fire on dead code that is harmless. Read the code before believing it.
   - **Security hotspots** next. A hotspot is a *question* ("is this usage
     safe?"), not a defect. Answer it by reading the surrounding code.
   - **Code smells** grouped by rule. One judgement usually settles every hit
     under a rule, so decide at the rule level first and only then look at
     individual instances.

3. **For each finding, reach one of three verdicts:**
   - *Valid, worth fixing* → it becomes a task. Fix it, or list it.
   - *Valid, not worth fixing here* → dismiss with `accept` and a reason.
   - *Does not apply to this code* → dismiss with `falsepositive` and a reason.

4. **Record every dismissal on the server** so it never returns:
   ```bash
   ./sonar-scan.sh --dismiss <issueKey> falsepositive "why it does not apply"
   ./sonar-scan.sh --dismiss <issueKey> accept "why it is not worth fixing"
   ./sonar-scan.sh --safe <hotspotKey> "why this usage is safe"
   ```
   Issue and hotspot keys are in `findings.json`. Always pass a reason — the
   next person to see the dismissal, quite possibly you, needs to know it was a
   judgement rather than an oversight.

   Never dismiss a finding you have not actually read the code for. A dismissal
   is permanent in a way a fix is not.

5. **Report back** with the tasks that survived triage, and say plainly what was
   dismissed and why. Do not present a raw dump of the report as if it were a
   task list — that is the part the user is asking to have done for them.

## Judging findings in this project

- **Test code** is held to a different standard than production code. Empty
  methods and duplicated literals in tests are usually deliberate.
- **Size-threshold rules are suppressed by decision, not oversight.** `S3776`
  (cognitive complexity) is off for every language via `*:S3776` in `pom.xml`,
  and `S2004` (nesting depth) and `S107` (parameter count) are dismissed as
  accepted. The user's position: a method's right size is set by what it does,
  not by a threshold. Dismiss new rules of this family the same way rather than
  refactoring to satisfy them.
- **Accessibility rules** on the frontend (`S1082`, `S6847`, `S6848`) fire on
  every `onClick` on a non-button. Some are real (a control a keyboard user
  cannot reach); some are backdrop or `stopPropagation` handlers that are not
  controls at all. The distinction is whether a user needs to *activate* it.
- **`java:S2077`** (dynamic SQL) fires on any concatenated query string. Check
  whether the concatenated part is user input or a hardcoded column name chosen
  by the code — the latter is safe.

## Scope

Rule configuration lives in `pom.xml` (`sonar.sources`, `sonar.tests`, the
`sonar.issue.ignore.multicriteria` entries) and in the custom **Chess** quality
profile on the server. Prefer dismissing individual findings over disabling a
rule; disable a rule only when it is wrong for the whole project, and say so.

## The IDE shows different findings from the script

Expected, and not a bug in either. The IntelliJ plugin runs **standalone**
unless it is bound to the server in Connected Mode, and standalone it:

- cannot see findings dismissed on the server;
- never reads `pom.xml`, so the `sonar.issue.ignore.multicriteria` suppressions
  (S3776) do not apply there — the IDE needs its own rule toggles;
- uses the default rule set rather than the Chess profile;
- analyses open and changed files only, so its count will not match a full scan
  even once bound.

Connected Mode fixes the first and third. The S3776 suppression is scanner-side
and has to be mirrored in the IDE's rule list per language.

**The IDE is not always wrong, though.** Two cases where it is right and the
server is blind, both seen for real:

- **Files outside `sonar.sources`.** The IDE analyses whatever is open. That is
  how a `docker:S6471` root-user finding in the `Dockerfile` surfaced while the
  server had never scanned it. If the IDE flags something in a file the server
  does not cover, decide whether the file belongs in `sonar.sources` — but note
  generated output (`frontend/playwright-report/`) should be excluded in the
  IDE instead, never added to the scan.
- **Rules missing from the Chess profile.** It carries fewer rules than the
  IDE's defaults, so `java:S5673`, `java:S9016` and `java:S2143` all fired in
  IntelliJ and nowhere else. Two were worth fixing.

So triage an IDE-only finding on its merits first, and only then ask why the
server missed it.

## Server upkeep

The server runs from `docker-compose.sonar.yml`: SonarQube on its **own
PostgreSQL**, separate from the app's `chess-db-1`. It is a development tool,
which is why it lives in its own compose file rather than the app's.

```bash
docker compose -f docker-compose.sonar.yml up -d     # sonar-scan.sh does this
./sonar-scan.sh --backup                             # pg_dump, ~6 MB
```

**Upgrading** is now supported: bump the `sonarqube:` image in the compose file
and restart. SonarQube migrates the schema itself. Take a `--backup` first
anyway. (It ran on embedded H2 until 2026-08-26, where upgrading was impossible
and the database had to be thrown away — hence the migration.)

Admin is `admin` / `Admin1234chess!`. SonarQube 26.8 enforces 12+ characters
with mixed case and a symbol, so the older `admin1234` no longer qualifies.

## Triage decisions are portable

The server's database is the only place dismissals live, so they are exported
to `sonar/triage.json`, which **is tracked in git**, alongside the Chess
quality profile in `sonar/quality-profile-java.xml`.

```bash
./sonar-scan.sh --export-triage          # after any triage session
./sonar-scan.sh --replay-triage [-n]     # onto a rebuilt server; -n is a dry run
```

Re-export after dismissing anything, or the record drifts from the server.
Replay matches on rule + file + line, falling back to rule + file + message
when lines have moved, and reports whatever it could not place.

It also handles rules being **reclassified between versions** — `java:S2077`
moved from Security Hotspot to Vulnerability in 26.8, so a saved hotspot that
no longer exists as one is matched against open issues instead and dismissed
as a false positive there. Expect more of this on future upgrades; a saved
entry that "is no longer raised" is worth a glance rather than a shrug.
