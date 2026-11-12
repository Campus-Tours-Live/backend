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
    void isSupported_rejectsNullAndMalformed() {
        assertFalse(SupportedLanguages.isSupported(null));
        assertFalse(SupportedLanguages.isSupported("en_US"));
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
    void requireSupported_canonicalizesBeforeDeduplication() {
        assertEquals(
                List.of("en-US", "zh-Hant-TW", "fr-FR"),
                SupportedLanguages.requireSupported(
                        List.of(" en-us ", "EN-US", "en-US", "zh-hant-tw", "ZH-HANT-TW", "fr-fr")));
    }

    @Test
    void requireSupported_preservesWellFormedUndeterminedAndPrivateUseTags() {
        assertEquals(
                List.of("und", "x-private", "fil-PH", "xx-YY"),
                SupportedLanguages.requireSupported(
                        List.of("UND", "und", "X-PRIVATE", "x-private", "fil-ph", "xx-yy")));
    }

    @Test
    void requireSupported_throwsOnMalformedTag() {
        assertThrows(
                ValidationException.class,
                () -> SupportedLanguages.requireSupported(List.of("en_US")));
        assertThrows(
                ValidationException.class,
                () -> SupportedLanguages.requireSupported(List.of("en--US")));
    }

    @Test
    void acceptsWellFormedRegionalAndScriptVariantsOfSupportedLanguages() {
        assertEquals(
                List.of("fr-FR", "en-GB", "en", "zh-CN", "zh-Hant-TW"),
                SupportedLanguages.requireSupported(
                        List.of("fr-FR", "en-GB", "en", "zh-CN", "zh-Hant-TW")));
        assertTrue(SupportedLanguages.isSupported("EN-gb"));
        assertTrue(SupportedLanguages.isSupported("it"));
        assertTrue(SupportedLanguages.isSupported("it-IT"));
        assertTrue(SupportedLanguages.isSupported("fra"));
        assertTrue(SupportedLanguages.isSupported("fil-PH"));
        assertTrue(SupportedLanguages.isSupported("xx-YY"));
        assertTrue(SupportedLanguages.isSupported("und"));
        assertTrue(SupportedLanguages.isSupported("x-private"));
        assertFalse(SupportedLanguages.isSupported(""));
        assertFalse(SupportedLanguages.isSupported("en-US-invalid-long-subtag"));
    }
}
