package com.CampusToursLive.integration.scorecard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.CampusToursLive.config.CacheConfig;
import com.CampusToursLive.integration.scorecard.ScorecardApi.DirectoryPage;
import com.CampusToursLive.integration.scorecard.ScorecardApi.DirectoryRow;
import com.CampusToursLive.integration.scorecard.UniversityDirectory.DirectorySchool;
import com.CampusToursLive.integration.scorecard.UniversityDirectory.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * The directory snapshot: paging, the integrity check that makes paging safe, bucketing, and the
 * cache that keeps ~20 outbound calls to once a day.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, ScorecardUniversityDirectory.class})
class ScorecardUniversityDirectoryTest {

    @MockitoBean private ScorecardApi api;

    /**
     * Injected as the interface: {@code @Cacheable} wraps the bean in a JDK dynamic proxy, so the
     * concrete type is not assignable — and it is the proxy we need for caching to apply at all.
     */
    @Autowired private UniversityDirectory directory;

    @Autowired private CacheManager caches;

    @BeforeEach
    void clearCaches() {
        caches.getCacheNames()
                .forEach(name -> Objects.requireNonNull(caches.getCache(name)).clear());
    }

    private static DirectoryRow row(String id, String name, String city, String state) {
        return new DirectoryRow(id, name, city, state);
    }

    /** A single page holding every row, with a total that agrees with it. */
    private static DirectoryPage onePage(DirectoryRow... rows) {
        return new DirectoryPage(List.of(rows), rows.length);
    }

    // --- happy path -------------------------------------------------------------------------

    @Test
    void bucketsSchoolsByStateAndSortsEachByName() {
        given(api.directoryPage(0))
                .willReturn(
                        onePage(
                                row("2", "Stanford University", "Stanford", "CA"),
                                row("1", "California Institute of Technology", "Pasadena", "CA"),
                                row("3", "Ohio State University", "Columbus", "OH")));

        Snapshot snap = directory.snapshot();

        assertThat(snap.inState("CA"))
                .extracting(DirectorySchool::name)
                .containsExactly("California Institute of Technology", "Stanford University");
        assertThat(snap.inState("OH"))
                .extracting(DirectorySchool::name)
                .containsExactly("Ohio State University");
        assertThat(snap.inState("WY")).isEmpty();
    }

    @Test
    void everyStateAndDcGetsABucket_andTerritoriesGetNone() {
        given(api.directoryPage(0))
                .willReturn(onePage(row("1", "Somewhere College", "Town", "CA")));

        Snapshot snap = directory.snapshot();

        assertThat(snap.byState()).hasSize(UniversityDirectory.US_STATE_CODES.size());
        assertThat(snap.byState()).containsKey("DC").doesNotContainKey("PR");
    }

    /**
     * THE invariant the whole design exists for: the browse page's figure for a state and the list
     * its state page shows are the same number, because they are two readings of one snapshot. Two
     * separate fetches is how a page says 148 and then lists 147.
     */
    @Test
    void everyCountIsExactlyTheLengthOfTheListItDescribes() {
        List<DirectoryRow> rows = new ArrayList<>();
        rows.add(row("1", "A College", "X", "CA"));
        rows.add(row("2", "B College", "Y", "CA"));
        rows.add(row("3", "C College", "Z", "NY"));
        rows.add(row("4", "D College", "W", "PR")); // territory — counted by neither
        given(api.directoryPage(0)).willReturn(new DirectoryPage(rows, rows.size()));

        Snapshot snap = directory.snapshot();

        snap.countsByState()
                .forEach((code, count) -> assertThat(snap.inState(code)).hasSize(count));
        assertThat(snap.total()).isEqualTo(3); // the territory row is in no state's total
    }

