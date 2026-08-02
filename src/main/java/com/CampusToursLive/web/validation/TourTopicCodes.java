package com.CampusToursLive.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a {@code List<String>} to the controlled tour-topic vocabulary — every non-null
 * element must exactly match a {@link com.CampusToursLive.domain.tour.TourTopic} enum name. {@code
 * null} and an empty list are both valid, so an optional/omitted field is unaffected; duplicates of
 * a valid code are allowed (this is not a uniqueness constraint). See {@link
 * TourTopicCodesValidator} for the single-source-of-truth enforcement (reads {@code
 * TourTopic.values()} at validation time — the 8 codes are never hardcoded here).
 *
 * <p>Applied ONLY to the onboarding command DTO ({@code ParticipantOnboardingRequest}); the PATCH
 * profile-edit DTO ({@code ParticipantProfileUpdateRequest}) intentionally stays free-text.
 */
@Documented
@Constraint(validatedBy = TourTopicCodesValidator.class)
@Target({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT,
    ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface TourTopicCodes {
    String message() default "each topic must be one of the supported tour-topic codes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
