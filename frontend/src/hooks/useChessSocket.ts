import { useState, useEffect, useRef, useCallback } from 'react';
import type { Piece, LegalMove, LastMove, Color, GameStatus, ServerMessage } from '../types';

const EMPTY_BOARD: (Piece | null)[][] = Array(8).fill(null).map(() => Array(8).fill(null));

export interface GameState {
  connected: boolean;
  gameStarted: boolean;
  playerColor: Color | null;
  board: (Piece | null)[][];
  currentTurn: Color;
  status: GameStatus;
  legalMoves: LegalMove[];
  lastMove: LastMove | null;
  capturedByWhite: string[];
  capturedByBlack: string[];
  promotionPending: { row: number; col: number } | null;
  statusMessage: string | null;
}

export function useChessSocket(token: string, botMode = false) {
  const [state, setState] = useState<GameState>({
    connected: false,
    gameStarted: false,
    playerColor: null,
    board: EMPTY_BOARD,
    currentTurn: 'WHITE',
    status: 'IN_PROGRESS',
    legalMoves: [],
    lastMove: null,
    capturedByWhite: [],
    capturedByBlack: [],
    promotionPending: null,
    statusMessage: null,
  });

  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const botParam = botMode ? '&bot=true' : '';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/game?token=${encodeURIComponent(token)}${botParam}`);
    wsRef.current = ws;

    ws.onopen = () => setState(s => ({ ...s, connected: true }));

    ws.onclose = () =>
      setState(s => ({ ...s, connected: false, statusMessage: 'Disconnected from server.', legalMoves: [] }));

    ws.onmessage = (event: MessageEvent) => {
      const msg: ServerMessage = JSON.parse(event.data as string);

      switch (msg.type) {
        case 'WAITING':
          setState(s => ({ ...s, playerColor: msg.color }));
          break;

        case 'BOARD_UPDATE': {
          const message =
            msg.status === 'CHECKMATE' ? `Checkmate! ${msg.currentTurn === 'WHITE' ? 'Black' : 'White'} wins!`
            : msg.status === 'STALEMATE' ? 'Draw — stalemate!'
            : msg.status === 'RESIGNED' ? `${msg.currentTurn} resigned. ${msg.currentTurn === 'WHITE' ? 'Black' : 'White'} wins!`
            : msg.status === 'CHECK' ? `${msg.currentTurn} is in check!`
            : null;
          setState(s => ({
            ...s,
            gameStarted: true,
            board: msg.board,
            currentTurn: msg.currentTurn,
            status: msg.status,
            legalMoves: msg.status === 'CHECKMATE' || msg.status === 'STALEMATE' || msg.status === 'RESIGNED' ? [] : msg.legalMoves,
            lastMove: msg.lastMove ?? null,
            capturedByWhite: msg.capturedByWhite,
            capturedByBlack: msg.capturedByBlack,
            promotionPending: null,
            statusMessage: message,
          }));
          break;
        }

        case 'PROMOTION_NEEDED':
          setState(s => ({
            ...s,
            promotionPending: { row: msg.promotionRow, col: msg.promotionCol },
            legalMoves: [],
          }));
          break;

        case 'OPPONENT_DISCONNECTED':
          setState(s => ({ ...s, statusMessage: 'Opponent disconnected.', legalMoves: [] }));
          break;

        case 'ERROR':
          console.error('Server error:', msg.message);
          break;
      }
    };

    return () => ws.close();
  }, [token, botMode]);

  const sendMove = useCallback((fromRow: number, fromCol: number, toRow: number, toCol: number) => {
    wsRef.current?.send(JSON.stringify({ type: 'MOVE', fromRow, fromCol, toRow, toCol }));
  }, []);

  const sendPromotion = useCallback((choice: string) => {
    wsRef.current?.send(JSON.stringify({ type: 'PROMOTE', choice }));
  }, []);

  const sendResign = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'RESIGN' }));
  }, []);

  return { ...state, sendMove, sendPromotion, sendResign };
}
