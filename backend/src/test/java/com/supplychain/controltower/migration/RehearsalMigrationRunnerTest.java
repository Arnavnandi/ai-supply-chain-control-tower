package com.supplychain.controltower.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.MigrateOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RehearsalMigrationRunnerTest {

    private static final String PROD_HOST = "ep-round-mode-ay6n5xo2";

    @Test
    @DisplayName("Execute Flyway V7 migration strictly against rehearsal Neon branch")
    @EnabledIfEnvironmentVariable(named = "NEON_REHEARSAL_JDBC_URL", matches = ".+")
    void executeFlywayV7OnRehearsalBranch() {
        String jdbcUrl = System.getenv("NEON_REHEARSAL_JDBC_URL");
        String dbUser = System.getenv("NEON_REHEARSAL_DB_USER");
        String dbPass = System.getenv("NEON_REHEARSAL_DB_PASS");

        assertNotNull(jdbcUrl, "NEON_REHEARSAL_JDBC_URL must not be null");
        assertNotNull(dbUser, "NEON_REHEARSAL_DB_USER must not be null");

        // Strict Safety Guard: Never connect to production host
        assertFalse(jdbcUrl.contains(PROD_HOST), "FATAL SAFETY VIOLATION: URL contains production host " + PROD_HOST);
        assertTrue(jdbcUrl.contains("ep-bold-bonus-ayb94wsf"), "Target must be the verified rehearsal endpoint ep-bold-bonus-ayb94wsf");

        System.out.println("==========================================================");
        System.out.println("EXECUTING FLYWAY MIGRATION ON REHEARSAL BRANCH");
        System.out.println("Endpoint: ep-bold-bonus-ayb94wsf (rehearsal-phase2-multitenancy)");
        System.out.println("==========================================================");

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(true)
                .load();

        MigrateResult result = flyway.migrate();

        System.out.println("Flyway migration completed successfully!");
        System.out.println("Initial Schema Version: " + result.initialSchemaVersion);
        System.out.println("Target Schema Version: " + result.targetSchemaVersion);
        System.out.println("Migrations Executed: " + result.migrationsExecuted);
        System.out.println("Success: " + result.success);

        for (MigrateOutput output : result.migrations) {
            System.out.println(" -> Executed: " + output.version + " (" + output.description + ") in " + output.executionTime + "ms");
        }

        assertTrue(result.success, "Flyway migration must succeed");
    }
}
