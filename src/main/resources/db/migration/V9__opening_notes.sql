-- Hand-written notes on opening traps and ideas, for game review comments. The rows are a copy of
-- src/main/resources/openings/notes.tsv with an embedding each; the app fills and refreshes them
-- (OpeningNotes), so there is nothing to insert here.

CREATE TABLE opening_notes (
    id        BIGSERIAL   PRIMARY KEY,
    opening   TEXT        NOT NULL,             -- part of an opening name, or '*' for any opening
    moves     TEXT        NOT NULL,             -- the idea's key moves in SAN, space-separated
    note      TEXT        NOT NULL,
    model     TEXT        NOT NULL,             -- the embedding model, e.g. 'nomic-embed-text'
    embedding vector(768) NOT NULL              -- nomic-embed-text's size; another size needs a migration
);
-- No vector index: there are a few dozen rows, and a search first narrows them by opening.
