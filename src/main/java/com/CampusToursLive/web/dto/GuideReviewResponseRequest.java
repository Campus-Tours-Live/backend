package com.CampusToursLive.web.dto;

/**
 * Request body for {@code POST /reviews/{reviewId}/response} — the guide's public reply to a review
 * of their tour. {@code response} is required, non-blank, and capped in length; validated in {@code
 * ReviewService} (mirroring the other write DTOs, which stay annotation-free records).
 */
public record GuideReviewResponseRequest(String response) {}
