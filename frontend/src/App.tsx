import { useState, useEffect } from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { CapturedPieces } from './components/CapturedPieces';
import { PromotionDialog } from './components/PromotionDialog';
import { LoginScreen } from './components/LoginScreen';
import { LobbyScreen } from './components/LobbyScreen';
import { type Theme, type BoardTheme } from './components/ThemePicker';
import { HistoryPage } from './components/HistoryPage';
import { ReplayViewer } from './components/ReplayViewer';
import './App.css';

export default function App() {
  const [username, setUsername] = useState<string | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [botType, setBotType] = useState('');
  const [theme, setTheme] = useState<Theme>('classic');
  const [boardTheme, setBoardTheme] = useState<BoardTheme>('classic');
  const [colorPreference, setColorPreference] = useState('RANDOM');
  const [clockMs, setClockMs] = useState(0);

  useEffect(() => {
    fetch('/api/auth/me')
      .then(r => r.ok ? r.json() as Promise<{ username: string }> : null)
      .then(data => { if (data) setUsername(data.username); })
      .catch(() => {})
      .finally(() => setAuthChecked(true));
  }, []);

  useEffect(() => {
    if (!username) return;
    fetch(`/api/users/${encodeURIComponent(username)}/preferences`)
      .then(r => r.ok ? r.json() as Promise<{ theme: string; colorPreference: string; boardTheme: string }> : null)
      .then(prefs => {
        if (prefs) {
          setTheme(prefs.theme as Theme);
          setColorPreference(prefs.colorPreference);
          setBoardTheme(prefs.boardTheme as BoardTheme);
        }
      })
      .catch(() => {});
  }, [username]);

  const handleLogin = (u: string) => setUsername(u);

  const handleLogout = () => {
    fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
    setUsername(null);
  };

  const savePreferences = (t: Theme, bp: BoardTheme, cp: string) => {
    if (username) {
      fetch(`/api/users/${encodeURIComponent(username)}/preferences`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ theme: t, colorPreference: cp, boardTheme: bp }),
      }).catch(() => {});
    }
  };

  const handleChangeTheme = (t: Theme) => {
    setTheme(t);
    savePreferences(t, boardTheme, colorPreference);
  };

  const handleChangeBoardTheme = (t: BoardTheme) => {
    setBoardTheme(t);
    savePreferences(theme, t, colorPreference);
  };

  const handleChangeColorPreference = (pref: string) => {
    setColorPreference(pref);
    savePreferences(theme, boardTheme, pref);
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
      <Route path="/" element={<Navigate to="/lobby" replace />} />
      <Route path="/lobby" element={
        <LobbyScreen
          username={username}
          botType={botType}
          theme={theme}
          boardTheme={boardTheme}
          colorPreference={colorPreference}
          clockMs={clockMs}
          onChangeBotType={setBotType}
          onChangeTheme={handleChangeTheme}
          onChangeBoardTheme={handleChangeBoardTheme}
          onChangeColorPreference={handleChangeColorPreference}
          onChangeClockMs={setClockMs}
          onLogout={handleLogout}
        />
      } />
      <Route path="/game" element={
        <ChessGame
          username={username}
          botType={botType}
          colorPreference={colorPreference}
          theme={theme}
          boardTheme={boardTheme}
          onLogout={handleLogout}
          onAuthFailed={handleLogout}
        />
      } />
      <Route path="/history" element={<HistoryPage username={username} />} />
      <Route path="/history/:gameId" element={<ReplayViewer />} />
      <Route path="*" element={<Navigate to="/lobby" replace />} />
    </Routes>
  );
}

function ChessGame({ username, botType, colorPreference, theme, boardTheme, onLogout, onAuthFailed }: {
  username: string;
  botType: string;
  colorPreference: string;
  theme: Theme;
  boardTheme: BoardTheme;
  onLogout: () => void;
  onAuthFailed: () => void;
}) {
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
    rematchState,
    sendRematchRequest,
    sendRematchDecline,
  } = useChessSocket(botType, colorPreference, onAuthFailed);

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
        boardTheme={boardTheme}
        onMove={sendMove}
      />

      <CapturedPieces pieces={opponentLost} color={opponentColor} theme={theme} />

      {isGameOver && (
        <div className="play-again">
          {rematchState === 'waiting' ? (
            <>
              <p className="rematch-status">Waiting for opponent…</p>
              <button type="button" className="lobby-games-btn" onClick={() => { sendRematchDecline(); navigate('/lobby'); }}>
                Cancel
              </button>
            </>
          ) : rematchState === 'declined' ? (
            <>
              <p className="rematch-status rematch-declined">Opponent did not want a rematch.</p>
              <button type="button" className="start-btn" onClick={() => navigate('/lobby')}>
                Back to Lobby
              </button>
            </>
          ) : (
            <>
              <button type="button" className="start-btn" onClick={sendRematchRequest}>Rematch</button>
              <button type="button" className="lobby-games-btn" onClick={() => navigate('/lobby')}>
                Back to Lobby
              </button>
            </>
          )}
        </div>
      )}

      {promotionPending && playerColor && (
        <PromotionDialog color={playerColor} theme={theme} boardTheme={boardTheme} onChoice={sendPromotion} />
      )}

    </div>
  );
}
