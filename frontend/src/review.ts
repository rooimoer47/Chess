import type { Color, Eval, MoveClassification } from './types';

/** Colours for the played-move arrow and labels; the engine's suggestion is always BEST_COLOR. */
export const CLASSIFICATION_COLOR: Record<MoveClassification, string> = {
  BEST: '#7fc97f',
  GOOD: '#7aa7d8',
  INACCURACY: '#e6c34a',
  MISTAKE: '#e8913a',
  BLUNDER: '#e06060',
  UNKNOWN: '#999999',
};
export const BEST_COLOR = CLASSIFICATION_COLOR.BEST;

export const CLASSIFICATION_LABEL: Record<MoveClassification, string> = {
  BEST: 'Best move',
  GOOD: 'Good move',
  INACCURACY: 'Inaccuracy',
  MISTAKE: 'Mistake',
  BLUNDER: 'Blunder',
  UNKNOWN: 'Not analysed',
};

/** Annotation symbols shown next to a move; only the bad ones get one. */
export const CLASSIFICATION_BADGE: Partial<Record<MoveClassification, string>> = {
  INACCURACY: '?!',
  MISTAKE: '?',
  BLUNDER: '??',
};

/** Grid position of a square like "e4" on a board drawn from `viewAs`'s side: 0..7 from the top-left. */
export function squareToGrid(square: string, viewAs: Color): { x: number; y: number } {
  const file = (square.codePointAt(0) ?? 97) - 97;
  const rank = Number(square[1]) - 1;
  return viewAs === 'WHITE'
    ? { x: file, y: 7 - rank }
    : { x: 7 - file, y: rank };
}

/** Same curve the server uses to label moves (Lichess's): centipawns to a win chance in -1..1. */
export function winChance(cp: number): number {
  return 2 / (1 + Math.exp(-0.00368208 * cp)) - 1;
}

/**
 * White's share of the eval bar, 0..1. `whiteToMove` is needed for mate 0 (the side to move is
 * checkmated), whose sign can't say who won.
 */
export function whiteShare(evaluation: Eval, whiteToMove: boolean): number {
  if (evaluation.mate !== null) {
    if (evaluation.mate > 0) return 1;
    if (evaluation.mate < 0) return 0;
    return whiteToMove ? 0 : 1;
  }
  return (winChance(evaluation.cp ?? 0) + 1) / 2;
}

/** "+0.4", "−1.2", "M3", "−M2", or "#" when the side to move is checkmated. */
export function formatEval(evaluation: Eval): string {
  if (evaluation.mate !== null) {
    if (evaluation.mate === 0) return '#';
    return evaluation.mate > 0 ? `M${evaluation.mate}` : `−M${-evaluation.mate}`;
  }
  const pawns = (evaluation.cp ?? 0) / 100;
  if (pawns === 0) return '0.0';
  return pawns > 0 ? `+${pawns.toFixed(1)}` : `−${(-pawns).toFixed(1)}`;
}

/** "12." for White's move, "12…" for Black's, from the ply the move leads to. */
export function moveNumberLabel(ply: number): string {
  const number = Math.ceil(ply / 2);
  return ply % 2 === 1 ? `${number}.` : `${number}…`;
}
