package com.supplychain.controltower.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;

import static org.junit.jupiter.api.Assertions.*;

class NeonRehearsalPostgresVerificationTest {

    private static final String PROD_HOST = "ep-round-mode-ay6n5xo2";

    @Test
    @DisplayName("Verify Neon Rehearsal Database Schema, Constraints, Demo Users, and Multi-Tenant Isolation")
    @EnabledIfEnvironmentVariable(named = "NEON_REHEARSAL_JDBC_URL", matches = ".+")
    void verifyRehearsalDatabaseIsolation() throws SQLException {
        String jdbcUrl = System.getenv("NEON_REHEARSAL_JDBC_URL");
        String dbUser = System.getenv("NEON_REHEARSAL_DB_USER");
        String dbPass = System.getenv("NEON_REHEARSAL_DB_PASS");

        assertNotNull(jdbcUrl);
        assertFalse(jdbcUrl.contains(PROD_HOST), "FATAL: Must never target production host");
        assertTrue(jdbcUrl.contains("ep-bold-bonus-ayb94wsf"), "Target must be rehearsal endpoint");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false);

            // 1. Verify Demo Company and Demo Users exist and are functional
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT u.username, u.role, c.id, c.code, c.status " +
                    "FROM users u JOIN companies c ON u.company_id = c.id " +
                    "WHERE u.username = 'admin'")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "Admin user must exist linked to company");
                    assertEquals("admin", rs.getString("username"));
                    assertEquals("ROLE_ADMIN", rs.getString("role"));
                    assertEquals(1L, rs.getLong("id"));
                    assertEquals("DEMO", rs.getString("code"));
                    assertEquals("ACTIVE", rs.getString("status"));
                    System.out.println("Verified demo admin user linked to Demo Company (id=1, status=ACTIVE)");
                }
            }

            // 2. Verify all 4 demo users belong to Company 1
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT count(*) FROM users WHERE company_id = 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(4, rs.getInt(1), "All 4 demo users must belong to company 1");
                    System.out.println("Verified 4 demo users all belong to company 1");
                }
            }

            // 3. Find an existing product SKU from Company 1
            String existingSku = null;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT sku FROM products WHERE company_id = 1 LIMIT 1")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        existingSku = rs.getString("sku");
                    }
                }
            }
            assertNotNull(existingSku, "At least 1 product SKU must exist in company 1");
            System.out.println("Existing Company 1 product SKU: " + existingSku);

            // 4. Test Multi-Tenant Isolation with Company 2:
            // Insert temporary Company 2
            long company2Id = 9999L;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO companies (id, name, code, status) VALUES (?, 'Rehearsal Beta Corp', 'BETA_REHEARSAL', 'ACTIVE')")) {
                stmt.setLong(1, company2Id);
                stmt.executeUpdate();
                System.out.println("Inserted temporary Company 2 (id=9999)");
            }

            // Insert product for Company 2 with the EXACT SAME SKU as Company 1
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO products (company_id, sku, name, price, reorder_level, safety_stock) " +
                    "VALUES (?, ?, 'Beta Duplicate SKU Product', 199.99, 10, 20)")) {
                stmt.setLong(1, company2Id);
                stmt.setString(2, existingSku);
                int rows = stmt.executeUpdate();
                assertEquals(1, rows);
                System.out.println("Successfully inserted product with identical SKU '" + existingSku + "' for Company 2!");
            }

            // 5. Test Composite Unique Constraint Violation:
            // Attempt to insert duplicate SKU for the SAME company (Company 2) -> Must fail
            Savepoint sp1 = conn.setSavepoint("sp_unique_test");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO products (company_id, sku, name, price, reorder_level, safety_stock) " +
                    "VALUES (?, ?, 'Beta Second Duplicate Product', 299.99, 15, 30)")) {
                stmt.setLong(1, company2Id);
                stmt.setString(2, existingSku);
                SQLException ex = assertThrows(SQLException.class, stmt::executeUpdate,
                        "Duplicate SKU within same company must throw unique constraint violation");
                assertTrue(ex.getMessage().contains("uk_products_company_sku") || ex.getMessage().toLowerCase().contains("unique"),
                        "Expected uk_products_company_sku violation, got: " + ex.getMessage());
                System.out.println("Verified composite unique constraint uk_products_company_sku rejected duplicate SKU for Company 2: " + ex.getMessage());
            } finally {
                conn.rollback(sp1);
            }

            // 6. Test Foreign Key Constraint Violation:
            // Attempt to insert product with invalid company_id (999999) -> Must fail
            Savepoint sp2 = conn.setSavepoint("sp_fk_test");
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO products (company_id, sku, name, price, reorder_level, safety_stock) " +
                    "VALUES (999999, 'SKU-ORPHAN', 'Orphan Product', 50.0, 5, 10)")) {
                SQLException ex = assertThrows(SQLException.class, stmt::executeUpdate,
                        "Foreign key violation expected for invalid company_id");
                assertTrue(ex.getMessage().contains("fk_products_company") || ex.getMessage().toLowerCase().contains("foreign key"),
                        "Expected fk_products_company violation, got: " + ex.getMessage());
                System.out.println("Verified foreign key constraint fk_products_company rejected invalid company_id: " + ex.getMessage());
            } finally {
                conn.rollback(sp2);
            }

            // Roll back all temporary rehearsal changes so the rehearsal database remains pristine!
            conn.rollback();
            System.out.println("Rolled back temporary test data. Rehearsal database remains in exact post-migration state.");
        }
    }
}
