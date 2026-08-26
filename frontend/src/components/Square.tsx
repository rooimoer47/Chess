import type { Piece as PieceType } from '../types';
import { Piece } from './Piece';

interface Props {
  row: number;
  col: number;
  piece: PieceType | null;
  isSelected: boolean;
  isLegalTarget: boolean;
  isLastMove: boolean;
  isLegalDropTarget: boolean;
  isDraggingSource: boolean;
  theme: string;
  lightColor: string;
  darkColor: string;
  onMouseDown: (e: React.MouseEvent) => void;
  onMouseEnter: () => void;
}

export function Square({ row, col, piece, isSelected, isLegalTarget, isLastMove, isLegalDropTarget, isDraggingSource, theme, lightColor, darkColor, onMouseDown, onMouseEnter }: Readonly<Props>) {
  const isLight = (row + col) % 2 === 0;

  let background = isLight ? lightColor : darkColor;
  if (isLastMove)       background = isLight ? '#f6f669' : '#baca2b';
  if (isSelected)       background = '#7fc97f';
  else if (isLegalDropTarget) background = isLight ? '#f5c518' : '#d4a900';
  else if (isLegalTarget)     background = isLight ? '#cdd16e' : '#aaa23a';

  // board[0][0] = a1 (see GameEngine.initializeBoard) — col 0 = 'a' file, row 0 = rank 1
  const square = `${String.fromCodePoint(97 + col)}${row + 1}`;

  return (
    <div
      className="square"
      data-testid={`square-${square}`}
      style={{ background }}
      onMouseDown={onMouseDown}
      onMouseEnter={onMouseEnter}
    >
      {piece && <Piece piece={piece} theme={theme} style={{ opacity: isDraggingSource ? 0.25 : 1 }} />}
      {isLegalTarget && !piece && <div className="legal-dot" />}
      {isLegalTarget && piece && !isDraggingSource && <div className="legal-capture" />}
    </div>
  );
}
