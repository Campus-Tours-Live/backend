package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository integration test against a REAL PostgreSQL (Testcontainers) — the only way to exercise
 * the {@code excl_guide_occurrence_no_overlap} GIST exclusion constraint (V1__schema.sql), the
 * invariant backstop asserting that a guide's materialized availability occurrences are always
 * disjoint. Mirrors {@code
 * BookingWriteIntegrationTest#overlappingGuideReservation_isRejectedByExclusionConstraint}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideAvailabilityOccurrenceRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired private GuideAvailabilityOccurrenceRepository occurrences;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;
    @Autowired private UniversityRepository universities;

    private UUID guideId;

    @BeforeEach
    void seedGuide() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();

        UserEntity guideUser = new UserEntity();
        guideUser.setId(UUID.randomUUID());
        guideUser.setOidcSubject("it-" + UUID.randomUUID());
        guideUser.setEmail("it-" + UUID.randomUUID() + "@example.com");
        guideUser.setDisplayName("Jane Guide");
        guideUser.setAccountStatus(AccountStatus.ACTIVE);
        guideUser.setPreferredLanguage("en-US");
        guideUser.setTimezone("America/Los_Angeles");
        users.save(guideUser);

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guides.save(guide);

        guideId = guide.getId();
    }

    @Test
    void overlappingOccurrences_forSameGuide_areRejectedByExclusionConstraint() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        occurrences.saveAndFlush(occurrence(guideId, start, start.plus(4, ChronoUnit.HOURS)));

        // Same guide, second occurrence starting mid-way through the first's window ->
        // excl_guide_occurrence_no_overlap must reject it at flush.
        assertThatThrownBy(
                        () ->
                                occurrences.saveAndFlush(
                                        occurrence(
                                                guideId,
                                                start.plus(2, ChronoUnit.HOURS),
                                                start.plus(6, ChronoUnit.HOURS))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonOverlappingOccurrences_forSameGuide_bothSucceed() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        occurrences.saveAndFlush(occurrence(guideId, start, start.plus(4, ChronoUnit.HOURS)));
        // Back-to-back, non-overlapping (tstzrange is [start, end) so touching bounds are fine).
        occurrences.saveAndFlush(
                occurrence(
                        guideId, start.plus(4, ChronoUnit.HOURS), start.plus(8, ChronoUnit.HOURS)));

        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId)).hasSize(2);
    }

    @Test
    void overlappingOccurrences_forDifferentGuides_bothSucceed() {
        UserEntity otherGuideUser = new UserEntity();
        otherGuideUser.setId(UUID.randomUUID());
        otherGuideUser.setOidcSubject("it-" + UUID.randomUUID());
        otherGuideUser.setEmail("it-" + UUID.randomUUID() + "@example.com");
        otherGuideUser.setDisplayName("Other Guide");
        otherGuideUser.setAccountStatus(AccountStatus.ACTIVE);
        otherGuideUser.setPreferredLanguage("en-US");
        otherGuideUser.setTimezone("America/Los_Angeles");
        users.save(otherGuideUser);

        GuideProfileEntity otherGuide = new GuideProfileEntity();
        otherGuide.setId(UUID.randomUUID());
        otherGuide.setUserId(otherGuideUser.getId());
        otherGuide.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guides.save(otherGuide);

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        occurrences.saveAndFlush(occurrence(guideId, start, start.plus(4, ChronoUnit.HOURS)));
        // Different guide, fully overlapping window -> the EXCLUDE is scoped per guide_id, so
        // this must succeed.
        occurrences.saveAndFlush(
                occurrence(otherGuide.getId(), start, start.plus(4, ChronoUnit.HOURS)));

        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(guideId)).hasSize(1);
        assertThat(occurrences.findByGuideIdOrderByDuringStartAtAsc(otherGuide.getId())).hasSize(1);
    }

    private static GuideAvailabilityOccurrenceEntity occurrence(
            UUID guideId, Instant startAt, Instant endAt) {
        GuideAvailabilityOccurrenceEntity occurrence = new GuideAvailabilityOccurrenceEntity();
        occurrence.setId(UUID.randomUUID());
        occurrence.setGuideId(guideId);
        occurrence.setDuringStartAt(startAt);
        occurrence.setDuringEndAt(endAt);
        occurrence.setGeneratedAt(Instant.now());
        return occurrence;
    }
}
