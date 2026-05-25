import { useChessSocket } from './hooks/useChessSocket';
import { Board } from './components/Board';
import { PromotionDialog } from './components/PromotionDialog';
import './App.css';

export default function App() {
  const {
    connected,
    gameStarted,
    playerColor,
    board,
    currentTurn,
    status,
    legalMoves,
    promotionPending,
    statusMessage,
    sendMove,
    sendPromotion,
  } = useChessSocket();

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

  const isGameOver = status === 'CHECKMATE' || status === 'STALEMATE';
  const isMyTurn = currentTurn === playerColor;

  return (
    <div className="app">
      <div className="info-bar">
        <span>You: <strong>{playerColor}</strong></span>
        <span className={`turn-indicator ${isMyTurn ? 'my-turn' : ''}`}>
          {isGameOver ? '—' : isMyTurn ? 'Your turn' : "Opponent's turn"}
        </span>
      </div>

      {statusMessage && (
        <div className={`status-message ${isGameOver ? 'game-over' : ''}`}>
          {statusMessage}
        </div>
      )}

      <Board
        board={board}
        legalMoves={legalMoves}
        playerColor={playerColor!}
        isMyTurn={isMyTurn && !isGameOver}
        onMove={sendMove}
      />

      {promotionPending && playerColor && (
        <PromotionDialog color={playerColor} onChoice={sendPromotion} />
      )}
    </div>
  );
}
