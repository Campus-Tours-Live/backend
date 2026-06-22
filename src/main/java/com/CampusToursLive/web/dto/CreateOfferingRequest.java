package com.CampusToursLive.web.dto;

import java.util.List;

/** Body for POST /guide/offerings — create a tour offering (starts as DRAFT). */
public record CreateOfferingRequest(
        String title,
        String universityId,
        String topic,
        Integer durationMin,
        Long priceCents,
        String description,
        List<String> languages) {}
