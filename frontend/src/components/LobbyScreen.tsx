import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export const THEMES = ['classic', 'generated'] as const;
export type Theme = typeof THEMES[number];

export const CLOCK_OPTIONS = [
  { label: 'No Clock', ms: 0 },
  { label: '1 min', ms: 60_000 },
  { label: '10 min', ms: 600_000 },
] as const;

interface Props {
  username: string;
  botType: string;
  theme: Theme;
  colorPreference: string;
  clockMs: number;
  onChangeBotType: (type: string) => void;
  onChangeTheme: (theme: Theme) => void;
  onChangeColorPreference: (pref: string) => void;
  onChangeClockMs: (ms: number) => void;
  onLogout: () => void;
}

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

function ThemePicker({ theme, onChangeTheme, onClose }: { theme: Theme; onChangeTheme: (t: Theme) => void; onClose: () => void }) {
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

export function LobbyScreen({ username, botType, theme, colorPreference: _colorPreference, clockMs, onChangeBotType, onChangeTheme, onChangeColorPreference: _onChangeColorPreference, onChangeClockMs, onLogout }: Props) {
  const [themePickerOpen, setThemePickerOpen] = useState(false);
  const navigate = useNavigate();

  return (
    <div className="lobby">
      <h1 className="lobby-title">Chess</h1>
      <p className="lobby-welcome">Welcome, <strong>{username}</strong></p>

      <div className="lobby-section">
        <span className="lobby-label">Opponent</span>
        <div className="mode-toggle">
          <button type="button" className={`mode-btn${botType === '' ? ' mode-btn-active' : ''}`} onClick={() => onChangeBotType('')}>
            vs Human
          </button>
          <button type="button" className={`mode-btn${botType !== '' ? ' mode-btn-active' : ''}`} onClick={() => onChangeBotType('random')}>
            vs Bot
          </button>
        </div>
      </div>

      <div className="lobby-section">
        <span className="lobby-label">Time Control</span>
        <div className="mode-toggle">
          {CLOCK_OPTIONS.map(opt => (
            <button
              key={opt.ms}
              type="button"
              className={`mode-btn${clockMs === opt.ms ? ' mode-btn-active' : ''}`}
              onClick={() => onChangeClockMs(opt.ms)}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      <div className="lobby-section">
        <span className="lobby-label">Piece Theme</span>
        <button className="theme-current-btn" onClick={() => setThemePickerOpen(true)}>
          <span>{theme.charAt(0).toUpperCase() + theme.slice(1)}</span>
          <span className="theme-current-chevron">▾</span>
        </button>
      </div>

      <button className="start-btn" onClick={() => navigate('/game')}>Start Game</button>
      <button className="logout-btn" type="button" onClick={onLogout}>Log out</button>

      {themePickerOpen && (
        <ThemePicker
          theme={theme}
          onChangeTheme={onChangeTheme}
          onClose={() => setThemePickerOpen(false)}
        />
      )}
    </div>
  );
}
