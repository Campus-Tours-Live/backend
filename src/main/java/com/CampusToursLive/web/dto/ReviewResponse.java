package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A participant's review of a completed booking, as returned to the author.
 *
 * <p>Time fields are ISO-8601 strings (UTC). Sub-ratings and {@code comment} are null when the
 * author omitted them. {@code privateFeedback} is echoed back only on this owner-scoped surface; it
 * is never included on any public review list.
 */
@Schema(name = "ReviewResponse", description = "A participant's review of a completed booking.")
public record ReviewResponse(
        @Schema(
                        description = "Review id (UUID).",
                        example = "r1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String id,
        @Schema(
                        description = "Id of the booking this review is for.",
                        example = "b1a2c3d4-0000-4000-8000-000000000001",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String bookingId,
        @Schema(
                        description = "Id of the tour offering that was reviewed.",
                        example = "o1a2c3d4-0000-4000-8000-000000000002",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String offeringId,
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
                        description =
                                "Private feedback for the guide (author-visible only), or null when"
                                        + " omitted.",
                        example = "The meeting-point instructions were a little unclear.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String privateFeedback,
        @Schema(
                        description =
                                "Moderation status: PENDING_MODERATION, PUBLISHED, or REMOVED.",
                        example = "PUBLISHED",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(
                        description = "The guide's response to the review, or null when none.",
                        example = "Thanks for the kind words — hope to tour with you again!",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String guideResponse,
        @Schema(
                        description = "ISO-8601 UTC time the review was created.",
                        example = "2026-08-15T18:30:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String createdAt,
        @Schema(
                        description =
                                "ISO-8601 UTC time the review was published, or null when not yet"
                                        + " published.",
                        example = "2026-08-15T18:30:00Z",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String publishedAt) {}
