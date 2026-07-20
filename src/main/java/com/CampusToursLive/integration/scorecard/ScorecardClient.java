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
     * Deliberately <strong>uncached</strong>: this is called once per guide-onboarding submit (an
     * authenticated, low-volume write path), not from the anonymous typeahead, so it has no organic
     * repetition to absorb. It still routes through {@link ScorecardApi} and is therefore still
     * covered by the Layer 2 outbound rate limiter.
     */
    @Override
    public SchoolRef getSchool(String schoolId) {
        if (schoolId == null || schoolId.isBlank()) return null;
        return api.getSchool(schoolId);
    }
}
