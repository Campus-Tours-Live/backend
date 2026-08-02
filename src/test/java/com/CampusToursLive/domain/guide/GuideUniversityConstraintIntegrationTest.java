package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers (real PostgreSQL, full Spring context) proof that {@code
 * guide_universities.entry_year} is NOT NULL at the database layer (V1__schema.sql), not merely by
 * application-side validation — CTL-97 Task 5. The full context boots offline via a mocked {@link
 * JwtDecoder}, matching the sibling {@code OnboardingServiceIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideUniversityConstraintIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private JdbcTemplate jdbc;

    /**
     * I5: the column cannot hold null, enforced by the database rather than by hope.
     *
     * <p>UPDATEs an existing seeded row rather than INSERTing a new one. An INSERT can raise
     * DataIntegrityViolationException for a foreign key, a unique index, some OTHER not-null
     * column, an enum cast, or simply an empty result from its SELECT — any of which would make
     * this test green while `entry_year` stayed nullable. Narrowing to a single-column UPDATE
     * leaves exactly one thing that can fail, and the SQLSTATE assertion names it: 23502 is
     * PostgreSQL's not_null_violation.
     */
    @Test
    void entryYear_isNotNullable() {
        UUID id = jdbc.queryForObject("SELECT id FROM guide_universities LIMIT 1", UUID.class);
        assertNotNull(id, "seed data must provide a row to test against");

        DataIntegrityViolationException ex =
                assertThrows(
                        DataIntegrityViolationException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE guide_universities SET entry_year = NULL WHERE id = ?",
                                        id));

        SQLException sql = (SQLException) ex.getMostSpecificCause();
        assertEquals(
                "23502", sql.getSQLState(), "must fail as not_null_violation, not incidentally");
    }
}
