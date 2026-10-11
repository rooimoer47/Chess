-- The mistakes and blunders that players (not bots) made in reviewed games, so a review can say
-- "this has happened before" and mistakes can be counted and searched. Written by the app when a
-- game is commented (MistakeMemory); the motifs and the description are worked out from the board,
-- never by the LLM.

CREATE TABLE mistakes (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users(id),
    game_id        BIGINT      NOT NULL REFERENCES games(id),
    ply            INT         NOT NULL,         -- the move that led to this ply, as in move_comments; odd = White's
    san            TEXT        NOT NULL,
    epd            TEXT        NOT NULL,         -- the position the move was played from
    classification TEXT        NOT NULL,         -- MISTAKE or BLUNDER
    motifs         TEXT[]      NOT NULL,         -- FORK, MISSED_FORK, ...; empty when none was recognised
    description    TEXT        NOT NULL,         -- the mistake without squares or move numbers
    model          TEXT,                         -- the embedding model; NULL until the description is embedded
    embedding      vector(768),                  -- filled in later, when the worker has nothing else to do
    UNIQUE (game_id, ply)
);
CREATE INDEX mistakes_user ON mistakes (user_id);
-- No vector index: a search is always within one user's rows.
