export type PieceType = 'PAWN' | 'KNIGHT' | 'BISHOP' | 'ROOK' | 'QUEEN' | 'KING';
export type Color = 'WHITE' | 'BLACK';
export type GameStatus = 'IN_PROGRESS' | 'CHECK' | 'CHECKMATE' | 'STALEMATE' | 'RESIGNED';

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

export type ServerMessage =
  | { type: 'WAITING'; color: Color }
  | { type: 'BOARD_UPDATE'; board: (Piece | null)[][]; currentTurn: Color; status: GameStatus; legalMoves: LegalMove[]; lastMove?: LastMove; capturedByWhite: string[]; capturedByBlack: string[] }
  | { type: 'PROMOTION_NEEDED'; promotionRow: number; promotionCol: number }
  | { type: 'OPPONENT_DISCONNECTED' }
  | { type: 'ERROR'; message: string };
