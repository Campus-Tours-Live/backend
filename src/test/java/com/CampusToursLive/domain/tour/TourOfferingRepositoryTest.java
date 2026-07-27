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
import org.springframework.data.domain.Page;
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
    private UUID activeUniversityId;
    private UUID approvedGuideId;
    private UUID discoverableId;
    private UUID draftId;
    private UUID pausedId;
    private UUID archivedId;
    private UUID pendingGuideOfferingId;
    private UUID inactiveUniversityOfferingId;

    @BeforeEach
    void seedVisibilityFixtures() {
        searchMarker = "vis-" + UUID.randomUUID().toString().substring(0, 8);

        activeUniversityId = insertUniversity("active-" + searchMarker, "ACTIVE");
        UUID inactiveUniversityId = insertUniversity("archived-" + searchMarker, "ARCHIVED");

        approvedGuideId = insertGuide(activeUniversityId, "VERIFIED");
        UUID pendingGuideUserId = UUID.randomUUID();
        insertUser(pendingGuideUserId, "Pending Guide");
        UUID pendingGuideId =
                insertGuideWithUser(activeUniversityId, pendingGuideUserId, "PENDING");

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
        assertThat(
                        offerings.findDiscoverable(
                                null,
                                false,
                                List.of(TourTopic.values()[0]),
                                "",
                                PageRequest.of(0, 20)))
                .isNotNull();
    }

    @Test
    void findDiscoverable_excludesNonVisibleOfferings() {
        List<TourOfferingEntity> results =
                offerings
                        .findDiscoverable(
                                null,
                                false,
                                List.of(TourTopic.values()[0]),
                                searchMarker,
                                PageRequest.of(0, 20))
                        .getContent();

        assertThat(results).extracting(TourOfferingEntity::getId).containsExactly(discoverableId);
    }

    /**
     * The count query and the list query must agree.
     *
     * <p>They are one @Query with the FROM/WHERE written twice, so a filter edited on one side and
     * not the other silently desynchronises them: the page reports a total the results cannot
     * account for, and pagination shows empty or unreachable pages. This pins the invariant against
     * a filter that actually excludes rows, so the two clauses have something to disagree about.
     */
    @Test
    void findDiscoverable_totalMatchesTheRowsItReturns() {
        Page<TourOfferingEntity> page =
                offerings.findDiscoverable(
                        null,
                        false,
                        List.of(TourTopic.values()[0]),
                        searchMarker,
                        PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
        assertThat(page.getContent())
                .extracting(TourOfferingEntity::getId)
                .containsExactly(discoverableId);
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

    @Test
    void findDiscoverable_matchesViaDescriptionOnly() {
        String token = "desc-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id =
                insertOffering(
                        approvedGuideId,
                        activeUniversityId,
                        "Generic title",
                        "generic-" + token,
                        "ACTIVE",
                        "Body mentions " + token + " here",
                        "GENERAL_CAMPUS");
        entityManager.flush();
        entityManager.clear();

        assertThat(
                        offerings
                                .findDiscoverable(
                                        null,
                                        false,
                                        List.of(TourTopic.values()[0]),
                                        token,
                                        PageRequest.of(0, 20))
                                .getContent())
                .extracting(TourOfferingEntity::getId)
                .containsExactly(id);
    }

    @Test
    void findDiscoverable_matchesViaUniversityName() {
        String token = "uni-" + UUID.randomUUID().toString().substring(0, 8);
        UUID uni = insertUniversity(token, "ACTIVE"); // name is "University " + token
        UUID guide = insertGuide(uni, "VERIFIED");
        UUID id =
                insertOffering(
                        guide,
                        uni,
                        "Plain tour",
                        "plain-" + token,
                        "ACTIVE",
                        "no marker",
                        "GENERAL_CAMPUS");
        entityManager.flush();
        entityManager.clear();

        assertThat(
                        offerings
                                .findDiscoverable(
                                        null,
                                        false,
                                        List.of(TourTopic.values()[0]),
                                        token,
                                        PageRequest.of(0, 20))
                                .getContent())
                .extracting(TourOfferingEntity::getId)
                .containsExactly(id);
    }

    @Test
    void findDiscoverable_filtersByUniversityIdAndTopic() {
        String token = "flt-" + UUID.randomUUID().toString().substring(0, 8);
        UUID targetUni = insertUniversity("target-" + token, "ACTIVE");
        UUID targetGuide = insertGuide(targetUni, "VERIFIED");
        UUID match =
                insertOffering(
                        targetGuide,
                        targetUni,
                        token + " dorm",
                        "dorm-" + token,
                        "ACTIVE",
                        "",
                        "DORM_HOUSING");
        // same university, different topic -> excluded by the topic filter
        insertOffering(
                targetGuide,
                targetUni,
                token + " general",
                "gen-" + token,
                "ACTIVE",
                "",
                "GENERAL_CAMPUS");
        // different university, same topic -> excluded by the universityId filter
        UUID otherUni = insertUniversity("other-" + token, "ACTIVE");
        UUID otherGuide = insertGuide(otherUni, "VERIFIED");
        insertOffering(
                otherGuide,
                otherUni,
                token + " dorm",
                "dorm2-" + token,
                "ACTIVE",
                "",
                "DORM_HOUSING");
        entityManager.flush();
        entityManager.clear();

        assertThat(
                        offerings.findDiscoverable(
                                targetUni,
                                true,
                                List.of(TourTopic.DORM_HOUSING),
                                token,
                                PageRequest.of(0, 20)))
                .extracting(TourOfferingEntity::getId)
                .containsExactly(match);
    }

    @Test
    void findDiscoverable_excludesRowsNotMatchingQuery() {
        String absent = "absent-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(
                        offerings
                                .findDiscoverable(
                                        null,
                                        false,
                                        List.of(TourTopic.values()[0]),
                                        absent,
                                        PageRequest.of(0, 20))
                                .getContent())
                .isEmpty();
    }

    @Test
    void findDiscoverable_escapeClauseMatchesLiteralWildcard() {
        String token = "pct-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id =
                insertOffering(
                        approvedGuideId,
                        activeUniversityId,
                        "Save 50% on " + token,
                        "save-" + token,
                        "ACTIVE",
                        "",
                        "GENERAL_CAMPUS");
        entityManager.flush();
        entityManager.clear();

        // Escaped "%" ("!%") matches a literal "%" via `escape '!'`; the title contains "50%".
        assertThat(
                        offerings
                                .findDiscoverable(
                                        null,
                                        false,
                                        List.of(TourTopic.values()[0]),
                                        "50!%",
                                        PageRequest.of(0, 20))
                                .getContent())
                .extracting(TourOfferingEntity::getId)
                .contains(id);
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

    /**
     * {@code universityId} is unused here (guide_profiles no longer carries a flat university_id
     * column — that lives on guide_universities instead, which this repository's queries don't
     * consult), kept only so callers don't need touching: they already have the id in hand for
     * {@link #insertOffering}.
     */
    private UUID insertGuideWithUser(UUID universityId, UUID userId, String applicationStatus) {
        UUID guideId = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO guide_profiles (id, user_id, application_status)
                        VALUES (:id, :userId, CAST(:applicationStatus AS guide_application_status))
                        """)
                .setParameter("id", guideId)
                .setParameter("userId", userId)
                .setParameter("applicationStatus", applicationStatus)
                .executeUpdate();
        return guideId;
    }

    private UUID insertOffering(
            UUID guideId, UUID universityId, String title, String slug, String status) {
        return insertOffering(guideId, universityId, title, slug, status, "", "GENERAL_CAMPUS");
    }

    private UUID insertOffering(
            UUID guideId,
            UUID universityId,
            String title,
            String slug,
            String status,
            String description,
            String topic) {
        UUID id = UUID.randomUUID();
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO tour_offerings (
                          id, guide_id, university_id, title, slug, description, topic,
                          duration_min, price_cents, status
                        )
                        VALUES (
                          :id, :guideId, :universityId, :title, :slug, :description,
                          CAST(:topic AS tour_topic), 60, 5000,
                          CAST(:status AS tour_status)
                        )
                        """)
                .setParameter("id", id)
                .setParameter("guideId", guideId)
                .setParameter("universityId", universityId)
                .setParameter("title", title)
                .setParameter("slug", slug)
                .setParameter("description", description)
                .setParameter("topic", topic)
                .setParameter("status", status)
                .executeUpdate();
        return id;
    }
}
