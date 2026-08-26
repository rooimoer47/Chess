import type { Color } from '../types';
import { BOARD_COLORS, type BoardTheme } from './ThemePicker';

interface Props {
  color: Color;
  theme: string;
  boardTheme: BoardTheme;
  onChoice: (choice: string) => void;
}

const CHOICES = ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT'] as const;

export function PromotionDialog({ color, theme, boardTheme, onChoice }: Readonly<Props>) {
  const { light, dark } = BOARD_COLORS[boardTheme];
  const squareColor = color === 'WHITE' ? dark : light;

  return (
    <div className="promotion-overlay">
      <div className="promotion-dialog">
        <p>Promote pawn to:</p>
        <div className="promotion-choices">
          {CHOICES.map((choice, i) => (
            <button
              key={choice}
              type="button"
              className="promotion-piece"
              style={{ background: squareColor }}
              title={choice}
              aria-label={choice}
              onClick={() => onChoice(choice)}
              // Promotion blocks the game until it is answered, so put the
              // keyboard on the first choice rather than leaving focus behind
              // on whatever was clicked to trigger the move.
              autoFocus={i === 0}
            >
              <img
                src={`/images/${theme}/${choice.toLowerCase()}_${color.toLowerCase()}.png`}
                alt=""
                draggable={false}
              />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
