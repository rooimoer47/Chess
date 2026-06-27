ALTER TABLE users
    ADD COLUMN theme            TEXT NOT NULL DEFAULT 'classic',
    ADD COLUMN color_preference TEXT NOT NULL DEFAULT 'RANDOM';
