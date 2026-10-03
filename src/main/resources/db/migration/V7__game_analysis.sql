-- Engine analysis for game review. One cached evaluation per position, shared by every game that
-- reaches it, plus a work queue that fills when a game ends.

CREATE TABLE position_evals (
    epd        TEXT        NOT NULL,             -- FEN without the move counters
    chess960   BOOLEAN     NOT NULL,             -- castling notation differs, so keep the caches apart
    depth      INT         NOT NULL,
    eval_cp    INT,                              -- White's view; NULL when the score is a mate
    mate_in    INT,                              -- White's view: >0 White mates in N, <0 Black mates in N,
                                                 -- 0 = the side to move (from the EPD) is checkmated
    best_uci   TEXT,                             -- NULL when there is no legal move (mate, stalemate)
    pv_uci     TEXT,                             -- first plies of the engine line, space-separated
    engine     TEXT        NOT NULL,             -- e.g. 'Stockfish 19'
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (epd, chess960)
);

-- One row per position of a game, claimed with FOR UPDATE SKIP LOCKED.
CREATE TABLE analysis_jobs (
    id         BIGSERIAL   PRIMARY KEY,
    game_id    BIGINT      NOT NULL REFERENCES games(id),
    ply        INT         NOT NULL,             -- 0 = start position
    epd        TEXT        NOT NULL,
    chess960   BOOLEAN     NOT NULL,             -- tells the worker which Stockfish mode and cache to use
    status     TEXT        NOT NULL DEFAULT 'QUEUED',  -- QUEUED, RUNNING, DONE, FAILED
    attempts   INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (game_id, ply)
);
-- Jobs are claimed in id order, which is game order and ply order within a game.
CREATE INDEX analysis_jobs_queued ON analysis_jobs (id) WHERE status = 'QUEUED';

-- Per-game review status and LLM details.
CREATE TABLE game_reviews (
    game_id      BIGINT      PRIMARY KEY REFERENCES games(id),
    status       TEXT        NOT NULL,           -- ENGINE, COMMENTING, DONE, FAILED
    llm_model    TEXT,                           -- e.g. 'qwen3:4b-q4_K_M'
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE move_comments (
    game_id BIGINT NOT NULL REFERENCES games(id),
    ply     INT    NOT NULL,                     -- the move that led to this ply
    comment TEXT   NOT NULL,
    PRIMARY KEY (game_id, ply)
);
