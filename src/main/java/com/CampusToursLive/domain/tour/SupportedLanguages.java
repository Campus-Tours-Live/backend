package com.CampusToursLive.domain.tour;

import com.CampusToursLive.error.ValidationException;
import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Curated language picker options, with well-formed BCP-47 variants accepted on writes so existing
 * regional/script tags remain editable. The picker is not an exhaustive list of accepted tags.
 */
public final class SupportedLanguages {

    public static final List<String> TAGS =
            List.of("en-US", "es", "zh", "fr", "de", "ja", "ko", "ar", "hi", "pt");

    private SupportedLanguages() {}

    public static boolean isSupported(String tag) {
        if (tag == null || tag.isBlank()) return false;
        try {
            new Locale.Builder().setLanguageTag(tag).build();
            return true;
        } catch (IllformedLocaleException ex) {
            return false;
        }
    }

    public static String displayName(String tag) {
        String name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH);
        return name == null || name.isBlank() ? tag : name;
    }

    /**
     * Validate BCP-47 syntax, not membership in the curated picker or a language registry. Tags are
     * canonicalized before deduplication; blanks are dropped and malformed tags fail the request.
     */
    public static List<String> requireSupported(List<String> raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null) return List.of();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            String tag = s.trim();
            try {
                out.add(new Locale.Builder().setLanguageTag(tag).build().toLanguageTag());
            } catch (IllformedLocaleException ex) {
                throw new ValidationException("Unsupported language: " + tag);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Normalize persisted tags for responses without making legacy data unreadable. Malformed
     * legacy labels are retained; new writes must still use {@link #requireSupported(List)}.
     */
    public static List<String> normalizeStored(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) continue;
            String tag = value.trim();
            try {
                out.add(new Locale.Builder().setLanguageTag(tag).build().toLanguageTag());
            } catch (IllformedLocaleException ex) {
                out.add(tag);
            }
        }
        return List.copyOf(out);
    }
}
