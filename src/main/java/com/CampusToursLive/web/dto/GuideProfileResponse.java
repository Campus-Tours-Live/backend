package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * Guide application / profile view, returned as an immutable record whose field names are the JSON
 * keys. The profile-level fields are null when the user has not started guide onboarding yet.
 */
public record GuideProfileResponse(
        String userId,
        String firstName,
        String lastName,
        String displayName,
        String email,
        String accountStatus,
        String universityId,
        String major,
        String classYear,
        String bio,
        List<String> languages,
        List<String> specialties,
        Long basePriceCents,
        String currency,
        String applicationStatus,
        String verificationStatus) {}
