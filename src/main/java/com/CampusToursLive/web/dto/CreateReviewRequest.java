package com.CampusToursLive.web.dto;

/**
 * Request body for {@code POST /bookings/{bookingId}/review}. {@code overallRating} is required
 * (1-5); the four sub-ratings are optional (each 1-5 when present). {@code comment} is the public
 * review text; {@code privateFeedback} is author-and-guide-only. Ranges and presence are validated
 * in {@code ReviewService} (mirroring the pattern used by the other write DTOs, which stay
 * annotation-free records).
 */
public record CreateReviewRequest(
        Integer overallRating,
        Integer knowledgeRating,
        Integer communicationRating,
        Integer friendlinessRating,
        Integer helpfulnessRating,
        String comment,
        String privateFeedback) {}
