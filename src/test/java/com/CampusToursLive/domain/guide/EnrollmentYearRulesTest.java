package com.CampusToursLive.domain.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class EnrollmentYearRulesTest {

    /** 2026-07-29T12:00:00Z — mid-year, far from any boundary. */
    private static final Clock MID_2026 =
            Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);

    private static EnrollmentYearRules at(Clock clock) {
        return new EnrollmentYearRules(clock);
    }

    @Test
    void entryYearRange_isTenYearsBackAndOneForward() {
        assertEquals(new EnrollmentYearRules.YearRange(2016, 2027), at(MID_2026).entryYearRange());
    }

    @Test
    void maxYearsToGraduate_mapsEachCredentialLevelAndDefault() {
        EnrollmentYearRules r = at(MID_2026);
        assertEquals(9, r.maxYearsToGraduate("Doctoral Degree"));
        assertEquals(9, r.maxYearsToGraduate("First Professional Degree"));
        assertEquals(3, r.maxYearsToGraduate("Master's Degree"));
        assertEquals(3, r.maxYearsToGraduate("Post-baccalaureate Certificate"));
        assertEquals(6, r.maxYearsToGraduate("Bachelor's Degree"));
        assertEquals(3, r.maxYearsToGraduate("Associate's Degree"));
        assertEquals(3, r.maxYearsToGraduate("Undergraduate Certificate"));
        assertEquals(3, r.maxYearsToGraduate("Diploma"));
        assertEquals(8, r.maxYearsToGraduate("Some Other Credential"));
        assertEquals(8, r.maxYearsToGraduate(null));
    }

    /**
     * Order, proven by a string where the groups DISAGREE. "Post-baccalaureate Certificate" hits
     * both the master group (3) and the certificate group (3) — identical answers, so it cannot
     * detect a reordering. A doctoral certificate can: doctor (9) must win over certificate (3)
     * because its group is declared first.
     */
    @Test
    void maxYearsToGraduate_firstDeclaredGroupWins_whenGroupsDisagree() {
        assertEquals(9, at(MID_2026).maxYearsToGraduate("Doctoral Certificate"));
    }

    @Test
    void maxYearsToGraduate_trimsAndIsCaseInsensitive() {
        EnrollmentYearRules r = at(MID_2026);
        assertEquals(6, r.maxYearsToGraduate("  BACHELOR'S DEGREE  "));
        assertEquals(6, r.maxYearsToGraduate("bachelor's degree"));
    }

    /**
     * Spec §8: the {@code Locale.ROOT} in {@code maxYearsToGraduate} is load-bearing, not defensive
     * decoration, and this is the only case that can tell the difference. Turkish maps uppercase
     * {@code I} to a DOTLESS {@code ı}, so a naive default-locale {@code toLowerCase()} turns
     * "FIRST PROFESSIONAL DEGREE" into "fırst professıonal degree" and "DIPLOMA" into "dıploma" —
     * neither contains its keyword any more, so both fall through to the default 8 instead of 9 and
     * 3. The case-insensitivity test above cannot catch that: none of its strings carry an {@code
     * I}.
     *
     * <p>Casing matters as much as the locale — a lowercase "First Professional Degree" is
     * unaffected by the Turkish mapping (only the uppercase {@code I} is remapped), so the inputs
     * here are deliberately uppercase.
     *
     * <p>{@code Locale.setDefault} is JVM-global and surefire reuses the JVM across classes, so the
     * restore lives in a {@code finally}: leaking a Turkish default out of this method would make
     * unrelated tests elsewhere in the suite fail in ways that point nowhere near here. Done with
     * plain try/finally rather than a locale JUnit extension so this adds no test dependency.
     */
    @Test
    void maxYearsToGraduate_usesLocaleRoot_soATurkishDefaultCannotBreakIBearingKeywords() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            EnrollmentYearRules r = at(MID_2026);
            assertEquals(9, r.maxYearsToGraduate("FIRST PROFESSIONAL DEGREE"));
            assertEquals(3, r.maxYearsToGraduate("DIPLOMA"));
            assertEquals(3, r.maxYearsToGraduate("UNDERGRADUATE CERTIFICATE"));
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * Spelling variants are deliberately NOT normalised (R3): an unhyphenated "post baccalaureate"
     * does not match the {@code post-baccalaureate} keyword. Asserted on strings carrying NO other
     * keyword, because "Post baccalaureate Certificate" still contains "certificate" and lands on 3
     * through a different group — which would prove nothing about normalisation.
     */
    @Test
    void maxYearsToGraduate_doesNotNormaliseSpellingVariants() {
        EnrollmentYearRules r = at(MID_2026);
        assertEquals(8, r.maxYearsToGraduate("Post baccalaureate Program"));
        assertEquals(8, r.maxYearsToGraduate("Postbaccalaureate Program"));
    }

    /**
     * The same unhyphenated variants WITH a second keyword: they miss {@code post-baccalaureate}
     * and are then caught by {@code certificate}. Pinned so the two behaviours cannot be confused
     * later — the variant is unmatched, the string is not unmatched.
     */
    @Test
    void maxYearsToGraduate_unhyphenatedVariantStillMatchesAnotherGroupsKeyword() {
        EnrollmentYearRules r = at(MID_2026);
        assertEquals(3, r.maxYearsToGraduate("Post baccalaureate Certificate"));
        assertEquals(3, r.maxYearsToGraduate("Postbaccalaureate Certificate"));
    }

    @Test
    void classYearRange_isAnchoredOnEntryYearNotToday() {
        // 2023 + bachelor(6) → [2024, 2029]. The clock says 2026 and must not appear anywhere.
        assertEquals(
                new EnrollmentYearRules.YearRange(2024, 2029),
                at(MID_2026).classYearRange(2023, "Bachelor's Degree"));
        // A 2016 enrollee's window is entirely in the past — correct, and impossible under the
        // old current-year anchoring.
        assertEquals(
                new EnrollmentYearRules.YearRange(2017, 2022),
                at(MID_2026).classYearRange(2016, "Bachelor's Degree"));
    }

    @Test
    void snapshot_cacheAgeCountsDownToTheBoundary() {
        Clock lastHour = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneOffset.UTC);
        assertEquals(3600L, at(lastHour).snapshot().cacheMaxAgeSeconds());
    }

    @Test
    void snapshot_cacheAgeIsCappedAtTheCeilingOnAnOrdinaryDay() {
        assertEquals(86_400L, at(MID_2026).snapshot().cacheMaxAgeSeconds());
    }

    /**
     * A snapshot must be internally consistent even if the year turns over WHILE it is being built.
     * A Clock.fixed cannot detect this — it returns the same instant on every read, so an
     * implementation calling clock.instant() and Year.now(clock) separately looks correct. This
     * clock advances 1ms per read, straddling midnight, which is what makes the defect visible: a
     * two-read implementation computes the countdown against 2028 and reports the 24h ceiling
     * instead of ~0, or serves a 2026 window beside a 2027 age.
     */
    @Test
    void snapshot_isBuiltFromASingleInstant_evenAcrossTheRollover() {
        Clock straddling = advancingByMillis(Instant.parse("2026-12-31T23:59:59.999Z"));

        EnrollmentYearRules.EnrollmentYearSnapshot snap = at(straddling).snapshot();

        // A single read sees the FIRST instant this clock returns, so the 2026 window is the only
        // correct answer — there is no "other side of midnight" case for correct code to land in.
        // An instant-then-year double read reports (2017, 2028) and fails here.
        assertEquals(new EnrollmentYearRules.YearRange(2016, 2027), snap.entryYear());

        // Non-negative catches the opposite double read (year first, instant second), whose
        // countdown starts AFTER the boundary and goes negative; <= 1 catches everything that is
        // not the sub-second remainder a single read at 23:59:59.999Z must produce.
        assertTrue(
                snap.cacheMaxAgeSeconds() >= 0 && snap.cacheMaxAgeSeconds() <= 1,
                "expected a sub-second age at the boundary, got " + snap.cacheMaxAgeSeconds());
    }

    /** A clock whose every read is 1ms later than the last — the opposite of Clock.fixed. */
    private static Clock advancingByMillis(Instant start) {
        return new Clock() {
            private Instant current = start;

            @Override
            public Instant instant() {
                Instant now = current;
                current = current.plusMillis(1);
                return now;
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }
        };
    }

    /**
     * The zone bug this whole clock injection exists to prevent: with a JVM default zone east of
     * UTC, an implementation reading the ambient clock reports 2027 while the UTC clock still says
     * 2026, and the served window would then disagree with the cache lifetime.
     */
    @Test
    void entryYearRange_usesUtcNotTheJvmDefaultZone() {
        // 2026-12-31T20:00Z is already 2027-01-01T04:00 in Asia/Taipei (UTC+8).
        Clock beforeUtcRollover =
                Clock.fixed(Instant.parse("2026-12-31T20:00:00Z"), ZoneOffset.UTC);
        assertEquals(
                new EnrollmentYearRules.YearRange(2016, 2027),
                at(beforeUtcRollover).entryYearRange());
    }
}
