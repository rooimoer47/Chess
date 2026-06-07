import type { Color } from '../types';

interface Props {
  pieces: string[];
  color: Color;
  theme: string;
}

export function CapturedPieces({ pieces, color, theme }: Props) {
  return (
    <div className="captured-pieces">
      {pieces.map((type, i) => (
        <img
          key={i}
          className="captured-piece"
          src={`/images/${theme}/${type.toLowerCase()}_${color.toLowerCase()}.png`}
          alt={`${color} ${type}`}
          draggable={false}
        />
      ))}
    </div>
  );
}
