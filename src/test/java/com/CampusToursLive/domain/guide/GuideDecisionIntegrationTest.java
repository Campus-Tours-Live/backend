package com.CampusToursLive.domain.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.audit.AuditLogEntity;
import com.CampusToursLive.domain.audit.AuditLogRepository;
import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.domain.tour.TourOfferingService;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.ForbiddenException;
import com.CampusToursLive.web.dto.CreateOfferingRequest;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GuideDecisionIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private GuideDecisionService decisions;
    @Autowired private TourOfferingService offerings;
    @Autowired private TourDiscoveryService discovery;
    @Autowired private GuideProfileRepository guides;
    @Autowired private GuideUniversityRepository guideUniversities;
    @Autowired private UniversityRepository universities;
    @Autowired private UserRepository users;
    @Autowired private AuditLogRepository auditLogs;

    @Test
    void approvedGuideCanPublishAnOfferingThatAppearsInTheCatalog() {
        UserEntity admin = seedUser("admin");
        Seed seed = seedPendingGuide();
        assertThatThrownBy(() -> offerings.activate(seed.guide(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(
                        decisions.decide(
                                admin.getId(), seed.guide().getId(), GuideDecision.APPROVE, null))
                .isEqualTo(GuideStatus.VERIFIED);

        TourOfferingResponse draft =
                offerings.create(
                        seed.guide(),
                        new CreateOfferingRequest(
                                "Decision Tour",
                                seed.universityId().toString(),
                                "GENERAL_CAMPUS",
                                60,
                                5000L,
                                null,
                                null));
        offerings.activate(seed.guide(), UUID.fromString(draft.id()));

        List<TourSummaryResponse> catalog =
                discovery
                        .list(
                                seed.universityId().toString(),
                                null,
                                null,
                                TourDiscoverySort.RECOMMENDED,
                                0,
                                20)
                        .getContent();
        assertThat(catalog).extracting(TourSummaryResponse::id).containsExactly(draft.id());

        GuideProfileEntity profile = guides.findById(seed.profileId()).orElseThrow();
        assertThat(profile.getApprovedAt()).isNotNull();
        assertThat(guideUniversities.findByGuideProfileId(seed.profileId()))
                .extracting(GuideUniversityEntity::getVerificationStatus)
                .containsExactly(GuideVerificationStatus.VERIFIED);
        assertThat(decisionAudits(seed.profileId()))
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getActorUserId()).isEqualTo(admin.getId());
                            assertThat(a.getMetadata()).containsEntry("decision", "APPROVE");
                        });
    }

    @Test
    void rejectedGuideStaysUnableToPublish() {
        UserEntity admin = seedUser("admin");
        Seed seed = seedPendingGuide();

        assertThat(
                        decisions.decide(
                                admin.getId(),
                                seed.guide().getId(),
                                GuideDecision.REJECT,
                                "  Student ID unreadable  "))
                .isEqualTo(GuideStatus.REJECTED);

        GuideProfileEntity profile = guides.findById(seed.profileId()).orElseThrow();
        assertThat(profile.getStatus()).isEqualTo(GuideStatus.REJECTED);
        assertThat(profile.getApprovedAt()).isNull();
        assertThat(guideUniversities.findByGuideProfileId(seed.profileId()))
                .extracting(GuideUniversityEntity::getVerificationStatus)
                .containsExactly(GuideVerificationStatus.REJECTED);
        assertThat(decisionAudits(seed.profileId()))
                .singleElement()
                .satisfies(
                        a ->
                                assertThat(a.getMetadata())
                                        .containsEntry("decision", "REJECT")
                                        .containsEntry("reason", "Student ID unreadable"));
        assertThatThrownBy(() -> offerings.activate(seed.guide(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void twoAdminsDecidingAtOnce_onlyOneWins() throws Exception {
        UserEntity approver = seedUser("approver");
        UserEntity rejecter = seedUser("rejecter");
        Seed seed = seedPendingGuide();
        UUID guideUserId = seed.guide().getId();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<GuideStatus>> results = new ArrayList<>();
        try {
            results.add(
                    pool.submit(
                            gated(
                                    start,
                                    () ->
                                            decisions.decide(
                                                    approver.getId(),
                                                    guideUserId,
                                                    GuideDecision.APPROVE,
                                                    null))));
            results.add(
                    pool.submit(
                            gated(
                                    start,
                                    () ->
                                            decisions.decide(
                                                    rejecter.getId(),
                                                    guideUserId,
                                                    GuideDecision.REJECT,
                                                    "duplicate account"))));
            start.countDown();

            List<GuideStatus> wins = new ArrayList<>();
            List<Throwable> losses = new ArrayList<>();
            for (Future<GuideStatus> result : results) {
                try {
                    wins.add(result.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    losses.add(e.getCause());
                }
            }

            assertThat(wins).hasSize(1);
            assertThat(losses)
                    .singleElement()
                    .isInstanceOfSatisfying(
                            ConflictException.class,
                            e -> assertThat(e.code()).isEqualTo("GUIDE_APPLICATION_NOT_PENDING"));
            assertThat(guides.findById(seed.profileId()).orElseThrow().getStatus())
                    .isEqualTo(wins.get(0));
            assertThat(decisionAudits(seed.profileId())).hasSize(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private static Callable<GuideStatus> gated(
            CountDownLatch start, Callable<GuideStatus> decision) {
        return () -> {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start latch never fired");
            }
            return decision.call();
        };
    }

    private List<AuditLogEntity> decisionAudits(UUID profileId) {
        return auditLogs.findByTargetTypeAndTargetId(
                GuideDecisionService.TARGET_TYPE_GUIDE_PROFILE, profileId.toString());
    }

    private record Seed(UserEntity guide, UUID profileId, UUID universityId) {}

    private Seed seedPendingGuide() {
        UserEntity user = seedUser("guide");

        GuideProfileEntity profile = new GuideProfileEntity();
        profile.setId(UUID.randomUUID());
        profile.setUserId(user.getId());
        profile.setStatus(GuideStatus.PENDING);
        guides.saveAndFlush(profile);

        UniversityEntity university = new UniversityEntity();
        university.setId(UUID.randomUUID());
        university.setSlug("decision-uni-" + UUID.randomUUID());
        university.setName("Decision Test University " + UUID.randomUUID());
        university.setCity("Testville");
        university.setTimezone("America/Los_Angeles");
        university.setStatus(UniversityStatus.ACTIVE);
        universities.saveAndFlush(university);

        GuideUniversityEntity school = new GuideUniversityEntity();
        school.setId(UUID.randomUUID());
        school.setGuideProfileId(profile.getId());
        school.setUniversityId(university.getId());
        school.setMajor("History");
        school.setDegree("Bachelor's Degree");
        school.setEntryYear(2023);
        school.setVerificationStatus(GuideVerificationStatus.PENDING);
        guideUniversities.saveAndFlush(school);

        return new Seed(user, profile.getId(), university.getId());
    }

    private UserEntity seedUser(String label) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setOidcSubject("decision-" + label + "-" + UUID.randomUUID());
        user.setEmail("decision-" + label + "-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Decision " + label);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPreferredLanguage("en-US");
        user.setTimezone("America/Los_Angeles");
        return users.saveAndFlush(user);
    }
}
