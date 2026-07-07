package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourTopic;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.Problem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
                            + " Public — no role required beyond a valid platform JWT.")
    @ApiResponse(
            responseCode = "200",
            description = "The tour-topic options.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.TOUR_TOPICS)))
    @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid platform JWT.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @GetMapping("/tour-topics")
    public ApiEnvelope<List<Option>> tourTopics() {
        List<Option> topics =
                java.util.Arrays.stream(TourTopic.values())
                        .map(t -> new Option(t.name(), TOPIC_LABELS.getOrDefault(t, t.name())))
                        .toList();
        return ApiEnvelope.of(topics);
    }
}
