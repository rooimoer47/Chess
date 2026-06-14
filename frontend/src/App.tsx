import { useState } from 'react';
import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { CapturedPieces } from './components/CapturedPieces';
import { PromotionDialog } from './components/PromotionDialog';
import { LoginScreen } from './components/LoginScreen';
import { LobbyScreen } from './components/LobbyScreen';
import type { Theme } from './components/LobbyScreen';
import './App.css';

type Screen = 'login' | 'lobby' | 'game';

export default function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('chess_token'));
  const [username, setUsername] = useState(() => localStorage.getItem('chess_username') ?? '');
  const [screen, setScreen] = useState<Screen>(() => localStorage.getItem('chess_token') ? 'lobby' : 'login');
  const [botMode, setBotMode] = useState(() => localStorage.getItem('chess_bot_mode') === 'true');
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem('chess_theme') as Theme | null) ?? 'classic');
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
        onChangeBotMode={handleBotModeChange}
        onChangeTheme={handleThemeChange}
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
      onBackToLobby={handleBackToLobby}
    />
  );
}

function ChessGame({ token, botMode, theme, onBackToLobby }: { token: string; botMode: boolean; theme: Theme; onBackToLobby: () => void }) {
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
  } = useChessSocket(token, botMode);

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

  const isGameOver = status === 'CHECKMATE' || status === 'STALEMATE' || status === 'RESIGNED' || status === 'THREEFOLD_REPETITION' || status === 'FIFTY_MOVE_RULE' || status === 'INSUFFICIENT_MATERIAL' || status === 'DRAW_AGREED';
  const isMyTurn = currentTurn === playerColor;

  const myLost       = playerColor === 'WHITE' ? capturedByBlack : capturedByWhite;
  const opponentLost = playerColor === 'WHITE' ? capturedByWhite : capturedByBlack;
  const opponentColor = playerColor === 'WHITE' ? 'BLACK' : 'WHITE';

  return (
    <div className="app">
      <div className="info-bar">
        <span>You: <strong>{playerColor}</strong></span>
        <span className={`turn-indicator ${isMyTurn ? 'my-turn' : ''}`}>
          {isGameOver ? '—' : isMyTurn ? 'Your turn' : "Opponent's turn"}
        </span>
        {!isGameOver && (
          <>
            <button
              className="draw-btn"
              onClick={sendDrawOffer}
              disabled={drawOfferPending || drawOfferedByOpponent}
            >
              {drawOfferPending ? 'Draw offered…' : 'Offer Draw'}
            </button>
            <button className="resign-btn" onClick={sendResign}>Resign</button>
          </>
        )}
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
        <button className="back-to-lobby-btn" onClick={onBackToLobby}>Back to Lobby</button>
      )}

      {promotionPending && playerColor && (
        <PromotionDialog color={playerColor} theme={theme} onChoice={sendPromotion} />
      )}
    </div>
  );
}
