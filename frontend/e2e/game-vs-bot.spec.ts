import { test, expect } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';

test.beforeEach(async ({ page }) => {
  const username = uniqueUsername('bot');
  await registerViaApi(page, username);
  await page.goto('/');
  await expectLobby(page, username);

  // Force White so the opening move is deterministic instead of a coin flip.
  // The underlying radio inputs are visually hidden behind styled labels, so
  // click the label (also sidesteps the ambiguity of "Random" appearing in
  // both the opponent list and the color-preference list).
  await page.locator('.color-pref-row label', { hasText: 'Always White' }).click();
  // "Random" bot moves instantly and unpredictably — good for a fast, simple opponent.
  await page.locator('.opponent-cards label', { hasText: 'Random' }).click();

  await page.getByRole('button', { name: 'Start Game' }).click();
  await expect(page.locator('.board')).toBeVisible({ timeout: 15_000 });
});

test('happy path: a legal move is played and the bot replies', async ({ page }) => {
  await expect(page.getByTestId('square-e2').locator('img')).toHaveAttribute('alt', 'WHITE PAWN');
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn');

  await page.getByTestId('square-e2').click();
  await page.getByTestId('square-e4').click();

  await expect(page.getByTestId('square-e4').locator('img')).toHaveAttribute('alt', 'WHITE PAWN');
  await expect(page.getByTestId('square-e2').locator('img')).toHaveCount(0);

  // Confirm the bot actually replied and turn came back to the player —
  // this exercises the full move → server → opponent-move → update round trip.
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn', { timeout: 10_000 });
});

test('an illegal move is rejected and the board does not change', async ({ page }) => {
  // A pawn cannot advance three squares — this move is not in the legal-move list.
  await page.getByTestId('square-a2').click();
  await page.getByTestId('square-a5').click();

  await expect(page.getByTestId('square-a2').locator('img')).toHaveAttribute('alt', 'WHITE PAWN');
  await expect(page.getByTestId('square-a5').locator('img')).toHaveCount(0);
  await expect(page.locator('.turn-indicator')).toHaveText('Your turn');
});

test('resigning ends the game and returns to the lobby', async ({ page }) => {
  await page.getByRole('button', { name: 'Resign' }).click();

  await expect(page.locator('.status-message.game-over')).toContainText('resigned');
  await expect(page.getByRole('button', { name: 'Back to Lobby' })).toBeVisible();

  await page.getByRole('button', { name: 'Back to Lobby' }).click();
  await expect(page).toHaveURL(/\/lobby$/);
});
