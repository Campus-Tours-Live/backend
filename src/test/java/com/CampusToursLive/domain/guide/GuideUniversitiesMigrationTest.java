package com.CampusToursLive.domain.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
 * Migration integration test against a REAL PostgreSQL (Testcontainers) proving that V1 creates the
 * {@code guide_universities} join table introduced by Profile Contract v2 Phase 2 (CTL-97). Flyway
 * runs V1 → V2 on a clean container. Asserts the table's columns (including the {@code citext
 * school_email} PII column and the {@code guide_verification_status} enum default), the unique
 * {@code (guide_profile_id, university_id)} constraint, and the {@code
 * ix_guide_universities_profile} index all exist. Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideUniversitiesMigrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private JdbcTemplate jdbc;

    @Test
    void tableExistsWithExpectedColumns() {
        List<Map<String, Object>> columns =
                jdbc.queryForList(
                        "SELECT column_name, data_type, udt_name, is_nullable, column_default "
                                + "FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'guide_universities' "
                                + "ORDER BY ordinal_position");

        Map<String, Map<String, Object>> byName =
                columns.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        c -> (String) c.get("column_name"), c -> c));

        assertThat(byName)
                .containsKeys(
                        "id",
                        "guide_profile_id",
                        "university_id",
                        "major",
                        "degree",
                        "class_year",
                        "entry_year",
                        "version",
                        "school_email",
                        "verification_status",
                        "verification_sent_at",
                        "verified_at",
                        "created_at",
                        "updated_at");

        assertThat(byName.get("id").get("data_type")).isEqualTo("uuid");
        assertThat(byName.get("id").get("is_nullable")).isEqualTo("NO");

        assertThat(byName.get("guide_profile_id").get("data_type")).isEqualTo("uuid");
        assertThat(byName.get("guide_profile_id").get("is_nullable")).isEqualTo("NO");

        assertThat(byName.get("university_id").get("data_type")).isEqualTo("uuid");
        assertThat(byName.get("university_id").get("is_nullable")).isEqualTo("NO");

        assertThat(byName.get("major").get("data_type")).isEqualTo("text");
        assertThat(byName.get("degree").get("data_type")).isEqualTo("text");
        assertThat(byName.get("class_year").get("data_type")).isEqualTo("text");
        assertThat(byName.get("entry_year").get("data_type")).isEqualTo("integer");
        assertThat(byName.get("entry_year").get("is_nullable")).isEqualTo("NO");

        assertThat(byName.get("version").get("data_type")).isEqualTo("bigint");
        assertThat(byName.get("version").get("is_nullable")).isEqualTo("NO");
        assertThat((String) byName.get("version").get("column_default")).contains("0");

        assertThat(byName.get("school_email").get("udt_name")).isEqualTo("citext");
        assertThat(byName.get("school_email").get("is_nullable")).isEqualTo("YES");

        assertThat(byName.get("verification_status").get("udt_name"))
                .isEqualTo("guide_verification_status");
        assertThat(byName.get("verification_status").get("is_nullable")).isEqualTo("NO");
        assertThat((String) byName.get("verification_status").get("column_default"))
                .contains("NOT_SUBMITTED");

        assertThat(byName.get("verification_sent_at").get("data_type"))
                .isEqualTo("timestamp with time zone");
        assertThat(byName.get("verified_at").get("data_type"))
                .isEqualTo("timestamp with time zone");

        assertThat(byName.get("created_at").get("data_type")).isEqualTo("timestamp with time zone");
        assertThat(byName.get("created_at").get("is_nullable")).isEqualTo("NO");
        assertThat(byName.get("updated_at").get("data_type")).isEqualTo("timestamp with time zone");
        assertThat(byName.get("updated_at").get("is_nullable")).isEqualTo("NO");
    }

    @Test
    void hasUniqueConstraintOnGuideProfileAndUniversity() {
        List<String> uniqueColumns =
                jdbc.queryForList(
                        "SELECT kcu.column_name "
                                + "FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage kcu "
                                + "  ON tc.constraint_name = kcu.constraint_name "
                                + " AND tc.table_schema = kcu.table_schema "
                                + "WHERE tc.table_schema = 'public' "
                                + "  AND tc.table_name = 'guide_universities' "
                                + "  AND tc.constraint_type = 'UNIQUE' "
                                + "ORDER BY kcu.ordinal_position",
                        String.class);

        assertThat(uniqueColumns).containsExactly("guide_profile_id", "university_id");
    }

    @Test
    void hasIndexOnGuideProfileId() {
        Integer indexCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM pg_indexes "
                                + "WHERE schemaname = 'public' "
                                + "  AND tablename = 'guide_universities' "
                                + "  AND indexname = 'ix_guide_universities_profile'",
                        Integer.class);

        assertThat(indexCount).isEqualTo(1);
    }
}
