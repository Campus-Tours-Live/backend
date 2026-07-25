package com.CampusToursLive.domain.tour;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Curated BCP-47 language tags a guide may attach to a profile or tour offering. Kept in one place
 * so {@code GET /meta/languages} and offering create validation cannot drift.
 */
public final class SupportedLanguages {

    public static final List<String> TAGS =
            List.of("en-US", "es", "zh", "fr", "de", "ja", "ko", "ar", "hi", "pt");

    private static final Set<String> ALLOWED = Set.copyOf(TAGS);

    private SupportedLanguages() {}

    public static boolean isSupported(String tag) {
        return tag != null && ALLOWED.contains(tag);
    }

    public static String displayName(String tag) {
        String name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH);
        return name == null || name.isBlank() ? tag : name;
    }
}
