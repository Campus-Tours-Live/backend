package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single review as shown on a public guide-profile or tour-detail surface.
 *
 * <p>Deliberately a superset-minus: it carries the ratings, public comment, and the guide's
 * response, but <b>never</b> {@code privateFeedback} (author/guide-only) and never the reviewer's
 * user id — only a display name. Time fields are ISO-8601 UTC strings.
 */
@Schema(
        name = "PublicReviewResponse",
        description = "A published review as shown on a public guide or tour page.")
public record PublicReviewResponse(
        @Schema(
                        description = "Review id (UUID).",
                        example = "r1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Id of the guide who was reviewed.",
                        example = "g1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideId,
        @Schema(
                        description = "Id of the tour offering that was reviewed.",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String offeringId,
        @Schema(
                        description =
                                "Reviewer's display name, or null when it cannot be resolved.",
                        example = "Pat P.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String reviewerName,
        @Schema(
                        description = "Overall rating, 1-5.",
                        example = "5",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int overallRating,
        @Schema(
                        description = "Knowledge sub-rating (1-5), or null when omitted.",
                        example = "5",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer knowledgeRating,
        @Schema(
                        description = "Communication sub-rating (1-5), or null when omitted.",
                        example = "4",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer communicationRating,
        @Schema(
                        description = "Friendliness sub-rating (1-5), or null when omitted.",
                        example = "5",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer friendlinessRating,
        @Schema(
                        description = "Helpfulness sub-rating (1-5), or null when omitted.",
                        example = "5",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Integer helpfulnessRating,
        @Schema(
                        description = "Public review text, or null when omitted.",
                        example = "Fantastic tour, learned a ton about the campus.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String comment,
        @Schema(
                        description = "The guide's response to the review, or null when none.",
                        example = "Thanks for the kind words!",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String guideResponse,
        @Schema(
                        description = "ISO-8601 UTC time the review was created.",
                        example = "2026-08-15T18:30:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String createdAt,
        @Schema(
                        description = "ISO-8601 UTC time the review was published.",
                        example = "2026-08-15T18:30:00Z",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String publishedAt) {}
