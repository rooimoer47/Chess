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
  drawOfferedByOpponent: boolean;
  drawOfferPending: boolean;
}

export function useChessSocket(botType = '', onAuthFailed?: () => void) {
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
    drawOfferedByOpponent: false,
    drawOfferPending: false,
  });

  const wsRef = useRef<WebSocket | null>(null);
  const onAuthFailedRef = useRef(onAuthFailed);
  onAuthFailedRef.current = onAuthFailed;

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const botParam = botType ? `?bot=${botType}` : '';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/game${botParam}`);
    wsRef.current = ws;

    let didOpen = false;
    ws.onopen = () => { didOpen = true; setState(s => ({ ...s, connected: true })); };

    ws.onclose = (event) => {
      if (!didOpen) {
        console.warn('WS closed before open — code:', event.code, 'reason:', event.reason);
        onAuthFailedRef.current?.();
        return;
      }
      setState(s => ({ ...s, connected: false, statusMessage: 'Disconnected from server.', legalMoves: [] }));
    };

    ws.onmessage = (event: MessageEvent) => {
      const msg: ServerMessage = JSON.parse(event.data as string);

      switch (msg.type) {
        case 'WAITING':
          setState(s => ({ ...s, playerColor: msg.color }));
          break;

        case 'BOARD_UPDATE': {
          const message =
            msg.status === 'CHECKMATE'            ? `Checkmate! ${msg.currentTurn === 'WHITE' ? 'Black' : 'White'} wins!`
            : msg.status === 'STALEMATE'           ? 'Draw — stalemate!'
            : msg.status === 'RESIGNED'            ? `${msg.currentTurn} resigned. ${msg.currentTurn === 'WHITE' ? 'Black' : 'White'} wins!`
            : msg.status === 'THREEFOLD_REPETITION'? 'Draw — threefold repetition!'
            : msg.status === 'FIFTY_MOVE_RULE'     ? 'Draw — fifty-move rule!'
            : msg.status === 'INSUFFICIENT_MATERIAL'? 'Draw — insufficient material!'
            : msg.status === 'DRAW_AGREED'         ? 'Draw by agreement!'
            : msg.status === 'CHECK'               ? `${msg.currentTurn} is in check!`
            : null;
          setState(s => ({
            ...s,
            gameStarted: true,
            board: msg.board,
            currentTurn: msg.currentTurn,
            status: msg.status,
            legalMoves: msg.status === 'CHECKMATE' || msg.status === 'STALEMATE' || msg.status === 'RESIGNED' || msg.status === 'THREEFOLD_REPETITION' || msg.status === 'FIFTY_MOVE_RULE' || msg.status === 'INSUFFICIENT_MATERIAL' || msg.status === 'DRAW_AGREED' ? [] : msg.legalMoves,
            drawOfferedByOpponent: false,
            drawOfferPending: false,
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

        case 'DRAW_OFFERED':
          setState(s => ({ ...s, drawOfferedByOpponent: true }));
          break;

        case 'DRAW_DECLINED':
          setState(s => ({ ...s, drawOfferPending: false, statusMessage: 'Draw offer declined.' }));
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
  }, [botType]);

  const sendMove = useCallback((fromRow: number, fromCol: number, toRow: number, toCol: number) => {
    wsRef.current?.send(JSON.stringify({ type: 'MOVE', fromRow, fromCol, toRow, toCol }));
  }, []);

  const sendPromotion = useCallback((choice: string) => {
    wsRef.current?.send(JSON.stringify({ type: 'PROMOTE', choice }));
  }, []);

  const sendResign = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'RESIGN' }));
  }, []);

  const sendDrawOffer = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'OFFER_DRAW' }));
    setState(s => ({ ...s, drawOfferPending: true }));
  }, []);

  const sendDrawResponse = useCallback((accept: boolean) => {
    wsRef.current?.send(JSON.stringify({ type: 'RESPOND_DRAW', accept }));
    setState(s => ({ ...s, drawOfferedByOpponent: false }));
  }, []);

  return { ...state, sendMove, sendPromotion, sendResign, sendDrawOffer, sendDrawResponse };
}