    @Test
    void pagesUntilUpstreamsTotalIsAccountedFor() {
        // 150 rows at 100 a page → two pages, and the loop must ask for both.
        List<DirectoryRow> first = new ArrayList<>();
        for (int i = 0; i < 100; i++) first.add(row("id" + i, "College " + i, "City", "CA"));
        List<DirectoryRow> second = new ArrayList<>();
        for (int i = 100; i < 150; i++) second.add(row("id" + i, "College " + i, "City", "NY"));

        given(api.directoryPage(0)).willReturn(new DirectoryPage(first, 150));
        given(api.directoryPage(1)).willReturn(new DirectoryPage(second, 150));

        Snapshot snap = directory.snapshot();

        assertThat(snap.total()).isEqualTo(150);
        assertThat(snap.inState("CA")).hasSize(100);
        assertThat(snap.inState("NY")).hasSize(50);
        verify(api).directoryPage(0);
        verify(api).directoryPage(1);
    }

    // --- refusing to serve something wrong ---------------------------------------------------

    /**
     * The check that makes paging safe at all. Without it a page we quietly lost surfaces not as an
     * error but as a smaller, entirely believable directory — a wrong number on screen with nothing
     * saying so.
     */
    @Test
    void discardsTheWholeSnapshotWhenTheRowsDoNotAddUpToUpstreamsTotal() {
        given(api.directoryPage(0))
                .willReturn(new DirectoryPage(List.of(row("1", "Only One", "X", "CA")), 150));

        assertThat(directory.snapshot().isEmpty()).isTrue();
    }

    @Test
    void discardsTheSnapshotWhenAnyPageFails() {
        List<DirectoryRow> first = new ArrayList<>();
        for (int i = 0; i < 100; i++) first.add(row("id" + i, "College " + i, "City", "CA"));
        given(api.directoryPage(0)).willReturn(new DirectoryPage(first, 150));
        given(api.directoryPage(1)).willReturn(null);

        assertThat(directory.snapshot().isEmpty()).isTrue();
    }

    @Test
    void isUnavailableWhenTheFirstPageCannotBeRead() {
        given(api.directoryPage(0)).willReturn(null);

        assertThat(directory.snapshot().isEmpty()).isTrue();
    }

    /**
     * The loop's length comes from a number upstream sent us. A corrupt total must not turn one
     * request into thousands of outbound calls — the limiter would stop them, but only after
     * burning the budget guide onboarding shares.
     */
    @Test
    void refusesAnImplausibleTotalRatherThanPagingForever() {
        given(api.directoryPage(0)).willReturn(new DirectoryPage(List.of(), 5_000_000));

        assertThat(directory.snapshot().isEmpty()).isTrue();
        verify(api, times(1)).directoryPage(anyInt()); // it never started paging
    }

    @Test
    void refusesAnEmptyDirectory_whichWouldMeanEveryStateShowsZero() {
        given(api.directoryPage(0)).willReturn(new DirectoryPage(List.of(), 0));

        assertThat(directory.snapshot().isEmpty()).isTrue();
    }

    /**
     * Unusable rows are dropped when bucketing, NOT while paging — dropping them earlier would make
     * a healthy page look short and trip the integrity check on good data.
     */
    @Test
    void dropsRowsWithNoIdOrName_withoutFailingTheIntegrityCheck() {
        given(api.directoryPage(0))
                .willReturn(
                        onePage(
                                row("", "No Id College", "X", "CA"),
                                row("2", "", "Y", "CA"),
                                row("3", "Real College", "Z", "CA")));

        Snapshot snap = directory.snapshot();

        assertThat(snap.isEmpty()).isFalse();
        assertThat(snap.inState("CA"))
                .extracting(DirectorySchool::name)
                .containsExactly("Real College");
    }

    // --- caching ----------------------------------------------------------------------------

    /** ~20 outbound calls a day, not ~20 per visitor. */
    @Test
    void theSnapshotIsPagedOncePerDay_notOncePerRequest() {
        given(api.directoryPage(0)).willReturn(onePage(row("1", "A College", "X", "CA")));

        directory.snapshot();
        directory.snapshot();

        verify(api, times(1)).directoryPage(0);
    }

    /** An outage must not be pinned for 24 hours; the next request has to try again. */
    @Test
    void anUnavailableSnapshotIsNotCached() {
        given(api.directoryPage(0)).willReturn(null);

        assertThat(directory.snapshot().isEmpty()).isTrue();
        assertThat(directory.snapshot().isEmpty()).isTrue();

        verify(api, times(2)).directoryPage(0);
    }
}
