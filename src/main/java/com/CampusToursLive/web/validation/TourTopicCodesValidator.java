package com.CampusToursLive.web.validation;

import com.CampusToursLive.domain.tour.TourTopic;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces {@link TourTopicCodes}: every element of the list must be one of {@link
 * TourTopic#values()}'s names. The allowed set is derived from the enum at validation time — never
 * hardcoded here — so this check and the DTOs' {@code @Schema allowableValues} doc mirror can never
 * drift out of sync with each other; both trace back to the same {@link TourTopic} enum.
 */
public class TourTopicCodesValidator implements ConstraintValidator<TourTopicCodes, List<String>> {

    private static final Set<String> ALLOWED_CODES =
            Arrays.stream(TourTopic.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(List<String> value, ConstraintValidatorContext context) {
        // NOT_REQUIRED field: null (omitted) and an empty list are both valid — only a non-empty
        // list is checked, and every element in it must be a real TourTopic name.
        if (value == null || value.isEmpty()) {
            return true;
        }
        return value.stream().allMatch(ALLOWED_CODES::contains);
    }
}
