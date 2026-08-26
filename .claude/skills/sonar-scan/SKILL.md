---
name: sonar-scan
description: Run the local SonarQube analysis over this project and turn the findings into a triaged task list — validating each finding against the actual code, dismissing the ones that don't apply, and fixing the ones that do. Use when asked to run Sonar, do a SonarQube scan, check static-analysis or code-quality findings, or work through the Sonar backlog.
---

# SonarQube scan and triage

`./sonar-scan.sh` handles the mechanics: it starts the `sonarqube` container if
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
- **Cognitive complexity** (`S3776`) is already suppressed for Java in
  `pom.xml`; the TypeScript equivalent is not. Treat them consistently — either
  raise both or suppress both, and say which you chose.
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

## Server upkeep

The container runs an **embedded H2 database** (`sonar.mv.db` in the data
volume). Everything worth keeping is in there — analysis history, the Chess
quality profile, and every recorded dismissal — and nothing else has a copy.

- `./sonar-scan.sh --backup` stops the server, tars the data volume, restarts.
  Roughly 330 MB and 15 seconds.
- `SONAR_IMAGE` in the script pins the version. Upgrading means editing that
  line, but **take a backup first**: SonarQube does not support upgrading with
  the embedded database, so an upgrade can fail mid-migration and leave the
  H2 file unusable.
- The current container predates the script and sits on **anonymous** volumes.
  A plain `docker rm` + `docker run` will silently attach fresh empty ones
  rather than reconnecting. To recreate it, either mount the existing volumes
  by their ID (`docker inspect sonarqube --format '{{json .Mounts}}'`) or
  migrate to the named volumes the script creates. Never recreate it without
  checking which volumes the new container will get.
