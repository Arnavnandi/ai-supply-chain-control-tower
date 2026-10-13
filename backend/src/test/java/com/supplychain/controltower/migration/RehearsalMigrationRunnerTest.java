package com.supplychain.controltower.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateOutput;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RehearsalMigrationRunnerTest {

    public static final String PROD_ENDPOINT_ID = "ep-round-mode-ay6n5xo2";
    public static final Set<String> APPROVED_REHEARSAL_HOSTS = Set.of(
            "ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech",
            "ep-bold-bonus-ayb94wsf-pooler.us-east-2.aws.neon.tech",
            "ep-bold-bonus-ayb94wsf"
    );

    public record ParsedEndpoint(String host, int port, String database) {}

    public static ParsedEndpoint parseJdbcEndpoint(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Invalid or missing PostgreSQL JDBC URL prefix.");
        }
        String stripped = jdbcUrl.substring("jdbc:".length());
        URI uri;
        try {
            uri = new URI(stripped);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed JDBC URL: " + e.getMessage(), e);
        }

        if (!"postgresql".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unexpected URI scheme: " + uri.getScheme());
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("JDBC URL must not embed user credentials in authority. Provide credentials separately.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Could not extract valid hostname from JDBC URL authority.");
        }
        if (host.contains("/") || host.contains("@") || host.contains(":") || host.contains("?")) {
            throw new IllegalArgumentException("Unexpected authority format in JDBC URL hostname: " + host);
        }

        int port = uri.getPort();
        if (port != -1 && port != 5432) {
            throw new IllegalArgumentException("Unexpected PostgreSQL port: " + port + ". Expected 5432 or default.");
        }

        String path = uri.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            throw new IllegalArgumentException("Missing database name in JDBC URL path.");
        }
        String dbName = path.startsWith("/") ? path.substring(1) : path;
        if (dbName.isBlank() || dbName.contains("/")) {
            throw new IllegalArgumentException("Unexpected path format in JDBC URL: " + path);
        }

        return new ParsedEndpoint(host.trim().toLowerCase(), port, dbName.trim().toLowerCase());
    }

    public static String parseJdbcHost(String jdbcUrl) {
        return parseJdbcEndpoint(jdbcUrl).host();
    }

    public static String parseJdbcDatabase(String jdbcUrl) {
        return parseJdbcEndpoint(jdbcUrl).database();
    }

    public static void validateRehearsalHost(String jdbcUrl) {
        validateRehearsalHost(jdbcUrl, System.getenv("NEON_REHEARSAL_EXPECTED_HOST"));
    }

    public static void validateRehearsalHost(String jdbcUrl, String configuredExpectedHost) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("NEON_REHEARSAL_JDBC_URL must not be null or blank.");
        }

        // 1. Requirement: Fail closed if NEON_REHEARSAL_EXPECTED_HOST is absent or blank
        if (configuredExpectedHost == null || configuredExpectedHost.isBlank()) {
            throw new IllegalStateException("FATAL: NEON_REHEARSAL_EXPECTED_HOST must be explicitly configured. Fail-closed: refusing unconfirmed host.");
        }

        // 2. Strict production blocklist
        if (jdbcUrl.contains(PROD_ENDPOINT_ID)) {
            throw new SecurityException("FATAL SAFETY VIOLATION: URL contains production host identifier " + PROD_ENDPOINT_ID);
        }

        // 3. Structural parsing and validation of host, port, database, and authority
        ParsedEndpoint endpoint = parseJdbcEndpoint(jdbcUrl);
        String actualHost = endpoint.host();

        // 4. Configured expected host must be in approved rehearsal hosts list
        String normalizedExpectedHost = configuredExpectedHost.trim().toLowerCase();
        if (!APPROVED_REHEARSAL_HOSTS.contains(normalizedExpectedHost)) {
            throw new SecurityException("FATAL: Configured expected host '" + configuredExpectedHost + "' is not in approved rehearsal hosts: " + APPROVED_REHEARSAL_HOSTS);
        }

        // 5. Hostname exact matching
        if (!actualHost.equals(normalizedExpectedHost)) {
            throw new SecurityException("FATAL: Parsed hostname '" + actualHost + "' does not match expected host '" + configuredExpectedHost + "'");
        }

        // 6. Database identity validation
        if (!"neondb".equals(endpoint.database())) {
            throw new SecurityException("FATAL: Target database must be 'neondb'. Found: " + endpoint.database());
        }
    }

    public static void validateBaselineAndPending(MigrationInfo current, MigrationInfo[] pending) {
        if (current == null) {
            throw new IllegalStateException("FATAL: Database has no applied Flyway baseline. Refusing to migrate uninitialized database.");
        }

        String currentVersion = current.getVersion() != null ? current.getVersion().getVersion() : null;
        if (!"7".equals(currentVersion)) {
            throw new IllegalStateException("FATAL: Rehearsal database must be strictly at baseline V7 before applying V8. Found version: " + currentVersion);
        }

        if (current.getState() != MigrationState.SUCCESS && current.getState() != MigrationState.BASELINE) {
            throw new IllegalStateException("FATAL: Baseline V7 migration is in state " + current.getState() + ", expected SUCCESS or BASELINE.");
        }

        if (pending == null || pending.length != 1) {
            int count = (pending != null) ? pending.length : 0;
            throw new IllegalStateException("FATAL: Expected exactly 1 pending migration (V8). Found " + count + " pending migrations.");
        }

        String pendingVersion = pending[0].getVersion() != null ? pending[0].getVersion().getVersion() : null;
        if (!"8".equals(pendingVersion)) {
            throw new IllegalStateException("FATAL: Pending migration must be V8. Found: " + pendingVersion);
        }
    }

    @Test
    @DisplayName("Host Guard: Fails closed when NEON_REHEARSAL_EXPECTED_HOST is absent")
    void testHostGuard_FailsClosedWhenExpectedHostAbsent() {
        String sampleUrl = "jdbc:postgresql://ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:5432/neondb";
        // When expected host is null or blank, must fail closed
        IllegalStateException exNull = assertThrows(IllegalStateException.class, () ->
                validateRehearsalHost(sampleUrl, null));
        assertTrue(exNull.getMessage().contains("NEON_REHEARSAL_EXPECTED_HOST must be explicitly configured"));

        IllegalStateException exBlank = assertThrows(IllegalStateException.class, () ->
                validateRehearsalHost(sampleUrl, "   "));
        assertTrue(exBlank.getMessage().contains("NEON_REHEARSAL_EXPECTED_HOST must be explicitly configured"));

        // Single-arg validateRehearsalHost uses System.getenv("NEON_REHEARSAL_EXPECTED_HOST") which is unset locally
        IllegalStateException exEnv = assertThrows(IllegalStateException.class, () ->
                validateRehearsalHost(sampleUrl));
        assertTrue(exEnv.getMessage().contains("NEON_REHEARSAL_EXPECTED_HOST must be explicitly configured"));
    }

    @Test
    @DisplayName("Host Guard: Rejects configured expected host if not on approved list")
    void testHostGuard_RejectsUnapprovedConfiguredHost() {
        String sampleUrl = "jdbc:postgresql://ep-unknown-database.us-east-2.aws.neon.tech:5432/neondb";
        SecurityException ex = assertThrows(SecurityException.class, () ->
                validateRehearsalHost(sampleUrl, "ep-unknown-database.us-east-2.aws.neon.tech"));
        assertTrue(ex.getMessage().contains("not in approved rehearsal hosts"));
    }

    @Test
    @DisplayName("Host Guard: Rejects production host URL even if attempted as expected host")
    void testHostGuard_RejectsProductionHost() {
        String prodUrl = "jdbc:postgresql://ep-round-mode-ay6n5xo2.us-east-2.aws.neon.tech:5432/neondb";
        SecurityException ex = assertThrows(SecurityException.class, () ->
                validateRehearsalHost(prodUrl, "ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech"));
        assertTrue(ex.getMessage().contains("production host identifier"));
    }

    @Test
    @DisplayName("Host Guard: Rejects unexpected host and URL parameter spoofing")
    void testHostGuard_RejectsUnexpectedHostAndParameterSpoofing() {
        String validExpected = "ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech";

        // Attacker attempts to spoof rehearsal host in query parameter
        String spoofedUrl = "jdbc:postgresql://evil-host.com:5432/neondb?note=ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech";
        SecurityException ex = assertThrows(SecurityException.class, () ->
                validateRehearsalHost(spoofedUrl, validExpected));
        assertTrue(ex.getMessage().contains("does not match expected host"));

        // Similar prefix host must be rejected by exact matching
        String prefixUrl = "jdbc:postgresql://ep-bold-bonus-ayb94wsf-spoofed.us-east-2.aws.neon.tech/neondb";
        assertThrows(SecurityException.class, () ->
                validateRehearsalHost(prefixUrl, validExpected));

        // Localhost must be rejected
        assertThrows(SecurityException.class, () ->
                validateRehearsalHost("jdbc:postgresql://localhost:5432/neondb", validExpected));
    }

    @Test
    @DisplayName("Host Guard: Rejects malformed URLs and unexpected authority formats")
    void testHostGuard_RejectsMalformedUrlsAndUnexpectedAuthority() {
        String validExpected = "ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech";

        // Wrong JDBC scheme
        assertThrows(IllegalArgumentException.class, () ->
                validateRehearsalHost("jdbc:mysql://ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:5432/neondb", validExpected));

        // Embedded credentials in authority
        assertThrows(IllegalArgumentException.class, () ->
                validateRehearsalHost("jdbc:postgresql://user:pass@ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:5432/neondb", validExpected));

        // Unexpected/non-standard port
        assertThrows(IllegalArgumentException.class, () ->
                validateRehearsalHost("jdbc:postgresql://ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:8080/neondb", validExpected));

        // Missing host
        assertThrows(IllegalArgumentException.class, () ->
                validateRehearsalHost("jdbc:postgresql:///neondb", validExpected));

        // Missing database name
        assertThrows(IllegalArgumentException.class, () ->
                validateRehearsalHost("jdbc:postgresql://ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:5432/", validExpected));
    }

    @Test
    @DisplayName("Host Guard: Rejects invalid or unexpected database name")
    void testHostGuard_RejectsUnexpectedDatabaseName() {
        String validExpected = "ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech";
        String wrongDbUrl = "jdbc:postgresql://ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:5432/production_db";
        SecurityException ex = assertThrows(SecurityException.class, () ->
                validateRehearsalHost(wrongDbUrl, validExpected));
        assertTrue(ex.getMessage().contains("Target database must be 'neondb'"));
    }

    @Test
    @DisplayName("Host Guard: Accepts exact approved rehearsal hosts with matching configured expected host")
    void testHostGuard_AcceptsExactApprovedHostsWithMatchingConfig() {
        String longHost = "ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech";
        assertDoesNotThrow(() ->
                validateRehearsalHost("jdbc:postgresql://ep-bold-bonus-ayb94wsf.us-east-2.aws.neon.tech:5432/neondb?sslmode=require", longHost));

        String poolerHost = "ep-bold-bonus-ayb94wsf-pooler.us-east-2.aws.neon.tech";
        assertDoesNotThrow(() ->
                validateRehearsalHost("jdbc:postgresql://ep-bold-bonus-ayb94wsf-pooler.us-east-2.aws.neon.tech:5432/neondb", poolerHost));

        String shortHost = "ep-bold-bonus-ayb94wsf";
        assertDoesNotThrow(() ->
                validateRehearsalHost("jdbc:postgresql://ep-bold-bonus-ayb94wsf/neondb", shortHost));
    }

    @Test
    @DisplayName("Baseline Guard: Rejects missing baseline (null/empty DB)")
    void testBaselineGuard_RejectsMissingBaseline() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(null, new MigrationInfo[0]));
        assertTrue(ex.getMessage().contains("no applied Flyway baseline"));
    }

    @Test
    @DisplayName("Baseline Guard: Rejects wrong baseline version (e.g. V6 or V8)")
    void testBaselineGuard_RejectsWrongBaselineVersion() {
        MigrationInfo mockV6 = mock(MigrationInfo.class);
        MigrationVersion v6 = MigrationVersion.fromVersion("6");
        when(mockV6.getVersion()).thenReturn(v6);

        IllegalStateException ex6 = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV6, new MigrationInfo[0]));
        assertTrue(ex6.getMessage().contains("strictly at baseline V7"));

        MigrationInfo mockV8 = mock(MigrationInfo.class);
        MigrationVersion v8 = MigrationVersion.fromVersion("8");
        when(mockV8.getVersion()).thenReturn(v8);

        IllegalStateException ex8 = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV8, new MigrationInfo[0]));
        assertTrue(ex8.getMessage().contains("strictly at baseline V7"));
    }

    @Test
    @DisplayName("Baseline Guard: Rejects baseline not in APPLIED/SUCCESS state")
    void testBaselineGuard_RejectsNonAppliedBaseline() {
        MigrationInfo mockV7Failed = mock(MigrationInfo.class);
        MigrationVersion v7 = MigrationVersion.fromVersion("7");
        when(mockV7Failed.getVersion()).thenReturn(v7);
        when(mockV7Failed.getState()).thenReturn(MigrationState.FAILED);

        IllegalStateException exFailed = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV7Failed, new MigrationInfo[0]));
        assertTrue(exFailed.getMessage().contains("FAILED"));

        MigrationInfo mockV7Pending = mock(MigrationInfo.class);
        when(mockV7Pending.getVersion()).thenReturn(v7);
        when(mockV7Pending.getState()).thenReturn(MigrationState.PENDING);

        IllegalStateException exPending = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV7Pending, new MigrationInfo[0]));
        assertTrue(exPending.getMessage().contains("PENDING"));
    }

    @Test
    @DisplayName("Pending Guard: Rejects unexpected pending migrations (e.g. multiple or non-V8)")
    void testPendingGuard_RejectsUnexpectedPendingMigrations() {
        MigrationInfo mockV7 = mock(MigrationInfo.class);
        MigrationVersion v7 = MigrationVersion.fromVersion("7");
        when(mockV7.getVersion()).thenReturn(v7);
        when(mockV7.getState()).thenReturn(MigrationState.SUCCESS);

        // 0 pending migrations
        IllegalStateException ex0 = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV7, new MigrationInfo[0]));
        assertTrue(ex0.getMessage().contains("Expected exactly 1 pending migration"));

        // 2 pending migrations
        MigrationInfo pending1 = mock(MigrationInfo.class);
        MigrationInfo pending2 = mock(MigrationInfo.class);
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV7, new MigrationInfo[]{pending1, pending2}));
        assertTrue(ex2.getMessage().contains("Found 2 pending migrations"));

        // 1 pending migration but wrong version (e.g. V9)
        when(pending1.getVersion()).thenReturn(MigrationVersion.fromVersion("9"));
        IllegalStateException exWrong = assertThrows(IllegalStateException.class, () ->
                validateBaselineAndPending(mockV7, new MigrationInfo[]{pending1}));
        assertTrue(exWrong.getMessage().contains("Pending migration must be V8"));
    }

    @Test
    @DisplayName("Pending Guard: Accepts exactly V7 applied and single pending V8")
    void testPendingGuard_AcceptsValidV7BaselineAndPendingV8() {
        MigrationInfo mockV7Success = mock(MigrationInfo.class);
        when(mockV7Success.getVersion()).thenReturn(MigrationVersion.fromVersion("7"));
        when(mockV7Success.getState()).thenReturn(MigrationState.SUCCESS);

        MigrationInfo mockPendingV8 = mock(MigrationInfo.class);
        when(mockPendingV8.getVersion()).thenReturn(MigrationVersion.fromVersion("8"));

        assertDoesNotThrow(() ->
                validateBaselineAndPending(mockV7Success, new MigrationInfo[]{mockPendingV8}));

        MigrationInfo mockV7Baseline = mock(MigrationInfo.class);
        when(mockV7Baseline.getVersion()).thenReturn(MigrationVersion.fromVersion("7"));
        when(mockV7Baseline.getState()).thenReturn(MigrationState.BASELINE);

        assertDoesNotThrow(() ->
                validateBaselineAndPending(mockV7Baseline, new MigrationInfo[]{mockPendingV8}));
    }

    @Test
    @DisplayName("Execute Flyway V8 migration strictly against rehearsal Neon branch")
    @EnabledIfEnvironmentVariable(named = "NEON_REHEARSAL_JDBC_URL", matches = ".+")
    void executeFlywayV8OnRehearsalBranch() {
        String jdbcUrl = System.getenv("NEON_REHEARSAL_JDBC_URL");
        String dbUser = System.getenv("NEON_REHEARSAL_DB_USER");
        String dbPass = System.getenv("NEON_REHEARSAL_DB_PASS");
        String expectedHost = System.getenv("NEON_REHEARSAL_EXPECTED_HOST");

        assertNotNull(jdbcUrl, "NEON_REHEARSAL_JDBC_URL must not be null");
        assertNotNull(dbUser, "NEON_REHEARSAL_DB_USER must not be null");
        assertNotNull(expectedHost, "NEON_REHEARSAL_EXPECTED_HOST must not be null");

        // 1. Strict Host and Database Validation (Exact match, structurally parsed, expected host required)
        validateRehearsalHost(jdbcUrl, expectedHost);

        System.out.println("==========================================================");
        System.out.println("EXECUTING FLYWAY V8 MIGRATION ON REHEARSAL BRANCH");
        System.out.println("Host: " + parseJdbcHost(jdbcUrl) + " (rehearsal-phase2-multitenancy)");
        System.out.println("Database: " + parseJdbcDatabase(jdbcUrl));
        System.out.println("==========================================================");

        // 2. Strict Fail-Closed Flyway Configuration
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false) // FAIL CLOSED: Never auto-baseline an uninitialized DB
                .outOfOrder(false)        // FAIL CLOSED: Strictly linear migrations only
                .group(false)             // ATOMIC: Migration V8 executed in dedicated transaction
                .load();

        // 3. Strict Pre-Migration Baseline & Pending Assertions
        MigrationInfo current = flyway.info().current();
        MigrationInfo[] pending = flyway.info().pending();
        validateBaselineAndPending(current, pending);

        // 4. Verify Active Database Identity from connection
        try (var conn = flyway.getConfiguration().getDataSource().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT current_database()")) {
            if (rs.next()) {
                String connectedDb = rs.getString(1);
                if (!"neondb".equals(connectedDb)) {
                    throw new SecurityException("FATAL: Connected database is '" + connectedDb + "', expected 'neondb'");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Database identity connection verification failed: " + e.getMessage(), e);
        }

        // 5. Execute Migration
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
        assertEquals("8", result.targetSchemaVersion, "Target schema version must be 8");
        assertEquals(1, result.migrationsExecuted, "Exactly 1 migration (V8) must be executed");
    }
}
