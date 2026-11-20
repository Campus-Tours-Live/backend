package com.CampusToursLive.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway runs in order with no outOfOrder, so a database that already applied V5/V6 rejects any
 * lower version. This starts from a database migrated to V6 and checks saved_tours still applies.
 */
@Testcontainers
class SavedToursMigrationUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Test
    void databaseAlreadyAtV6UpgradesToSavedTours() {
        MigrateResult existing = flyway().target("6").load().migrate();
        assertThat(existing.targetSchemaVersion).isEqualTo("6");

        MigrateResult upgrade = flyway().load().migrate();
        assertThat(upgrade.migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.targetSchemaVersion).isEqualTo("7");
        flyway().load().validate();

        JdbcTemplate jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_regclass('public.saved_tours') IS NOT NULL",
                                Boolean.class))
                .isTrue();
    }

    private static FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
    }
}
