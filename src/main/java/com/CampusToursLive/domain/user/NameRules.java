package com.CampusToursLive.domain.user;

import com.CampusToursLive.error.ValidationException;
import java.util.regex.Pattern;

/**
 * Server-side defense for person names (first / last), mirroring the client-side rules so a direct
 * API call can't store junk. A name may hold letters of any language (accents, CJK, …) plus spaces,
 * hyphens, apostrophes, and periods; it must contain at least one letter and stay within {@link
 * #MAX_LENGTH} characters. Blank / absent is treated as "not provided" and left to the caller —
 * names are optional on a partial profile update.
 */
public final class NameRules {

    public static final int MAX_LENGTH = 50;

    private static final Pattern ALLOWED =
            Pattern.compile("^[\\p{L} .'-]+$", Pattern.UNICODE_CHARACTER_CLASS);

    private NameRules() {}

    /** Validate a name field when a non-blank value is present; a no-op for null / blank. */
    public static void validate(String field, String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty()) return;
        if (v.length() > MAX_LENGTH) {
            throw new ValidationException(field + " must be at most " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(v).matches() || v.codePoints().noneMatch(Character::isLetter)) {
            throw new ValidationException(
                    field + " may only contain letters, spaces, hyphens, apostrophes, and periods");
        }
    }
}
