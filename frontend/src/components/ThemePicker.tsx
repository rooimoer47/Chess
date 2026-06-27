export const THEMES = ['classic', 'generated'] as const;
export type Theme = typeof THEMES[number];

function pieceAt(row: number, col: number): { type: string; color: string } | null {
  if (row >= 2 && row <= 5) return null;
  const color = row >= 6 ? 'black' : 'white';
  if (row === 1 || row === 6) return { type: 'pawn', color };
  const types = ['rook', 'knight', 'bishop', 'queen', 'king', 'bishop', 'knight', 'rook'];
  return { type: types[col], color };
}

function StaticBoard({ theme }: { theme: string }) {
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
              style={{ background: isLight ? '#f0d9b5' : '#b58863', cursor: 'default' }}
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

export function ThemePicker({ theme, onChangeTheme, onClose }: {
  theme: Theme;
  onChangeTheme: (t: Theme) => void;
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
          <StaticBoard theme={theme} />
          <div className="theme-picker-options">
            {THEMES.map(t => (
              <button
                key={t}
                type="button"
                className={`theme-picker-option${theme === t ? ' theme-picker-option-active' : ''}`}
                onClick={() => onChangeTheme(t)}
              >
                {t.charAt(0).toUpperCase() + t.slice(1)}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
