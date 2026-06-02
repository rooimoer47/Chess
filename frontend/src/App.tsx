import { useState } from 'react';
import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { CapturedPieces } from './components/CapturedPieces';
import { PromotionDialog } from './components/PromotionDialog';
import { LoginScreen } from './components/LoginScreen';
import './App.css';

export default function App() {
  const [token, setToken] = useState<string | null>(null);
  const [botMode, setBotMode] = useState(false);
  const [gameKey, setGameKey] = useState(0);

  if (!token) {
    return <LoginScreen onLogin={(t, bot) => { setToken(t); setBotMode(bot); }} />;
  }

  const handlePlayAgain = (newBotMode: boolean) => {
    setBotMode(newBotMode);
    setGameKey(k => k + 1);
  };

  return <ChessGame key={gameKey} token={token} botMode={botMode} onPlayAgain={handlePlayAgain} />;
}

function ChessGame({ token, botMode, onPlayAgain }: { token: string; botMode: boolean; onPlayAgain: (botMode: boolean) => void }) {
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

  const isGameOver = status === 'CHECKMATE' || status === 'STALEMATE' || status === 'RESIGNED' || status === 'THREEFOLD_REPETITION' || status === 'FIFTY_MOVE_RULE';
  const isMyTurn = currentTurn === playerColor;

  // Pieces of my color captured by opponent (shown at top)
  const myLost      = playerColor === 'WHITE' ? capturedByBlack : capturedByWhite;
  // Opponent's pieces I captured (shown at bottom)
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
          <button className="resign-btn" onClick={sendResign}>Resign</button>
        )}
      </div>

      {statusMessage && (
        <div className={`status-message ${isGameOver ? 'game-over' : ''}`}>
          {statusMessage}
        </div>
      )}

      <CapturedPieces pieces={myLost} color={playerColor!} />

      <Board
        board={board}
        legalMoves={legalMoves}
        lastMove={lastMove}
        playerColor={playerColor!}
        isMyTurn={isMyTurn && !isGameOver}
        onMove={sendMove}
      />

      <CapturedPieces pieces={opponentLost} color={opponentColor} />

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
        <PromotionDialog color={playerColor} onChoice={sendPromotion} />
      )}
    </div>
  );
}
