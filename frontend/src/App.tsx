import { useState, useEffect, useRef } from 'react';
import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { CapturedPieces } from './components/CapturedPieces';
import { PromotionDialog } from './components/PromotionDialog';
import { LoginScreen } from './components/LoginScreen';
import { LobbyScreen } from './components/LobbyScreen';
import type { Theme } from './components/LobbyScreen';
import type { Color } from './types';
import './App.css';

type Screen = 'login' | 'lobby' | 'game';

export default function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('chess_token'));
  const [username, setUsername] = useState(() => localStorage.getItem('chess_username') ?? '');
  const [screen, setScreen] = useState<Screen>(() => localStorage.getItem('chess_token') ? 'lobby' : 'login');
  const [botMode, setBotMode] = useState(() => localStorage.getItem('chess_bot_mode') === 'true');
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem('chess_theme') as Theme | null) ?? 'classic');
  const [clockMs, setClockMs] = useState(() => Number(localStorage.getItem('chess_clock_ms') ?? '0'));
  const [gameKey, setGameKey] = useState(0);

  const handleLogin = (t: string, u: string) => {
    localStorage.setItem('chess_token', t);
    localStorage.setItem('chess_username', u);
    setToken(t);
    setUsername(u);
    setScreen('lobby');
  };

  const handleLogout = () => {
    localStorage.removeItem('chess_token');
    localStorage.removeItem('chess_username');
    localStorage.removeItem('chess_bot_mode');
    localStorage.removeItem('chess_theme');
    localStorage.removeItem('chess_clock_ms');
    setToken(null);
    setUsername('');
    setScreen('login');
  };

  const handleBotModeChange = (bot: boolean) => {
    localStorage.setItem('chess_bot_mode', String(bot));
    setBotMode(bot);
  };

  const handleThemeChange = (t: Theme) => {
    localStorage.setItem('chess_theme', t);
    setTheme(t);
  };

  const handleClockMsChange = (ms: number) => {
    localStorage.setItem('chess_clock_ms', String(ms));
    setClockMs(ms);
  };

  const handleStartGame = () => {
    setGameKey(k => k + 1);
    setScreen('game');
  };

  const handleBackToLobby = () => {
    setScreen('lobby');
  };

  if (screen === 'login' || !token) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  if (screen === 'lobby') {
    return (
      <LobbyScreen
        username={username}
        botMode={botMode}
        theme={theme}
        clockMs={clockMs}
        onChangeBotMode={handleBotModeChange}
        onChangeTheme={handleThemeChange}
        onChangeClockMs={handleClockMsChange}
        onStartGame={handleStartGame}
        onLogout={handleLogout}
      />
    );
  }

  return (
    <ChessGame
      key={gameKey}
      token={token}
      botMode={botMode}
      theme={theme}
      clockMs={clockMs}
      onBackToLobby={handleBackToLobby}
    />
  );
}

