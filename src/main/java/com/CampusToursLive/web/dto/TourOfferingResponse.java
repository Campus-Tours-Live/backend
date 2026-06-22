package com.CampusToursLive.web.dto;

/**
 * A guide's tour offering, as returned by the offerings endpoints — an immutable record whose field
 * names are the JSON keys.
 */
public record TourOfferingResponse(
        String id,
        String title,
        String slug,
        String status,
        String topic,
        String universityId,
        Integer durationMin,
        Long priceCents,
        String currency,
        String description) {}
