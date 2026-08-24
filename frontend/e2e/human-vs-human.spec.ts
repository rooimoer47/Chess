import { test, expect, type Page, type Browser, type BrowserContext } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';
import { selectChess960, readRank, expectLegalBackRank } from './chess960';

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
async function setupMatchedHumans(
  browser: Browser,
  prefA: ColorPref | null,
  prefB: ColorPref | null,
  chess960 = false,
): Promise<MatchedPair> {
  const contextA = await browser.newContext();
  const contextB = await browser.newContext();
  try {
    return await matchTwoHumans(contextA, contextB, prefA, prefB, chess960);
  } catch (e) {
    // Callers close these in their own `finally`, but only once this function
    // has RETURNED them — a throw here would otherwise leak two live contexts,
    // and a leaked context keeps its WebSocket (and so its queued player) alive
    // for the rest of the worker run, where it steals the next test's partner
    // and turns one flake into a cascade of them.
    await contextA.close();
    await contextB.close();
    throw e;
  }
}

async function matchTwoHumans(
  contextA: BrowserContext,
  contextB: BrowserContext,
  prefA: ColorPref | null,
  prefB: ColorPref | null,
  chess960: boolean,
): Promise<MatchedPair> {
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

  // Both or neither — the queue is partitioned by variant, so a mixed pair
  // would simply never match (that rule is unit-tested in GameSessionManagerTest).
  if (chess960) {
    await selectChess960(pageA);
    await selectChess960(pageB);
  }

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

test('two Chess960 players are matched into the same randomised position', async ({ browser }) => {
  const { pageA, pageB, contextA, contextB } = await setupMatchedHumans(browser, null, null, true);

  try {
    const rankA = await readRank(pageA, 1);
    expectLegalBackRank(rankA);

    // Both clients must see the one position the server dealt for this game —
    // not two independently generated ones.
    expect(await readRank(pageB, 1), 'both players must see the same dealt position').toBe(rankA);
    expect(await readRank(pageA, 8), 'black back rank must mirror white').toBe(rankA);

    // Both are in a real, playable 960 game rather than a stalled handshake.
    const [whitePage, blackPage] = (await isWhite(pageA)) ? [pageA, pageB] : [pageB, pageA];
    await expect(whitePage.locator('.turn-indicator')).toHaveText('Your turn');
    await expect(blackPage.locator('.turn-indicator')).toHaveText("Opponent's turn");
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

test('two Random preferences give the players opposite colours', async ({ browser }) => {
  // Only the structural half of the rule is checked here: exactly one White and
  // one Black, both ends agreeing on who is who.
  //
  // That both assignments actually *occur* is a distribution property, and
  // asserting it from the browser means one fresh pair of contexts per coin
  // flip — so few flips are affordable that the assertion fails by chance
  // (8 flips landing alike is 1 run in 128, and it did). It lives in
  // GameSessionManagerTest.matchedRandomPreferences_produceBothColourAssignments
  // instead, where 200 flips cost milliseconds.
  const { pageA, pageB, contextA, contextB } = await setupMatchedHumans(browser, null, null);

  try {
    const aIsWhite = await isWhite(pageA);
    expect(await isWhite(pageB), 'the two players must not both be the same colour').toBe(!aIsWhite);
  } finally {
    await contextA.close();
    await contextB.close();
  }
});
