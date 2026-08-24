import { useState, useRef, useEffect } from 'react';
import type { Piece, LegalMove, LastMove, Color } from '../types';
import { Square } from './Square';
import { BOARD_COLORS, type BoardTheme } from './ThemePicker';

interface Props {
  board: (Piece | null)[][];
  legalMoves: LegalMove[];
  lastMove: LastMove | null;
  playerColor: Color;
  isMyTurn: boolean;
  theme: string;
  boardTheme: BoardTheme;
  onMove: (fromRow: number, fromCol: number, toRow: number, toCol: number) => void;
}

interface DragState {
  row: number;
  col: number;
  startX: number;
  startY: number;
  x: number;
  y: number;
  squareSize: number;
  active: boolean;
  hasPiece: boolean;
}

const DRAG_THRESHOLD = 5;

export function Board({ board, legalMoves, lastMove, playerColor, isMyTurn, theme, boardTheme, onMove }: Props) {
  const { light: lightColor, dark: darkColor } = BOARD_COLORS[boardTheme];
  const [selected, setSelected] = useState<{ row: number; col: number } | null>(null);
  const [drag, setDrag] = useState<DragState | null>(null);
  const [dropTarget, setDropTarget] = useState<{ row: number; col: number } | null>(null);
  const boardRef = useRef<HTMLDivElement>(null);

  // Refs to avoid stale closures in document-level event listeners
  const dragRef = useRef<DragState | null>(null);
  const dropTargetRef = useRef<{ row: number; col: number } | null>(null);
  const legalMovesRef = useRef(legalMoves);
  const onMoveRef = useRef(onMove);
  const selectedRef = useRef(selected);
  const boardRef2 = useRef(board);
  const playerColorRef = useRef(playerColor);
  const isMyTurnRef = useRef(isMyTurn);

  dragRef.current = drag;
  dropTargetRef.current = dropTarget;
  legalMovesRef.current = legalMoves;
  onMoveRef.current = onMove;
  selectedRef.current = selected;
  boardRef2.current = board;
  playerColorRef.current = playerColor;
  isMyTurnRef.current = isMyTurn;

  const legalTargets = selected
    ? legalMoves
        .filter(m => m.fromRow === selected.row && m.fromCol === selected.col)
        .map(m => ({ row: m.toRow, col: m.toCol }))
    : [];

  useEffect(() => {
    function onMouseMove(e: MouseEvent) {
      const d = dragRef.current;
      if (!d) return;
      const dx = e.clientX - d.startX;
      const dy = e.clientY - d.startY;
      const active = d.active || Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD;
      setDrag(prev => prev ? { ...prev, x: e.clientX, y: e.clientY, active } : null);
    }

    function onMouseUp(e: MouseEvent) {
      const d = dragRef.current;
      if (!d) return;

      const dx = e.clientX - d.startX;
      const dy = e.clientY - d.startY;
      const didDrag = d.active || Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD;

      if (!didDrag) {
        // Treat as click — replicate the click-to-select / click-to-move flow
        const sel = selectedRef.current;
        const lm = legalMovesRef.current;
        const b = boardRef2.current;
        const pc = playerColorRef.current;
        const imt = isMyTurnRef.current;
        const { row, col } = d;

        if (sel) {
          const targets = lm
            .filter(m => m.fromRow === sel.row && m.fromCol === sel.col)
            .map(m => ({ row: m.toRow, col: m.toCol }));
          if (targets.some(t => t.row === row && t.col === col)) {
            onMoveRef.current(sel.row, sel.col, row, col);
            setSelected(null);
            setDrag(null);
            setDropTarget(null);
            return;
          }
        }

        if (imt) {
          const piece = b[row][col];
          if (piece && piece.color === pc) {
            setSelected({ row, col });
            setDrag(null);
            setDropTarget(null);
            return;
          }
        }

        setSelected(null);
        setDrag(null);
        setDropTarget(null);
        return;
      }

      // Drag release — execute if over a legal target
      const dt = dropTargetRef.current;
      if (dt && d.hasPiece) {
        const targets = legalMovesRef.current
          .filter(m => m.fromRow === d.row && m.fromCol === d.col)
          .map(m => ({ row: m.toRow, col: m.toCol }));
        if (targets.some(t => t.row === dt.row && t.col === dt.col)) {
          onMoveRef.current(d.row, d.col, dt.row, dt.col);
          setSelected(null);
        }
      }
      setDrag(null);
      setDropTarget(null);
    }

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    return () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
  }, []);

  function handleSquareMouseDown(row: number, col: number, e: React.MouseEvent) {
    e.preventDefault();
    const piece = board[row][col];
    const isOwnPiece = isMyTurn && piece != null && piece.color === playerColor;
    const squareSize = boardRef.current ? boardRef.current.clientWidth / 8 : 64;

    // A square holding our own piece can still be a legal destination: a
    // Chess960 castle is issued as king-onto-own-rook. Re-selecting here would
    // replace the king's selection before mouseup can read it, so the castle
    // could never be clicked — only dragged.
    const isTargetOfSelection = selected != null && legalMoves.some(m =>
      m.fromRow === selected.row && m.fromCol === selected.col && m.toRow === row && m.toCol === col);

    if (isOwnPiece && !isTargetOfSelection) {
      setSelected({ row, col });
    }

    setDrag({
      row, col,
      startX: e.clientX, startY: e.clientY,
      x: e.clientX, y: e.clientY,
      squareSize,
      active: false,
      hasPiece: isOwnPiece,
    });
  }

  function handleSquareMouseEnter(row: number, col: number) {
    if (dragRef.current?.active) {
      setDropTarget({ row, col });
    }
  }

  const rows = playerColor === 'WHITE'
    ? [7, 6, 5, 4, 3, 2, 1, 0]
    : [0, 1, 2, 3, 4, 5, 6, 7];
  const cols = playerColor === 'WHITE'
    ? [0, 1, 2, 3, 4, 5, 6, 7]
    : [7, 6, 5, 4, 3, 2, 1, 0];

  const draggingPiece = drag?.hasPiece && drag.active ? board[drag.row][drag.col] : null;

  return (
    <>
      <div
        ref={boardRef}
        className="board"
        style={{ cursor: drag?.active ? 'grabbing' : undefined, userSelect: 'none' }}
        onMouseLeave={() => { if (drag?.active) setDropTarget(null); }}
      >
        {rows.map(row =>
          cols.map(col => {
            const isLegalDropTarget =
              drag?.active &&
              dropTarget?.row === row && dropTarget?.col === col &&
              legalTargets.some(t => t.row === row && t.col === col);
            return (
              <Square
                key={`${row}-${col}`}
                row={row}
                col={col}
                piece={board[row][col]}
                isSelected={selected?.row === row && selected?.col === col}
                isLegalTarget={legalTargets.some(t => t.row === row && t.col === col)}
                isLastMove={lastMove != null && (
                  (lastMove.fromRow === row && lastMove.fromCol === col) ||
                  (lastMove.toRow === row && lastMove.toCol === col)
                )}
                isLegalDropTarget={!!isLegalDropTarget}
                isDraggingSource={!!(drag?.active && drag.hasPiece && drag.row === row && drag.col === col)}
                theme={theme}
                lightColor={lightColor}
                darkColor={darkColor}
                onMouseDown={e => handleSquareMouseDown(row, col, e)}
                onMouseEnter={() => handleSquareMouseEnter(row, col)}
              />
            );
          })
        )}
      </div>

      {draggingPiece && drag && (
        <img
          src={`/images/${theme}/${draggingPiece.type.toLowerCase()}_${draggingPiece.color.toLowerCase()}.png`}
          alt=""
          style={{
            position: 'fixed',
            left: drag.x - drag.squareSize / 2,
            top: drag.y - drag.squareSize / 2,
            width: drag.squareSize,
            height: drag.squareSize,
            pointerEvents: 'none',
            zIndex: 1000,
            opacity: 0.92,
          }}
        />
      )}
    </>
  );
}
