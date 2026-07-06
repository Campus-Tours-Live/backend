package com.CampusToursLive.web.dto;

import java.util.List;

/** Full public detail for a single bookable tour offering. */
public record TourDetailResponse(
        String id,
        String title,
        String slug,
        String topic,
        String description,
        List<String> languages,
        String universityId,
        String universityName,
        String universitySlug,
        String universityCity,
        String universityRegion,
        String guideId,
        String guideDisplayName,
        String guideBio,
        int durationMin,
        long priceCents,
        String currency,
        double avgRating,
        int reviewCount) {}
