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
import { EloHistoryPage } from './components/EloHistoryPage';
import { ReplayViewer } from './components/ReplayViewer';
import type { ActiveGameSummary, Variant } from './types';
import './App.css';

export default function App() {
  const navigate = useNavigate();
  const [username, setUsername] = useState<string | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [botType, setBotType] = useState('');
  const [theme, setTheme] = useState<Theme>('classic');
  const [boardTheme, setBoardTheme] = useState<BoardTheme>('classic');
  const [colorPreference, setColorPreference] = useState('RANDOM');
  const [clockMs, setClockMs] = useState(0);
  const [variant, setVariant] = useState<Variant>('STANDARD');
  const [resumeGameId, setResumeGameId] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/auth/me')
      .then(r => r.ok ? r.json() as Promise<{ username: string }> : null)
      .then(data => { if (data) setUsername(data.username); })
      .catch(() => {})
      .finally(() => setAuthChecked(true));
  }, []);

  useEffect(() => {
    if (!username) return;
    fetch(`/api/users/${encodeURIComponent(username)}/active-games`)
      .then(r => r.ok ? r.json() as Promise<ActiveGameSummary[]> : [])
      .then(games => {
        // Bot-game resume is a lobby-level choice (Phase 4) — here we only
        // auto-rejoin the one PvP game a user can have in flight, so a
        // reload or a reconnect after a locked phone doesn't strand them
        // in the lobby with no way back to a game that's still live.
        const pvp = games.find(g => g.mode === 'HUMAN');
        if (pvp) {
          setResumeGameId(String(pvp.gameId));
          setVariant(pvp.variant);
          navigate('/game');
        }
      })
      .catch(() => {});
  }, [username, navigate]);

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
          variant={variant}
          onChangeBotType={setBotType}
          onChangeTheme={handleChangeTheme}
          onChangeBoardTheme={handleChangeBoardTheme}
          onChangeColorPreference={handleChangeColorPreference}
          onChangeClockMs={setClockMs}
          onChangeVariant={setVariant}
          onLogout={handleLogout}
          onStartGame={() => { setResumeGameId(null); navigate('/game'); }}
          onResumeGame={(bt, gameId, v) => { setBotType(bt); setResumeGameId(gameId); setVariant(v); navigate('/game'); }}
        />
      } />
      <Route path="/game" element={
        <ChessGame
          username={username}
          botType={botType}
          colorPreference={colorPreference}
          theme={theme}
          boardTheme={boardTheme}
          variant={variant}
          resumeGameId={resumeGameId}
          onLogout={handleLogout}
          onAuthFailed={handleLogout}
          onLeaveGame={() => { setResumeGameId(null); navigate('/lobby'); }}
        />
      } />
      <Route path="/history" element={<HistoryPage username={username} variant={variant} />} />
      <Route path="/history/:gameId" element={<ReplayViewer />} />
      <Route path="/profile" element={<EloHistoryPage username={username} variant={variant} />} />
      <Route path="*" element={<Navigate to="/lobby" replace />} />
    </Routes>
  );
}

function turnLabel(isGameOver: boolean, isMyTurn: boolean): string {
  if (isGameOver) return '\u2014';
  return isMyTurn ? 'Your turn' : "Opponent's turn";
}

