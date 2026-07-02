package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.CampusToursLive.domain.university.UniversityRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies guide availability TIME columns round-trip without UTC shifting (regression for
 * hibernate.jdbc.time_zone=UTC + LocalTime).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
class GuideAvailabilityRuleRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private UniversityRepository universities;
    @Autowired private EntityManager entityManager;

    private UUID guideId;

    @BeforeEach
    void seedGuide() {
        UUID universityId =
                universities.findAll(PageRequest.of(0, 1)).getContent().getFirst().getId();
        UUID userId = UUID.randomUUID();
        guideId = UUID.randomUUID();

        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO users (id, display_name, account_status)
                        VALUES (:userId, 'Availability Test Guide', 'ACTIVE')
                        """)
                .setParameter("userId", userId)
                .executeUpdate();

        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO guide_profiles (id, user_id, university_id, major)
                        VALUES (:guideId, :userId, :universityId, 'Computer Science')
                        """)
                .setParameter("guideId", guideId)
                .setParameter("userId", userId)
                .setParameter("universityId", universityId)
                .executeUpdate();

        entityManager.flush();
    }

    @Test
    void persistsLocalWallClockTimesWithoutUtcShift() {
        GuideAvailabilityRuleEntity rule = new GuideAvailabilityRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setGuideId(guideId);
        rule.setDayOfWeek((short) 2);
        rule.setStartLocal(LocalTime.of(9, 0));
        rule.setEndLocal(LocalTime.of(21, 45));
        rule.setTimezone("America/Los_Angeles");
        rule.setEffectiveFrom(LocalDate.parse("2026-07-02"));
        rule.setActive(true);

        rules.saveAndFlush(rule);
        entityManager.clear();

        GuideAvailabilityRuleEntity loaded = rules.findById(rule.getId()).orElseThrow();
        assertThat(loaded.getStartLocal()).isEqualTo(LocalTime.of(9, 0));
        assertThat(loaded.getEndLocal()).isEqualTo(LocalTime.of(21, 45));

        Object[] row =
                (Object[])
                        entityManager
                                .createNativeQuery(
                                        """
                                        SELECT start_local, end_local
                                        FROM guide_availability_rules
                                        WHERE id = :id
                                        """)
                                .setParameter("id", rule.getId())
                                .getSingleResult();
        assertThat(row[0].toString()).startsWith("09:00");
        assertThat(row[1].toString()).startsWith("21:45");
    }
}
