import type { Color } from '../types';

interface Props {
  color: Color;
  theme: string;
  onChoice: (choice: string) => void;
}

const CHOICES = ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT'] as const;

export function PromotionDialog({ color, theme, onChoice }: Props) {
  return (
    <div className="promotion-overlay">
      <div className="promotion-dialog">
        <p>Promote pawn to:</p>
        <div className="promotion-choices">
          {CHOICES.map(choice => (
            <img
              key={choice}
              className="promotion-piece"
              src={`/images/${theme}/${choice.toLowerCase()}_${color.toLowerCase()}.png`}
              alt={choice}
              title={choice}
              onClick={() => onChoice(choice)}
              draggable={false}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
