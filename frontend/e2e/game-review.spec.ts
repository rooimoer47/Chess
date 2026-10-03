import { test, expect, type Page } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby, logoutCookie } from './fixtures';

// Squares as the server names them: row 0 = rank 1, col 0 = the a-file.
const square = (row: number, col: number) => `${String.fromCodePoint(97 + col)}${row + 1}`;

interface StoredMove { fromRow: number; fromCol: number; toRow: number; toCol: number }

/**
 * Plays 1.e4 against the Random bot, waits for its reply, resigns, and opens the replay.
 * Returns the game id and the bot's reply as squares.
 */
async function playShortGameAndOpenReplay(page: Page): Promise<{ gameId: string; reply: { from: string; to: string } }> {
  const username = uniqueUsername('review');
  await registerViaApi(page, username);
  await page.goto('/');
  await expectLobby(page, username);
  await page.locator('.color-pref-row label', { hasText: 'Always White' }).click();
  await page.locator('.opponent-cards label', { hasText: 'Random' }).click();
  await page.getByRole('button', { name: 'Start Game' }).click();
  await expect(page.locator('.board')).toBeVisible({ timeout: 15_000 });

  await page.getByTestId('square-e2').click();
  await page.getByTestId('square-e4').click();
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn', { timeout: 10_000 });
  await page.getByRole('button', { name: 'Resign' }).click();
  await expect(page.locator('.status-message.game-over')).toContainText('resigned');

  await page.goto('/history');
  await page.getByRole('button', { name: 'Replay' }).first().click();
  await expect(page).toHaveURL(/\/history\/\d+$/);
  const gameId = page.url().split('/').pop()!;

  const moves = await (await page.request.get(`/api/games/${gameId}/moves`)).json() as StoredMove[];
  const bot = moves[1];
  return { gameId, reply: { from: square(bot.fromRow, bot.fromCol), to: square(bot.toRow, bot.toCol) } };
}

test('a replay is only visible to the players of the game', async ({ page }) => {
  const { gameId } = await playShortGameAndOpenReplay(page);
  await expect(page.locator('.board')).toBeVisible();

  await logoutCookie(page);
  await registerViaApi(page, uniqueUsername('stranger'));
  await page.goto(`/history/${gameId}`);

  await expect(page.getByText('Failed to load game')).toBeVisible();
  expect((await page.request.get(`/api/games/${gameId}/moves`)).status()).toBe(404);
  expect((await page.request.get(`/api/games/${gameId}/analysis`)).status()).toBe(404);
});

test('a review shows labels, arrows and the engine move', async ({ page }) => {
  const { gameId, reply } = await playShortGameAndOpenReplay(page);
  // The engine "prefers" a different reply from the one the bot played.
  const best = reply.from === 'e7' && reply.to === 'e5'
    ? { san: 'd5', uci: 'd7d5', from: 'd7', to: 'd5' }
    : { san: 'e5', uci: 'e7e5', from: 'e7', to: 'e5' };

  await page.route(`**/api/games/${gameId}/analysis`, route => route.fulfill({
    json: {
      status: 'DONE', positionsTotal: 3, positionsDone: 3,
      evals: [{ cp: 30, mate: null }, { cp: 35, mate: null }, { cp: 400, mate: null }],
      moves: [
        { ply: 1, san: 'e4', uci: 'e2e4', from: 'e2', to: 'e4', classification: 'BEST',
          bestSan: 'e4', bestUci: 'e2e4', bestFrom: 'e2', bestTo: 'e4', line: ['e4', 'c5'], comment: null },
        { ply: 2, san: 'Reply', uci: `${reply.from}${reply.to}`, from: reply.from, to: reply.to, classification: 'BLUNDER',
          bestSan: best.san, bestUci: best.uci, bestFrom: best.from, bestTo: best.to, line: [best.san, 'Nf3'], comment: null },
      ],
    },
  }));
  await page.reload();

  const list = page.getByTestId('move-list');
  await expect(list).toBeVisible();
  await expect(page.getByTestId('move-2')).toHaveText('Reply??');

  // Jump to the blunder from the move list.
  await page.getByTestId('move-2').click();
  await expect(page.locator('.replay-move-counter')).toHaveText('Move 2 / 2');
  await expect(page.getByTestId('review-panel')).toContainText('Blunder');
  await expect(page.getByTestId('review-panel')).toContainText(`Best was ${best.san}`);
  await expect(page.getByTestId('arrow-played')).toHaveAttribute('data-from', reply.from);
  await expect(page.getByTestId('arrow-played')).toHaveAttribute('data-to', reply.to);
  await expect(page.getByTestId('arrow-best')).toHaveAttribute('data-to', best.to);
  await expect(page.getByTestId('eval-bar')).toHaveAttribute('title', '+4.0');

  // Back one move: the engine's move was played, so there is no second arrow.
  await page.keyboard.press('ArrowLeft');
  await expect(page.locator('.replay-move-counter')).toHaveText('Move 1 / 2');
  await expect(page.getByTestId('review-panel')).toContainText('Best move');
  await expect(page.getByTestId('arrow-played')).toHaveAttribute('data-to', 'e4');
  await expect(page.getByTestId('arrow-best')).toHaveCount(0);
});

test('an unanalysed game can be queued from the replay page', async ({ page }) => {
  const { gameId } = await playShortGameAndOpenReplay(page);
  let queued = false;
  await page.route(`**/api/games/${gameId}/analysis`, route => {
    if (route.request().method() === 'POST') {
      queued = true;
      return route.fulfill({ status: 202 });
    }
    return route.fulfill({
      json: queued
        ? { status: 'ENGINE', positionsTotal: 3, positionsDone: 1, evals: [], moves: [] }
        : { status: 'NONE', positionsTotal: 0, positionsDone: 0, evals: [], moves: [] },
    });
  });
  await page.reload();

  await page.getByTestId('analyse-btn').click();

  await expect(page.getByTestId('review-status')).toHaveText('Analysing… 1 / 3 positions');
  expect(queued).toBe(true);
});

test('with Stockfish installed, a finished game is reviewed for real', async ({ page }) => {
  test.skip(!process.env.STOCKFISH_PATH, 'set STOCKFISH_PATH for the backend and this run to include it');
  await playShortGameAndOpenReplay(page);

  // The game was queued when it ended; the page polls until the review is done.
  await expect(page.getByTestId('move-list')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId('move-1')).toContainText('e4');
  await page.getByTestId('move-1').click();
  await expect(page.getByTestId('arrow-played')).toHaveAttribute('data-from', 'e2');
  await expect(page.getByTestId('eval-bar')).toBeVisible();
});