function ChessGame({ username, botType, colorPreference, theme, boardTheme, variant, resumeGameId, onLogout, onAuthFailed, onLeaveGame }: Readonly<{
  username: string;
  botType: string;
  colorPreference: string;
  theme: Theme;
  boardTheme: BoardTheme;
  variant: Variant;
  resumeGameId: string | null;
  onLogout: () => void;
  onAuthFailed: () => void;
  onLeaveGame: () => void;
}>) {
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
    waitSeconds,
  } = useChessSocket(botType, colorPreference, resumeGameId, variant, onAuthFailed);

  if (!connected) {
    return <div className="screen"><p>Connecting to server…</p></div>;
  }

  if (!gameStarted) {
    const waitLabel = waitSeconds !== null
      ? `Searching for opponent… ${Math.floor(waitSeconds / 60)}:${String(waitSeconds % 60).padStart(2, '0')}`
      : 'Waiting for opponent to connect…';
    // Nobody to pair with yet, so a Random preference hasn't actually been
    // resolved to a colour — show the literal word rather than the
    // provisional guess the server sends before a match exists.
    const displayColor = colorPreference === 'RANDOM' ? 'RANDOM' : (playerColor ?? '…');
    return (
      <div className="screen">
        <p>You are: <strong>{displayColor}</strong></p>
        <p>{waitLabel}</p>
        <button type="button" className="lobby-games-btn" onClick={onLeaveGame}>
          Cancel
        </button>
      </div>
    );
  }

  const isGameOver = status === 'CHECKMATE' || status === 'STALEMATE' || status === 'RESIGNED'
    || status === 'THREEFOLD_REPETITION' || status === 'FIFTY_MOVE_RULE'
    || status === 'INSUFFICIENT_MATERIAL' || status === 'DRAW_AGREED' || status === 'TIMEOUT';
  const isMyTurn = currentTurn === playerColor;

  function rematchControls() {
    if (rematchState === 'waiting') {
      return (
        <>
          <p className="rematch-status">Waiting for opponent…</p>
          <button type="button" className="lobby-games-btn" onClick={() => { sendRematchDecline(); onLeaveGame(); }}>
            Cancel
          </button>
        </>
      );
    }
    if (rematchState === 'declined') {
      return (
        <>
          <p className="rematch-status rematch-declined">Opponent did not want a rematch.</p>
          <button type="button" className="start-btn" onClick={onLeaveGame}>
            Back to Lobby
          </button>
        </>
      );
    }
    return (
      <>
        <button type="button" className="start-btn" onClick={sendRematchRequest}>Rematch</button>
        <button type="button" className="lobby-games-btn" onClick={onLeaveGame}>
          Back to Lobby
        </button>
      </>
    );
  }

  const myLost       = playerColor === 'WHITE' ? capturedByBlack : capturedByWhite;
  const opponentLost = playerColor === 'WHITE' ? capturedByWhite : capturedByBlack;
  const opponentColor = playerColor === 'WHITE' ? 'BLACK' : 'WHITE';

  return (
    <div className="app">
      <div className="info-bar">
        <span className="game-variant-title">{variant === 'CHESS960' ? 'Chessnuts960' : 'Chessnuts'}</span>
        <span>You: <strong>{username}</strong></span>
        <span className={`turn-indicator ${isMyTurn ? 'my-turn' : ''}`}>
          {turnLabel(isGameOver, isMyTurn)}
        </span>
        {!isGameOver && (
          <>
            <button type="button" className="draw-btn" onClick={sendDrawOffer}
              disabled={drawOfferPending || drawOfferedByOpponent}>
              {drawOfferPending ? 'Draw offered…' : 'Offer Draw'}
            </button>
            <button type="button" className="resign-btn" onClick={sendResign}>Resign</button>
          </>
        )}
        <button type="button" className="logout-btn" onClick={onLogout}>Logout</button>
      </div>

      {statusMessage && (
        <div className={`status-message ${isGameOver ? 'game-over' : ''}`}>
          {statusMessage}
        </div>
      )}

      {drawOfferedByOpponent && !isGameOver && (
        <div className="draw-offer-bar">
          <span>Opponent offers a draw</span>
          <button type="button" className="draw-accept-btn" onClick={() => sendDrawResponse(true)}>Accept</button>
          <button type="button" className="draw-decline-btn" onClick={() => sendDrawResponse(false)}>Decline</button>
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
          {rematchControls()}
        </div>
      )}

      {promotionPending && playerColor && (
        <PromotionDialog color={playerColor} theme={theme} boardTheme={boardTheme} onChoice={sendPromotion} />
      )}

    </div>
  );
}
