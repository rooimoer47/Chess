import type { Color, Eval } from '../types';
import { formatEval, whiteShare } from '../review';

/** Vertical bar beside the board: how much of it is White's is White's winning chance. */
export function EvalBar({ evaluation, whiteToMove, viewAs }: Readonly<{
  evaluation: Eval | null;
  whiteToMove: boolean;
  viewAs: Color;
}>) {
  const share = evaluation ? whiteShare(evaluation, whiteToMove) : 0.5;
  // White's part grows from whichever edge White sits at.
  const whiteAtBottom = viewAs === 'WHITE';
  return (
    <div className="eval-bar" data-testid="eval-bar" title={evaluation ? formatEval(evaluation) : 'No evaluation'}>
      <div
        className="eval-bar-white"
        style={{ height: `${share * 100}%`, [whiteAtBottom ? 'bottom' : 'top']: 0 }}
      />
      <span className={`eval-bar-label ${share >= 0.5 === whiteAtBottom ? 'eval-bar-label-bottom' : 'eval-bar-label-top'}`}>
        {evaluation ? formatEval(evaluation) : ''}
      </span>
    </div>
  );
}
