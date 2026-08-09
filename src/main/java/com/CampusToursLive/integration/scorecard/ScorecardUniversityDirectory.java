package com.CampusToursLive.integration.scorecard;

import com.CampusToursLive.integration.scorecard.ScorecardApi.DirectoryPage;
import com.CampusToursLive.integration.scorecard.ScorecardApi.DirectoryRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * {@link UniversityDirectory} over College Scorecard: pages the whole in-boundary set once, checks
 * it against upstream's own total, buckets it by state, and holds it for a day.
 *
 * <p>A separate bean from {@link ScorecardClient} because it is a separate contract with its own
 * cache, but it obeys the same load-bearing rule: the paging loop lives HERE and calls {@link
 * ScorecardApi} through its proxy, so every page consumes its own rate-limit permit. Moving the
 * loop inside {@code ScorecardApi} would make it a self-invocation, the proxy would be bypassed,
 * and the limiter would silently never fire — see that class for the full rationale.
 *
 * <p>Cost: about 20 calls (1,944 schools at 100 a page), once a day, against an 800/h budget.
 */
@Component
public class ScorecardUniversityDirectory implements UniversityDirectory {

    private static final Logger log = LoggerFactory.getLogger(ScorecardUniversityDirectory.class);

    /**
     * Refuse to page a directory larger than this.
     *
     * <p>The loop's length comes from a number upstream sent us. A corrupt or wildly changed total
     * would otherwise turn one request into thousands of outbound calls — the limiter would stop
     * them, but only after burning the whole hour's budget that guide onboarding shares. 20,000 is
     * ten times the real figure: room for the boundary to be widened without touching this, and
     * still nowhere near a runaway.
     */
    private static final int MAX_DIRECTORY_SIZE = 20_000;

    private final ScorecardApi api;

    public ScorecardUniversityDirectory(ScorecardApi api) {
        this.api = api;
    }

    /**
     * Cached for 24 hours under one fixed key — there is one answer for the whole application, and
     * it moves when IPEDS publishes, which is annual.
     *
     * <p>{@code unless} keeps an unavailable snapshot out of the cache. Pinning one would turn a
     * few seconds of upstream trouble into a day of a dead directory, long after upstream
     * recovered.
     *
     * <p>A stampede costs 20× here rather than 1×, and {@code sync = true} cannot be combined with
     * {@code unless} (see {@link ScorecardClient}). At one refresh a day on a single instance that
     * is comfortable; it is the first thing to revisit if this ever becomes hot.
     */
    @Override
    @Cacheable(cacheNames = "scorecardDirectory", key = "'all'", unless = "#result.isEmpty()")
    public Snapshot snapshot() {
        DirectoryPage first = api.directoryPage(0);
        if (first == null) {
            log.warn("Scorecard directory unavailable: first page returned nothing usable.");
            return Snapshot.unavailable();
        }

        int expected = first.total();
        if (expected <= 0 || expected > MAX_DIRECTORY_SIZE) {
            log.warn(
                    "Scorecard directory reported an implausible total of {}; refusing.", expected);
            return Snapshot.unavailable();
        }

        List<DirectoryRow> rows = new ArrayList<>(expected);
        rows.addAll(first.rows());

        int pages =
                (expected + ScorecardApi.DIRECTORY_PAGE_SIZE - 1)
                        / ScorecardApi.DIRECTORY_PAGE_SIZE;
        for (int page = 1; page < pages; page++) {
            DirectoryPage next = api.directoryPage(page);
            if (next == null) {
                log.warn("Scorecard directory page {} of {} failed; discarding.", page, pages);
                return Snapshot.unavailable();
            }
            rows.addAll(next.rows());
        }

        // THE integrity check. Everything downstream — the state counts, the state pages — is a
        // view of this list, so a page we silently lost would not surface as an error but as a
        // smaller, entirely believable directory. Comparing what we hold against the count upstream
        // told us to expect is the only thing standing between that and a wrong number on screen.
        if (rows.size() != expected) {
            log.warn(
                    "Scorecard directory incomplete: collected {} rows, upstream said {}."
                            + " Discarding rather than serving a short directory.",
                    rows.size(),
                    expected);
            return Snapshot.unavailable();
        }

        return bucketByState(rows);
    }

    /**
     * Rows → one list per state, sorted by name.
     *
     * <p>Two kinds of row are dropped, and only here — never during paging, where dropping would
     * corrupt the integrity check above: rows with no id or name (Scorecard occasionally returns
     * them), and rows in territories, which the map does not show. Both are counted in the log so a
     * sudden change in either is visible rather than silent.
     */
    private static Snapshot bucketByState(List<DirectoryRow> rows) {
        Map<String, List<DirectorySchool>> byState = new LinkedHashMap<>();
        for (String code : US_STATE_CODES) byState.put(code, new ArrayList<>());

        int unusable = 0;
        int offMap = 0;
        for (DirectoryRow row : rows) {
            if (row.id().isBlank() || row.name().isBlank()) {
                unusable++;
                continue;
            }
            List<DirectorySchool> bucket = byState.get(row.state());
            if (bucket == null) {
                offMap++;
                continue;
            }
            bucket.add(new DirectorySchool(row.id(), row.name(), row.city()));
        }

        Comparator<DirectorySchool> byName =
                Comparator.comparing(DirectorySchool::name, String.CASE_INSENSITIVE_ORDER);
        Map<String, List<DirectorySchool>> sealed = new LinkedHashMap<>(byState.size());
        byState.forEach(
                (code, schools) -> {
                    schools.sort(byName);
                    sealed.put(code, List.copyOf(schools));
                });

        log.info(
                "Scorecard directory: {} schools across {} states and DC ({} in territories,"
                        + " {} unusable rows).",
                rows.size() - unusable - offMap,
                US_STATE_CODES.size() - 1,
                offMap,
                unusable);

        return new Snapshot(Collections.unmodifiableMap(sealed));
    }
}
