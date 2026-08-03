package com.CampusToursLive.domain.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.error.ValidationException;
import org.junit.jupiter.api.Test;

/**
 * NameRules — the server-side first/last name guard (length + allowed characters + has-a-letter).
 */
class NameRulesTest {

    @Test
    void acceptsNullBlankAndValidInternationalNames() {
        assertThatCode(
                        () -> {
                            NameRules.validate("firstName", null); // null → no-op
                            NameRules.validate("firstName", "   "); // blank → no-op
                            NameRules.validate("firstName", "Jordan");
                            NameRules.validate("lastName", "O'Brien-Smith");
                            NameRules.validate("firstName", "Anne Marie");
                            NameRules.validate("firstName", "J. R.");
                            NameRules.validate("lastName", "García"); // accents
                            NameRules.validate("lastName", "李"); // CJK
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDigitsAndSymbols() {
        assertThatThrownBy(() -> NameRules.validate("firstName", "Ann3"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> NameRules.validate("firstName", "Bob@home"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNamesWithNoLetter() {
        assertThatThrownBy(() -> NameRules.validate("firstName", "-.'"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNamesOverTheLengthCap() {
        String tooLong = "a".repeat(NameRules.MAX_LENGTH + 1);
        assertThatThrownBy(() -> NameRules.validate("firstName", tooLong))
                .isInstanceOf(ValidationException.class);
    }
}
