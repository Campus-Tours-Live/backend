package com.CampusToursLive.domain.university;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository integration test against a REAL PostgreSQL (Testcontainers) — the only way to exercise
 * what H2 can't: the Flyway migrations actually applying (V1..V2), the {@code university_status} PG
 * enum mapping through JPA, and the catalog search query. {@code replace=NONE} keeps the container
 * datasource (wired by {@code @ServiceConnection}) instead of an embedded DB; {@code ddl-auto=none}
 * (application.properties) means Flyway owns the schema.
 *
 * <p>Requires a running Docker daemon. Named *Test so Surefire runs it; rename to *IT if a
 * Failsafe/integration-test phase is added later.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UniversityRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private UniversityRepository universities;

    @Test
    void migrations_applyAndSeedCatalog() {
        // If this passes, V1..V2 ran against a real Postgres (schema + V2 seed of ~100 unis).
        assertThat(universities.count()).isGreaterThan(0);
    }

    @Test
    void search_returnsOnlyActiveRows_mappingPgEnum() {
        var results = universities.search("", PageRequest.of(0, 200));
        assertThat(results).isNotEmpty();
        // Proves the university_status PG enum round-trips via @Enumerated +
        // stringtype=unspecified.
        assertThat(results)
                .allSatisfy(u -> assertThat(u.getStatus()).isEqualTo(UniversityStatus.ACTIVE));
    }

    @Test
    void search_isCaseInsensitive_onShortName() {
        assertThat(universities.search("mit", PageRequest.of(0, 10)))
                .anySatisfy(u -> assertThat(u.getShortName()).isEqualTo("MIT"));
    }
}
