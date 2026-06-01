import type { Color } from '../types';

interface Props {
  pieces: string[];
  color: Color;
}

export function CapturedPieces({ pieces, color }: Props) {
  return (
    <div className="captured-pieces">
      {pieces.map((type, i) => (
        <img
          key={i}
          className="captured-piece"
          src={`/images/${type.toLowerCase()}_${color.toLowerCase()}.png`}
          alt={`${color} ${type}`}
          draggable={false}
        />
      ))}
    </div>
  );
}
