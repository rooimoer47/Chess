package pvt.phgg.chess.server;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class DatabaseMigrationTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbcTemplate;

    /** Every migration applies cleanly to a fresh database, so a broken one fails here, not on deploy. */
    @Test
    void allMigrationsApplyToAnEmptyDatabase() {
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.info().applied().length > 0);
    }

    /** The container really is Postgres 17, matching production. */
    @Test
    void runsAgainstPostgres17() {
        String version = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        assertTrue(version.startsWith("17"), "expected Postgres 17, got " + version);
    }
}
