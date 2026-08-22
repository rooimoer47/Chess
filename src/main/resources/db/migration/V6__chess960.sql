ALTER TABLE games
    ADD COLUMN variant           TEXT NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN starting_position TEXT NOT NULL DEFAULT 'RNBQKBNR';
-- variant values: 'STANDARD' | 'CHESS960'
-- starting_position: 8-char back rank, files a-h, e.g. 'RNBQKBNR' or 'BBQNNRKR'

ALTER TABLE users
    ADD COLUMN elo_960         INTEGER NOT NULL DEFAULT 1000,
    ADD COLUMN games_rated_960 INTEGER NOT NULL DEFAULT 0;
