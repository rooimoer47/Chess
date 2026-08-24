import { test, expect, type Page } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';
import {
  STANDARD_BACK_RANK, selectChess960, readRank, expectLegalBackRank, playAnyLegalMove, findImmediateCastle,
} from './chess960';

/**
 * Registers a fresh user and drops them into a Chess960 game against the
 * Random bot, playing White so the first move is theirs.
 *
 * A new user per game on purpose: the lobby offers to *resume* an active bot
 * game of the same type rather than dealing a new position, which would defeat
 * the point of any test that wants a freshly generated back rank.
 */
async function startBotGame(page: Page, chess960: boolean): Promise<void> {
  const username = uniqueUsername(chess960 ? 'c960' : 'cstd');
  await registerViaApi(page, username);
  await page.goto('/');
  await expectLobby(page, username);

  if (chess960) await selectChess960(page);
  await page.locator('.color-pref-row label', { hasText: 'Always White' }).click();
  await page.locator('.opponent-cards label', { hasText: 'Random' }).click();

  await page.getByRole('button', { name: 'Start Game' }).click();
  await expect(page.locator('.board')).toBeVisible({ timeout: 15_000 });
}

const startBotGame960 = (page: Page) => startBotGame(page, true);

test('control: a standard game still reads back as RNBQKBNR', async ({ page }) => {
  // Pins `readRank` against a position whose answer is known, so the Chess960
  // assertions below can't all be passing off a reader that has, say, its files
  // reversed — a mirrored back rank satisfies every 960 invariant too.
  await startBotGame(page, false);
  expect(await readRank(page, 1)).toBe(STANDARD_BACK_RANK);
  expect(await readRank(page, 8)).toBe(STANDARD_BACK_RANK);
});

test('Chess960 deals a legal, randomised back rank mirrored by both colours', async ({ page }) => {
  // The generator can legitimately produce the standard arrangement (1 in 960),
  // so "randomised" is asserted across several games rather than off a single
  // one — three standard deals in a row is a ~1-in-a-billion event.
  const dealt: string[] = [];

  for (let i = 0; i < 3; i++) {
    await startBotGame960(page);

    const white = await readRank(page, 1);
    expectLegalBackRank(white);

    // Black's back rank mirrors White's file for file, and the pawns are
    // untouched by the variant — both are what makes the position playable.
    expect(await readRank(page, 8), 'black back rank must mirror white').toBe(white);
    expect(await readRank(page, 2), 'white pawns still fill rank 2').toBe('PPPPPPPP');
    expect(await readRank(page, 7), 'black pawns still fill rank 7').toBe('PPPPPPPP');

    dealt.push(white);
  }

  expect(dealt.some(rank => rank !== STANDARD_BACK_RANK),
    `all ${dealt.length} Chess960 games dealt the standard back rank (${dealt.join(', ')}) — the position is not being randomised`,
  ).toBe(true);
});

test('the bot keeps replying move after move in a Chess960 game', async ({ page }) => {
  // The regression guard for the opening-book hang: the book's entries are
  // hardcoded to standard-chess squares, so consulting it in a 960 position can
  // hand back a move that is illegal there. That used to be swallowed silently
  // and the game would sit forever on the bot's turn. Playing several moves
  // exercises the real WebSocket/bot turn-taking loop, not just the strategy.
  await startBotGame960(page);

  let played = 0;
  for (let move = 1; move <= 4; move++) {
    // A random bot can end the game early (checkmate, or a stalemate against a
    // cornered king). That is a legitimate finish, not the hang being guarded
    // against — stop asking for another move rather than failing.
    if (await page.locator('.status-message.game-over').count() > 0) break;

    await expect(page.locator('.turn-indicator'), `stuck before move ${move}`).toHaveText('Your turn');
    await playAnyLegalMove(page, 'WHITE');
    played++;

    // Either the turn comes back, or the bot's reply ended the game. Both mean
    // the bot moved; only a hang leaves neither.
    await expect
      .poll(async () => {
        if (await page.locator('.status-message.game-over').count() > 0) return 'GAME OVER';
        return (await page.locator('.turn-indicator').textContent())?.trim() ?? '';
      }, { timeout: 10_000, message: `bot never replied to move ${move}` })
      .toMatch(/^(Your turn|GAME OVER)$/);
  }

  expect(played, 'the bot stopped responding before a single move was completed').toBeGreaterThan(0);
});

