package com.CampusToursLive.domain.university;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migration integration test against a REAL PostgreSQL (Testcontainers) proving the forward-only
 * image_url backfill. Flyway runs V1 → V4 on a clean container (V2 seeds the demo universities; V4
 * reconciles image_url/name). Asserts that after the full chain every seeded university row has a
 * non-null {@code image_url} — i.e. no row is left with the {@code ON CONFLICT DO NOTHING} gap that
 * V4 exists to close. {@code replace=NONE} keeps the container datasource; {@code ddl-auto=none}
 * means Flyway owns the schema. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UniversityImageBackfillMigrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private JdbcTemplate jdbc;

    @Test
    void everySeededUniversityHasImageUrlAfterV4() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM universities", Integer.class);
        Integer missing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM universities WHERE image_url IS NULL", Integer.class);

        assertThat(total).isNotNull().isGreaterThan(0);
        assertThat(missing).isZero();
    }

    @Test
    void imageUrlsPointAtTheSeededR2Bucket() {
        Integer offBucket =
                jdbc.queryForObject(
                        "SELECT count(*) FROM universities "
                                + "WHERE image_url NOT LIKE 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/%'",
                        Integer.class);

        assertThat(offBucket).isZero();
    }
}
