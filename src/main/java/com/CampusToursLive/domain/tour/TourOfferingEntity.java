package com.CampusToursLive.domain.tour;

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
 * Maps {@code tour_offerings} (V1__schema.sql) — a guide's sellable tour product. Only the columns
 * the onboarding/supply slice reads or writes are mapped; the rest (avg_rating, review_count) keep
 * their DB defaults on insert.
 */
@Getter
@Setter
@Entity
@Table(name = "tour_offerings")
public class TourOfferingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "university_id", nullable = false)
    private UUID universityId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "description", nullable = false)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "topic", columnDefinition = "tour_topic", nullable = false)
    private TourTopic topic;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    /** JSONB array of BCP-47 language tags. Raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "languages", columnDefinition = "jsonb", nullable = false)
    private String languages = "[\"en-US\"]";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "tour_status", nullable = false)
    private TourStatus status = TourStatus.DRAFT;

    @Column(name = "avg_rating", nullable = false, insertable = false, updatable = false)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false, insertable = false, updatable = false)
    private int reviewCount;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
