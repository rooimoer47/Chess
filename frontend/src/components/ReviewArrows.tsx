import type { Color } from '../types';
import { squareToGrid } from '../review';

export interface Arrow {
  from: string;
  to: string;
  color: string;
  testId: string;
}

const SHAFT_WIDTH = 0.16;
const HEAD_WIDTH = 0.42;
const HEAD_LENGTH = 0.38;

// One arrow as a polygon, in board units (each square is 1×1, centres at .5).
function arrowPoints(arrow: Arrow, viewAs: Color): string {
  const a = squareToGrid(arrow.from, viewAs);
  const b = squareToGrid(arrow.to, viewAs);
  const ax = a.x + 0.5, ay = a.y + 0.5, bx = b.x + 0.5, by = b.y + 0.5;
  const length = Math.hypot(bx - ax, by - ay);
  const dx = (bx - ax) / length, dy = (by - ay) / length;   // along the arrow
  const nx = -dy, ny = dx;                                    // across it
  const tipX = bx - dx * 0.15, tipY = by - dy * 0.15;         // stop short of the target's centre
  const baseX = tipX - dx * HEAD_LENGTH, baseY = tipY - dy * HEAD_LENGTH;
  const startX = ax + dx * 0.15, startY = ay + dy * 0.15;
  const s = SHAFT_WIDTH / 2, h = HEAD_WIDTH / 2;
  const points = [
    [startX + nx * s, startY + ny * s],
    [baseX + nx * s, baseY + ny * s],
    [baseX + nx * h, baseY + ny * h],
    [tipX, tipY],
    [baseX - nx * h, baseY - ny * h],
    [baseX - nx * s, baseY - ny * s],
    [startX - nx * s, startY - ny * s],
  ];
  return points.map(([x, y]) => `${x.toFixed(3)},${y.toFixed(3)}`).join(' ');
}

/** Arrows drawn over the board; sits on top of it and lets clicks through. */
export function ReviewArrows({ arrows, viewAs }: Readonly<{ arrows: Arrow[]; viewAs: Color }>) {
  return (
    <svg className="review-arrows" viewBox="0 0 8 8" aria-hidden="true">
      {arrows.map(arrow => (
        <polygon
          key={arrow.testId}
          data-testid={arrow.testId}
          data-from={arrow.from}
          data-to={arrow.to}
          points={arrowPoints(arrow, viewAs)}
          fill={arrow.color}
          opacity={0.8}
        />
      ))}
    </svg>
  );
}
