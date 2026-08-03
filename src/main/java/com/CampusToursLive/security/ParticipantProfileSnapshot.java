package com.CampusToursLive.security;

import com.CampusToursLive.domain.participant.ParticipantProfileEntity;
import com.CampusToursLive.domain.participant.ParticipantType;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable, read-only snapshot of a {@code participant_profiles} row, built from a {@link
 * ParticipantProfileEntity} — never the managed entity itself, so callers can't accidentally mutate
 * it back into the persistence context. Loaded by {@link CurrentUser#requireParticipant()}.
 */
public record ParticipantProfileSnapshot(
        UUID id,
        UUID userId,
        ParticipantType participantType,
        String gradeLevel,
        String intendedMajor,
        String interests,
        boolean guardianRequired,
        Instant createdAt,
        Instant updatedAt) {

    static ParticipantProfileSnapshot from(ParticipantProfileEntity entity) {
        return new ParticipantProfileSnapshot(
                entity.getId(),
                entity.getUserId(),
                entity.getParticipantType(),
                entity.getGradeLevel(),
                entity.getIntendedMajor(),
                entity.getInterests(),
                entity.isGuardianRequired(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
