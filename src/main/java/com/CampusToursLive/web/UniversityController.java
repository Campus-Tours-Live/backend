package com.CampusToursLive.web;

import com.CampusToursLive.error.ValidationException;
import com.CampusToursLive.integration.scorecard.UniversityDirectory;
import com.CampusToursLive.integration.scorecard.UniversityDirectory.DirectorySchool;
import com.CampusToursLive.integration.scorecard.UniversityDirectory.Snapshot;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.StateUniversitiesResponse;
import com.CampusToursLive.web.dto.StateUniversityCountsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The browsable university directory — the browse-by-state page and the state pages behind it.
 *
 * <p><strong>Not under /meta.</strong> {@code /meta/*} is controlled vocabularies: enum lists a
 * dropdown needs so the frontend does not hardcode them. This is a resource a visitor navigates —
 * it has states, lists, and a page per state — so it lives at its own path. The onboarding
 * typeahead stays at {@code /meta/universities} because that genuinely is a picker's lookup, and it
 * searches this same population (see the directory boundary on {@code ScorecardApi}).
 *
 * <p>Public, like the tour catalog: this is what an anonymous visitor came to look at.
 */
@RestController
@RequestMapping("/universities")
@Tag(name = "Universities", description = "The browsable U.S. university directory, by state.")
public class UniversityController {

    /**
     * How long a client may hold a directory response. A day, matching the server-side snapshot:
     * the underlying IPEDS data is published annually, so anything shorter buys re-fetches of
     * numbers that have not moved.
     */
    private static final long DIRECTORY_MAX_AGE_SECONDS = 86_400;

    private final UniversityDirectory directory;

    public UniversityController(UniversityDirectory directory) {
        this.directory = directory;
    }

    @Operation(
            summary = "University counts per state",
            description =
                    "How many universities the directory holds in each state, keyed by USPS code."
                            + " Backs the browse-by-state page. Each figure is exactly the length of the"
                            + " list GET /universities?state=… returns, because both are read off one"
                            + " directory snapshot. Public: served anonymously. Cacheable for a day; the"
                            + " source data is published annually.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The per-state counts.",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                value = ApiExamples.STATE_UNIVERSITY_COUNTS))),
        @ApiResponse(
                responseCode = "503",
                description =
                        "The directory could not be read. Deliberately an error rather than a"
                                + " response of zeros: a page rendering 'California — 0' would be a"
                                + " confident wrong answer, and nothing on it would say so.",
                content = @Content)
    })
    @GetMapping("/state-summary")
    public ResponseEntity<ApiEnvelope<StateUniversityCountsResponse>> stateSummary() {
        Snapshot snapshot = requireDirectory();
        StateUniversityCountsResponse body =
                new StateUniversityCountsResponse(snapshot.countsByState(), snapshot.total());
        return cacheable().body(ApiEnvelope.of(body));
    }

    @Operation(
            summary = "List one state's universities",
            description =
                    "Every university in one state, sorted by name. Not paginated — the largest"
                            + " state holds about 150 schools. Served from the same snapshot the"
                            + " counts come from, so this list's length always equals that state's"
                            + " figure on the browse page. Public: served anonymously. Cacheable for a"
                            + " day.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The state's universities.",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples = @ExampleObject(value = ApiExamples.STATE_UNIVERSITIES))),
        @ApiResponse(
                responseCode = "422",
                description =
                        "`state` is missing, or is not one of the 50 states or DC. Territories are"
                                + " rejected here rather than answered with an empty list, which would"
                                + " read as 'Puerto Rico has no universities'.",
                content = @Content),
        @ApiResponse(
                responseCode = "503",
                description = "The directory could not be read.",
                content = @Content)
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<StateUniversitiesResponse>> inState(
            @Parameter(
                            description =
                                    "USPS state code, case-insensitive. One of the 50 states or DC.",
                            example = "CA",
                            required = true)
                    /*
                     * `required = false` even though the parameter IS required.
                     *
                     * Spring's own enforcement raises MissingServletRequestParameterException, which
                     * no handler here answers — so omitting `state` came back as a 500. A request
                     * that is plainly the caller's fault must not be reported as the server
                     * breaking. Letting it through as null puts it through the same check as "ZZ"
                     * and "Puerto Rico" below, which answers 422 with a message naming what was
                     * wrong. The @Parameter annotation above still documents it as required.
                     */
                    @RequestParam(value = "state", required = false)
                    String state) {
        String code = state == null ? "" : state.strip().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            // Told apart from a wrong value on purpose: "got ''" tells a caller who forgot the
            // parameter nothing about what to do next.
            throw new ValidationException("state is required (a USPS code, e.g. CA)");
        }
        if (!UniversityDirectory.US_STATE_CODES.contains(code)) {
            throw new ValidationException(
                    "state must be one of the 50 U.S. states or DC (got '" + state + "')");
        }

        List<DirectorySchool> schools = requireDirectory().inState(code);
        List<StateUniversitiesResponse.University> universities =
                schools.stream()
                        .map(
                                s ->
                                        new StateUniversitiesResponse.University(
                                                s.id(), s.name(), s.city()))
                        .toList();
        return cacheable()
                .body(
                        ApiEnvelope.of(
                                new StateUniversitiesResponse(
                                        code, universities, universities.size())));
    }

    /**
     * The snapshot, or a 503.
     *
     * <p>Both endpoints fail loudly rather than degrading, which is the opposite of what the
     * onboarding lookups do — and deliberately. An empty typeahead reads as "no matches" and costs
     * a guide one retry; an empty directory reads as "this state has no universities", which is a
     * confident wrong answer a visitor has no way to question. When the truth is unavailable, say
     * so.
     */
    private Snapshot requireDirectory() {
        Snapshot snapshot = directory.snapshot();
        if (snapshot.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The university directory is temporarily unavailable.");
        }
        return snapshot;
    }

    /** Cache headers for a successful directory read. 503s carry none, so they are never held. */
    private static ResponseEntity.BodyBuilder cacheable() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + DIRECTORY_MAX_AGE_SECONDS);
    }
}
