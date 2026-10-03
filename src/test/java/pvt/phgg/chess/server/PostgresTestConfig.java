package pvt.phgg.chess.server;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// A real Postgres for tests that depend on its behaviour (SKIP LOCKED,
// ON CONFLICT, partial indexes), which a mocked JdbcTemplate can't check.
// The pgvector image is plain Postgres 17 plus the extension, so the same
// setup keeps working once the vector tables arrive.
//
// Spring caches the test context, so every test class using
// @PostgresIntegrationTest shares one container per run.
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGE);
    }
}
