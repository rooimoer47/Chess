# End-to-end tests (Playwright)

Browser-driven tests covering the real login/register flow, lobby navigation,
and a full game against a bot (legal move, illegal move rejection, resign).
Each test creates its own throwaway user (`e2e_<timestamp>_<random>`), so the
suite can be run repeatedly against the same database without cleanup.

## Running (one command)

From the repo root:

```bash
./run-e2e-tests.sh
```

This starts the Postgres container if it isn't already running, starts the
backend (`run-backend.sh`) if it isn't already up on `:8080`, runs the suite,
and stops only what it started (the database is left running so repeat runs
are fast — start it again is a no-op). Anything it started itself is torn
down even if the tests fail or the script is interrupted.

`host-local.sh` (which builds the whole app into a single Docker image on
port 8080) and `get-local-url.sh` (which opens that instance via the host's
public IP) aren't used here — they're for running/sharing the built app, not
for iterating on tests. If you want to point the suite at that stack instead,
change `baseURL` in `playwright.config.ts` to `http://localhost:8080` and
drop the `webServer` block (the built app is served directly by Spring Boot,
no separate dev server needed).

## Running manually / other options

If you're already keeping the database and backend running yourself (e.g.
during active backend development), skip the script and just run:

```bash
cd frontend
npm run e2e         # headless, once
npm run e2e:ui       # interactive UI mode — step through tests, inspect DOM
npx playwright test e2e/game-vs-bot.spec.ts   # a single file
npx playwright show-report                      # view the HTML report from the last run
```

## Layout

- `fixtures.ts` — shared helpers: unique usernames, registering a user via the
  API (fast setup, skips the form), login helper, lobby assertion.
- `auth.spec.ts` — register/login/logout, both happy path and rejections
  (duplicate username, bad format, wrong password, unknown user).
- `lobby.spec.ts` — ELO badge display, navigation to History/Profile and
  back, and the matchmaking queue's waiting state when no opponent is
  available.
- `game-vs-bot.spec.ts` — starts a game against the "Random" bot, plays a
  legal opening move and confirms the bot replies, attempts an illegal move
  and confirms nothing changes, and resigns.

Board squares carry a `data-testid="square-<file><rank>"` attribute (e.g.
`square-e2`) added to `Square.tsx` specifically so moves can be expressed in
algebraic notation instead of brittle DOM positions.
