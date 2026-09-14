package com.CampusToursLive.web.dto;

/**
 * Request body for {@code POST /reviews/{reviewId}/moderation}. {@code status} is the target
 * moderation state — {@code PUBLISHED} or {@code REMOVED} only (case-insensitive); any other value,
 * including {@code PENDING_MODERATION}, is rejected. Validated in {@code ReviewService}.
 */
public record ModerateReviewRequest(String status) {}
