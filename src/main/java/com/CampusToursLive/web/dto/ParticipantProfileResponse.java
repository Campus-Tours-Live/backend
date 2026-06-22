package com.CampusToursLive.web.dto;

/**
 * Participant profile view, returned as an immutable record whose field names are the JSON keys.
 * The interest fields ({@code topicsOfInterest}, {@code universitiesOfInterest}, {@code
 * accessibilityPreferences}) come from a free-form JSON blob, so they stay typed as {@code Object}.
 * Profile-level fields are null before participant onboarding.
 */
public record ParticipantProfileResponse(
        String userId,
        String firstName,
        String lastName,
        String displayName,
        String email,
        String preferredLanguage,
        String timezone,
        String participantType,
        String gradeLevel,
        String intendedMajor,
        Boolean guardianRequired,
        Object topicsOfInterest,
        Object universitiesOfInterest,
        Object accessibilityPreferences) {}
