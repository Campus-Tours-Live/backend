package com.CampusToursLive.domain.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code guide_universities} table (V1__schema.sql) — a guide's per-university offering
 * (major/degree/class year) plus its own school-email verification lifecycle, independent of the
 * guide's primary {@code guide_profiles} university.
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

    /**
     * Year the guide entered this university, e.g. 2023. NOT NULL at the database layer — every
     * stored row has one.
     *
     * <p>Kept as {@link Integer}, NOT {@code int}, deliberately: a primitive would default to 0 and
     * silently write a garbage year past the NOT NULL constraint, and {@code
     * GuideService.updateProfile} still needs {@code null} to mean "the PATCH did not supply this"
     * when it merges the request against the stored row. Do not "fix" this to {@code int}.
     */
    @Column(name = "entry_year")
    private Integer entryYear;

    /**
     * Optimistic lock (spec D8/I3). entryYear and classYear are now INTERDEPENDENT — each is
     * validated against the other — so two concurrent single-field PATCHes can each be valid
     * against the snapshot they read while their combination is not. Per-request validation cannot
     * see that; a version check can. The loser surfaces as OptimisticLockingFailureException, which
     * GlobalExceptionHandler already maps to 409 (same treatment BookingEntity gets).
     */
    @Version
    @Column(nullable = false)
    private long version;

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
