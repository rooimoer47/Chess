package pvt.phgg.chess.server.history;

import pvt.phgg.chess.server.dto.LastMoveDto;
import pvt.phgg.chess.server.dto.PieceDto;

public record BoardSnapshotDto(int moveNumber, PieceDto[][] board, LastMoveDto lastMove) {}
