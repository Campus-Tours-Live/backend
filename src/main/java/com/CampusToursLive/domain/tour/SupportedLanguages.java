package com.CampusToursLive.domain.tour;

import com.CampusToursLive.error.ValidationException;
import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Curated language picker options, with well-formed BCP-47 variants accepted on writes so existing
 * regional/script tags remain editable. The picker is not an exhaustive list of accepted tags.
 */
public final class SupportedLanguages {

    public static final List<String> TAGS =
            List.of("en-US", "es", "zh", "fr", "de", "ja", "ko", "ar", "hi", "pt");

    private static final Set<String> ALLOWED_LANGUAGES =
            TAGS.stream()
                    .map(tag -> Locale.forLanguageTag(tag).getLanguage())
                    .collect(Collectors.toUnmodifiableSet());

    private SupportedLanguages() {}

    public static boolean isSupported(String tag) {
        if (tag == null || tag.isBlank()) return false;
        try {
            Locale locale = new Locale.Builder().setLanguageTag(tag).build();
            return ALLOWED_LANGUAGES.contains(locale.getLanguage());
        } catch (IllformedLocaleException ex) {
            return false;
        }
    }

    public static String displayName(String tag) {
        String name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH);
        return name == null || name.isBlank() ? tag : name;
    }

    /**
     * Validate client-supplied language tags. Duplicates and blanks are dropped; unknown tags fail
     * the request.
     */
    public static List<String> requireSupported(List<String> raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null) return List.of();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            String tag = s.trim();
            if (!isSupported(tag)) {
                throw new ValidationException("Unsupported language: " + tag);
            }
            out.add(tag);
        }
        return List.copyOf(out);
    }
}