function formatTime(ms: number): string {
  const totalSeconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function ClockDisplay({ timeMs, active }: { timeMs: number | null; active: boolean }) {
  if (timeMs === null) return null;
  const lowTime = active && timeMs < 30_000;
  return (
    <div className={`clock${active ? ' clock-active' : ' clock-inactive'}${lowTime ? ' clock-low' : ''}`}>
      {formatTime(timeMs)}
    </div>
  );
}

function ChessGame({ token, botMode, theme, clockMs, onBackToLobby }: {
  token: string; botMode: boolean; theme: Theme; clockMs: number; onBackToLobby: () => void;
}) {
  const {
    connected, gameStarted, playerColor, board, currentTurn, status,
    legalMoves, lastMove, capturedByWhite, capturedByBlack, promotionPending,
    statusMessage, sendMove, sendPromotion, sendResign, sendDrawOffer,
    sendDrawResponse, drawOfferedByOpponent, drawOfferPending,
    whiteTimeMs, blackTimeMs, serverUpdateTime, sendFlag, serverError,
  } = useChessSocket(token, botMode, clockMs);

  const [tick, setTick] = useState(0);
  const flagSentRef = useRef(false);

  const isGameOver = status === 'CHECKMATE' || status === 'STALEMATE' || status === 'RESIGNED'
    || status === 'THREEFOLD_REPETITION' || status === 'FIFTY_MOVE_RULE'
    || status === 'INSUFFICIENT_MATERIAL' || status === 'DRAW_AGREED' || status === 'TIMEOUT';

  // Reset flag guard when turn changes
  useEffect(() => { flagSentRef.current = false; }, [currentTurn]);

  // Drive countdown re-renders every 100ms when a clock is active
  useEffect(() => {
    if (whiteTimeMs === null || isGameOver) return;
    const id = setInterval(() => setTick(t => t + 1), 100);
    return () => clearInterval(id);
  }, [whiteTimeMs !== null, isGameOver]); // eslint-disable-line react-hooks/exhaustive-deps

  const isMyTurn = currentTurn === playerColor;
  const myColor = playerColor as Color;
  const opponentColor = (playerColor === 'WHITE' ? 'BLACK' : 'WHITE') as Color;

  function getDisplayMs(color: Color): number | null {
    const serverMs = color === 'WHITE' ? whiteTimeMs : blackTimeMs;
    if (serverMs === null || serverUpdateTime === null) return null;
    if (currentTurn !== color || isGameOver) return serverMs;
    return Math.max(0, serverMs - (Date.now() - serverUpdateTime));
  }

  const myDisplayMs = getDisplayMs(myColor);
  const opponentDisplayMs = getDisplayMs(opponentColor);

  // Send flag when my clock reaches zero
  useEffect(() => {
    if (!isMyTurn || myDisplayMs === null || isGameOver || flagSentRef.current) return;
    if (myDisplayMs <= 0) {
      flagSentRef.current = true;
      sendFlag();
    }
  }, [tick]); // eslint-disable-line react-hooks/exhaustive-deps

  const myLost       = playerColor === 'WHITE' ? capturedByBlack : capturedByWhite;
  const opponentLost = playerColor === 'WHITE' ? capturedByWhite : capturedByBlack;

  if (!connected) {
    return (
      <div className="screen">
        {serverError ? (
          <>
            <p className="login-error">{serverError}</p>
            <br />
            <button className="back-to-lobby-btn" onClick={onBackToLobby}>Back to Lobby</button>
          </>
        ) : (
          <p>Connecting to server…</p>
        )}
      </div>
    );
  }

  if (!gameStarted) {
    return (
      <div className="screen">
        <p>You are: <strong>{playerColor ?? '…'}</strong></p>
        <p>Waiting for opponent to connect…</p>
      </div>
    );
  }

  return (
    <div className="app">
      <div className="info-bar">
        <span>You: <strong>{playerColor}</strong></span>
        <span className={`turn-indicator ${isMyTurn ? 'my-turn' : ''}`}>
          {isGameOver ? '—' : isMyTurn ? 'Your turn' : "Opponent's turn"}
        </span>
        {!isGameOver && (
          <>
            <button className="draw-btn" onClick={sendDrawOffer} disabled={drawOfferPending || drawOfferedByOpponent}>
              {drawOfferPending ? 'Draw offered…' : 'Offer Draw'}
            </button>
            <button className="resign-btn" onClick={sendResign}>Resign</button>
          </>
        )}
      </div>

      {statusMessage && (
        <div className={`status-message ${isGameOver ? 'game-over' : ''}`}>{statusMessage}</div>
      )}

      {drawOfferedByOpponent && !isGameOver && (
        <div className="draw-offer-bar">
          <span>Opponent offers a draw</span>
          <button className="draw-accept-btn" onClick={() => sendDrawResponse(true)}>Accept</button>
          <button className="draw-decline-btn" onClick={() => sendDrawResponse(false)}>Decline</button>
        </div>
      )}

      <div className="player-row">
        <CapturedPieces pieces={opponentLost} color={opponentColor} theme={theme} />
        <ClockDisplay timeMs={opponentDisplayMs} active={!isMyTurn && !isGameOver} />
      </div>

      <Board
        board={board}
        legalMoves={legalMoves}
        lastMove={lastMove}
        playerColor={playerColor!}
        isMyTurn={isMyTurn && !isGameOver}
        theme={theme}
        onMove={sendMove}
      />

      <div className="player-row">
        <CapturedPieces pieces={myLost} color={playerColor!} theme={theme} />
        <ClockDisplay timeMs={myDisplayMs} active={isMyTurn && !isGameOver} />
      </div>

      {isGameOver && (
        <button className="back-to-lobby-btn" onClick={onBackToLobby}>Back to Lobby</button>
      )}

      {promotionPending && playerColor && (
        <PromotionDialog color={playerColor} theme={theme} onChoice={sendPromotion} />
      )}
    </div>
  );
}
