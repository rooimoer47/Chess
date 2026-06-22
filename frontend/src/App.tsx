import { useState } from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { CapturedPieces } from './components/CapturedPieces';
import { PromotionDialog } from './components/PromotionDialog';
import { LoginScreen } from './components/LoginScreen';
import { HistoryPage } from './components/HistoryPage';
import { ReplayViewer } from './components/ReplayViewer';
import { isTokenExpired } from './utils/token';
import './App.css';

function loadToken(): string | null {
  const t = localStorage.getItem('chess_token');
  if (t && isTokenExpired(t)) {
    localStorage.removeItem('chess_token');
    localStorage.removeItem('chess_bot_mode');
    return null;
  }
  return t;
}

export default function App() {
  const [token, setToken] = useState<string | null>(loadToken);
  const [botMode, setBotMode] = useState(() => localStorage.getItem('chess_bot_mode') === 'true');
  const [gameKey, setGameKey] = useState(0);

  const handleLogin = (t: string, bot: boolean) => {
    localStorage.setItem('chess_token', t);
    localStorage.setItem('chess_bot_mode', String(bot));
    setToken(t);
    setBotMode(bot);
  };

  const handleLogout = () => {
    localStorage.removeItem('chess_token');
    localStorage.removeItem('chess_bot_mode');
    setToken(null);
  };

  const handlePlayAgain = (newBotMode: boolean) => {
    localStorage.setItem('chess_bot_mode', String(newBotMode));
    setBotMode(newBotMode);
    setGameKey(k => k + 1);
  };

  if (!token) {
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
        <ChessGame key={gameKey} token={token} botMode={botMode}
          onPlayAgain={handlePlayAgain} onLogout={handleLogout} onAuthFailed={handleLogout} />
      } />
      <Route path="/history" element={<HistoryPage />} />
      <Route path="/history/:gameId" element={<ReplayViewer />} />
      <Route path="*" element={<Navigate to="/game" replace />} />
    </Routes>
  );
}

const THEMES = ['classic', 'generated'] as const;
type Theme = typeof THEMES[number];

function ChessGame({ token, botMode, onPlayAgain, onLogout, onAuthFailed }: {
  token: string;
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
  } = useChessSocket(token, botMode, onAuthFailed);

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
        <span>You: <strong>{playerColor}</strong></span>
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
