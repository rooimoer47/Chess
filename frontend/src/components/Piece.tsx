import type { Piece as PieceType } from '../types';

interface Props {
  piece: PieceType;
  theme: string;
}

export function Piece({ piece, theme }: Props) {
  const src = `/images/${theme}/${piece.type.toLowerCase()}_${piece.color.toLowerCase()}.png`;
  return (
    <img
      className="piece"
      src={src}
      alt={`${piece.color} ${piece.type}`}
      draggable={false}
    />
  );
}
