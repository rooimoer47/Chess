import { expect, type Page, type Browser, type BrowserContext } from '@playwright/test';
import { uniqueUsername, registerViaApi, expectLobby } from './fixtures';
import { selectChess960 } from './chess960';

/** Shared setup for tests that need two real players matched into one game. */

export type ColorPref = 'Always White' | 'Always Black' | 'Random';

export interface MatchedPair {
  pageA: Page;
  pageB: Page;
  contextA: BrowserContext;
  contextB: BrowserContext;
  usernameA: string;
  usernameB: string;
}

/**
 * Registers two fresh users, sets their colour preference (leaves the
 * default "Random" alone when null), and starts a Human game for both —
 * since both are freshly registered at the same default ELO, the
 * matchmaking queue pairs them with each other.
 */
export async function setupMatchedHumans(
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

  return { pageA, pageB, contextA, contextB, usernameA, usernameB };
}

// White moves first, so whoever sees "Your turn" right after the board loads is White.
export async function isWhite(page: Page): Promise<boolean> {
  const text = await page.locator('.turn-indicator').textContent();
  return !!text?.includes('Your turn');
}


/** Plays a move by clicking its two squares, and waits until the board shows it was accepted. */
export async function play(page: Page, from: string, to: string): Promise<void> {
  await page.getByTestId(`square-${from}`).click();
  await page.getByTestId(`square-${to}`).click();
  await expect(page.getByTestId(`square-${to}`).locator('img')).toHaveCount(1);
  await expect(page.locator('.turn-indicator')).not.toHaveText('Your turn');
}
