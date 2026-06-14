export const THEMES = ['classic', 'generated'] as const;
export type Theme = typeof THEMES[number];

interface Props {
  username: string;
  botMode: boolean;
  theme: Theme;
  onChangeBotMode: (bot: boolean) => void;
  onChangeTheme: (theme: Theme) => void;
  onStartGame: () => void;
  onLogout: () => void;
}

export function LobbyScreen({ username, botMode, theme, onChangeBotMode, onChangeTheme, onStartGame, onLogout }: Props) {
  return (
    <div className="lobby">
      <h1 className="lobby-title">Chess</h1>
      <p className="lobby-welcome">Welcome, <strong>{username}</strong></p>

      <div className="lobby-section">
        <span className="lobby-label">Opponent</span>
        <div className="mode-toggle">
          <button type="button" className={`mode-btn${!botMode ? ' mode-btn-active' : ''}`} onClick={() => onChangeBotMode(false)}>
            vs Human
          </button>
          <button type="button" className={`mode-btn${botMode ? ' mode-btn-active' : ''}`} onClick={() => onChangeBotMode(true)}>
            vs Bot
          </button>
        </div>
      </div>

      <div className="lobby-section">
        <span className="lobby-label">Piece Theme</span>
        <div className="theme-options">
          {THEMES.map(t => (
            <button
              key={t}
              type="button"
              className={`theme-option${theme === t ? ' theme-option-active' : ''}`}
              onClick={() => onChangeTheme(t)}
            >
              <img src={`/images/${t}/queen_white.png`} alt={t} className="theme-preview" draggable={false} />
              <span>{t.charAt(0).toUpperCase() + t.slice(1)}</span>
            </button>
          ))}
        </div>
      </div>

      <button className="start-btn" onClick={onStartGame}>Start Game</button>
      <button className="logout-btn" type="button" onClick={onLogout}>Log out</button>
    </div>
  );
}
