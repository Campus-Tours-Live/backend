package com.CampusToursLive.web.dto;

import com.CampusToursLive.domain.university.UniversityEntity;

/** Public catalog item for the universities picker. */
public record UniversityResponse(
        String id, String slug, String name, String shortName, String city, String region) {

    public static UniversityResponse from(UniversityEntity u) {
        return new UniversityResponse(
                u.getId().toString(),
                u.getSlug(),
                u.getName(),
                u.getShortName(),
                u.getCity(),
                u.getRegion());
    }
}
