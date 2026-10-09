package com.CampusToursLive.domain.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourOfferingEntity;
import com.CampusToursLive.domain.tour.TourOfferingRepository;
import com.CampusToursLive.domain.tour.TourStatus;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Saved tours against a real PostgreSQL (Testcontainers). Requires a running Docker daemon. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({SavedTourService.class, TourDiscoveryService.class})
class SavedTourServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @Autowired SavedTourService service;
    @Autowired SavedTourRepository savedTours;
    @Autowired UserRepository users;
    @Autowired GuideProfileRepository guides;
    @Autowired TourOfferingRepository offerings;
    @Autowired UniversityRepository universities;
    @Autowired JdbcTemplate jdbc;

    private UUID participantId;
    private GuideProfileEntity guide;
    private UUID universityId;

    @BeforeEach
    void seedGraph() {
        UniversityEntity university =
                universities.findAll().stream()
                        .filter(u -> u.getStatus() == UniversityStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow();
        universityId = university.getId();

        participantId = users.saveAndFlush(user("Pat Participant")).getId();
        UserEntity guideUser = users.saveAndFlush(user("Jane Guide"));

        GuideProfileEntity g = new GuideProfileEntity();
        g.setId(UUID.randomUUID());
        g.setUserId(guideUser.getId());
        g.setStatus(GuideStatus.VERIFIED);
        guide = guides.saveAndFlush(g);
    }

    @Test
    void save_thenResave_isIdempotent() {
        TourOfferingEntity tour = offering("Campus Walk", TourStatus.ACTIVE);

        assertThat(service.save(participantId, tour.getId())).isTrue();
        assertThat(service.save(participantId, tour.getId())).isFalse();

        assertThat(service.savedTourIds(participantId)).containsExactly(tour.getId());
    }

    @Test
    void save_nonDiscoverableOffering_throwsNotFound() {
        TourOfferingEntity draft = offering("Draft Walk", TourStatus.DRAFT);

        assertThatThrownBy(() -> service.save(participantId, draft.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(service.savedTourIds(participantId)).isEmpty();
    }

    @Test
    void unsave_removesOnlyThatTour_andIsSafeToRepeat() {
        TourOfferingEntity a = offering("A", TourStatus.ACTIVE);
        TourOfferingEntity b = offering("B", TourStatus.ACTIVE);
        service.save(participantId, a.getId());
        service.save(participantId, b.getId());

        service.unsave(participantId, a.getId());
        service.unsave(participantId, a.getId());

        assertThat(service.savedTourIds(participantId)).containsExactly(b.getId());
    }

    @Test
    void list_isNewestFirst_andSkipsToursNoLongerVisible() {
        TourOfferingEntity older = offering("Older", TourStatus.ACTIVE);
        TourOfferingEntity newer = offering("Newer", TourStatus.ACTIVE);
        TourOfferingEntity paused = offering("Paused", TourStatus.ACTIVE);
        saveAt(older, "2026-01-01T00:00:00Z");
        saveAt(newer, "2026-03-01T00:00:00Z");
        saveAt(paused, "2026-02-01T00:00:00Z");

        paused.setStatus(TourStatus.PAUSED);
        offerings.saveAndFlush(paused);

        Page<TourSummaryResponse> page = service.list(participantId, 0, 20);

        assertThat(page.getContent())
                .extracting(TourSummaryResponse::id)
                .containsExactly(newer.getId().toString(), older.getId().toString());
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(service.savedTourIds(participantId)).hasSize(3);
    }

    @Test
    void list_isScopedToTheCaller() {
        TourOfferingEntity tour = offering("Mine", TourStatus.ACTIVE);
        UUID otherId = users.saveAndFlush(user("Other Participant")).getId();
        service.save(otherId, tour.getId());

        assertThat(service.list(participantId, 0, 20).getTotalElements()).isZero();
        assertThat(service.list(otherId, 0, 20).getTotalElements()).isEqualTo(1);
    }

    /** now() is fixed for the whole test transaction, so created_at must be set explicitly. */
    private void saveAt(TourOfferingEntity tour, String createdAt) {
        service.save(participantId, tour.getId());
        jdbc.update(
                "update saved_tours set created_at = ?::timestamptz"
                        + " where user_id = ? and tour_offering_id = ?",
                createdAt,
                participantId,
                tour.getId());
    }

    private TourOfferingEntity offering(String title, TourStatus status) {
        TourOfferingEntity o = new TourOfferingEntity();
        o.setId(UUID.randomUUID());
        o.setGuideId(guide.getId());
        o.setUniversityId(universityId);
        o.setTitle(title);
        o.setSlug("saved-" + UUID.randomUUID().toString().substring(0, 8));
        o.setTopic(TourTopic.GENERAL_CAMPUS);
        o.setDurationMin(60);
        o.setPriceCents(5000L);
        o.setStatus(status);
        return offerings.saveAndFlush(o);
    }

    private static UserEntity user(String displayName) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setOidcSubject("it-" + UUID.randomUUID());
        u.setEmail("it-" + UUID.randomUUID() + "@example.com");
        u.setDisplayName(displayName);
        u.setAccountStatus(AccountStatus.ACTIVE);
        u.setPreferredLanguage("en-US");
        u.setTimezone("America/Los_Angeles");
        return u;
    }
}
