import { expect, type Page } from '@playwright/test';

export const STANDARD_BACK_RANK = 'RNBQKBNR';

const PIECE_LETTERS: Record<string, string> = {
  ROOK: 'R',
  KNIGHT: 'N',
  BISHOP: 'B',
  QUEEN: 'Q',
  KING: 'K',
  PAWN: 'P',
};

export const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'] as const;

/** Flips the lobby's top-level variant toggle to Chess960 and waits for the rebrand to land. */
export async function selectChess960(page: Page): Promise<void> {
  await page.locator('.variant-toggle .mode-btn', { hasText: 'Chess960' }).click();
  await expect(page.locator('.lobby-title')).toHaveText('Chessnuts960');
}

/**
 * Reads one rank off the rendered board as an 8-character string (files a–h).
 *
 * Square test ids are absolute (a1…h8, see Square.tsx), so this is independent
 * of which colour the board is currently oriented for.
 */
export async function readRank(page: Page, rank: 1 | 2 | 7 | 8): Promise<string> {
  const letters = await Promise.all(
    FILES.map(async file => {
      const img = page.getByTestId(`square-${file}${rank}`).locator('img');
      if (await img.count() === 0) return '.';
      const alt = await img.getAttribute('alt');
      const pieceType = (alt ?? '').split(' ')[1] ?? '';
      return PIECE_LETTERS[pieceType] ?? '?';
    }),
  );
  return letters.join('');
}

/**
 * Asserts a back rank is a legal Chess960 arrangement: every piece present in
 * the right count, bishops on opposite-coloured squares, and the king strictly
 * between the two rooks. Standard chess satisfies these too — that is the
 * point; 960 legality is a superset, not a different shape.
 */
export function expectLegalBackRank(backRank: string): void {
  expect(backRank, 'back rank should be 8 files wide').toHaveLength(8);

  const counts = [...backRank].reduce<Record<string, number>>((acc, c) => {
    acc[c] = (acc[c] ?? 0) + 1;
    return acc;
  }, {});
  expect(counts, `unexpected piece counts in "${backRank}"`).toEqual({ R: 2, N: 2, B: 2, Q: 1, K: 1 });

  const bishops = [...backRank].flatMap((c, i) => (c === 'B' ? [i] : []));
  expect(bishops[0] % 2, `bishops must sit on opposite colours in "${backRank}"`).not.toBe(bishops[1] % 2);

  const rooks = [...backRank].flatMap((c, i) => (c === 'R' ? [i] : []));
  const king = backRank.indexOf('K');
  expect(king > rooks[0] && king < rooks[1], `king must sit strictly between the rooks in "${backRank}"`).toBe(true);
}

/**
 * Plays one legal move for the given side, whatever the position is.
 *
 * Selecting a piece makes the server's legal targets for it visible (Square.tsx
 * renders `.legal-dot` on empty targets and `.legal-capture` on occupied ones),
 * so this tries the player's pieces until one offers a target, then takes it —
 * it only ever plays a move the server already offered.
 *
 * Deliberately not restricted to pawn pushes: a Chess960 deal can leave a piece
 * bearing on the king early, and when the side to move is in check no pawn push
 * is legal at all — only king moves, blocks and captures are.
 *
 * Returns the move played, e.g. "c2c3".
 */
export async function playAnyLegalMove(page: Page, color: 'WHITE' | 'BLACK'): Promise<string> {
  // One round trip for the whole board rather than 64 locator queries.
  const squares = await page.locator('.square').evaluateAll(nodes =>
    nodes.map(node => ({
      id: node.getAttribute('data-testid') ?? '',
      alt: node.querySelector('img')?.getAttribute('alt') ?? null,
    })),
  );

  const mine = squares.filter(sq => sq.alt?.startsWith(`${color} `));
  if (mine.length === 0) throw new Error(`no ${color} pieces on the board`);

  const targets = page.locator('.square:has(.legal-dot), .square:has(.legal-capture)');

  for (const square of mine) {
    await page.locator(`[data-testid="${square.id}"]`).click();

    // A piece can have no legal moves at all (blocked, pinned, or the king is
    // in check and this piece can't address it) — move on to the next one.
    try {
      await targets.first().waitFor({ state: 'attached', timeout: 1_000 });
    } catch {
      continue;
    }

    const target = targets.first();
    const targetId = await target.getAttribute('data-testid');
    await target.click();

    const from = square.id.replace('square-', '');
    const to = (targetId ?? '').replace('square-', '');
    await expect(page.getByTestId(`square-${from}`).locator('img'),
      `move ${from}${to} did not take effect`).toHaveCount(0);
    return `${from}${to}`;
  }

  throw new Error(`no legal move was available for ${color}`);
}
