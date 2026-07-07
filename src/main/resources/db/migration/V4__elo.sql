ALTER TABLE users
  ADD COLUMN elo         INTEGER NOT NULL DEFAULT 1000,
  ADD COLUMN games_rated INTEGER NOT NULL DEFAULT 0;

ALTER TABLE games
  ADD COLUMN white_elo_before INTEGER,
  ADD COLUMN black_elo_before INTEGER,
  ADD COLUMN white_elo_after  INTEGER,
  ADD COLUMN black_elo_after  INTEGER;

CREATE TABLE elo_history (
  id          BIGSERIAL   PRIMARY KEY,
  user_id     BIGINT      NOT NULL REFERENCES users(id),
  game_id     BIGINT      NOT NULL REFERENCES games(id),
  elo_before  INTEGER     NOT NULL,
  elo_after   INTEGER     NOT NULL,
  delta       INTEGER     NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX elo_history_user_idx ON elo_history(user_id, recorded_at DESC);
