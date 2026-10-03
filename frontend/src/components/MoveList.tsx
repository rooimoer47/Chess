import { useEffect, useRef } from 'react';
import type { MoveReview } from '../types';
import { CLASSIFICATION_BADGE, CLASSIFICATION_COLOR } from '../review';

/** The game's moves in pairs, with ?!/?/?? badges; click a move to jump to the position after it. */
export function MoveList({ moves, currentPly, onSelect }: Readonly<{
  moves: MoveReview[];
  currentPly: number;
  onSelect: (ply: number) => void;
}>) {
  const listRef = useRef<HTMLOListElement>(null);

  useEffect(() => {
    listRef.current?.querySelector('.move-current')?.scrollIntoView({ block: 'nearest' });
  }, [currentPly]);

  const rows: { number: number; white?: MoveReview; black?: MoveReview }[] = [];
  for (const move of moves) {
    const number = Math.ceil(move.ply / 2);
    if (move.ply % 2 === 1) rows.push({ number, white: move });
    else if (rows.length > 0 && rows[rows.length - 1].number === number) rows[rows.length - 1].black = move;
    else rows.push({ number, black: move });
  }

  const cell = (move?: MoveReview) => {
    if (!move) return <span className="move-cell" />;
    const badge = CLASSIFICATION_BADGE[move.classification];
    return (
      <button
        type="button"
        data-testid={`move-${move.ply}`}
        className={`move-cell move-btn${move.ply === currentPly ? ' move-current' : ''}`}
        onClick={() => onSelect(move.ply)}
      >
        {move.san}
        {badge && (
          <span className="move-badge" style={{ color: CLASSIFICATION_COLOR[move.classification] }}>{badge}</span>
        )}
      </button>
    );
  };

  return (
    <ol className="move-list" data-testid="move-list" ref={listRef}>
      {rows.map(row => (
        <li key={row.number} className="move-row">
          <span className="move-number">{row.number}.</span>
          {cell(row.white)}
          {cell(row.black)}
        </li>
      ))}
    </ol>
  );
}
