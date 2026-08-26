import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import type { GameSummary, Variant } from '../types';
import { errorMessage } from '../errors';

interface EloSummary {
  elo: number;
  gamesRated: number;
  provisional: boolean;
  elo960: number;
  gamesRated960: number;
  provisional960: boolean;
}

/** The one rating track the page is showing, or zeroes before the fetch lands. */
function trackOf(summary: EloSummary | null, is960: boolean) {
  if (!summary) return { rating: 0, ratedGames: 0, isProvisional: false };
  return is960
    ? { rating: summary.elo960, ratedGames: summary.gamesRated960, isProvisional: summary.provisional960 }
    : { rating: summary.elo, ratedGames: summary.gamesRated, isProvisional: summary.provisional };
}

function deltaClass(delta: number): string {
  if (delta > 0) return 'result-win';
  if (delta < 0) return 'result-loss';
  return '';
}

interface EloHistoryEntry {
  gameId: number;
  eloAfter: number;
  delta: number;
  variant: Variant;
  recordedAt: string;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatResult(game: GameSummary): string {
  if (!game.result) return 'In progress';
  const draws = ['STALEMATE', 'THREEFOLD_REPETITION', 'FIFTY_MOVE_RULE', 'INSUFFICIENT_MATERIAL', 'DRAW_AGREED'];
  if (draws.includes(game.result)) return 'Draw';
  if ((game.result === 'CHECKMATE' || game.result === 'RESIGNED') && game.winnerColor) {
    return game.winnerColor === game.playerColor ? 'Win' : 'Loss';
  }
  return game.result;
}

export function EloHistoryPage({ username, variant: initialVariant = 'STANDARD' }: Readonly<{ username: string; variant?: Variant }>) {
  const [summary, setSummary] = useState<EloSummary | null>(null);
  const [history, setHistory] = useState<EloHistoryEntry[]>([]);
  const [gamesMap, setGamesMap] = useState<Map<number, GameSummary>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [variant, setVariant] = useState<Variant>(initialVariant);
  const navigate = useNavigate();

  useEffect(() => {
    setLoading(true);
    const enc = encodeURIComponent(username);
    Promise.all([
      fetch(`/api/users/${enc}/elo`).then(r => r.ok ? r.json() as Promise<EloSummary> : Promise.reject(new Error('Failed to load ELO'))),
      fetch(`/api/users/${enc}/elo-history?limit=50&variant=${variant}`).then(r => r.ok ? r.json() as Promise<EloHistoryEntry[]> : Promise.reject(new Error('Failed to load ELO history'))),
      fetch(`/api/users/${enc}/games?variant=${variant}`).then(r => r.ok ? r.json() as Promise<GameSummary[]> : Promise.reject(new Error('Failed to load games'))),
    ])
      .then(([sum, hist, games]) => {
        setSummary(sum);
        setHistory(hist);
        const map = new Map<number, GameSummary>();
        games.forEach(g => map.set(g.id, g));
        setGamesMap(map);
        setLoading(false);
      })
      .catch(e => { setError(errorMessage(e)); setLoading(false); });
  }, [username, variant]);

  // The two tracks are separate ratings, so the page shows one at a time —
  // interleaving them would make both the chart and the "ELO before → after"
  // column jump between unrelated numbers.
  const is960 = variant === 'CHESS960';
  const { rating, ratedGames, isProvisional } = trackOf(summary, is960);

  // Chart needs oldest-first; history arrives newest-first
  const chartData = [...history].reverse().map((e, i) => ({ game: i + 1, elo: e.eloAfter }));

  return (
    <div className="history-page">
      <div className="history-header">
        <button className="back-btn" onClick={() => navigate('/lobby')}>← Back to Lobby</button>
        <h2>{is960 ? 'Chessnuts960' : 'Chessnuts'} ELO Rating</h2>
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
      {error   && <p className="history-status error">{error}</p>}

      {!loading && !error && summary && (
        <>
          <div className="elo-summary">
            <span className="elo-rating">
              {rating}
              {isProvisional && <span className="elo-provisional">?</span>}
            </span>
            <span className="elo-games">
              {ratedGames} rated game{ratedGames !== 1 ? 's' : ''}
            </span>
          </div>

          {chartData.length >= 2 && (
            <div className="elo-chart">
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                  <XAxis dataKey="game" tick={{ fill: '#888', fontSize: 12 }} />
                  <YAxis tick={{ fill: '#888', fontSize: 12 }} domain={['auto', 'auto']} width={48} />
                  <Tooltip
                    contentStyle={{ background: '#1e1e1e', border: '1px solid #444', borderRadius: 6 }}
                    labelStyle={{ color: '#888' }}
                    itemStyle={{ color: '#eee' }}
                  />
                  <Line type="monotone" dataKey="elo" stroke="#7fc97f" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}

          {history.length === 0 && (
            <p className="history-status">No rated games yet.</p>
          )}

          {history.length > 0 && (
            <table className="history-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Opponent</th>
                  <th>Result</th>
                  <th>ELO</th>
                  <th>Change</th>
                </tr>
              </thead>
              <tbody>
                {history.map(entry => {
                  const game = gamesMap.get(entry.gameId);
                  const eloBefore = entry.eloAfter - entry.delta;
                  const deltaLabel = entry.delta > 0 ? `+${entry.delta}` : String(entry.delta);
                  const deltaCls = deltaClass(entry.delta);
                  return (
                    <tr key={entry.gameId}>
                      <td>{formatDate(entry.recordedAt)}</td>
                      <td>{game?.opponent ?? '—'}</td>
                      <td>{game ? formatResult(game) : '—'}</td>
                      <td className="elo-range">{eloBefore} → {entry.eloAfter}</td>
                      <td className={deltaCls}>{deltaLabel}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
}
