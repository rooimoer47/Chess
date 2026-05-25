import { useState } from 'react';
import type { Piece, LegalMove, Color } from '../types';
import { Square } from './Square';

interface Props {
  board: (Piece | null)[][];
  legalMoves: LegalMove[];
  playerColor: Color;
  isMyTurn: boolean;
  onMove: (fromRow: number, fromCol: number, toRow: number, toCol: number) => void;
}

export function Board({ board, legalMoves, playerColor, isMyTurn, onMove }: Props) {
  const [selected, setSelected] = useState<{ row: number; col: number } | null>(null);

  const legalTargets = selected
    ? legalMoves
        .filter(m => m.fromRow === selected.row && m.fromCol === selected.col)
        .map(m => ({ row: m.toRow, col: m.toCol }))
    : [];

  function handleSquareClick(row: number, col: number) {
    // Move if a target square is clicked
    if (selected && legalTargets.some(t => t.row === row && t.col === col)) {
      onMove(selected.row, selected.col, row, col);
      setSelected(null);
      return;
    }

    // Select own piece on your turn
    if (isMyTurn) {
      const piece = board[row][col];
      if (piece && piece.color === playerColor) {
        setSelected({ row, col });
        return;
      }
    }

    setSelected(null);
  }

  // White sees row 0 at the bottom; black sees row 7 at the bottom
  const rows = playerColor === 'WHITE'
    ? [7, 6, 5, 4, 3, 2, 1, 0]
    : [0, 1, 2, 3, 4, 5, 6, 7];
  const cols = playerColor === 'WHITE'
    ? [0, 1, 2, 3, 4, 5, 6, 7]
    : [7, 6, 5, 4, 3, 2, 1, 0];

  return (
    <div className="board">
      {rows.map(row =>
        cols.map(col => (
          <Square
            key={`${row}-${col}`}
            row={row}
            col={col}
            piece={board[row][col]}
            isSelected={selected?.row === row && selected?.col === col}
            isLegalTarget={legalTargets.some(t => t.row === row && t.col === col)}
            onClick={() => handleSquareClick(row, col)}
          />
        ))
      )}
    </div>
  );
}
