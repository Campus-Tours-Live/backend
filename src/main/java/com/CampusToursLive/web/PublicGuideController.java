package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.PublicGuideProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public guide read surface (Core: /guides/{guideId}). Genuinely public — anonymous, like the tour
 * catalog — so a marketplace visitor can view the guide leading a tour before booking. Only
 * VERIFIED guides are exposed (see {@code SecurityConfig}'s {@code permitAll} GET matcher); an
 * unknown or non-VERIFIED guide is a 404.
 */
@RestController
@RequestMapping("/guides")
@Tag(
        name = "Public guides",
        description =
                "A verified guide's public marketplace profile — anonymous, read-only. Private"
                        + " onboarding details are never exposed.")
public class PublicGuideController {

    private final GuideService guideService;

    public PublicGuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    /** A verified guide's public profile. */
    @Operation(
            summary = "Get a guide's public profile",
            description =
                    "Returns a VERIFIED guide's public profile (display name, bio, spoken languages,"
                            + " tour specialties). Anonymous. An unknown or non-verified guide yields"
                            + " a 404.")
    @ApiResponse(
            responseCode = "200",
            description = "The guide's public profile.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.PUBLIC_GUIDE_PROFILE)))
    @ApiResponse(
            responseCode = "404",
            description = "No such guide, or the guide is not verified.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @GetMapping("/{guideId}")
    public ApiEnvelope<PublicGuideProfileResponse> get(@PathVariable UUID guideId) {
        return ApiEnvelope.of(guideService.getPublicProfile(guideId));
    }
}
