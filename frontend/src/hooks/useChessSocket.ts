import { useState, useEffect, useRef, useCallback } from 'react';
import type { Piece, LegalMove, LastMove, Color, GameStatus, ServerMessage, Variant } from '../types';

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
  rematchState: null | 'waiting' | 'declined';
  waitSeconds: number | null;
}

const RECONNECT_DELAYS_MS = [1000, 2000, 5000, 10000];

export function useChessSocket(botType = '', colorPreference = 'RANDOM', gameId: string | null = null, variant: Variant = 'STANDARD', onAuthFailed?: () => void) {
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
    rematchState: null,
    waitSeconds: null,
  });

  const wsRef = useRef<WebSocket | null>(null);
  const onAuthFailedRef = useRef(onAuthFailed);
  onAuthFailedRef.current = onAuthFailed;

  useEffect(() => {
    let cleanedUp = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let attempt = 0;

    const connect = () => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const params = new URLSearchParams();
      if (botType) params.set('bot', botType);
      if (colorPreference !== 'RANDOM') params.set('color', colorPreference);
      if (gameId) params.set('gameId', gameId);
      if (variant === 'CHESS960') params.set('variant', 'chess960');
      const query = params.size ? `?${params.toString()}` : '';
      const ws = new WebSocket(`${protocol}//${window.location.host}/ws/game${query}`);
      wsRef.current = ws;

      let didOpen = false;
      ws.onopen = () => { didOpen = true; attempt = 0; setState(s => ({ ...s, connected: true })); };

      ws.onclose = (event) => {
        // A close before open usually means the server rejected the handshake
        // (e.g. expired auth cookie) — a reconnect would just fail the same
        // way, so treat it as an auth failure rather than retrying. Effect
        // cleanup also closes the socket before it opens (React StrictMode
        // double-invokes this effect in dev, and any real unmount does too)
        // — that's an intentional close, not an auth failure.
        if (!didOpen && !cleanedUp) {
          console.warn('WS closed before open — code:', event.code, 'reason:', event.reason);
          onAuthFailedRef.current?.();
          return;
        }
        if (cleanedUp) return;
        const delay = RECONNECT_DELAYS_MS[Math.min(attempt, RECONNECT_DELAYS_MS.length - 1)];
        attempt += 1;
        setState(s => ({ ...s, connected: false, statusMessage: 'Disconnected from server. Reconnecting…', legalMoves: [] }));
        reconnectTimer = setTimeout(() => { if (!cleanedUp) connect(); }, delay);
      };

      ws.onmessage = (event: MessageEvent) => {
        const msg: ServerMessage = JSON.parse(event.data as string);

        switch (msg.type) {
          case 'WAITING':
            setState(s => ({ ...s, playerColor: msg.color, waitSeconds: null }));
            break;

          case 'WAITING_QUEUE':
            setState(s => ({ ...s, playerColor: msg.color, waitSeconds: msg.waitSeconds }));
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
              : msg.status === 'TIMEOUT'              ? `${msg.currentTurn} disconnected too long. ${msg.currentTurn === 'WHITE' ? 'Black' : 'White'} wins!`
              : null;
            setState(s => ({
              ...s,
              gameStarted: true,
              waitSeconds: null,
              board: msg.board,
              currentTurn: msg.currentTurn,
              status: msg.status,
              legalMoves: msg.status === 'CHECKMATE' || msg.status === 'STALEMATE' || msg.status === 'RESIGNED' || msg.status === 'THREEFOLD_REPETITION' || msg.status === 'FIFTY_MOVE_RULE' || msg.status === 'INSUFFICIENT_MATERIAL' || msg.status === 'DRAW_AGREED' || msg.status === 'TIMEOUT' ? [] : msg.legalMoves,
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

          case 'REMATCH_REQUESTED':
            setState(s => ({ ...s, rematchState: 'waiting' }));
            break;

          case 'REMATCH_DECLINED':
            setState(s => ({ ...s, rematchState: 'declined' }));
            break;

          case 'REMATCH_START':
            setState({
              connected: true,
              gameStarted: true,
              playerColor: msg.color as Color,
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
              rematchState: null,
              waitSeconds: null,
            });
            break;

          case 'ERROR':
            console.error('Server error:', msg.message);
            break;
        }
      };
    };

    connect();

    return () => {
      cleanedUp = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      wsRef.current?.close();
    };
  }, [botType, colorPreference, gameId, variant]);

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

  const sendRematchRequest = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'REMATCH_REQUEST' }));
  }, []);

  const sendRematchDecline = useCallback(() => {
    wsRef.current?.send(JSON.stringify({ type: 'REMATCH_DECLINE' }));
  }, []);

  return { ...state, sendMove, sendPromotion, sendResign, sendDrawOffer, sendDrawResponse, sendRematchRequest, sendRematchDecline };
}
