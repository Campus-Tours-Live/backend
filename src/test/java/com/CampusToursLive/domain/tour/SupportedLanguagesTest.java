package com.CampusToursLive.domain.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.CampusToursLive.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupportedLanguagesTest {

    @Test
    void isSupported_rejectsNullAndUnknown() {
        assertFalse(SupportedLanguages.isSupported(null));
        assertFalse(SupportedLanguages.isSupported("xx-YY"));
        assertTrue(SupportedLanguages.isSupported("en-US"));
    }

    @Test
    void displayName_returnsEnglishLabelOrFallback() {
        assertEquals("English", SupportedLanguages.displayName("en-US"));
        // Unknown / empty tags have no English display language → fall back to the tag itself.
        assertEquals("qqq", SupportedLanguages.displayName("qqq"));
        assertEquals("", SupportedLanguages.displayName(""));
    }

    @Test
    void requireSupported_filtersBlanksAndDedupes() {
        assertEquals(List.of(), SupportedLanguages.requireSupported(null));
        assertEquals(
                List.of("en-US", "es"),
                SupportedLanguages.requireSupported(
                        Arrays.asList(null, "  ", "en-US", "en-US", "es")));
    }

    @Test
    void requireSupported_throwsOnUnknownTag() {
        assertThrows(
                ValidationException.class,
                () -> SupportedLanguages.requireSupported(List.of("fr-FR")));
    }
}
