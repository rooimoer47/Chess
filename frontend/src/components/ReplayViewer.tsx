import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Board } from './Board';
import { EvalBar } from './EvalBar';
import { MoveList } from './MoveList';
import { ReviewArrows, type Arrow } from './ReviewArrows';
import { ReviewPanel } from './ReviewPanel';
import type { BoardTheme } from './ThemePicker';
import type { BoardSnapshot, Color, GameAnalysis } from '../types';
import { BEST_COLOR, CLASSIFICATION_COLOR } from '../review';
import { errorMessage } from '../errors';

const PLAY_INTERVAL_MS = 800;
const ANALYSIS_POLL_MS = 3000;
// The server only reviews games with at least one move by each side.
const MIN_PLIES_TO_ANALYSE = 2;

export function ReplayViewer() {
  const { gameId } = useParams<{ gameId: string }>();
  const navigate = useNavigate();
  const [snapshots, setSnapshots] = useState<BoardSnapshot[]>([]);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [viewAs, setViewAs] = useState<Color>('WHITE');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // null until loaded, and stays null when the game has no review to show (e.g. still in progress).
  const [analysis, setAnalysis] = useState<GameAnalysis | null>(null);
  const [analysisError, setAnalysisError] = useState('');
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    fetch(`/api/games/${gameId}/boards`)
      .then(r => r.ok ? r.json() as Promise<BoardSnapshot[]> : Promise.reject(new Error('Failed to load game')))
      .then(data => { setSnapshots(data); setLoading(false); })
      .catch(e => { setError(errorMessage(e)); setLoading(false); });
  }, [gameId, navigate]);

  const loadAnalysis = useCallback(() => {
    fetch(`/api/games/${gameId}/analysis`)
      .then(r => {
        if (r.status === 404) return null;
        return r.ok ? r.json() as Promise<GameAnalysis> : Promise.reject(new Error('Failed to load the review'));
      })
      .then(data => { setAnalysis(data); setAnalysisError(''); })
      .catch(e => setAnalysisError(errorMessage(e)));
  }, [gameId]);

  useEffect(() => { loadAnalysis(); }, [loadAnalysis]);

  // Keep checking while the server is still working on the review.
  const inProgress = analysis?.status === 'ENGINE' || analysis?.status === 'COMMENTING';
  useEffect(() => {
    if (!inProgress) return;
    const timer = setTimeout(loadAnalysis, ANALYSIS_POLL_MS);
    return () => clearTimeout(timer);
  }, [inProgress, analysis, loadAnalysis]);

  const requestAnalysis = () => {
    setAnalysisError('');
    fetch(`/api/games/${gameId}/analysis`, { method: 'POST' })
      .then(async r => {
        if (!r.ok) throw new Error((await r.text()) || 'Could not start the analysis');
        loadAnalysis();
      })
      .catch(e => setAnalysisError(errorMessage(e)));
  };

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

  const goTo = useCallback((ply: number) => {
    stopPlay();
    setIndex(Math.max(0, Math.min(total, ply)));
  }, [stopPlay, total]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') goTo(index - 1);
      else if (e.key === 'ArrowRight') goTo(index + 1);
      else if (e.key === 'Home') goTo(0);
      else if (e.key === 'End') goTo(total);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [goTo, index, total]);

  const reviewed = analysis !== null && analysis.moves.length > 0;
  const move = reviewed && index > 0 ? analysis.moves[index - 1] ?? null : null;
  const arrows: Arrow[] = [];
  if (move && move.classification !== 'UNKNOWN') {
    arrows.push({ from: move.from, to: move.to, color: CLASSIFICATION_COLOR[move.classification], testId: 'arrow-played' });
    if (move.bestFrom && move.bestTo && move.bestUci !== move.uci) {
      arrows.push({ from: move.bestFrom, to: move.bestTo, color: BEST_COLOR, testId: 'arrow-best' });
    }
  }

  return (
    <div className="replay-page">
      <div className="replay-nav-bar">
        <button type="button" className="back-btn" onClick={() => navigate('/history')}>← Back</button>
        <span className="replay-move-counter">Move {index} / {total}</span>
        <button type="button" className="view-toggle" onClick={() => setViewAs(v => v === 'WHITE' ? 'BLACK' : 'WHITE')}>
          View as {viewAs}
        </button>
      </div>

      {loading && <p className="history-status">Loading…</p>}
      {error && <p className="history-status error">{error}</p>}

      {snapshot && (
        <div className="review-layout">
          <div className="review-board-row">
            {reviewed && (
              <EvalBar evaluation={analysis.evals[index] ?? null} whiteToMove={index % 2 === 0} viewAs={viewAs} />
            )}
            <div className="board-wrap">
              <Board
                board={snapshot.board}
                legalMoves={[]}
                lastMove={snapshot.lastMove}
                playerColor={viewAs}
                isMyTurn={false}
                theme={localStorage.getItem('chess_theme') ?? 'classic'}
                boardTheme={(localStorage.getItem('chess_board_theme') ?? 'classic') as BoardTheme}
                onMove={() => {}}
              />
              <ReviewArrows arrows={arrows} viewAs={viewAs} />
            </div>
          </div>

          {reviewed && (
            <div className="review-side">
              <ReviewPanel move={move} />
              <MoveList moves={analysis.moves} currentPly={index} onSelect={goTo} />
            </div>
          )}
        </div>
      )}

      <div className="replay-controls">
        <button type="button" title="First" onClick={() => goTo(0)}>⏮</button>
        <button type="button" title="Previous" onClick={() => goTo(index - 1)}>⏪</button>
        <button type="button" title={playing ? 'Pause' : 'Play'} onClick={() => setPlaying(p => !p)}>
          {playing ? '⏸' : '▶'}
        </button>
        <button type="button" title="Next" onClick={() => goTo(index + 1)}>⏩</button>
        <button type="button" title="Last" onClick={() => goTo(total)}>⏭</button>
      </div>

      <ReviewStatus
        analysis={analysis}
        canAnalyse={total >= MIN_PLIES_TO_ANALYSE}
        error={analysisError}
        onAnalyse={requestAnalysis}
      />
    </div>
  );
}

function ReviewStatus({ analysis, canAnalyse, error, onAnalyse }: Readonly<{
  analysis: GameAnalysis | null;
  canAnalyse: boolean;
  error: string;
  onAnalyse: () => void;
}>) {
  if (error) return <p className="review-status error" data-testid="review-status">{error}</p>;
  if (!analysis) return null;
  switch (analysis.status) {
    case 'NONE':
      return canAnalyse ? (
        <button type="button" className="analyse-btn" data-testid="analyse-btn" onClick={onAnalyse}>
          Analyse this game
        </button>
      ) : null;
    case 'ENGINE':
      return (
        <p className="review-status" data-testid="review-status">
          Analysing… {analysis.positionsDone} / {analysis.positionsTotal} positions
        </p>
      );
    case 'COMMENTING':
      return <p className="review-status" data-testid="review-status">Writing comments…</p>;
    case 'FAILED':
      return (
        <p className="review-status error" data-testid="review-status">
          The analysis failed.{' '}
          <button type="button" className="analyse-btn" data-testid="analyse-btn" onClick={onAnalyse}>Try again</button>
        </p>
      );
    default:
      return null;
  }
}
