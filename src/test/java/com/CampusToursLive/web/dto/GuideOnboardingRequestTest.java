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
 * Bean-validation contract for {@link GuideOnboardingRequest} — the dedicated full-create DTO for
 * the onboarding COMMAND (Task 7's controller, not wired yet). Unlike the partial {@code
 * GuideProfileUpdateRequest}, every first-time-required field the service enforces on {@code
 * submit=true} (universityId, major, degree, verificationEmail, bio, >=1 tourTopic — see {@code
 * GuideService.updateProfile}) is required here via annotations, since the command implies submit.
 */
class GuideOnboardingRequestTest {

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

    private static GuideOnboardingRequest valid() {
        return new GuideOnboardingRequest(
                "Maya",
                "Chen",
                "u1a2c3d4-0000-4000-8000-000000000003",
                "Marine Biology",
                "2027",
                "Third-year student and campus tour lead.",
                List.of("en-US"),
                List.of("DORM_HOUSING"),
                "maya.chen@ncu.edu",
                "Bachelor's Degree",
                2023);
    }

    @Test
    void fullyPopulatedRequest_hasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void missingUniversityId_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        null,
                        valid().major(),
                        valid().classYear(),
                        valid().bio(),
                        valid().spokenLanguages(),
                        valid().tourTopics(),
                        valid().verificationEmail(),
                        valid().degree(),
                        valid().entryYear());
        Set<ConstraintViolation<GuideOnboardingRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("universityId"));
    }

    @Test
    void missingMajor_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().universityId(),
                        " ",
                        valid().classYear(),
                        valid().bio(),
                        valid().spokenLanguages(),
                        valid().tourTopics(),
                        valid().verificationEmail(),
                        valid().degree(),
                        valid().entryYear());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("major"));
    }

    @Test
    void missingDegree_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().universityId(),
                        valid().major(),
                        valid().classYear(),
                        valid().bio(),
                        valid().spokenLanguages(),
                        valid().tourTopics(),
                        valid().verificationEmail(),
                        null,
                        valid().entryYear());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("degree"));
    }

    @Test
    void blankBio_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().universityId(),
                        valid().major(),
                        valid().classYear(),
                        "",
                        valid().spokenLanguages(),
                        valid().tourTopics(),
                        valid().verificationEmail(),
                        valid().degree(),
                        valid().entryYear());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("bio"));
    }

    @Test
    void emptyTourTopics_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().universityId(),
                        valid().major(),
                        valid().classYear(),
                        valid().bio(),
                        valid().spokenLanguages(),
                        List.of(),
                        valid().verificationEmail(),
                        valid().degree(),
                        valid().entryYear());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("tourTopics"));
    }

    @Test
    void invalidVerificationEmail_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().universityId(),
                        valid().major(),
                        valid().classYear(),
                        valid().bio(),
                        valid().spokenLanguages(),
                        valid().tourTopics(),
                        "not-an-email",
                        valid().degree(),
                        valid().entryYear());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("verificationEmail"));
    }

    @Test
    void malformedClassYear_isRejected() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        valid().firstName(),
                        valid().lastName(),
                        valid().universityId(),
                        valid().major(),
                        "not-a-year",
                        valid().bio(),
                        valid().spokenLanguages(),
                        valid().tourTopics(),
                        valid().verificationEmail(),
                        valid().degree(),
                        valid().entryYear());
        assertThat(validator.validate(req))
                .anyMatch(v -> v.getPropertyPath().toString().equals("classYear"));
    }

    @Test
    void omittedOptionalFields_stillValid() {
        GuideOnboardingRequest req =
                new GuideOnboardingRequest(
                        null,
                        null,
                        valid().universityId(),
                        valid().major(),
                        null,
                        valid().bio(),
                        null,
                        valid().tourTopics(),
                        valid().verificationEmail(),
                        valid().degree(),
                        null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void hasNoSubmitField() {
        assertThat(GuideOnboardingRequest.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("submit"));
    }
}
