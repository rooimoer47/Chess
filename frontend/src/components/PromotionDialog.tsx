import type { Color } from '../types';
import { BOARD_COLORS, type BoardTheme } from './ThemePicker';

interface Props {
  color: Color;
  theme: string;
  boardTheme: BoardTheme;
  onChoice: (choice: string) => void;
}

const CHOICES = ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT'] as const;

export function PromotionDialog({ color, theme, boardTheme, onChoice }: Props) {
  const { light, dark } = BOARD_COLORS[boardTheme];
  const squareColor = color === 'WHITE' ? dark : light;

  return (
    <div className="promotion-overlay">
      <div className="promotion-dialog">
        <p>Promote pawn to:</p>
        <div className="promotion-choices">
          {CHOICES.map(choice => (
            <img
              key={choice}
              className="promotion-piece"
              style={{ background: squareColor }}
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
