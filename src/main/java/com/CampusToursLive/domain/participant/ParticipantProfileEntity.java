package com.CampusToursLive.domain.participant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps the {@code participant_profiles} table (V1__schema.sql). */
@Getter
@Setter
@Entity
@Table(name = "participant_profiles")
public class ParticipantProfileEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", columnDefinition = "participant_type", nullable = false)
    private ParticipantType participantType;

    @Column(name = "grade_level")
    private String gradeLevel;

    @Column(name = "intended_major")
    private String intendedMajor;

    /** JSONB: topic tags, saved universities, etc. Stored as a raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interests", columnDefinition = "jsonb", nullable = false)
    private String interests = "{}";

    @Column(name = "guardian_required", nullable = false)
    private boolean guardianRequired;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
