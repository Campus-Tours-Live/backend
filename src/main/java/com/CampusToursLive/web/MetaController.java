package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.EnrollmentYearRules;
import com.CampusToursLive.domain.tour.TourFeatureCatalog;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.EnrollmentYearRulesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference / lookup data for the UI (BFF maps /v1/meta/* → here). Single source of truth for
 * controlled vocabularies like tour topics, so the frontend never hardcodes the list.
 */
@RestController
@RequestMapping("/meta")
@Tag(
        name = "Meta",
        description =
                "Reference / lookup data (controlled vocabularies) so the frontend never hardcodes"
                        + " enum lists.")
public class MetaController {

    private final SchoolDirectory schools;
    private final EnrollmentYearRules rules;

    public MetaController(SchoolDirectory schools, EnrollmentYearRules rules) {
        this.schools = schools;
        this.rules = rules;
    }

    @Schema(name = "Option", description = "A { value, label } option for a controlled vocabulary.")
    public record Option(
            @Schema(
                            description = "Stable enum code (the value stored/sent by the API).",
                            example = "GENERAL_CAMPUS",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String value,
            @Schema(
                            description = "Human-readable label for display.",
                            example = "General campus",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String label) {}

    private static final Map<TourTopic, String> TOPIC_LABELS =
            Map.of(
                    TourTopic.GENERAL_CAMPUS, "General campus",
                    TourTopic.DORM_HOUSING, "Dorms & housing",
                    TourTopic.DINING_STUDENT_LIFE, "Dining & student life",
                    TourTopic.MAJOR_SPECIFIC, "Major-specific",
                    TourTopic.INTERNATIONAL_STUDENT, "International student",
                    TourTopic.PARENT_FOCUSED, "Parent-focused",
                    TourTopic.FRESHMAN, "Freshman",
                    TourTopic.TRANSFER, "Transfer");

    @Operation(
            summary = "List tour topics",
            description =
                    "Returns the controlled tour-topic vocabulary as { value, label } options."
                            + " Public: served anonymously, no token required.")
    @ApiResponse(
            responseCode = "200",
            description = "The tour-topic options.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_TOPICS)))
    @GetMapping("/tour-topics")
    public ApiEnvelope<List<Option>> tourTopics() {
        List<Option> topics =
                java.util.Arrays.stream(TourTopic.values())
                        .map(t -> new Option(t.name(), TOPIC_LABELS.getOrDefault(t, t.name())))
                        .toList();
        return ApiEnvelope.of(topics);
    }

    @Operation(
            summary = "List tour feature options by topic",
            description =
                    "The controlled feature vocabulary a guide may attach to an offering, grouped by"
                            + " topic (each topic offers 10 options; a guide picks up to 3). Keyed by"
                            + " TourTopic name → { value, label } options. Single source of truth for the"
                            + " tour-creation dropdown and for rendering feature chips.")
    @ApiResponse(
            responseCode = "200",
            description = "The feature options grouped by topic.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_FEATURES)))
    @GetMapping("/tour-features")
    public ApiEnvelope<Map<String, List<Option>>> tourFeatures() {
        Map<String, List<Option>> byTopic = new LinkedHashMap<>();
        for (TourTopic topic : TourTopic.values()) {
            List<Option> options =
                    TourFeatureCatalog.allowedFor(topic).stream()
                            .map(f -> new Option(f.name(), f.label()))
                            .toList();
            byTopic.put(topic.name(), options);
        }
        return ApiEnvelope.of(byTopic);
    }

    /** Curated set of languages a guide may offer a tour / list on their profile in. */
    private static final List<String> SUPPORTED_LANGUAGES =
            List.of("en-US", "es", "zh", "fr", "de", "ja", "ko", "ar", "hi", "pt");

    @Operation(
            summary = "List supported languages",
            description =
                    "The languages a guide may select for a profile / tour, as { value, label }"
                            + " options. `value` is the BCP-47 tag stored by the API; `label` is the"
                            + " English display name (derived from the tag).")
    @ApiResponse(
            responseCode = "200",
            description = "The supported language options.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.LANGUAGES)))
    @GetMapping("/languages")
    public ApiEnvelope<List<Option>> languages() {
        List<Option> options =
                SUPPORTED_LANGUAGES.stream()
                        .map(tag -> new Option(tag, languageName(tag)))
                        .toList();
        return ApiEnvelope.of(options);
    }

    private static String languageName(String tag) {
        String name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH);
        return name == null || name.isBlank() ? tag : name;
    }

    @Operation(
            summary = "Search universities (live)",
            description =
                    "Typeahead search over every U.S. institution via the College Scorecard API, as"
                            + " { value = school id, label = 'Name — City, ST' }. Supports page/limit"
                            + " for incremental typeahead loading. Backs the guide onboarding"
                            + " university picker and the home header search; the chosen school is"
                            + " upserted on guide-profile submit.")
    @ApiResponse(
            responseCode = "200",
            description = "Matching universities (live Scorecard search).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.UNIVERSITIES_LIVE)))
    @GetMapping("/universities")
    public ApiEnvelope<List<Option>> universities(
            @Parameter(description = "Free-text school name search.") @RequestParam("q") String q,
            @Parameter(description = "Max rows per page (clamped 1–50).")
                    @RequestParam(name = "limit", required = false, defaultValue = "20")
                    int limit,
            @Parameter(description = "Zero-based page index (College Scorecard page).")
                    @RequestParam(name = "page", required = false, defaultValue = "0")
                    int page) {
        int capped = Math.min(Math.max(limit, 1), 50);
        int safePage = Math.max(page, 0);
        return ApiEnvelope.of(schools.searchSchools(q.trim(), capped, safePage));
    }

    @Operation(
            summary = "List a school's majors (live)",
            description =
                    "The distinct fields of study a school actually offers (College Scorecard CIP-4"
                            + " program titles), as { value = label = title }. Backs the onboarding major"
                            + " picker for the selected university.")
    @ApiResponse(
            responseCode = "200",
            description = "The school's majors (live Scorecard).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.MAJORS_LIVE)))
    @GetMapping("/majors")
    public ApiEnvelope<List<Option>> majors(@RequestParam("schoolId") String schoolId) {
        return ApiEnvelope.of(schools.majorsForSchool(schoolId));
    }

    @Operation(
            summary = "List a school's degree levels (live)",
            description =
                    "The distinct credential levels a school awards (from its College Scorecard"
                            + " programs), as { value = label = credential title, e.g. \"Bachelor's"
                            + " Degree\" }, ordered lowest → highest. Backs the onboarding degree picker"
                            + " for the selected university.")
    @ApiResponse(
            responseCode = "200",
            description = "The school's degree levels (live Scorecard).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.DEGREES_LIVE)))
    @GetMapping("/degrees")
    public ApiEnvelope<List<Option>> degrees(@RequestParam("schoolId") String schoolId) {
        return ApiEnvelope.of(schools.degreesForSchool(schoolId));
    }

    @Operation(
            summary = "Enrolment-year validation rules",
            description =
                    "The acceptable enrolment-year window (from the server's UTC clock) and the"
                            + " ordered degree → longest-time-to-graduate table used to derive the"
                            + " expected graduation-year window. Served so the browser never"
                            + " hardcodes these numbers or reads its own clock. Cacheable, but the"
                            + " max-age contracts to expire when the server's year turns over.")
    @ApiResponse(
            responseCode = "200",
            description = "The current enrolment-year rules.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.ENROLLMENT_YEARS)))
    @GetMapping("/enrollment-years")
    public ResponseEntity<ApiEnvelope<EnrollmentYearRulesResponse>> enrollmentYears() {
        // ONE snapshot for the whole response. Calling entryYearRange() and a separate
        // cacheMaxAgeSeconds() would read the clock twice and can, across midnight UTC, emit a
        // body describing one year beside a header computed for the next.
        EnrollmentYearRules.EnrollmentYearSnapshot snap = rules.snapshot();
        EnrollmentYearRules.YearRange entry = snap.entryYear();
        List<EnrollmentYearRulesResponse.DegreeRuleView> table =
                rules.degreeRules().stream()
                        .map(
                                r ->
                                        new EnrollmentYearRulesResponse.DegreeRuleView(
                                                r.matches(), r.years()))
                        .toList();
        EnrollmentYearRulesResponse body =
                new EnrollmentYearRulesResponse(
                        new EnrollmentYearRulesResponse.YearRangeView(entry.min(), entry.max()),
                        table,
                        rules.defaultMaxYearsToGraduate());
        // Same snapshot as the body above — contracts to the year boundary, ceiling 24h.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + snap.cacheMaxAgeSeconds())
                .body(ApiEnvelope.of(body));
    }
}
