package com.CampusToursLive.integration.scorecard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.CampusToursLive.config.CacheConfig;
import com.CampusToursLive.integration.scorecard.SchoolDirectory.SchoolRef;
import com.CampusToursLive.web.MetaController.Option;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Layer 1 of the Scorecard quota defense. The whole point of splitting {@link ScorecardApi} out
 * into its own bean is that a cache HIT returns from Spring's cache interceptor without ever
 * entering the rate-limited bean — so "did the collaborator get invoked?" is the assertion that
 * actually proves a permit was not consumed.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, ScorecardClient.class})
class ScorecardClientCacheTest {

    private static final List<Option> HITS =
            List.of(new Option("243744", "Stanford University — Stanford, CA"));
    private static final List<Option> MAJORS = List.of(new Option("Biology", "Biology"));

    @MockitoBean private ScorecardApi api;

    /**
     * Injected as {@link SchoolDirectory}, not {@code ScorecardClient}: the caching aspect wraps
     * the bean in a JDK dynamic proxy (it implements an interface), so the concrete type is not
     * assignable. Going through the interface is also exactly how every real caller reaches it —
     * and it is the proxy, not the raw bean, that we need here for @Cacheable to apply at all.
     */
    @Autowired private SchoolDirectory client;

    @Autowired private CacheManager caches;

    @BeforeEach
    void clearCaches() {
        caches.getCacheNames()
                .forEach(name -> Objects.requireNonNull(caches.getCache(name)).clear());
    }

    // --- searchSchools ----------------------------------------------------------------------

    @Test
    void secondIdenticalSearchIsServedFromCache_outboundCallHappensOnce() {
        given(api.searchSchools("stanford", 5)).willReturn(HITS);

        assertThat(client.searchSchools("stanford", 5)).isEqualTo(HITS);
        assertThat(client.searchSchools("stanford", 5)).isEqualTo(HITS);

        verify(api, times(1)).searchSchools("stanford", 5);
    }

    @Test
    void cacheKeyNormalisesCaseAndSurroundingWhitespace() {
        given(api.searchSchools(anyString(), anyInt())).willReturn(HITS);

        client.searchSchools("Stanford", 5);
        client.searchSchools("  stanford  ", 5);
        client.searchSchools("STANFORD", 5);

        verify(api, times(1)).searchSchools(anyString(), anyInt());
    }

    /**
     * The limit is part of the cache key: the same query at a different limit is a different result
     * set (a 5-row page is not a prefix guarantee of a 10-row page), so it must not collide.
     */
    @Test
    void differentLimitForSameQueryDoesNotCollide() {
        given(api.searchSchools(anyString(), anyInt())).willReturn(HITS);

        client.searchSchools("stanford", 5);
        client.searchSchools("stanford", 10);

        verify(api, times(1)).searchSchools("stanford", 5);
        verify(api, times(1)).searchSchools("stanford", 10);
    }

    /**
     * {@code unless="#result.isEmpty()"}. The api degrades every upstream failure to an empty list,
     * so caching an empty result would pin an outage in memory for the whole 30m TTL.
     */
    @Test
    void emptyResultIsNotCached_soARecoveredUpstreamIsRetried() {
        given(api.searchSchools("obscure", 5)).willReturn(List.of());

        assertThat(client.searchSchools("obscure", 5)).isEmpty();
        assertThat(client.searchSchools("obscure", 5)).isEmpty();

        verify(api, times(2)).searchSchools("obscure", 5);
    }

    /**
     * The SpEL key is evaluated before the method body's guard runs, so it has to
     * null-short-circuit itself — otherwise {@code #query.strip()} NPEs before the guard can return
     * an empty list.
     */
    @Test
    void nullOrBlankQueryShortCircuitsWithoutNpeAndWithoutOutboundCall() {
        assertThat(client.searchSchools(null, 5)).isEmpty();
        assertThat(client.searchSchools("   ", 5)).isEmpty();

        verify(api, never()).searchSchools(any(), anyInt());
    }

    // --- majorsForSchool --------------------------------------------------------------------

    @Test
    void majorsAreCachedPerSchoolId() {
        given(api.majorsForSchool("243744")).willReturn(MAJORS);

        assertThat(client.majorsForSchool("243744")).isEqualTo(MAJORS);
        assertThat(client.majorsForSchool("  243744  ")).isEqualTo(MAJORS);

        verify(api, times(1)).majorsForSchool(anyString());
    }

    @Test
    void emptyMajorsAreNotCached() {
        given(api.majorsForSchool("999")).willReturn(List.of());

        client.majorsForSchool("999");
        client.majorsForSchool("999");

        verify(api, times(2)).majorsForSchool("999");
    }

    @Test
    void nullOrBlankSchoolIdShortCircuitsWithoutNpe() {
        assertThat(client.majorsForSchool(null)).isEmpty();
        assertThat(client.majorsForSchool(" ")).isEmpty();

        verify(api, never()).majorsForSchool(any());
    }

    // --- getSchool (uncached, but still rate-limited) ----------------------------------------

    @Test
    void getSchoolIsUncached_everyCallReachesTheRateLimitedBean() {
        SchoolRef ref = new SchoolRef("243744", "Stanford University", "Stanford", "CA");
        given(api.getSchool("243744")).willReturn(ref);

        assertThat(client.getSchool("243744")).isEqualTo(ref);
        assertThat(client.getSchool("243744")).isEqualTo(ref);

        verify(api, times(2)).getSchool("243744");
    }

    @Test
    void getSchoolReturnsNullForBlankIdWithoutOutboundCall() {
        assertThat(client.getSchool(null)).isNull();
        assertThat(client.getSchool("  ")).isNull();

        verifyNoInteractions(api);
    }
}
