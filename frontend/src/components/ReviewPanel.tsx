import type { MoveReview } from '../types';
import { CLASSIFICATION_BADGE, CLASSIFICATION_COLOR, CLASSIFICATION_LABEL, moveNumberLabel } from '../review';

/** What happened on the current move: its label, what the engine preferred, and any comment. */
export function ReviewPanel({ move }: Readonly<{ move: MoveReview | null }>) {
  if (!move) {
    return <div className="review-panel" data-testid="review-panel"><p className="review-muted">Start position</p></div>;
  }
  const badge = CLASSIFICATION_BADGE[move.classification] ?? '';
  const playedBest = move.bestUci !== null && move.bestUci === move.uci;
  return (
    <div className="review-panel" data-testid="review-panel">
      <p className="review-move">
        <span>{moveNumberLabel(move.ply)} {move.san}{badge}</span>
        <span className="review-label" style={{ color: CLASSIFICATION_COLOR[move.classification] }}>
          {CLASSIFICATION_LABEL[move.classification]}
        </span>
      </p>
      {move.bestSan && !playedBest && (
        <p className="review-best">
          Best was <strong>{move.bestSan}</strong>
          {move.line.length > 1 && <span className="review-line"> ({move.line.join(' ')})</span>}
        </p>
      )}
      {move.comment && <p className="review-comment">{move.comment}</p>}
    </div>
  );
}
