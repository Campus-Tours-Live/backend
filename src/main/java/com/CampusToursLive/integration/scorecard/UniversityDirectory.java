package com.CampusToursLive.integration.scorecard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The browsable U.S. university directory: every school the platform recognises, bucketed by state.
 *
 * <p><strong>One snapshot, two views.</strong> The browse-by-state page needs a count per state;
 * the state page needs that state's list. Serving those from two different fetches is how a page
 * ends up saying "California — 148" and then listing 147 rows, with nothing to reconcile them. Here
 * both are read off ONE {@link Snapshot}, so {@code countsByState().get("CA") ==
 * inState("CA").size()} holds by construction rather than by luck.
 *
 * <p>Separate from {@link SchoolDirectory}, which answers per-school questions for guide onboarding
 * (search, majors, degrees). Same underlying population — see {@link ScorecardApi}'s directory
 * boundary — but a different shape and a different consumer, so a different contract.
 */
public interface UniversityDirectory {

    /**
     * The 50 states plus the District of Columbia, in USPS order — the exact set the directory
     * buckets into.
     *
     * <p>Territories (PR, GU, VI, AS, MP and the Pacific compacts) are deliberately absent: they
     * are not on the browse-by-state map, so counting them would put schools in a total no state
     * accounts for. Rows in those states are dropped when the snapshot is built.
     */
    List<String> US_STATE_CODES =
            List.of(
                    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID",
                    "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO",
                    "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA",
                    "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY");

    /**
     * One school as the directory lists it. No {@code state} field: a school only ever appears
     * under its own state's key, so repeating it on every row would be 1,900 chances for the two to
     * disagree.
     */
    record DirectorySchool(String id, String name, String city) {}

    /**
     * The whole directory at one moment, bucketed by USPS code, each state's list sorted by name.
     *
     * <p>An EMPTY snapshot means "we could not find out", never "there are no universities". The
     * two must not share a representation: an empty list rendered as a state page is a confident,
     * wrong-looking answer, so callers are expected to fail visibly on {@link #isEmpty()} rather
     * than render it.
     */
    record Snapshot(Map<String, List<DirectorySchool>> byState) {

        public static Snapshot unavailable() {
            return new Snapshot(Map.of());
        }

        public boolean isEmpty() {
            return byState.isEmpty();
        }

        public List<DirectorySchool> inState(String stateCode) {
            return byState.getOrDefault(stateCode, List.of());
        }

        /** Every state's size, in {@link #US_STATE_CODES} order. Derived, never stored. */
        public Map<String, Integer> countsByState() {
            Map<String, Integer> counts = new LinkedHashMap<>(byState.size());
            byState.forEach((code, schools) -> counts.put(code, schools.size()));
            return Collections.unmodifiableMap(counts);
        }

        /**
         * Every school across the states and DC. Excludes the territories, which are not listed.
         */
        public int total() {
            return byState.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * The current snapshot, or {@link Snapshot#unavailable()} when the directory cannot be read.
     */
    Snapshot snapshot();
}
