import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ThemePicker, THEMES, type Theme, type BoardTheme } from './ThemePicker';
import type { ActiveGameSummary } from '../types';

export { THEMES, type Theme, type BoardTheme };

export const CLOCK_OPTIONS = [
  { label: 'No Clock', ms: 0 },
  { label: '1 min', ms: 60_000 },
  { label: '10 min', ms: 600_000 },
] as const;

const OPPONENTS = [
  { type: '',        icon: '👤', label: 'Human',   stars: '',    desc: '' },
  { type: 'random',  icon: '🎲', label: 'Random',  stars: '',    desc: 'Random moves' },
  { type: 'alan',    icon: '🤖', label: 'Alan',    stars: '★',   desc: 'Alan Turing — father of computing' },
  { type: 'barbara', icon: '🤖', label: 'Barbara', stars: '★★',  desc: 'Barbara Liskov — OOP pioneer' },
  { type: 'claude',  icon: '🤖', label: 'Claude',  stars: '★★★', desc: 'Claude Shannon — inventor of computer chess' },
] as const;

const COLOR_PREFS = [
  { value: 'WHITE',  label: 'Always White' },
  { value: 'RANDOM', label: 'Random' },
  { value: 'BLACK',  label: 'Always Black' },
] as const;

interface Props {
  username: string;
  botType: string;
  theme: Theme;
  boardTheme: BoardTheme;
  colorPreference: string;
  clockMs: number;
  onChangeBotType: (type: string) => void;
  onChangeTheme: (theme: Theme) => void;
  onChangeBoardTheme: (theme: BoardTheme) => void;
  onChangeColorPreference: (pref: string) => void;
  onChangeClockMs: (ms: number) => void;
  onLogout: () => void;
  onStartGame: () => void;
  onResumeGame: (botType: string, gameId: string) => void;
}

export function LobbyScreen({ username, botType, theme, boardTheme, colorPreference, clockMs, onChangeBotType, onChangeTheme, onChangeBoardTheme, onChangeColorPreference, onChangeClockMs, onLogout, onStartGame, onResumeGame }: Props) {
  const [themePickerOpen, setThemePickerOpen] = useState(false);
  const [eloDisplay, setEloDisplay] = useState<string | null>(null);
  const [activeBotGames, setActiveBotGames] = useState<ActiveGameSummary[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    fetch(`/api/users/${encodeURIComponent(username)}/elo`)
      .then(r => r.ok ? r.json() as Promise<{ elo: number; provisional: boolean }> : null)
      .then(data => {
        if (data) setEloDisplay(`${data.elo}${data.provisional ? '?' : ''}`);
      })
      .catch(() => { /* silently ignore — ELO is cosmetic */ });
  }, [username]);

  useEffect(() => {
    fetch(`/api/users/${encodeURIComponent(username)}/active-games`)
      .then(r => r.ok ? r.json() as Promise<ActiveGameSummary[]> : [])
      .then(games => setActiveBotGames(games.filter(g => g.mode === 'BOT')))
      .catch(() => {});
  }, [username]);

  const activeByBotType = new Map(activeBotGames.map(g => [g.botType, g]));

  const handleNewGame = async (type: string, existingGameId: number) => {
    onChangeBotType(type);
    try {
      await fetch(`/api/users/${encodeURIComponent(username)}/active-games/${existingGameId}/abandon`, { method: 'POST' });
    } catch {
      // Best-effort — if this fails, join() will still refuse a duplicate
      // slot rather than silently orphaning the old game.
    }
    setActiveBotGames(games => games.filter(g => g.gameId !== existingGameId));
    onStartGame();
  };

  return (
    <div className="lobby">
      <h1 className="lobby-title">Chess</h1>
      <p className="lobby-welcome">
        Welcome, <strong>{username}</strong>
        {eloDisplay && <span className="lobby-elo"> · ELO {eloDisplay}</span>}
      </p>

      <div className="lobby-section">
        <span className="lobby-label">Opponent</span>
        <div className="opponent-cards">
          {OPPONENTS.map(opp => {
            const active = opp.type ? activeByBotType.get(opp.type) : undefined;
            return (
              <div key={opp.type} className="opponent-card-wrap">
                <label className={`opponent-card${botType === opp.type ? ' opponent-card-active' : ''}`}>
                  <input
                    type="radio"
                    name="opponent"
                    value={opp.type}
                    checked={botType === opp.type}
                    onChange={() => onChangeBotType(opp.type)}
                  />
                  <span className="opponent-card-icon">{opp.icon}</span>
                  <span className="opponent-card-label">{opp.label}</span>
                  {opp.stars && <span className="opponent-card-stars">{opp.stars}</span>}
                  {opp.desc && <span className="opponent-card-desc">{opp.desc}</span>}
                </label>
                {active && (
                  <div className="opponent-card-actions">
                    <button type="button" className="opponent-resume-btn" onClick={() => onResumeGame(opp.type, String(active.gameId))}>
                      Resume
                    </button>
                    <button type="button" className="opponent-new-btn" onClick={() => handleNewGame(opp.type, active.gameId)}>
                      New
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      <div className="lobby-section">
        <span className="lobby-label">Play as</span>
        <div className="color-pref-row">
          {COLOR_PREFS.map(pref => (
            <label
              key={pref.value}
              className={`color-pref-option${colorPreference === pref.value ? ' color-pref-active' : ''}`}
            >
              <input
                type="radio"
                name="colorPref"
                value={pref.value}
                checked={colorPreference === pref.value}
                onChange={() => onChangeColorPreference(pref.value)}
              />
              {pref.value === 'RANDOM' ? (
                <div className="pawn-split-wrap">
                  <img className="pawn-split-half pawn-split-white" src={`/images/${theme}/pawn_white.png`} alt="" draggable={false} />
                  <img className="pawn-split-half pawn-split-black" src={`/images/${theme}/pawn_black.png`} alt="" draggable={false} />
                </div>
              ) : (
                <img
                  className="color-pref-pawn"
                  src={`/images/${theme}/pawn_${pref.value.toLowerCase()}.png`}
                  alt={pref.label}
                  draggable={false}
                />
              )}
              <span className="color-pref-label">{pref.label}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="lobby-section">
        <span className="lobby-label">Appearance</span>
        <button className="theme-current-btn" onClick={() => setThemePickerOpen(true)}>
          Theme
        </button>
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

      <button className="start-btn" onClick={onStartGame}>Start Game</button>
      <div className="lobby-secondary-btns">
        <button className="lobby-games-btn" onClick={() => navigate('/history')}>My Games</button>
        <button className="lobby-games-btn" onClick={() => navigate('/profile')}>ELO Profile</button>
      </div>
      <button className="logout-btn" type="button" onClick={onLogout}>Log out</button>

      {themePickerOpen && (
        <ThemePicker
          theme={theme}
          boardTheme={boardTheme}
          onChangeTheme={onChangeTheme}
          onChangeBoardTheme={onChangeBoardTheme}
          onClose={() => setThemePickerOpen(false)}
        />
      )}
    </div>
  );
}