test('castling in Chess960 sends the king to c/g and the rook to d/f', async ({ page }) => {
  // The one Chess960 mechanic the browser can reach but unit tests cannot: the
  // king-onto-rook gesture. Rather than pin a back rank (which would mean a
  // test-only handshake param in production code), keep dealing until one of
  // the ~17% of positions that allow an immediate castle turns up. Expected
  // cost is ~6 deals; 40 attempts makes a false failure about 1 in 1700.
  const MAX_DEALS = 40;
  let castle: Awaited<ReturnType<typeof findImmediateCastle>> = null;
  let deals = 0;

  while (castle === null && deals < MAX_DEALS) {
    await startBotGame960(page);
    deals++;
    castle = await findImmediateCastle(page);
  }

  expect(castle, `no castleable position in ${MAX_DEALS} deals`).not.toBeNull();
  const { king, rook, side } = castle!;

  await page.getByTestId(`square-${rook}`).click();

  // FIDE Chess960: the king always finishes on the c- or g-file and the rook
  // on the d- or f-file, wherever the two of them started.
  const kingDest = side === 'kingside' ? 'g1' : 'c1';
  const rookDest = side === 'kingside' ? 'f1' : 'd1';

  await expect(page.getByTestId(`square-${kingDest}`).locator('img'),
    `${side} castle from ${king}: king must land on ${kingDest}`).toHaveAttribute('alt', 'WHITE KING');
  await expect(page.getByTestId(`square-${rookDest}`).locator('img'),
    `${side} castle from ${rook}: rook must land on ${rookDest}`).toHaveAttribute('alt', 'WHITE ROOK');

  // Vacated squares really are vacated — catches a castle that copies pieces
  // rather than moving them, which the destination checks alone would miss.
  for (const square of [king, rook]) {
    if (square === kingDest || square === rookDest) continue;
    await expect(page.getByTestId(`square-${square}`).locator('img'),
      `${square} must be empty after castling`).toHaveCount(0);
  }

  // And it was a real move, not a local board edit: the turn passed over.
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn', { timeout: 10_000 });
});

test('a finished Chess960 game replays from its randomised starting position', async ({ page }) => {
  // A replay that wrongly rebuilt from the standard position would still match
  // if this game happened to be dealt the standard back rank, so keep dealing
  // until the position is one that can actually tell the two apart.
  let dealt = STANDARD_BACK_RANK;
  for (let attempt = 0; attempt < 3 && dealt === STANDARD_BACK_RANK; attempt++) {
    await startBotGame960(page);
    dealt = await readRank(page, 1);
  }
  expect(dealt, 'never dealt a non-standard position to replay').not.toBe(STANDARD_BACK_RANK);
  expectLegalBackRank(dealt);

  await playAnyLegalMove(page, 'WHITE');
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn', { timeout: 10_000 });

  await page.getByRole('button', { name: 'Resign' }).click();
  await expect(page.locator('.status-message.game-over')).toContainText('resigned');
  await page.getByRole('button', { name: 'Back to Lobby' }).click();
  await expect(page).toHaveURL(/\/lobby$/);

  // The history list follows the same variant toggle as the lobby, so the game
  // just played is the one listed under Chessnuts960.
  await page.getByRole('button', { name: 'My Games' }).click();
  await expect(page.getByRole('heading', { name: 'My Chessnuts960 Games' })).toBeVisible();
  await page.getByRole('button', { name: 'Replay' }).first().click();

  // Move 0 is the starting position, reconstructed server-side from the stored
  // starting_position column — this is the end-to-end check that the dealt back
  // rank was persisted rather than replaced by the standard one on replay.
  await expect(page.locator('.replay-move-counter')).toContainText('Move 0');
  await expect(page.locator('.board')).toBeVisible();
  expect(await readRank(page, 1), 'replay must reconstruct the position the game was actually played from').toBe(dealt);
});
