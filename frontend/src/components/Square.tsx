import type { Piece as PieceType } from '../types';
import { Piece } from './Piece';

interface Props {
  row: number;
  col: number;
  piece: PieceType | null;
  isSelected: boolean;
  isLegalTarget: boolean;
  isLastMove: boolean;
  theme: string;
  onClick: () => void;
}

export function Square({ row, col, piece, isSelected, isLegalTarget, isLastMove, theme, onClick }: Props) {
  const isLight = (row + col) % 2 === 0;

  let background = isLight ? '#f0d9b5' : '#b58863';
  if (isLastMove) background = isLight ? '#f6f669' : '#baca2b';
  if (isSelected) background = '#7fc97f';
  else if (isLegalTarget) background = isLight ? '#cdd16e' : '#aaa23a';

  return (
    <div className="square" style={{ background }} onClick={onClick}>
      {piece && <Piece piece={piece} theme={theme} />}
      {isLegalTarget && !piece && <div className="legal-dot" />}
      {isLegalTarget && piece && <div className="legal-capture" />}
    </div>
  );
}
