export type PieceType = 'PAWN' | 'KNIGHT' | 'BISHOP' | 'ROOK' | 'QUEEN' | 'KING';
export type Color = 'WHITE' | 'BLACK';
export type Variant = 'STANDARD' | 'CHESS960';
export type GameStatus = 'IN_PROGRESS' | 'CHECK' | 'CHECKMATE' | 'STALEMATE' | 'RESIGNED' | 'THREEFOLD_REPETITION' | 'FIFTY_MOVE_RULE' | 'INSUFFICIENT_MATERIAL' | 'DRAW_AGREED' | 'TIMEOUT';

export interface Piece {
  type: PieceType;
  color: Color;
}

export interface LegalMove {
  fromRow: number;
  fromCol: number;
  toRow: number;
  toCol: number;
}

export interface LastMove {
  fromRow: number;
  fromCol: number;
  toRow: number;
  toCol: number;
}

export interface GameSummary {
  id: number;
  opponent: string;
  playerColor: Color;
  result: string | null;
  winnerColor: Color | null;
  mode: string;
  variant: Variant;
  startedAt: string;
  endedAt: string | null;
}

export interface ActiveGameSummary {
  gameId: number;
  mode: string;
  botType: string | null;
  opponentUsername: string | null;
  color: Color;
  status: string;
  variant: Variant;
}

export interface BoardSnapshot {
  moveNumber: number;
  board: (Piece | null)[][];
  lastMove: LastMove | null;
}

export type ServerMessage =
  | { type: 'WAITING'; color: Color }
  | { type: 'WAITING_QUEUE'; color: Color; waitSeconds: number }
  | { type: 'BOARD_UPDATE'; board: (Piece | null)[][]; currentTurn: Color; status: GameStatus; legalMoves: LegalMove[]; lastMove?: LastMove; capturedByWhite: string[]; capturedByBlack: string[]; whiteTimeMs?: number; blackTimeMs?: number }
  | { type: 'PROMOTION_NEEDED'; promotionRow: number; promotionCol: number }
  | { type: 'DRAW_OFFERED' }
  | { type: 'DRAW_DECLINED' }
  | { type: 'OPPONENT_DISCONNECTED' }
  | { type: 'REMATCH_REQUESTED' }
  | { type: 'REMATCH_DECLINED' }
  | { type: 'REMATCH_START'; color: Color }
  | { type: 'ERROR'; message: string };

// ---- Game review (GET /api/games/{id}/analysis) ----

export type ReviewStatus = 'NONE' | 'ENGINE' | 'COMMENTING' | 'DONE' | 'FAILED';
export type MoveClassification = 'BEST' | 'GOOD' | 'INACCURACY' | 'MISTAKE' | 'BLUNDER' | 'UNKNOWN';

/** White's point of view; exactly one is set. mate 0 = the side to move is checkmated. */
export interface Eval {
  cp: number | null;
  mate: number | null;
}

export interface MoveReview {
  ply: number;          // the position this move leads to (1 = White's first move)
  san: string;
  uci: string;
  from: string;         // squares like "e2"
  to: string;
  classification: MoveClassification;
  bestSan: string | null;
  bestUci: string | null;
  bestFrom: string | null;
  bestTo: string | null;
  line: string[];       // engine line in SAN, starting with the best move
  comment: string | null;
}

export interface GameAnalysis {
  status: ReviewStatus;
  positionsTotal: number;
  positionsDone: number;
  evals: (Eval | null)[];   // indexed by ply
  moves: MoveReview[];
}
