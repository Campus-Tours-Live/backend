package com.CampusToursLive.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
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
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SCORECARD_UNIVERSITIES = "scorecardUniversities";
    public static final String SCORECARD_MAJORS = "scorecardMajors";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
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
        return manager;
    }
}
