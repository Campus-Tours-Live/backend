package com.CampusToursLive.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.CampusToursLive.domain.guide.GuideApplicationStatus;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.user.AccountStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.web.dto.RulesReplaceRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TRUE atomicity / rollback test for the CTL-54 B2 atomic weekly-rule replace ({@link
 * AvailabilityWriteService#replaceRules(UUID, RulesReplaceRequest)}): a failure that PASSES the
 * entry guard but fails MID-transaction, AFTER the weekday's active rules have been deleted (here,
 * an exception thrown from inside {@code rematerialize}), must roll the WHOLE transaction back — so
 * the guide's prior rule is STILL PRESENT and no data is lost. This is the actual B2 data-loss
 * guard; the entry-guard tests in {@code AvailabilityWriteServiceIntegrationTest} (nothing deleted)
 * do NOT cover it.
 *
 * <p><b>Why {@code @SpringBootTest} (not {@code @DataJpaTest}).</b> A genuine rollback needs the
 * production {@code @Transactional} proxy on {@code replaceRules} to open a real transaction whose
 * rollback on the mid-transaction throw undoes the delete — and the prior rule must be read back
 * from COMMITTED state (seeded in a separate committed transaction beforehand). Under
 * {@code @DataJpaTest} the service is {@code new}-constructed (no proxy) and the whole test runs in
 * ONE rolled-back transaction, so a service-level rollback could never be observed. The context
 * boots offline via a mocked {@link JwtDecoder}, exactly as the sibling override-atomicity test
 * does.
 *
 * <p><b>Injecting the post-delete failure.</b> {@link AvailabilityService} is a {@link
 * MockitoSpyBean}; {@code rematerialize} is stubbed to throw AFTER the seed is committed, so the
 * throw fires at the end of {@code replaceRules} — after the weekday's active-rule delete has run
 * inside the transaction. {@code rematerialize} runs at Spring {@code REQUIRED} propagation (joins
 * the caller's transaction), so its throw rolls back the outer delete too. Were it {@code
 * REQUIRES_NEW}, the delete would have committed and this assertion would fail — which is exactly
 * the correctness this test pins.
 */
@SpringBootTest(
        properties = {
            // Pin the scheduled horizon job to a cron that will not fire during the test run.
            "availability.horizon-job.cron=0 0 0 1 1 ?"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AvailabilityReplaceRulesAtomicityIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private AvailabilityWriteService writeService;
    @MockitoSpyBean private AvailabilityService availabilityService;
    @Autowired private GuideAvailabilityRuleRepository rules;
    @Autowired private GuideProfileRepository guides;
    @Autowired private UserRepository users;

    @Test
    void replaceRules_midTransactionFailure_rollsBack_priorRuleSurvives() {
        UUID guideId = seedGuide();
        short dayOfWeek = 3; // Wednesday — any fixed weekday.

        // Seed (COMMIT) a prior rule on this weekday that must survive the rollback.
        GuideAvailabilityRuleEntity prior = new GuideAvailabilityRuleEntity();
        prior.setId(UUID.randomUUID());
        prior.setGuideId(guideId);
        prior.setDayOfWeek(dayOfWeek);
        prior.setStartLocal(LocalTime.of(9, 0));
        prior.setWindowMin(60);
        prior.setTimezone("America/Los_Angeles");
        prior.setEffectiveFrom(LocalDate.now(ZoneOffset.UTC));
        prior.setEffectiveTo(null);
        prior.setActive(true);
        rules.saveAndFlush(prior);

        // Inject a post-delete, mid-transaction failure: rematerialize throws (fires AFTER the
        // weekday's active-rule delete inside replaceRules' transaction).
        doThrow(new RuntimeException("boom")).when(availabilityService).rematerialize(guideId);

        // A request that PASSES the entry guard (a valid 10:00 window) but will fail mid-tx.
        RulesReplaceRequest ok =
                new RulesReplaceRequest(
                        (int) dayOfWeek, List.of(new RulesReplaceRequest.Window("10:00", 60)));

        assertThatThrownBy(() -> writeService.replaceRules(guideId, ok))
                .isInstanceOf(RuntimeException.class);

        // The whole transaction rolled back: the prior rule is STILL there, unchanged — the delete
        // was undone, so no data was lost.
        List<GuideAvailabilityRuleEntity> stored =
                rules.findByGuideIdAndDayOfWeekAndActiveTrue(guideId, dayOfWeek);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartLocal()).isEqualTo(LocalTime.of(9, 0));
        assertThat(stored.get(0).getWindowMin()).isEqualTo(60);
    }

    private UUID seedGuide() {
        UserEntity guideUser = new UserEntity();
        guideUser.setId(UUID.randomUUID());
        guideUser.setOidcSubject("b2r-" + UUID.randomUUID());
        guideUser.setEmail("b2r-" + UUID.randomUUID() + "@example.com");
        guideUser.setDisplayName("B2 Rules Guide");
        guideUser.setAccountStatus(AccountStatus.ACTIVE);
        guideUser.setPreferredLanguage("en-US");
        guideUser.setTimezone("America/Los_Angeles");
        users.save(guideUser);

        GuideProfileEntity guide = new GuideProfileEntity();
        guide.setId(UUID.randomUUID());
        guide.setUserId(guideUser.getId());
        guide.setApplicationStatus(GuideApplicationStatus.VERIFIED);
        guides.save(guide);

        return guide.getId();
    }
}
