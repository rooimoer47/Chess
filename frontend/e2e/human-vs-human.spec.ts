import { test, expect } from '@playwright/test';
import { readRank, expectLegalBackRank } from './chess960';
import { setupMatchedHumans, isWhite } from './humans';

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
