import type { Piece as PieceType } from '../types';

interface Props {
  piece: PieceType;
}

export function Piece({ piece }: Props) {
  const src = `/images/${piece.type.toLowerCase()}_${piece.color.toLowerCase()}.png`;
  return (
    <img
      className="piece"
      src={src}
      alt={`${piece.color} ${piece.type}`}
      draggable={false}
    />
  );
}
