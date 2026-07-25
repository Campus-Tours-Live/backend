package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.SupportedLanguages;
import com.CampusToursLive.domain.tour.TourFeatureCatalog;
import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.integration.scorecard.SchoolDirectory;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public MetaController(SchoolDirectory schools) {
        this.schools = schools;
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
                SupportedLanguages.TAGS.stream()
                        .map(tag -> new Option(tag, SupportedLanguages.displayName(tag)))
                        .toList();
        return ApiEnvelope.of(options);
    }

    @Operation(
            summary = "Search universities (live)",
            description =
                    "Typeahead search over every U.S. institution via the College Scorecard API, as"
                            + " { value = school id, label = 'Name — City, ST' }. Backs the guide"
                            + " onboarding university picker; the chosen school is upserted on submit.")
    @ApiResponse(
            responseCode = "200",
            description = "Matching universities (live Scorecard search).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.UNIVERSITIES_LIVE)))
    @GetMapping("/universities")
    public ApiEnvelope<List<Option>> universities(@RequestParam("q") String q) {
        return ApiEnvelope.of(schools.searchSchools(q, 20));
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
}
