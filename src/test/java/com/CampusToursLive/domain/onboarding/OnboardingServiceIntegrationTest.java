package com.CampusToursLive.domain.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.domain.audit.AuditLogEntity;
import com.CampusToursLive.domain.audit.AuditLogRepository;
import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideProfileRepository;
import com.CampusToursLive.domain.guide.GuideStatus;
import com.CampusToursLive.domain.participant.ParticipantProfileRepository;
import com.CampusToursLive.domain.university.UniversityEntity;
import com.CampusToursLive.domain.university.UniversityRepository;
import com.CampusToursLive.domain.university.UniversityStatus;
import com.CampusToursLive.domain.user.UserEntity;
import com.CampusToursLive.domain.user.UserRepository;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.domain.user.UserRoleRepository;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.web.dto.GuideOnboardingRequest;
import com.CampusToursLive.web.dto.OnboardingResponse;
import com.CampusToursLive.web.dto.ParticipantOnboardingRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers (real PostgreSQL, full Spring context) proof for {@link OnboardingService} — CTL-
 * 97 Core-B Task 6, spec &sect;5. {@code @SpringBootTest} (not {@code @DataJpaTest}) is required: a
 * genuine atomicity/rollback proof needs the production {@code @Transactional} proxy on {@code
 * onboardGuide}/{@code onboardParticipant} to open a real transaction whose rollback on a
 * mid-transaction throw undoes the user-row insert — under {@code @DataJpaTest} the whole test runs
 * in one already-rolled-back wrapper transaction, so a service-level rollback could never be
 * observed. The context boots offline via a mocked {@link JwtDecoder}, mirroring the sibling
 * availability atomicity tests.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OnboardingServiceIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    // The real JwtDecoder fetches Google's JWKS at startup; mock it so the context boots offline.
    @MockitoBean private JwtDecoder jwtDecoder;

    @Autowired private OnboardingService onboardingService;
    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private GuideProfileRepository guideProfiles;
    @Autowired private ParticipantProfileRepository participantProfiles;
    @Autowired private UniversityRepository universities;
    @Autowired private AuditLogRepository auditLogs;

    private static final String ISSUER = "https://accounts.google.com";

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("name", "Test User")
                .build();
    }

    private UUID seedUniversity() {
        UniversityEntity u = new UniversityEntity();
        u.setId(UUID.randomUUID());
        u.setSlug("onboarding-test-uni-" + UUID.randomUUID());
        u.setName("Onboarding Test University");
        u.setCity("Testville");
        u.setTimezone("America/Los_Angeles");
        u.setStatus(UniversityStatus.ACTIVE);
        universities.saveAndFlush(u);
        return u.getId();
    }

    private static GuideOnboardingRequest guideRequest(UUID universityId) {
        return new GuideOnboardingRequest(
                "Maya",
                "Chen",
                universityId.toString(),
                "Marine Biology",
                "2027",
                "Third-year student and campus tour lead.",
                List.of("en-US"),
                List.of("DORM_HOUSING"),
                "maya.chen@ncu.edu",
                "Bachelor's Degree",
                2023);
    }

    private static ParticipantOnboardingRequest participantRequest(String participantType) {
        return new ParticipantOnboardingRequest(
                "Sam",
                "Rivera",
                "Sam Rivera",
                participantType,
                "High school senior",
                "Computer Science",
                List.of(),
                List.of(),
                "en-US",
                "America/New_York",
                null);
    }

    // ---- happy path: new identity, fully atomic, both audit rows ---------------------------

    @Test
    void onboardGuide_newIdentity_persistsUserRoleProfileAndAudit_atomically() {
        UUID universityId = seedUniversity();
        String subject = "guide-new-" + UUID.randomUUID();

        OnboardingResponse response =
                onboardingService.onboardGuide(jwt(subject), guideRequest(universityId));

        assertThat(response.acquiredRole()).isEqualTo(UserRole.GUIDE);
        assertThat(response.roles()).containsExactly(UserRole.GUIDE);

        UserEntity saved = users.findByOidcSubject(subject).orElseThrow();
        UUID userId = saved.getId();
        assertThat(userRoles.existsByUserIdAndRole(userId, UserRole.GUIDE)).isTrue();
        GuideProfileEntity profile = guideProfiles.findByUserId(userId).orElseThrow();
        assertThat(profile.getStatus()).isEqualTo(GuideStatus.PENDING);

        List<AuditLogEntity> auditRows =
                auditLogs.findByTargetTypeAndTargetId("user", userId.toString());
        assertThat(auditRows)
                .extracting(AuditLogEntity::getAction)
                .containsExactlyInAnyOrder("ACCOUNT_PROVISIONED", "ROLE_ACQUIRED");
    }

    @Test
    void onboardParticipant_newIdentity_persistsUserRoleProfileAndAudit_atomically() {
        String subject = "participant-new-" + UUID.randomUUID();

        OnboardingResponse response =
                onboardingService.onboardParticipant(
                        jwt(subject), participantRequest("PROSPECTIVE"));

        assertThat(response.acquiredRole()).isEqualTo(UserRole.PARTICIPANT);
        UserEntity saved = users.findByOidcSubject(subject).orElseThrow();
        UUID userId = saved.getId();
        assertThat(userRoles.existsByUserIdAndRole(userId, UserRole.PARTICIPANT)).isTrue();
        assertThat(participantProfiles.findByUserId(userId)).isPresent();

        List<AuditLogEntity> auditRows =
                auditLogs.findByTargetTypeAndTargetId("user", userId.toString());
        assertThat(auditRows)
                .extracting(AuditLogEntity::getAction)
                .containsExactlyInAnyOrder("ACCOUNT_PROVISIONED", "ROLE_ACQUIRED");
    }

    // ---- role already held -------------------------------------------------------------------

    @Test
    void onboardGuide_roleAlreadyHeld_returns409_andDoesNotDuplicateAnything() {
        UUID universityId = seedUniversity();
        String subject = "guide-repeat-" + UUID.randomUUID();
        onboardingService.onboardGuide(jwt(subject), guideRequest(universityId));

        assertThatThrownBy(
                        () ->
                                onboardingService.onboardGuide(
                                        jwt(subject), guideRequest(universityId)))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_ALREADY_GRANTED"));

        UUID userId = users.findByOidcSubject(subject).orElseThrow().getId();
        assertThat(userRoles.findByUserId(userId)).hasSize(1);
    }

    // ---- I13 eligibility: PARENT participant cannot onboard as GUIDE -----------------------

    @Test
    void onboardGuide_parentParticipant_returns409RoleNotEligible_andCreatesNoGuideState() {
        UUID universityId = seedUniversity();
        String subject = "parent-" + UUID.randomUUID();
        onboardingService.onboardParticipant(jwt(subject), participantRequest("PARENT"));

        assertThatThrownBy(
                        () ->
                                onboardingService.onboardGuide(
                                        jwt(subject), guideRequest(universityId)))
                .isInstanceOf(ConflictException.class)
                .satisfies(
                        ex ->
                                assertThat(((ConflictException) ex).code())
                                        .isEqualTo("ROLE_NOT_ELIGIBLE"));

        UUID userId = users.findByOidcSubject(subject).orElseThrow().getId();
        assertThat(guideProfiles.findByUserId(userId)).isEmpty();
        assertThat(userRoles.existsByUserIdAndRole(userId, UserRole.GUIDE)).isFalse();
    }

    // ---- atomicity: a failure AFTER the user insert rolls back the whole transaction -------

    @Test
    void onboardGuide_profileCreateFailsMidTransaction_rollsBackUserInsertAndAudit() {
        String subject = "rollback-" + UUID.randomUUID();
        // A syntactically valid UUID that was never seeded -- GuideService.updateProfile's
        // required-field/university validation throws (ValidationException, unchecked) AFTER
        // this same transaction has already provisioned+flushed the users row.
        UUID neverSeededUniversityId = UUID.randomUUID();
        long auditCountBefore = auditLogs.count();

        assertThatThrownBy(
                        () ->
                                onboardingService.onboardGuide(
                                        jwt(subject), guideRequest(neverSeededUniversityId)))
                .isInstanceOf(RuntimeException.class);

        assertThat(users.findByOidcSubject(subject)).isEmpty();
        assertThat(auditLogs.count()).isEqualTo(auditCountBefore);
    }
}
