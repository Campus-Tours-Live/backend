package com.CampusToursLive.security;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import com.CampusToursLive.domain.guide.GuideStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable, read-only snapshot of a {@code guide_profiles} row, built from a {@link
 * GuideProfileEntity} — never the managed entity itself, so callers can't accidentally mutate it
 * back into the persistence context. Loaded by {@link CurrentUser#requireGuide()}.
 */
public record GuideProfileSnapshot(
        UUID id,
        UUID userId,
        String bio,
        String spokenLanguages,
        String tourTopics,
        GuideStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static GuideProfileSnapshot from(GuideProfileEntity entity) {
        return new GuideProfileSnapshot(
                entity.getId(),
                entity.getUserId(),
                entity.getBio(),
                entity.getSpokenLanguages(),
                entity.getTourTopics(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
