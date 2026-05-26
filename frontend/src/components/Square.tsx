import type { Piece as PieceType } from '../types';
import { Piece } from './Piece';

interface Props {
  row: number;
  col: number;
  piece: PieceType | null;
  isSelected: boolean;
  isLegalTarget: boolean;
  onClick: () => void;
}

export function Square({ row, col, piece, isSelected, isLegalTarget, onClick }: Props) {
  const isLight = (row + col) % 2 === 0;

  let background = isLight ? '#f0d9b5' : '#b58863';
  if (isSelected) background = '#7fc97f';
  else if (isLegalTarget) background = isLight ? '#cdd16e' : '#aaa23a';

  return (
    <div className="square" style={{ background }} onClick={onClick}>
      {piece && <Piece piece={piece} />}
      {isLegalTarget && !piece && <div className="legal-dot" />}
      {isLegalTarget && piece && <div className="legal-capture" />}
    </div>
  );
}
