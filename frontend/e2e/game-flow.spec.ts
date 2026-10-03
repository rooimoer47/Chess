import { test, expect, type Page } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';
import { setupMatchedHumans, play, type MatchedPair } from './humans';

/**
 * How a game is played and ends, through the real UI and server: results both players see, what
 * gets recorded, and getting back into a game. Two human players are used (not a bot) so the move
 * sequences are deterministic. Player A is always White.
 */

async function withWhiteAndBlack(
  browser: Parameters<typeof setupMatchedHumans>[0],
  body: (white: Page, black: Page, pair: MatchedPair) => Promise<void>,
): Promise<void> {
  const pair = await setupMatchedHumans(browser, 'Always White', 'Always Black');
  try {
    await body(pair.pageA, pair.pageB, pair);
  } finally {
    await pair.contextA.close();
    await pair.contextB.close();
  }
}

async function rating(page: Page, username: string): Promise<number> {
  const res = await page.request.get(`/api/users/${encodeURIComponent(username)}/elo`);
  return (await res.json() as { elo: number }).elo;
}

test('checkmate ends the game for both players, rates it, and records the result', async ({ browser }) => {
  await withWhiteAndBlack(browser, async (white, black, pair) => {
    // Fool's mate.
    await play(white, 'f2', 'f3');
    await play(black, 'e7', 'e5');
    await play(white, 'g2', 'g4');
    await play(black, 'd8', 'h4');

    await expect(white.locator('.status-message.game-over')).toHaveText('Checkmate! Black wins!');
    await expect(black.locator('.status-message.game-over')).toHaveText('Checkmate! Black wins!');
    await expect(white.locator('.turn-indicator')).toHaveText('—');

    // Ratings are updated in the background after the game ends.
    await expect.poll(() => rating(black, pair.usernameB)).toBeGreaterThan(1000);
    const blackRating = await rating(black, pair.usernameB);
    expect(await rating(white, pair.usernameA)).toBeLessThan(1000);

    await black.getByRole('button', { name: 'Back to Lobby' }).click();
    await expect(black.locator('.lobby-elo')).toContainText(`ELO ${blackRating}`);
    await black.getByRole('button', { name: 'My Games' }).click();
    await expect(black.locator('.history-table tbody tr').first()).toContainText('Win');

    await white.goto('/history');
    await expect(white.locator('.history-table tbody tr').first()).toContainText('Loss');
  });
});

test('a pawn promotes to the piece chosen in the dialog, on both boards', async ({ browser }) => {
  await withWhiteAndBlack(browser, async (white, black) => {
    for (const [page, from, to] of [
      [white, 'a2', 'a4'], [black, 'b7', 'b5'], [white, 'a4', 'b5'], [black, 'a7', 'a6'],
      [white, 'b5', 'a6'], [black, 'c8', 'b7'], [white, 'a6', 'b7'], [black, 'b8', 'c6'],
    ] as const) {
      await play(page, from, to);
    }

    await white.getByTestId('square-b7').click();
    await white.getByTestId('square-a8').click();
    await expect(white.locator('.promotion-dialog')).toBeVisible();
    await expect(black.locator('.promotion-dialog')).toHaveCount(0);
    // A knight rather than the default queen, so the choice itself is checked.
    await white.getByRole('button', { name: 'KNIGHT' }).click();

    await expect(white.locator('.promotion-dialog')).toHaveCount(0);
    await expect(white.getByTestId('square-a8').locator('img')).toHaveAttribute('alt', 'WHITE KNIGHT');
    await expect(black.getByTestId('square-a8').locator('img')).toHaveAttribute('alt', 'WHITE KNIGHT');
    await expect(black.locator('.turn-indicator')).toHaveText('Your turn');
  });
});

