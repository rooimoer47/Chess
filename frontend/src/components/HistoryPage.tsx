import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import type { GameSummary, Variant } from '../types';
import { errorMessage } from '../errors';

function formatResult(game: GameSummary): { label: string; cls: string } {
  if (!game.result) return { label: 'In progress', cls: '' };
  const draws = ['STALEMATE', 'THREEFOLD_REPETITION', 'FIFTY_MOVE_RULE', 'INSUFFICIENT_MATERIAL', 'DRAW_AGREED'];
  if (draws.includes(game.result)) return { label: 'Draw', cls: 'result-draw' };
  if (game.result === 'CHECKMATE' || game.result === 'RESIGNED') {
    if (!game.winnerColor) return { label: game.result, cls: '' };
    return game.winnerColor === game.playerColor
      ? { label: 'Win', cls: 'result-win' }
      : { label: 'Loss', cls: 'result-loss' };
  }
  return { label: game.result, cls: '' };
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function HistoryPage({ username, variant: initialVariant = 'STANDARD' }: Readonly<{ username: string; variant?: Variant }>) {
  const [games, setGames] = useState<GameSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [variant, setVariant] = useState<Variant>(initialVariant);
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    fetch(`/api/users/${encodeURIComponent(username)}/games?variant=${variant}`)
      .then(r => r.ok ? r.json() as Promise<GameSummary[]> : Promise.reject(new Error('Failed to load games')))
      .then(data => { setGames(data); setLoading(false); })
      .catch(e => { setError(errorMessage(e)); setLoading(false); });
  }, [username, variant]);

  return (
    <div className="history-page">
      <div className="history-header">
        <button type="button" className="back-btn" onClick={() => navigate('/lobby')}>← Back to Lobby</button>
        <h2>My {variant === 'CHESS960' ? 'Chessnuts960' : 'Chessnuts'} Games</h2>
      </div>

      <div className="variant-toggle mode-toggle">
        <button
          type="button"
          className={`mode-btn${variant === 'STANDARD' ? ' mode-btn-active' : ''}`}
          onClick={() => setVariant('STANDARD')}
        >
          Standard
        </button>
        <button
          type="button"
          className={`mode-btn${variant === 'CHESS960' ? ' mode-btn-active' : ''}`}
          onClick={() => setVariant('CHESS960')}
        >
          ♟ Chess960
        </button>
      </div>

      {loading && <p className="history-status">Loading…</p>}
      {error && <p className="history-status error">{error}</p>}
      {!loading && !error && games.length === 0 && (
        <p className="history-status">No completed games yet.</p>
      )}

      {games.length > 0 && (
        <table className="history-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Mode</th>
              <th>Opponent</th>
              <th>Color</th>
              <th>Result</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {games.map(game => {
              const { label, cls } = formatResult(game);
              return (
                <tr key={game.id}>
                  <td>{formatDate(game.startedAt)}</td>
                  <td>{game.mode === 'BOT' ? 'vs Bot' : 'vs Human'}</td>
                  <td>{game.opponent}</td>
                  <td>{game.playerColor}</td>
                  <td className={cls}>{label}</td>
                  <td>
                    <button type="button" className="replay-btn" onClick={() => navigate(`/history/${game.id}`)}>
                      Replay
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
