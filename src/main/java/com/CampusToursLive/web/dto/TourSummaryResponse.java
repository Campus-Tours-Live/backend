package com.CampusToursLive.web.dto;

/** Public marketplace card for a bookable tour offering. */
public record TourSummaryResponse(
        String id,
        String title,
        String slug,
        String topic,
        String universityId,
        String universityName,
        String guideId,
        String guideDisplayName,
        int durationMin,
        long priceCents,
        String currency,
        double avgRating,
        int reviewCount) {}
