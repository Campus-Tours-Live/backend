package com.CampusToursLive.web.dto;

import java.util.List;

/**
 * PATCH /participant/profile body. All fields optional (partial update). Maps to: users
 * (displayName, preferredLanguage, timezone) + participant_profiles (participantType, gradeLevel,
 * intendedMajor) + participant_profiles.interests JSON (universitiesOfInterest, topicsOfInterest,
 * accessibilityPreferences).
 */
public record ParticipantProfileUpdateRequest(
        String firstName,
        String lastName,
        String displayName,
        String participantType,
        String gradeLevel,
        String intendedMajor,
        List<String> universitiesOfInterest,
        List<String> topicsOfInterest,
        String preferredLanguage,
        String timezone,
        String accessibilityPreferences) {}
