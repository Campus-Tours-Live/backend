package com.CampusToursLive.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bean-validation contract for {@link ParticipantOnboardingRequest} — the dedicated full-create DTO
 * for the onboarding COMMAND (Task 7's controller, not wired yet). Participant onboarding is
 * lighter than guide onboarding ({@code ParticipantService.updateProfile} defaults {@code
 * participantType} to PROSPECTIVE and requires nothing else), so the only field enforced here is a
 * valid {@code participantType} — the minimal sensible first-time set for a command that acquires
 * the role.
 */
class ParticipantOnboardingRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ParticipantOnboardingRequest valid() {
        return new ParticipantOnboardingRequest(
                "Sam",
                "Rivera",
                "Sam Rivera",
                "PROSPECTIVE",
                "High school senior",
                "Computer Science",
                List.of("166683"),
                List.of("DORM_HOUSING"),
                "en-US",
                "America/New_York",
                null);
    }

    @Test
    void fullyPopulatedRequest_hasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void missingParticipantType_isRejected() {
        ParticipantOnboardingRequest req =
                new ParticipantOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().displayName(),
                        null,
                        valid().gradeLevel(),
                        valid().intendedMajor(),
                        valid().universitiesOfInterest(),
                        valid().topicsOfInterest(),
                        valid().preferredLanguage(),
                        valid().timezone(),
                        valid().accessibilityPreferences());
        Set<ConstraintViolation<ParticipantOnboardingRequest>> violations = validator.validate(req);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("participantType"));
    }

    @Test
    void blankParticipantType_isRejected() {
        ParticipantOnboardingRequest req =
                new ParticipantOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().displayName(),
                        "  ",
                        valid().gradeLevel(),
                        valid().intendedMajor(),
                        valid().universitiesOfInterest(),
                        valid().topicsOfInterest(),
                        valid().preferredLanguage(),
                        valid().timezone(),
                        valid().accessibilityPreferences());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("participantType"));
    }

    @Test
    void unknownParticipantType_isRejected() {
        ParticipantOnboardingRequest req =
                new ParticipantOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().displayName(),
                        "BOGUS",
                        valid().gradeLevel(),
                        valid().intendedMajor(),
                        valid().universitiesOfInterest(),
                        valid().topicsOfInterest(),
                        valid().preferredLanguage(),
                        valid().timezone(),
                        valid().accessibilityPreferences());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("participantType"));
    }

    @Test
    void omittedOptionalFields_stillValid() {
        ParticipantOnboardingRequest req =
                new ParticipantOnboardingRequest(
                        null, null, null, "PROSPECTIVE", null, null, null, null, null, null, null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void hasNoSubmitField() {
        assertThat(ParticipantOnboardingRequest.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("submit"));
    }
}
