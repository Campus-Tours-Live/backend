package com.CampusToursLive.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process Caffeine caches. Each cache gets its own spec (size + TTL) rather than one global
 * default, because their staleness budgets differ by orders of magnitude.
 *
 * <p>These back Layer 1 of the College Scorecard quota defense — see {@code ScorecardClient} for
 * why the cache and the outbound rate limiter must live on two different beans.
 *
 * <ul>
 *   <li>{@code scorecardUniversities} — anonymous typeahead search results. 30m TTL: school names
 *       effectively never change, but a short-ish TTL bounds how long a bad/partial result set can
 *       stick around. 5000 entries covers the long tail of popular queries at a few MB.
 *   <li>{@code scorecardMajors} — a school's CIP-4 program list, which is near-static (IPEDS
 *       updates annually), so 24h.
 *   <li>{@code scorecardDegrees} — the distinct credential levels a school awards, derived from the
 *       same near-static program list, so 24h.
 *   <li>{@code scorecardSchools} — a single school looked up by id (onboarding upsert). Also
 *       near-static, so 24h.
 *   <li>{@code scorecardDirectory} — the whole browsable university directory, bucketed by state.
 *       ONE entry (fixed key) and 24h, because there is one answer for the application and it moves
 *       when IPEDS publishes, annually. The TTL does more work here than anywhere else: a miss
 *       costs ~20 paged calls, so this cache is what keeps every browse-by-state and state page off
 *       the outbound budget entirely.
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SCORECARD_UNIVERSITIES = "scorecardUniversities";
    public static final String SCORECARD_MAJORS = "scorecardMajors";
    public static final String SCORECARD_DEGREES = "scorecardDegrees";
    public static final String SCORECARD_SCHOOLS = "scorecardSchools";
    public static final String SCORECARD_DIRECTORY = "scorecardDirectory";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Lock the manager to exactly these names FIRST. Left in its default "dynamic" mode,
        // CaffeineCacheManager lazily builds an UNBOUNDED, TTL-less cache for any name it has not
        // seen — so a typo'd @Cacheable(cacheNames=...) would silently get a memory leak instead
        // of failing. setCacheNames installs defaults and disables dynamic creation;
        // registerCustomCache below then replaces those defaults with the tuned specs.
        manager.setCacheNames(
                List.of(
                        SCORECARD_UNIVERSITIES,
                        SCORECARD_MAJORS,
                        SCORECARD_DEGREES,
                        SCORECARD_SCHOOLS,
                        SCORECARD_DIRECTORY));
        manager.registerCustomCache(
                SCORECARD_UNIVERSITIES,
                Caffeine.newBuilder()
                        .maximumSize(5000)
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .build());
        manager.registerCustomCache(
                SCORECARD_MAJORS,
                Caffeine.newBuilder()
                        .maximumSize(5000)
                        .expireAfterWrite(Duration.ofHours(24))
                        .build());
        manager.registerCustomCache(
                SCORECARD_DEGREES,
                Caffeine.newBuilder()
                        .maximumSize(5000)
                        .expireAfterWrite(Duration.ofHours(24))
                        .build());
        manager.registerCustomCache(
                SCORECARD_SCHOOLS,
                Caffeine.newBuilder()
                        .maximumSize(5000)
                        .expireAfterWrite(Duration.ofHours(24))
                        .build());
        // Size 1, not 5000: one fixed key holds the whole directory, so any headroom above 1 would
        // reserve space for entries that cannot exist.
        manager.registerCustomCache(
                SCORECARD_DIRECTORY,
                Caffeine.newBuilder()
                        .maximumSize(1)
                        .expireAfterWrite(Duration.ofHours(24))
                        .build());
        return manager;
    }
}
