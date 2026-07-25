package com.CampusToursLive.integration.scorecard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.CampusToursLive.integration.scorecard.SchoolDirectory.SchoolRef;
import com.CampusToursLive.web.MetaController.Option;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The outbound half of the Scorecard integration: HTTP + parsing + degradation. This is the
 * behavior that used to live in {@link ScorecardClient} before the cache/rate-limit split ??it must
 * be preserved exactly, since {@code ScorecardClient} is now a thin caching facade over it.
 */
class ScorecardApiTest {

    private static final String BASE = "https://scorecard.test/v1";

    /** Wire an api against a mock transport; returns both so tests can set expectations. */
    private record Fixture(ScorecardApi api, MockRestServiceServer server) {}

    private static Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new ScorecardApi(builder.build(), apiKey), server);
    }

    // --- searchSchools ----------------------------------------------------------------------

    @Test
    void searchSchools_parsesResultsIntoValueLabelOptions() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("school.name=Stanford")))
                .andRespond(
                        withSuccess(
                                """
                                {"results":[
                                  {"id":243744,"school.name":"Stanford University",
                                   "school.city":"Stanford","school.state":"CA"}
                                ]}
                                """,
                                MediaType.APPLICATION_JSON));

        List<Option> out = f.api().searchSchools("Stanford", 5, 0);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).value()).isEqualTo("243744");
        assertThat(out.get(0).label()).startsWith("Stanford University");
        assertThat(out.get(0).label()).contains("Stanford, CA");
        f.server().verify();
    }

    @Test
    void searchSchools_skipsRowsMissingIdOrName_andOmitsLocationWhenAbsent() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("/schools")))
                .andRespond(
                        withSuccess(
                                """
                                {"results":[
                                  {"id":"","school.name":"No Id University"},
                                  {"id":"1","school.name":""},
                                  {"id":"2","school.name":"Nowhere College",
                                   "school.city":"","school.state":""}
                                ]}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(f.api().searchSchools("x", 5, 0))
                .containsExactly(new Option("2", "Nowhere College"));
    }

    @Test
    void searchSchools_clampsPerPageIntoOneToFifty() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("per_page=50")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        assertThat(f.api().searchSchools("x", 999, 0)).isEmpty();
        f.server().verify();

        Fixture g = fixture("KEY");
        g.server()
                .expect(requestTo(Matchers.containsString("per_page=1")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        assertThat(g.api().searchSchools("x", 0, 0)).isEmpty();
        g.server().verify();
    }

    @Test
    void searchSchools_forwardsZeroBasedPageToScorecard() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("page=2")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        assertThat(f.api().searchSchools("x", 8, 2)).isEmpty();
        f.server().verify();
    }

    @Test
    void searchSchools_degradesToEmptyOnUpstreamFailure() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("/schools")))
                .andRespond(withServerError());

        assertThat(f.api().searchSchools("Stanford", 5, 0)).isEmpty();
    }

    @Test
    void searchSchools_returnsEmptyForBlankKeyOrBlankQuery_withoutCallingUpstream() {
        // No MockRestServiceServer expectations are set: any HTTP call would fail the test.
        assertThat(fixture("").api().searchSchools("Stanford", 5, 0)).isEmpty();
        assertThat(fixture("KEY").api().searchSchools(null, 5, 0)).isEmpty();
        assertThat(fixture("KEY").api().searchSchools("   ", 5, 0)).isEmpty();
        // A null key is normalised to blank rather than NPE-ing.
        assertThat(new ScorecardApi(RestClient.builder().build(), null).searchSchools("x", 5, 0))
                .isEmpty();
    }

    // --- majorsForSchool --------------------------------------------------------------------

    @Test
    void majorsForSchool_dedupesCleanedTitlesAndSortsCaseInsensitively() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("id=243744")))
                .andRespond(
                        withSuccess(
                                """
                                {"results":[{"latest.programs.cip_4_digit":[
                                  {"title":"Computer   Science."},
                                  {"title":"Computer Science"},
                                  {"title":"anthropology."},
                                  {"title":"   "},
                                  {"title":"Biology."}
                                ]}]}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(f.api().majorsForSchool("243744"))
                .extracting(Option::label)
                .containsExactly("anthropology", "Biology", "Computer Science");
    }

    @Test
    void majorsForSchool_degradesToEmptyOnFailureOrBlankInput() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("/schools")))
                .andRespond(withServerError());
        assertThat(f.api().majorsForSchool("243744")).isEmpty();

        assertThat(fixture("").api().majorsForSchool("243744")).isEmpty();
        assertThat(fixture("KEY").api().majorsForSchool(null)).isEmpty();
        assertThat(fixture("KEY").api().majorsForSchool(" ")).isEmpty();
    }

    // --- getSchool --------------------------------------------------------------------------

    @Test
    void getSchool_mapsIdentityAndTrimsId() {
        Fixture f = fixture("KEY");
        f.server()
                .expect(requestTo(Matchers.containsString("id=243744")))
                .andRespond(
                        withSuccess(
                                """
                                {"results":[{"id":243744,"school.name":"Stanford University",
                                 "school.city":"Stanford","school.state":"CA"}]}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(f.api().getSchool("  243744  "))
                .isEqualTo(new SchoolRef("243744", "Stanford University", "Stanford", "CA"));
    }

    @Test
    void getSchool_returnsNullWhenNotFoundOrFailedOrBlank() {
        Fixture notFound = fixture("KEY");
        notFound.server()
                .expect(requestTo(Matchers.containsString("/schools")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        assertThat(notFound.api().getSchool("999")).isNull();

        Fixture failed = fixture("KEY");
        failed.server()
                .expect(requestTo(Matchers.containsString("/schools")))
                .andRespond(withServerError());
        assertThat(failed.api().getSchool("243744")).isNull();

        assertThat(fixture("").api().getSchool("243744")).isNull();
        assertThat(fixture("KEY").api().getSchool(null)).isNull();
        assertThat(fixture("KEY").api().getSchool(" ")).isNull();
    }

    // --- rate-limiter fallbacks -------------------------------------------------------------

    /**
     * The fallbacks are what a caller sees when the "scorecard" limiter is exhausted. They must
     * return the same degraded value the happy path degrades to, and must never rethrow ??a
     * propagating RequestNotPermittedException would surface as a 500 on the public typeahead.
     */
    @Test
    void rateLimiterFallbacks_returnDegradedValuesAndNeverThrow() {
        ScorecardApi api = fixture("KEY").api();
        Throwable limitHit = new RuntimeException("RequestNotPermitted");

        assertThatCode(
                        () -> {
                            assertThat(api.searchSchoolsRateLimited("stanford", 5, 0, limitHit))
                                    .isEmpty();
                            assertThat(api.majorsForSchoolRateLimited("243744", limitHit))
                                    .isEmpty();
                            assertThat(api.getSchoolRateLimited("243744", limitHit)).isNull();
                        })
                .doesNotThrowAnyException();
    }
}
