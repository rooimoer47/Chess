import { useState, useEffect } from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { CapturedPieces } from './components/CapturedPieces';
import { PromotionDialog } from './components/PromotionDialog';
import { LoginScreen } from './components/LoginScreen';
import { HistoryPage } from './components/HistoryPage';
import { ReplayViewer } from './components/ReplayViewer';
import './App.css';

export default function App() {
  const [username, setUsername] = useState<string | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [botMode, setBotMode] = useState(() => localStorage.getItem('chess_bot_mode') === 'true');
  const [gameKey, setGameKey] = useState(0);

  useEffect(() => {
    fetch('/api/auth/me')
      .then(r => r.ok ? r.json() as Promise<{ username: string }> : null)
      .then(data => { if (data) setUsername(data.username); })
      .catch(() => {})
      .finally(() => setAuthChecked(true));
  }, []);

  const handleLogin = (u: string, bot: boolean) => {
    localStorage.setItem('chess_bot_mode', String(bot));
    setUsername(u);
    setBotMode(bot);
  };

  const handleLogout = () => {
    fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
    localStorage.removeItem('chess_bot_mode');
    setUsername(null);
  };

  const handlePlayAgain = (newBotMode: boolean) => {
    localStorage.setItem('chess_bot_mode', String(newBotMode));
    setBotMode(newBotMode);
    setGameKey(k => k + 1);
  };

  if (!authChecked) return null;

  if (!username) {
    return (
      <Routes>
        <Route path="*" element={<LoginScreen onLogin={handleLogin} />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/game" replace />} />
      <Route path="/game" element={
        <ChessGame key={gameKey} username={username} botMode={botMode}
          onPlayAgain={handlePlayAgain} onLogout={handleLogout} onAuthFailed={handleLogout} />
      } />
      <Route path="/history" element={<HistoryPage username={username} />} />
      <Route path="/history/:gameId" element={<ReplayViewer />} />
      <Route path="*" element={<Navigate to="/game" replace />} />
    </Routes>
  );
}

const THEMES = ['classic', 'generated'] as const;
type Theme = typeof THEMES[number];

function ChessGame({ username, botMode, onPlayAgain, onLogout, onAuthFailed }: {
  username: string;
  botMode: boolean;
  onPlayAgain: (botMode: boolean) => void;
  onLogout: () => void;
  onAuthFailed: () => void;
}) {
  const [theme, setTheme] = useState<Theme>('classic');
  const navigate = useNavigate();
  const {
    connected,
    gameStarted,
    playerColor,
    board,
    currentTurn,
    status,
    legalMoves,
    lastMove,
    capturedByWhite,
    capturedByBlack,
    promotionPending,
    statusMessage,
    sendMove,
    sendPromotion,
    sendResign,
    sendDrawOffer,
    sendDrawResponse,
    drawOfferedByOpponent,
    drawOfferPending,
  } = useChessSocket(botMode, onAuthFailed);

  if (!connected) {
    return <div className="screen"><p>Connecting to server…</p></div>;
  }

  if (!gameStarted) {
    return (
      <div className="screen">
        <p>You are: <strong>{playerColor ?? '…'}</strong></p>
        <p>Waiting for opponent to connect…</p>
      </div>
    );
  }

  const isGameOver = status === 'CHECKMATE' || status === 'STALEMATE' || status === 'RESIGNED'
    || status === 'THREEFOLD_REPETITION' || status === 'FIFTY_MOVE_RULE'
    || status === 'INSUFFICIENT_MATERIAL' || status === 'DRAW_AGREED';
  const isMyTurn = currentTurn === playerColor;

  const myLost       = playerColor === 'WHITE' ? capturedByBlack : capturedByWhite;
  const opponentLost = playerColor === 'WHITE' ? capturedByWhite : capturedByBlack;
  const opponentColor = playerColor === 'WHITE' ? 'BLACK' : 'WHITE';

  return (
    <div className="app">
      <div className="info-bar">
        <span>You: <strong>{username}</strong></span>
        <select
          className="theme-select"
          value={theme}
          onChange={e => setTheme(e.target.value as Theme)}
          aria-label="Piece theme"
        >
          {THEMES.map(t => (
            <option key={t} value={t}>{t.charAt(0).toUpperCase() + t.slice(1)}</option>
          ))}
        </select>
        <span className={`turn-indicator ${isMyTurn ? 'my-turn' : ''}`}>
          {isGameOver ? '—' : isMyTurn ? 'Your turn' : "Opponent's turn"}
        </span>
        {!isGameOver && (
          <>
            <button className="draw-btn" onClick={sendDrawOffer}
              disabled={drawOfferPending || drawOfferedByOpponent}>
              {drawOfferPending ? 'Draw offered…' : 'Offer Draw'}
            </button>
            <button className="resign-btn" onClick={sendResign}>Resign</button>
          </>
        )}
        <button className="history-btn" onClick={() => navigate('/history')}>My Games</button>
        <button className="logout-btn" onClick={onLogout}>Logout</button>
      </div>

      {statusMessage && (
        <div className={`status-message ${isGameOver ? 'game-over' : ''}`}>
          {statusMessage}
        </div>
      )}

      {drawOfferedByOpponent && !isGameOver && (
        <div className="draw-offer-bar">
          <span>Opponent offers a draw</span>
          <button className="draw-accept-btn" onClick={() => sendDrawResponse(true)}>Accept</button>
          <button className="draw-decline-btn" onClick={() => sendDrawResponse(false)}>Decline</button>
        </div>
      )}

      <CapturedPieces pieces={myLost} color={playerColor!} theme={theme} />

      <Board
        board={board}
        legalMoves={legalMoves}
        lastMove={lastMove}
        playerColor={playerColor!}
        isMyTurn={isMyTurn && !isGameOver}
        theme={theme}
        onMove={sendMove}
      />

      <CapturedPieces pieces={opponentLost} color={opponentColor} theme={theme} />

      {isGameOver && (
        <div className="play-again">
          <span>Play again?</span>
          <div className="mode-toggle">
            <button type="button" className="mode-btn" onClick={() => onPlayAgain(false)}>vs Human</button>
            <button type="button" className="mode-btn" onClick={() => onPlayAgain(true)}>vs Bot</button>
          </div>
        </div>
      )}

      {promotionPending && playerColor && (
        <PromotionDialog color={playerColor} theme={theme} onChoice={sendPromotion} />
      )}
    </div>
  );
}
