package com.bank.cbs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the whole application starts against real (containerised)
 * Postgres, Redis, MongoDB and RabbitMQ.
 *
 * What this catches in Jenkins, before anything reaches UAT:
 *  - Flyway migrations that fail on an empty database
 *  - edited migrations (checksum mismatch)
 *  - entity / migration drift (ddl-auto: validate fails)
 *  - missing or mistyped configuration properties
 *  - broken Spring wiring (missing beans, circular dependencies)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CbsApplicationTests {

    @Test
    void contextLoads() {
    }
}
