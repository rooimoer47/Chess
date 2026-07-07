import { test, expect, type Page, type Browser, type BrowserContext } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';

type ColorPref = 'Always White' | 'Always Black' | 'Random';

interface MatchedPair {
  pageA: Page;
  pageB: Page;
  contextA: BrowserContext;
  contextB: BrowserContext;
}

/**
 * Registers two fresh users, sets their colour preference (leaves the
 * default "Random" alone when null), and starts a Human game for both —
 * since both are freshly registered at the same default ELO, the
 * matchmaking queue pairs them with each other.
 */
async function setupMatchedHumans(browser: Browser, prefA: ColorPref | null, prefB: ColorPref | null): Promise<MatchedPair> {
  const contextA = await browser.newContext();
  const contextB = await browser.newContext();
  const pageA = await contextA.newPage();
  const pageB = await contextB.newPage();

  const usernameA = uniqueUsername('h2h-a');
  const usernameB = uniqueUsername('h2h-b');
  await registerViaApi(pageA, usernameA);
  await registerViaApi(pageB, usernameB);

  await pageA.goto('/');
  await expectLobby(pageA, usernameA);
  await pageB.goto('/');
  await expectLobby(pageB, usernameB);

  if (prefA) await pageA.locator('.color-pref-row label', { hasText: prefA }).click();
  if (prefB) await pageB.locator('.color-pref-row label', { hasText: prefB }).click();

  await pageA.getByRole('button', { name: 'Start Game' }).click();
  await pageB.getByRole('button', { name: 'Start Game' }).click();

  await expect(pageA.locator('.board')).toBeVisible({ timeout: 15_000 });
  await expect(pageB.locator('.board')).toBeVisible({ timeout: 15_000 });

  return { pageA, pageB, contextA, contextB };
}

// White moves first, so whoever sees "Your turn" right after the board loads is White.
async function isWhite(page: Page): Promise<boolean> {
  const text = await page.locator('.turn-indicator').textContent();
  return !!text?.includes('Your turn');
}

test('happy path: two players are matched and a move syncs between them', async ({ browser }) => {
  const { pageA, pageB, contextA, contextB } = await setupMatchedHumans(browser, null, null);

  try {
    const [whitePage, blackPage] = (await isWhite(pageA)) ? [pageA, pageB] : [pageB, pageA];

    await expect(whitePage.locator('.turn-indicator')).toHaveText('Your turn');
    await expect(blackPage.locator('.turn-indicator')).toHaveText("Opponent's turn");

    await whitePage.getByTestId('square-e2').click();
    await whitePage.getByTestId('square-e4').click();

    // The real point of this test: the move must show up on the OTHER
    // player's board too, not just the mover's.
    await expect(whitePage.getByTestId('square-e4').locator('img')).toHaveAttribute('alt', 'WHITE PAWN');
    await expect(blackPage.getByTestId('square-e4').locator('img')).toHaveAttribute('alt', 'WHITE PAWN');
    await expect(blackPage.getByTestId('square-e2').locator('img')).toHaveCount(0);

    await expect(whitePage.locator('.turn-indicator')).toHaveText("Opponent's turn");
    await expect(blackPage.locator('.turn-indicator')).toHaveText('Your turn');
  } finally {
    await contextA.close();
    await contextB.close();
  }
});

test('Random paired with Always White gets the opposite colour (Black)', async ({ browser }) => {
  const { pageA, pageB, contextA, contextB } = await setupMatchedHumans(browser, null, 'Always White');

  try {
    expect(await isWhite(pageB), 'the Always White player must get White').toBe(true);
    expect(await isWhite(pageA), 'the Random player paired with a fixed White must get Black').toBe(false);
  } finally {
    await contextA.close();
    await contextB.close();
  }
});

test('Random paired with Always Black gets the opposite colour (White)', async ({ browser }) => {
  const { pageA, pageB, contextA, contextB } = await setupMatchedHumans(browser, null, 'Always Black');

  try {
    expect(await isWhite(pageB), 'the Always Black player must get Black').toBe(false);
    expect(await isWhite(pageA), 'the Random player paired with a fixed Black must get White').toBe(true);
  } finally {
    await contextA.close();
    await contextB.close();
  }
});

test('two Random preferences can produce either colour assignment', async ({ browser }) => {
  // Genuinely random, so this asserts the distribution rather than a single
  // outcome: run repeated matches (stopping early once both are observed)
  // and fail only if the same assignment happens every single time.
  const outcomes = new Set<boolean>();
  const MAX_ATTEMPTS = 8;

  for (let i = 0; i < MAX_ATTEMPTS && outcomes.size < 2; i++) {
    const { pageA, contextA, contextB } = await setupMatchedHumans(browser, null, null);
    try {
      outcomes.add(await isWhite(pageA));
    } finally {
      await contextA.close();
      await contextB.close();
    }
  }

  expect(outcomes.size, `expected both White and Black to occur across ${MAX_ATTEMPTS} random-vs-random matches`).toBe(2);
});
