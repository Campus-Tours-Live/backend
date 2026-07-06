package com.CampusToursLive.domain.tour;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Exercises {@link TourOfferingRepository#findDiscoverable} and {@link
 * TourOfferingRepository#findDiscoverableById} against real Postgres so the JPQL visibility filter
 * is proven — not just mocked at the service layer.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TourOfferingRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private TourOfferingRepository offerings;
    @Autowired private EntityManager entityManager;

    private String searchMarker;
    private UUID discoverableId;
    private UUID draftId;
    private UUID pausedId;
    private UUID archivedId;
    private UUID pendingGuideOfferingId;
    private UUID inactiveUniversityOfferingId;

    @BeforeEach
    void seedVisibilityFixtures() {
        searchMarker = "vis-" + UUID.randomUUID().toString().substring(0, 8);

        UUID activeUniversityId = insertUniversity("active-" + searchMarker, "ACTIVE");
        UUID inactiveUniversityId = insertUniversity("archived-" + searchMarker, "ARCHIVED");

        UUID approvedGuideId = insertGuide(activeUniversityId, "APPROVED");
        UUID pendingGuideUserId = UUID.randomUUID();
        insertUser(pendingGuideUserId, "Pending Guide");
        UUID pendingGuideId =
                insertGuideWithUser(activeUniversityId, pendingGuideUserId, "PENDING_REVIEW");

        discoverableId =
                insertOffering(
                        approvedGuideId,
                        activeUniversityId,
                        searchMarker + " visible",
                        "visible-" + searchMarker,
                        "ACTIVE");
        draftId =
                insertOffering(
                        approvedGuideId,
                        activeUniversityId,
                        searchMarker + " draft",
                        "draft-" + searchMarker,
                        "DRAFT");
        pausedId =
                insertOffering(
                        approvedGuideId,
                        activeUniversityId,
                        searchMarker + " paused",
                        "paused-" + searchMarker,
                        "PAUSED");
        archivedId =
                insertOffering(
                        approvedGuideId,
                        activeUniversityId,
                        searchMarker + " archived",
                        "archived-" + searchMarker,
                        "ARCHIVED");
        pendingGuideOfferingId =
                insertOffering(
                        pendingGuideId,
                        activeUniversityId,
                        searchMarker + " pending-guide",
                        "pending-guide-" + searchMarker,
                        "ACTIVE");
        inactiveUniversityOfferingId =
                insertOffering(
                        approvedGuideId,
                        inactiveUniversityId,
                        searchMarker + " inactive-university",
                        "inactive-uni-" + searchMarker,
                        "ACTIVE");

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findDiscoverable_succeedsWithNullOptionalFilters() {
        assertThat(offerings.findDiscoverable(null, null, "", PageRequest.of(0, 20))).isNotNull();
    }

    @Test
    void findDiscoverable_excludesNonVisibleOfferings() {
        List<TourOfferingEntity> results =
                offerings.findDiscoverable(null, null, searchMarker, PageRequest.of(0, 20));

        assertThat(results).extracting(TourOfferingEntity::getId).containsExactly(discoverableId);
    }

    @Test
    void findDiscoverableById_returnsOnlyVisibleOffering() {
        assertThat(offerings.findDiscoverableById(discoverableId)).isPresent();
        assertThat(offerings.findDiscoverableById(draftId)).isEmpty();
        assertThat(offerings.findDiscoverableById(pausedId)).isEmpty();
        assertThat(offerings.findDiscoverableById(archivedId)).isEmpty();
    }

    @Test
    void findDiscoverableById_excludesPendingGuideAndInactiveUniversity() {
        assertThat(offerings.findDiscoverableById(pendingGuideOfferingId)).isEmpty();
        assertThat(offerings.findDiscoverableById(inactiveUniversityOfferingId)).isEmpty();
    }

    private void insertUser(UUID userId, String displayName) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO users (id, display_name, account_status)
                        VALUES (:id, :displayName, 'ACTIVE')
                        """)
                .setParameter("id", userId)
                .setParameter("displayName", displayName)
                .executeUpdate();
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
        insertUser(userId, "Guide " + applicationStatus);
        return insertGuideWithUser(universityId, userId, applicationStatus);
    }

    private UUID insertGuideWithUser(UUID universityId, UUID userId, String applicationStatus) {
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
            UUID guideId, UUID universityId, String title, String slug, String status) {
        UUID id = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO tour_offerings (
                          id, guide_id, university_id, title, slug, topic,
                          duration_min, price_cents, status
                        )
                        VALUES (
                          :id, :guideId, :universityId, :title, :slug,
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
