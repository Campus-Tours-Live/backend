package com.CampusToursLive.domain.guide;

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

/**
 * Maps the {@code guide_universities} table (V6__guide_universities.sql) — a guide's per-university
 * offering (major/degree/class year) plus its own school-email verification lifecycle, independent
 * of the guide's primary {@code guide_profiles} university.
 */
@Getter
@Setter
@Entity
@Table(name = "guide_universities")
public class GuideUniversityEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_profile_id", nullable = false)
    private UUID guideProfileId;

    @Column(name = "university_id", nullable = false)
    private UUID universityId;

    @Column(name = "major")
    private String major;

    @Column(name = "degree")
    private String degree;

    @Column(name = "class_year")
    private String classYear;

    /** PII, never serialized. */
    @Column(name = "school_email")
    private String schoolEmail;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "verification_status",
            columnDefinition = "guide_verification_status",
            nullable = false)
    private GuideVerificationStatus verificationStatus = GuideVerificationStatus.NOT_SUBMITTED;

    @Column(name = "verification_sent_at")
    private Instant verificationSentAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
