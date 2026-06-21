CREATE TABLE users (
    id             BIGSERIAL   PRIMARY KEY,
    username       TEXT        NOT NULL UNIQUE,
    password_hash  TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE games (
    id              BIGSERIAL   PRIMARY KEY,
    white_player_id BIGINT      REFERENCES users(id),
    black_player_id BIGINT      REFERENCES users(id),
    mode            TEXT        NOT NULL,
    result          TEXT,
    winner_color    TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE TABLE game_moves (
    id               BIGSERIAL   PRIMARY KEY,
    game_id          BIGINT      NOT NULL REFERENCES games(id),
    move_number      INT         NOT NULL,
    from_row         INT         NOT NULL,
    from_col         INT         NOT NULL,
    to_row           INT         NOT NULL,
    to_col           INT         NOT NULL,
    promotion_choice TEXT,
    played_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
