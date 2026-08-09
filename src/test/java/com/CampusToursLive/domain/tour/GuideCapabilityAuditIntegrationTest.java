package com.CampusToursLive.domain.tour;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
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
 * CTL-97 Task 12 — RELEASE-BLOCKING guide capability audit, discovery/search exposure capability.
 *
 * <p>A GUIDE+PENDING user must never become "bookable": even if their offering somehow reaches
 * {@code ACTIVE} (e.g. it was activated while VERIFIED, then the guide's application status later
 * reverted to PENDING), the public marketplace catalog ({@link
 * TourOfferingRepository#findDiscoverable} / {@link TourOfferingRepository#findDiscoverableById})
 * must exclude it — real Postgres, not a mock, since the gate lives in the JPQL {@code g.status =
 * VERIFIED} filter, not in application code.
 *
 * <p>This duplicates the fixture shape of {@code TourOfferingRepositoryTest} (same visibility
 * filter) but is kept as an independent, dedicated file for the CTL-97 audit: it must keep failing
 * a future refactor even if someone edits/removes the unrelated pre-existing repository test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideCapabilityAuditIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private TourOfferingRepository offerings;
    @Autowired private EntityManager entityManager;

    @Test
    void guideAndPending_offeringIsExcludedFromDiscoverableById_evenWhenActive() {
        String marker = "capaudit-" + UUID.randomUUID().toString().substring(0, 8);
        UUID universityId = insertUniversity(marker, "ACTIVE");
        UUID pendingGuideId = insertGuide(marker, "PENDING");
        UUID verifiedGuideId = insertGuide(marker, "VERIFIED");

        UUID pendingGuideOfferingId =
                insertOffering(
                        pendingGuideId,
                        universityId,
                        marker + " pending guide tour",
                        "pending-" + marker,
                        "ACTIVE");
        UUID verifiedGuideOfferingId =
                insertOffering(
                        verifiedGuideId,
                        universityId,
                        marker + " verified guide tour",
                        "verified-" + marker,
                        "ACTIVE");
        entityManager.flush();
        entityManager.clear();

        // The release gate: an ACTIVE offering owned by a GUIDE+PENDING guide is invisible to the
        // public catalog, both by direct id lookup and in the paged search.
        assertThat(offerings.findDiscoverableById(pendingGuideOfferingId)).isEmpty();
        // Positive control: the VERIFIED guide's identical ACTIVE offering IS discoverable, so this
        // test is proving the guide_status filter specifically, not e.g. an unrelated fixture bug.
        assertThat(offerings.findDiscoverableById(verifiedGuideOfferingId)).isPresent();

        List<TourOfferingEntity> results =
                offerings
                        .findDiscoverable(
                                null,
                                false,
                                List.of(TourTopic.values()[0]),
                                marker,
                                PageRequest.of(0, 20))
                        .getContent();
        assertThat(results)
                .extracting(TourOfferingEntity::getId)
                .containsExactly(verifiedGuideOfferingId);
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

    private UUID insertGuide(String marker, String guideStatus) {
        UUID userId = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO users (id, display_name, account_status)
                        VALUES (:id, :displayName, 'ACTIVE')
                        """)
                .setParameter("id", userId)
                .setParameter("displayName", "Guide " + guideStatus + " " + marker)
                .executeUpdate();
        UUID guideId = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO guide_profiles (id, user_id, guide_status)
                        VALUES (:id, :userId, CAST(:guideStatus AS guide_application_status))
                        """)
                .setParameter("id", guideId)
                .setParameter("userId", userId)
                .setParameter("guideStatus", guideStatus)
                .executeUpdate();
        return guideId;
    }

    private UUID insertOffering(
            UUID guideId, UUID universityId, String title, String slug, String status) {
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
                          CAST('GENERAL_CAMPUS' AS tour_topic), 60, 5000,
                          CAST(:status AS tour_status)
                        )
                        """)
                .setParameter("id", id)
                .setParameter("guideId", guideId)
                .setParameter("universityId", universityId)
                .setParameter("title", title)
                .setParameter("slug", slug)
                .setParameter("status", status)
                .executeUpdate();
        return id;
    }
}
