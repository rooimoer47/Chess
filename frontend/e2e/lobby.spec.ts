import { test, expect, type Page } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';

async function selectColorPreference(page: Page, label: 'Always White' | 'Always Black' | 'Random') {
  await page.locator('.color-pref-row label', { hasText: label }).click();
}

test.beforeEach(async ({ page }) => {
  const username = uniqueUsername('lobby');
  await registerViaApi(page, username);
  await page.goto('/');
  await expectLobby(page, username);
});

test('shows the ELO badge for a freshly registered account', async ({ page }) => {
  // New accounts default to 1000 and are provisional (fewer than 30 rated games).
  await expect(page.locator('.lobby-elo')).toContainText('ELO 1000?');
});

test('navigates to My Games and back', async ({ page }) => {
  await page.getByRole('button', { name: 'My Games' }).click();
  await expect(page).toHaveURL(/\/history$/);
  await expect(page.getByRole('heading', { name: 'My Games' })).toBeVisible();
  await expect(page.getByText('No completed games yet.')).toBeVisible();

  await page.getByRole('button', { name: '← Back to Lobby' }).click();
  await expect(page).toHaveURL(/\/lobby$/);
});

test('navigates to the ELO profile page and back', async ({ page }) => {
  await page.getByRole('button', { name: 'ELO Profile' }).click();
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: 'ELO Rating' })).toBeVisible();
  await expect(page.getByText('No rated games yet.')).toBeVisible();

  await page.getByRole('button', { name: '← Back to Lobby' }).click();
  await expect(page).toHaveURL(/\/lobby$/);
});

test('starting a human game with no opponent available shows a waiting state', async ({ page }) => {
  // Default opponent selection is "Human" — with nobody else queued, the
  // player is parked in the matchmaking queue (WAITING_QUEUE) instead of
  // getting a board.
  await page.getByRole('button', { name: 'Start Game' }).click();
  await expect(page).toHaveURL(/\/game$/);

  await expect(page.getByText('Searching for opponent…')).toBeVisible();
  await expect(page.locator('.board')).toHaveCount(0);
});

test('cancelling while queued for a human opponent returns to the lobby', async ({ page }) => {
  await page.getByRole('button', { name: 'Start Game' }).click();
  await expect(page).toHaveURL(/\/game$/);
  await expect(page.getByText('Searching for opponent…')).toBeVisible();

  await page.getByRole('button', { name: 'Cancel' }).click();

  await expect(page).toHaveURL(/\/lobby$/);
  await expect(page.getByRole('button', { name: 'Start Game' })).toBeVisible();
});

test('wait time displayed while queued increases every second', async ({ page }) => {
  await page.getByRole('button', { name: 'Start Game' }).click();
  await expect(page).toHaveURL(/\/game$/);

  const waitLine = page.locator('p', { hasText: 'Searching for opponent' });
  await expect(waitLine).toHaveText('Searching for opponent… 0:00');

  // The server re-evaluates the queue (and pushes an updated wait time) on a
  // 1s tick, so each of these should resolve almost immediately if the timer
  // is actually ticking every second rather than jumping in coarse steps.
  await expect(waitLine).not.toHaveText('Searching for opponent… 0:00', { timeout: 3_000 });
  const afterFirstTick = await waitLine.textContent();

  await expect(waitLine).not.toHaveText(afterFirstTick ?? '', { timeout: 3_000 });
});

test.describe('waiting screen shows the chosen colour preference', () => {
  const cases: { label: 'Always White' | 'Always Black' | 'Random'; expectedColor: 'WHITE' | 'BLACK' }[] = [
    { label: 'Always White', expectedColor: 'WHITE' },
    { label: 'Always Black', expectedColor: 'BLACK' },
    // Nobody to pair with yet, so a lone Random queuer is shown White
    // provisionally — the real coin toss only happens once matched with
    // an opponent (see human-vs-human.spec.ts).
    { label: 'Random', expectedColor: 'WHITE' },
  ];

  for (const { label, expectedColor } of cases) {
    test(`"${label}" shows "You are: ${expectedColor}" while waiting alone`, async ({ page }) => {
      await selectColorPreference(page, label);
      await page.getByRole('button', { name: 'Start Game' }).click();
      await expect(page).toHaveURL(/\/game$/);

      await expect(page.locator('p', { hasText: 'You are:' })).toContainText(expectedColor);
    });
  }
});