test('a draw offer can be declined, and an accepted one ends the game as a draw', async ({ browser }) => {
  await withWhiteAndBlack(browser, async (white, black) => {
    await white.getByRole('button', { name: 'Offer Draw' }).click();
    await expect(white.getByRole('button', { name: 'Draw offered…' })).toBeDisabled();
    await expect(black.locator('.draw-offer-bar')).toContainText('Opponent offers a draw');

    await black.getByRole('button', { name: 'Decline' }).click();
    await expect(black.locator('.draw-offer-bar')).toHaveCount(0);
    await expect(white.locator('.status-message')).toHaveText('Draw offer declined.');
    await expect(white.getByRole('button', { name: 'Offer Draw' })).toBeEnabled();

    // Play goes on after a declined offer.
    await play(white, 'e2', 'e4');

    await white.getByRole('button', { name: 'Offer Draw' }).click();
    await black.getByRole('button', { name: 'Accept' }).click();

    await expect(white.locator('.status-message.game-over')).toHaveText('Draw by agreement!');
    await expect(black.locator('.status-message.game-over')).toHaveText('Draw by agreement!');
    await white.goto('/history');
    await expect(white.locator('.history-table tbody tr').first()).toContainText('Draw');
  });
});

test('an accepted rematch starts a new game with colours swapped', async ({ browser }) => {
  await withWhiteAndBlack(browser, async (white, black) => {
    await white.getByRole('button', { name: 'Resign' }).click();
    await expect(black.locator('.status-message.game-over')).toContainText('resigned');

    await white.getByRole('button', { name: 'Rematch' }).click();
    await expect(white.locator('.rematch-status')).toHaveText('Waiting for opponent…');
    await expect(black.locator('.rematch-status')).toHaveText('Opponent wants a rematch.');
    await black.getByRole('button', { name: 'Accept' }).click();

    // The old White is Black now, so it is the other player's turn.
    await expect(white.locator('.turn-indicator')).toHaveText("Opponent's turn");
    await expect(black.locator('.turn-indicator')).toHaveText('Your turn');
    await play(black, 'e2', 'e4');
    await expect(white.getByTestId('square-e4').locator('img')).toHaveAttribute('alt', 'WHITE PAWN');
  });
});

test('a declined rematch tells the player who asked', async ({ browser }) => {
  await withWhiteAndBlack(browser, async (white, black) => {
    await white.getByRole('button', { name: 'Resign' }).click();
    await white.getByRole('button', { name: 'Rematch' }).click();
    await black.getByRole('button', { name: 'Decline' }).click();

    await expect(black).toHaveURL(/\/lobby$/);
    await expect(white.locator('.rematch-status')).toHaveText('Opponent did not want a rematch.');
    await white.getByRole('button', { name: 'Back to Lobby' }).click();
    await expect(white).toHaveURL(/\/lobby$/);
  });
});

test('reloading the page mid-game puts the player back into the same game', async ({ browser }) => {
  await withWhiteAndBlack(browser, async (white, black) => {
    await play(white, 'e2', 'e4');

    await white.reload();

    await expect(white.getByTestId('square-e4').locator('img')).toHaveAttribute('alt', 'WHITE PAWN', { timeout: 15_000 });
    await expect(white.locator('.turn-indicator')).toHaveText("Opponent's turn");
    // The game carries on in both directions after the reload.
    await play(black, 'e7', 'e5');
    await expect(white.getByTestId('square-e5').locator('img')).toHaveAttribute('alt', 'BLACK PAWN');
    await play(white, 'g1', 'f3');
    await expect(black.getByTestId('square-f3').locator('img')).toHaveAttribute('alt', 'WHITE KNIGHT');
  });
});

test('a bot game left for the lobby can be resumed from there', async ({ page }) => {
  const username = uniqueUsername('resume');
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

  await page.goto('/lobby');
  await page.getByRole('button', { name: 'Resume' }).click();

  await expect(page.getByTestId('square-e4').locator('img')).toHaveAttribute('alt', 'WHITE PAWN', { timeout: 15_000 });
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn');
});
