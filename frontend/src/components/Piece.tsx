import type { CSSProperties } from 'react';
import type { Piece as PieceType } from '../types';

interface Props {
  piece: PieceType;
  theme: string;
  style?: CSSProperties;
}

export function Piece({ piece, theme, style }: Props) {
  const src = `/images/${theme}/${piece.type.toLowerCase()}_${piece.color.toLowerCase()}.png`;
  return (
    <img
      className="piece"
      src={src}
      alt={`${piece.color} ${piece.type}`}
      draggable={false}
      style={style}
    />
  );
}
