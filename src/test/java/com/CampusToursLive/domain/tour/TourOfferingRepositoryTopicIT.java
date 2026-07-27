package com.CampusToursLive.domain.tour;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the multi-topic {@code IN} filter + {@code :filterByTopic = false} guard in {@link
 * TourOfferingRepository#findDiscoverable} against real Postgres — not just mocked at the service
 * layer. Mirrors the datasource/Testcontainers setup and native-SQL seed helpers used by {@link
 * TourOfferingRepositoryTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TourOfferingRepositoryTopicIT {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private TourOfferingRepository offerings;
    @Autowired private EntityManager entityManager;

    private final Map<TourTopic, Long> seededCounts = new HashMap<>();

    // V2__seed_demo_data.sql pre-loads demo tour_offerings into every fresh Testcontainers
    // Postgres this @DataJpaTest boots (Flyway runs on migrate). Scoping every query below to our
    // own freshly-inserted university isolates these assertions from that unrelated demo data,
    // since universityId is an equality filter (unlike the OR-based `q` match).
    private UUID universityId;

    @BeforeEach
    void seedOfferingsAcrossTopics() {
        String marker = "topic-it-" + UUID.randomUUID().toString().substring(0, 8);
        universityId = insertUniversity("uni-" + marker, "ACTIVE");
        UUID guideId = insertGuide(universityId, "VERIFIED");

        // Two GENERAL_CAMPUS rows so paging (page size 1) has more than one page to walk.
        seedOffering(guideId, universityId, marker + " general 1", TourTopic.GENERAL_CAMPUS);
        seedOffering(guideId, universityId, marker + " general 2", TourTopic.GENERAL_CAMPUS);
        seedOffering(guideId, universityId, marker + " dorm", TourTopic.DORM_HOUSING);
        seedOffering(guideId, universityId, marker + " major", TourTopic.MAJOR_SPECIFIC);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void inFilter_subset_returnsOnlyThoseTopics() {
        Page<TourOfferingEntity> p =
                offerings.findDiscoverable(
                        universityId,
                        true,
                        List.of(TourTopic.GENERAL_CAMPUS, TourTopic.DORM_HOUSING),
                        "",
                        PageRequest.of(0, 20));
        assertThat(p.getContent())
                .extracting(TourOfferingEntity::getTopic)
                .containsOnly(TourTopic.GENERAL_CAMPUS, TourTopic.DORM_HOUSING);
    }

    @Test
    void guardFalse_placeholderList_returnsAllTopics() {
        Page<TourOfferingEntity> p =
                offerings.findDiscoverable(
                        universityId,
                        false,
                        List.of(TourTopic.values()[0]),
                        "",
                        PageRequest.of(0, 50));
        assertThat(p.getContent())
                .extracting(TourOfferingEntity::getTopic)
                .contains(
                        TourTopic.GENERAL_CAMPUS, TourTopic.DORM_HOUSING, TourTopic.MAJOR_SPECIFIC);
    }

    @Test
    void countMatchesContent_underSubsetAndPaging() {
        Page<TourOfferingEntity> page0 =
                offerings.findDiscoverable(
                        universityId,
                        true,
                        List.of(TourTopic.GENERAL_CAMPUS),
                        "",
                        PageRequest.of(0, 1));
        // totalElements counts the whole filtered set; page size is 1
        assertThat(page0.getTotalElements()).isEqualTo(countSeededWith(TourTopic.GENERAL_CAMPUS));
        assertThat(page0.getContent()).hasSize(1);

        Page<TourOfferingEntity> page1 =
                offerings.findDiscoverable(
                        universityId,
                        true,
                        List.of(TourTopic.GENERAL_CAMPUS),
                        "",
                        PageRequest.of(1, 1));
        assertThat(page1.getContent())
                .hasSize(Math.max(0, (int) page0.getTotalElements() - 1) >= 1 ? 1 : 0);
    }

    private long countSeededWith(TourTopic topic) {
        return seededCounts.getOrDefault(topic, 0L);
    }

    private void seedOffering(UUID guideId, UUID universityId, String title, TourTopic topic) {
        insertOffering(guideId, universityId, title, "ACTIVE", topic.name());
        seededCounts.merge(topic, 1L, Long::sum);
    }

    private UUID insertUniversity(String slug, String status) {
        UUID id = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO universities (id, slug, name, city, status)
                        VALUES (:id, :slug, :name, 'Test City', CAST(:status AS university_status))
                        """)
                .setParameter("id", id)
                .setParameter("slug", slug)
                .setParameter("name", "University " + slug)
                .setParameter("status", status)
                .executeUpdate();
        return id;
    }

    private UUID insertGuide(UUID universityId, String applicationStatus) {
        UUID userId = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO users (id, display_name, account_status)
                        VALUES (:id, :displayName, 'ACTIVE')
                        """)
                .setParameter("id", userId)
                .setParameter("displayName", "Guide " + applicationStatus)
                .executeUpdate();

        UUID guideId = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO guide_profiles (id, user_id, university_id, major, application_status)
                        VALUES (:id, :userId, :universityId, 'Computer Science',
                                CAST(:applicationStatus AS guide_application_status))
                        """)
                .setParameter("id", guideId)
                .setParameter("userId", userId)
                .setParameter("universityId", universityId)
                .setParameter("applicationStatus", applicationStatus)
                .executeUpdate();
        return guideId;
    }

    private UUID insertOffering(
            UUID guideId, UUID universityId, String title, String status, String topic) {
        UUID id = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO tour_offerings (
                          id, guide_id, university_id, title, slug, description, topic,
                          duration_min, price_cents, status
                        )
                        VALUES (
                          :id, :guideId, :universityId, :title, :slug, '',
                          CAST(:topic AS tour_topic), 60, 5000,
                          CAST(:status AS tour_status)
                        )
                        """)
                .setParameter("id", id)
                .setParameter("guideId", guideId)
                .setParameter("universityId", universityId)
                .setParameter("title", title)
                .setParameter("slug", "slug-" + id)
                .setParameter("topic", topic)
                .setParameter("status", status)
                .executeUpdate();
        return id;
    }
}
