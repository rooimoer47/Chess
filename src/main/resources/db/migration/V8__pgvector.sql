-- pgvector, for the opening notes and mistake memory of game review. Needs the pgvector Postgres
-- image (pgvector/pgvector:pg17); on plain Postgres this migration fails and the app won't start.

CREATE EXTENSION IF NOT EXISTS vector;

-- That image is Debian (glibc); the one before it was Alpine (musl). The two sort en_US.utf8 text
-- differently, so every index on a text column built under the old image is out of order under the
-- new one: lookups miss rows and unique checks let duplicates in. Postgres gives no warning.
-- Rebuilding every index here repairs that before the app serves a request. On a database created
-- under the new image it only rebuilds indexes that were already right.
-- Flyway's own table is left out: its indexes hold no text, and Flyway has it locked.
DO $$
DECLARE
    tbl regclass;
BEGIN
    FOR tbl IN
        SELECT c.oid
        FROM pg_class c
        WHERE c.relnamespace = current_schema()::regnamespace
          AND c.relkind = 'r'
          AND c.relname <> 'flyway_schema_history'
    LOOP
        EXECUTE format('REINDEX TABLE %s', tbl);
    END LOOP;
END $$;
