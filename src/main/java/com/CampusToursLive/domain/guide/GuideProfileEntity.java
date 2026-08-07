package com.CampusToursLive.domain.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    @Column(name = "bio")
    private String bio;

    /** JSONB array of BCP-47 language tags, e.g. ["en-US","es"]. Raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spoken_languages", columnDefinition = "jsonb", nullable = false)
    private String spokenLanguages = "[\"en-US\"]";

    /** JSONB array of tour_topic values. Raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tour_topics", columnDefinition = "jsonb", nullable = false)
    private String tourTopics = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "guide_status", columnDefinition = "guide_application_status", nullable = false)
    private GuideStatus status = GuideStatus.PENDING;

    /** Aggregate review score maintained by a DB trigger / nightly batch. Read-only from JPA. */
    @Column(name = "avg_rating", insertable = false, updatable = false)
    private BigDecimal avgRating;

    /** Total published review count. Read-only from JPA. */
    @Column(name = "review_count", nullable = false, insertable = false, updatable = false)
    private int reviewCount;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
