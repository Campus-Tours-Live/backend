package com.CampusToursLive.integration.scorecard;

import com.CampusToursLive.web.MetaController.Option;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * {@link SchoolDirectory} over the U.S. Dept. of Education College Scorecard API (IPEDS-derived).
 * The API key is server-side only (never exposed to the browser). Failures degrade to an empty list
 * so a flaky upstream never breaks onboarding — the caller shows "no matches" rather than erroring.
 *
 * <p>This class is <strong>Layer 1</strong> of the shared-API-key quota defense: an in-process
 * Caffeine cache in front of the outbound calls. <strong>Layer 2</strong> — the outbound rate
 * limiter — lives on {@link ScorecardApi}, a separate bean this one delegates to.
 *
 * <p><strong>The two-bean split is load-bearing; do not "simplify" it into one class.</strong>
 * {@code @Cacheable} and {@code @RateLimiter} are both proxy-based Spring AOP, and a
 * self-invocation inside a single bean bypasses the proxy — a limiter on a private helper of this
 * class would silently never apply. Keeping them on methods of two different beans means both
 * aspects fire, and it buys the property that matters: <strong>a cache HIT does not consume a
 * rate-limit permit</strong>, because Spring's cache interceptor returns before {@link
 * ScorecardApi} is ever called. See {@link ScorecardApi} for the full rationale.
 *
 * <p><strong>Why not {@code @Cacheable(sync = true)}?</strong> Without it, N concurrent misses on
 * the same key each reach {@link ScorecardApi} before the first one backfills (a cache stampede),
 * so concurrent identical searches are not de-duplicated. {@code sync = true} would single-flight
 * them — but Spring does <em>not</em> support {@code unless} together with {@code sync}, and {@code
 * unless = "#result.isEmpty()"} is load-bearing here: this client degrades failures to an empty
 * list, and caching one would pin an upstream outage in memory for the full TTL. Keeping {@code
 * unless} is the better trade — the stampede only affects organic concurrency on a hot key (bounded
 * anyway by the Layer 2 limiter), whereas caching a swallowed failure is a self-inflicted outage.
 * Adding {@code sync = true} here fails fast at startup; that is intentional, not a bug.
 */
@Component
public class ScorecardClient implements SchoolDirectory {

    private final ScorecardApi api;

    public ScorecardClient(ScorecardApi api) {
        this.api = api;
    }

    /**
     * Cached for 30 minutes. The cache key is evaluated by the interceptor <em>before</em> the
     * method body runs, so it must null-short-circuit on its own or a null query would NPE inside
     * SpEL. The query is normalised (strip + lowercase) so trivially different spellings share an
     * entry, and {@code limit} is part of the key — the same query at a different limit is a
     * different result set and must not collide. {@code unless} keeps empty results out of the
     * cache: this class degrades failures to {@code List.of()}, and caching a swallowed failure
     * would pin an outage in memory for 30 minutes.
     */
    @Override
    @Cacheable(
            cacheNames = "scorecardUniversities",
            key = "(#query == null ? '' : #query.strip().toLowerCase()) + '|' + #limit",
            unless = "#result.isEmpty()")
    public List<Option> searchSchools(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        return api.searchSchools(query, limit);
    }

    /**
     * Cached for 24 hours — a school's program list is near-static. Key null-short-circuits for the
     * same reason as above; {@code unless} keeps degraded empty results uncached.
     */
    @Override
    @Cacheable(
            cacheNames = "scorecardMajors",
            key = "(#schoolId == null ? '' : #schoolId.strip())",
            unless = "#result.isEmpty()")
    public List<Option> majorsForSchool(String schoolId) {
        if (schoolId == null || schoolId.isBlank()) return List.of();
        return api.majorsForSchool(schoolId);
    }

    /**
     * Cached for 24 hours — a school looked up by id is near-static, and this shares the single
     * 800/h outbound budget with the anonymous typeahead, so every hit here is a permit the search
     * path keeps.
     *
     * <p>{@code unless} skips caching a {@code null}: null means "not found <em>or</em> degraded"
     * (upstream error, or the rate limiter tripped and the fallback fired), and pinning a degraded
     * lookup for 24h would outlast the outage.
     *
     * <p><strong>Known limitation.</strong> Because the degraded path returns {@code null}, the
     * onboarding upsert cannot tell "no such school" from "we were rate-limited", and reports the
     * guide's real university as {@code Unknown university}. Distinguishing them would mean
     * breaking the "this client never throws, it degrades" contract that keeps a flaky upstream
     * from failing onboarding outright — not worth it at the current (very low) trip frequency.
     */
    @Override
    @Cacheable(
            cacheNames = "scorecardSchools",
            key = "(#schoolId == null ? '' : #schoolId.strip())",
            unless = "#result == null")
    public SchoolRef getSchool(String schoolId) {
        if (schoolId == null || schoolId.isBlank()) return null;
        return api.getSchool(schoolId);
    }
}
