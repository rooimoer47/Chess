package pvt.phgg.chess.server;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Boots the full application against a throwaway Postgres container, with
// every Flyway migration applied. Skipped, not failed, when Docker isn't
// available, so `mvn test` still runs the rest of the suite without it.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "chess.jwt.secret=aW50ZWdyYXRpb24tdGVzdHMtb25seS1ub3QtYS1yZWFsLWp3dC1zZWNyZXQ=",
        // Tests drive the analysis worker step by step instead of racing a background thread.
        "chess.analysis.worker-enabled=false"
})
@Import(PostgresTestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface PostgresIntegrationTest {
}
