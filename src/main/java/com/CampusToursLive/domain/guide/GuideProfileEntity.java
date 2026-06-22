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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps the {@code guide_profiles} table (V1__schema.sql) — the supply side of the marketplace. Only
 * the columns the signup / onboarding slice reads or writes are mapped; the rest (ratings, Stripe
 * refs, payouts) keep their DB defaults.
 */
@Getter
@Setter
@Entity
@Table(name = "guide_profiles")
public class GuideProfileEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "university_id", nullable = false)
    private UUID universityId;

    @Column(name = "major", nullable = false)
    private String major;

    @Column(name = "class_year")
    private String classYear;

    @Column(name = "bio")
    private String bio;

    /** JSONB array of BCP-47 language tags, e.g. ["en-US","es"]. Raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "languages", columnDefinition = "jsonb", nullable = false)
    private String languages = "[\"en-US\"]";

    /** JSONB array of tour_topic values. Raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specialties", columnDefinition = "jsonb", nullable = false)
    private String specialties = "[]";

    @Enumerated(EnumType.STRING)
    @Column(
            name = "application_status",
            columnDefinition = "guide_application_status",
            nullable = false)
    private GuideApplicationStatus applicationStatus = GuideApplicationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "verification_status",
            columnDefinition = "guide_verification_status",
            nullable = false)
    private GuideVerificationStatus verificationStatus = GuideVerificationStatus.NOT_SUBMITTED;

    @Column(name = "base_price_cents", nullable = false)
    private long basePriceCents = 2800L;

    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
