import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Board } from './Board';
import type { BoardSnapshot, Color } from '../types';

const PLAY_INTERVAL_MS = 800;

export function ReplayViewer() {
  const { gameId } = useParams<{ gameId: string }>();
  const navigate = useNavigate();
  const [snapshots, setSnapshots] = useState<BoardSnapshot[]>([]);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [viewAs, setViewAs] = useState<Color>('WHITE');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    fetch(`/api/games/${gameId}/boards`)
      .then(r => r.ok ? r.json() as Promise<BoardSnapshot[]> : Promise.reject('Failed to load game'))
      .then(data => { setSnapshots(data); setLoading(false); })
      .catch(e => { setError(String(e)); setLoading(false); });
  }, [gameId, navigate]);

  const stopPlay = useCallback(() => {
    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setPlaying(false);
  }, []);

  useEffect(() => {
    if (!playing || snapshots.length === 0) return;
    intervalRef.current = setInterval(() => {
      setIndex(i => {
        if (i >= snapshots.length - 1) { stopPlay(); return i; }
        return i + 1;
      });
    }, PLAY_INTERVAL_MS);
    return () => { if (intervalRef.current !== null) clearInterval(intervalRef.current); };
  }, [playing, snapshots.length, stopPlay]);

  const total = Math.max(0, snapshots.length - 1);
  const snapshot = snapshots[index];

  return (
    <div className="replay-page">
      <div className="replay-nav-bar">
        <button className="back-btn" onClick={() => navigate('/history')}>← Back</button>
        <span className="replay-move-counter">Move {index} / {total}</span>
        <button className="view-toggle" onClick={() => setViewAs(v => v === 'WHITE' ? 'BLACK' : 'WHITE')}>
          View as {viewAs}
        </button>
      </div>

      {loading && <p className="history-status">Loading…</p>}
      {error && <p className="history-status error">{error}</p>}

      {snapshot && (
        <Board
          board={snapshot.board}
          legalMoves={[]}
          lastMove={snapshot.lastMove}
          playerColor={viewAs}
          isMyTurn={false}
          theme={localStorage.getItem('chess_theme') ?? 'classic'}
          onMove={() => {}}
        />
      )}

      <div className="replay-controls">
        <button title="First" onClick={() => { stopPlay(); setIndex(0); }}>⏮</button>
        <button title="Previous" onClick={() => { stopPlay(); setIndex(i => Math.max(0, i - 1)); }}>⏪</button>
        <button title={playing ? 'Pause' : 'Play'} onClick={() => setPlaying(p => !p)}>
          {playing ? '⏸' : '▶'}
        </button>
        <button title="Next" onClick={() => { stopPlay(); setIndex(i => Math.min(total, i + 1)); }}>⏩</button>
        <button title="Last" onClick={() => { stopPlay(); setIndex(total); }}>⏭</button>
      </div>
    </div>
  );
}
