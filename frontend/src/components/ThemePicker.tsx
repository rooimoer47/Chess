export const THEMES = ['classic', 'generated'] as const;
export type Theme = typeof THEMES[number];

export const BOARD_THEMES = ['classic', 'forest', 'ocean', 'walnut'] as const;
export type BoardTheme = typeof BOARD_THEMES[number];

export const BOARD_COLORS: Record<BoardTheme, { light: string; dark: string; label: string }> = {
  classic: { light: '#ffffff', dark: '#000000', label: 'Classic' },
  forest:  { light: '#eeeed2', dark: '#769656', label: 'Forest'  },
  ocean:   { light: '#d6e8f0', dark: '#5b8db8', label: 'Ocean'   },
  walnut:  { light: '#f2d9b0', dark: '#7b4f2e', label: 'Walnut'  },
};

function pieceAt(row: number, col: number): { type: string; color: string } | null {
  if (row >= 2 && row <= 5) return null;
  const color = row >= 6 ? 'black' : 'white';
  if (row === 1 || row === 6) return { type: 'pawn', color };
  const types = ['rook', 'knight', 'bishop', 'queen', 'king', 'bishop', 'knight', 'rook'];
  return { type: types[col], color };
}

function StaticBoard({ theme, boardTheme }: { theme: string; boardTheme: BoardTheme }) {
  const { light, dark } = BOARD_COLORS[boardTheme];
  const rows = [7, 6, 5, 4, 3, 2, 1, 0];
  const cols = [0, 1, 2, 3, 4, 5, 6, 7];
  return (
    <div className="board" style={{ cursor: 'default' }}>
      {rows.map(row =>
        cols.map(col => {
          const isLight = (row + col) % 2 === 0;
          const piece = pieceAt(row, col);
          return (
            <div
              key={`${row}-${col}`}
              className="square"
              style={{ background: isLight ? light : dark, cursor: 'default' }}
            >
              {piece && (
                <img
                  src={`/images/${theme}/${piece.type}_${piece.color}.png`}
                  alt=""
                  className="piece"
                  draggable={false}
                />
              )}
            </div>
          );
        })
      )}
    </div>
  );
}

function BoardSwatch({ light, dark }: { light: string; dark: string }) {
  return (
    <div className="board-swatch">
      <div style={{ background: light }} />
      <div style={{ background: dark }} />
      <div style={{ background: dark }} />
      <div style={{ background: light }} />
    </div>
  );
}

export function ThemePicker({ theme, boardTheme, onChangeTheme, onChangeBoardTheme, onClose }: {
  theme: Theme;
  boardTheme: BoardTheme;
  onChangeTheme: (t: Theme) => void;
  onChangeBoardTheme: (t: BoardTheme) => void;
  onClose: () => void;
}) {
  return (
    <div className="theme-picker-overlay" onClick={onClose}>
      <div className="theme-picker-dialog" onClick={e => e.stopPropagation()}>
        <div className="theme-picker-header">
          <span>Pick a Theme</span>
          <button className="theme-picker-close" onClick={onClose}>✕</button>
        </div>
        <div className="theme-picker-body">
          <StaticBoard theme={theme} boardTheme={boardTheme} />
          <div className="theme-picker-options">
            <span className="theme-picker-section-label">Pieces</span>
            <div className="piece-theme-swatches">
              {THEMES.map(t => (
                <button
                  key={t}
                  type="button"
                  className={`board-swatch-btn${theme === t ? ' board-swatch-btn-active' : ''}`}
                  onClick={() => onChangeTheme(t)}
                  title={t.charAt(0).toUpperCase() + t.slice(1)}
                >
                  <img
                    src={`/images/${t}/knight_white.png`}
                    alt={t}
                    className="piece-theme-knight"
                    draggable={false}
                  />
                  <span className="board-swatch-label">{t.charAt(0).toUpperCase() + t.slice(1)}</span>
                </button>
              ))}
            </div>
            <span className="theme-picker-section-label" style={{ marginTop: 8 }}>Board</span>
            <div className="board-theme-swatches">
              {BOARD_THEMES.map(t => (
                <button
                  key={t}
                  type="button"
                  className={`board-swatch-btn${boardTheme === t ? ' board-swatch-btn-active' : ''}`}
                  onClick={() => onChangeBoardTheme(t)}
                  title={BOARD_COLORS[t].label}
                >
                  <BoardSwatch light={BOARD_COLORS[t].light} dark={BOARD_COLORS[t].dark} />
                  <span className="board-swatch-label">{BOARD_COLORS[t].label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
