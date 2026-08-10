package com.CampusToursLive.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.integration.scorecard.UniversityDirectory;
import com.CampusToursLive.integration.scorecard.UniversityDirectory.DirectorySchool;
import com.CampusToursLive.integration.scorecard.UniversityDirectory.Snapshot;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.StateUniversitiesResponse;
import com.CampusToursLive.web.dto.StateUniversityCountsResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/** The browsable directory's two endpoints: the state summary and one state's list. */
class UniversityControllerTest {

    private static Snapshot snapshotWith(Map<String, List<String>> namesByState) {
        Map<String, List<DirectorySchool>> byState = new LinkedHashMap<>();
        for (String code : UniversityDirectory.US_STATE_CODES) {
            List<String> names = namesByState.getOrDefault(code, List.of());
            byState.put(
                    code,
                    names.stream()
                            .map(n -> new DirectorySchool(n + "-id", n, "Somewhere"))
                            .toList());
        }
        return new Snapshot(byState);
    }

    private static UniversityController controllerOver(Snapshot snapshot) {
        return new UniversityController(() -> snapshot);
    }

    private static final UniversityController CONTROLLER =
            controllerOver(
                    snapshotWith(
                            Map.of(
                                    "CA", List.of("Stanford University", "Caltech"),
                                    "NY", List.of("Columbia University"))));

    // --- /universities/state-summary ---------------------------------------------------------

    @Test
    void stateSummary_reportsEveryStateAndTheirSum() {
        StateUniversityCountsResponse body =
                Objects.requireNonNull(CONTROLLER.stateSummary().getBody()).data();

        assertThat(body.byState()).hasSize(UniversityDirectory.US_STATE_CODES.size());
        assertThat(body.byState())
                .containsEntry("CA", 2)
                .containsEntry("NY", 1)
                .containsEntry("WY", 0);
        assertThat(body.total()).isEqualTo(3);
    }

    @Test
    void stateSummary_isCacheableForADay() {
        assertThat(CONTROLLER.stateSummary().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=86400");
    }

    // --- /universities?state= ----------------------------------------------------------------

    @Test
    void inState_listsThatStatesUniversities() {
        StateUniversitiesResponse body =
                Objects.requireNonNull(CONTROLLER.inState("CA").getBody()).data();

        assertThat(body.state()).isEqualTo("CA");
        assertThat(body.universities())
                .extracting(StateUniversitiesResponse.University::name)
                .containsExactly("Stanford University", "Caltech");
        assertThat(body.total()).isEqualTo(2);
    }

    @Test
    void inState_acceptsAnyCasingAndSurroundingSpace() {
        assertThat(Objects.requireNonNull(CONTROLLER.inState("  ca ").getBody()).data().state())
                .isEqualTo("CA");
    }

    @Test
    void inState_isCacheableForADay() {
        assertThat(CONTROLLER.inState("CA").getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=86400");
    }

    /**
     * A state we do not cover is rejected, not answered with an empty list. "Puerto Rico: no
     * universities" is a confident wrong answer; "we do not list Puerto Rico" is the truth.
     */
    @Test
    void inState_rejectsAnythingThatIsNotOneOfTheFiftyOne() {
        for (String bad : List.of("PR", "GU", "ZZ", "California")) {
            assertThatThrownBy(() -> CONTROLLER.inState(bad))
                    .as("state=%s", bad)
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("50 U.S. states");
        }
    }

    /**
     * A missing parameter is a 422 like any other bad request, NOT a 500.
     *
     * <p>Spring's own {@code required = true} enforcement raises an exception no handler here
     * answers, so it surfaced as a server error — a request that is plainly the caller's fault
     * reported as the server breaking. The parameter is accepted as optional and rejected here
     * instead, with a message that tells a caller who forgot it what to send.
     */
    @Test
    void inState_rejectsAMissingStateAsABadRequest_notAServerError() {
        for (String missing : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> CONTROLLER.inState(missing))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("state is required");
        }
    }

    /** A state with nothing in it is a real, valid answer — distinct from an unknown state. */
    @Test
    void inState_servesAnEmptyStateAsAnEmptyList() {
        StateUniversitiesResponse body =
                Objects.requireNonNull(CONTROLLER.inState("WY").getBody()).data();

        assertThat(body.universities()).isEmpty();
        assertThat(body.total()).isZero();
    }

    // --- the cross-endpoint invariant --------------------------------------------------------

    /**
     * What the whole one-snapshot design buys: the figure the browse page shows for a state is the
     * length of the list its state page serves. Two separate fetches is how a page says 148 and
     * then lists 147.
     */
    @Test
    void everyCountOnTheSummaryMatchesTheListForThatState() {
        Map<String, Integer> counts =
                Objects.requireNonNull(CONTROLLER.stateSummary().getBody()).data().byState();

        counts.forEach(
                (code, count) -> {
                    ResponseEntity<ApiEnvelope<StateUniversitiesResponse>> res =
                            CONTROLLER.inState(code);
                    StateUniversitiesResponse body = Objects.requireNonNull(res.getBody()).data();
                    assertThat(body.universities()).as("state %s", code).hasSize(count);
                    assertThat(body.total()).as("state %s", code).isEqualTo(count);
                });
    }

    // --- degradation -------------------------------------------------------------------------

    /**
     * Both endpoints fail loudly rather than degrading, which is the opposite of the onboarding
     * lookups and deliberate: an empty typeahead reads as "no matches" and costs a retry, while an
     * empty directory reads as "this state has no universities" — a confident wrong answer a
     * visitor has no way to question.
     */
    @Test
    void bothEndpointsFailLoudlyWhenTheDirectoryCannotBeRead() {
        UniversityController down = controllerOver(Snapshot.unavailable());

        assertThatThrownBy(down::stateSummary)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> down.inState("CA"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /** …and a bad state code is still a 422, checked before the directory is even consulted. */
    @Test
    void anUnknownStateIsRejectedEvenWhenTheDirectoryIsDown() {
        assertThatThrownBy(() -> controllerOver(Snapshot.unavailable()).inState("PR"))
                .isInstanceOf(ValidationException.class);
    }
}
