package com.CampusToursLive.domain.review;

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
 * Maps the {@code reviews} table (V1__schema.sql) — a participant's review of a completed booking.
 *
 * <p>{@code guideId} / {@code tourOfferingId} are denormalized from the reviewed booking (the
 * {@code guide_profiles.id} PK and {@code tour_offerings.id} PK), not supplied by the client. One
 * review per booking is enforced by the DB {@code UNIQUE(booking_id)} constraint. Rating columns
 * are {@code smallint} bounded 1-5 by DB CHECK constraints; {@link #overallRating} is required, the
 * four sub-ratings are optional.
 */
@Getter
@Setter
@Entity
@Table(name = "reviews")
public class ReviewEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "participant_user_id", nullable = false, updatable = false)
    private UUID participantUserId;

    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @Column(name = "tour_offering_id", nullable = false, updatable = false)
    private UUID tourOfferingId;

    @Column(name = "overall_rating", nullable = false)
    private short overallRating;

    @Column(name = "knowledge_rating")
    private Short knowledgeRating;

    @Column(name = "communication_rating")
    private Short communicationRating;

    @Column(name = "friendliness_rating")
    private Short friendlinessRating;

    @Column(name = "helpfulness_rating")
    private Short helpfulnessRating;

    @Column(name = "comment")
    private String comment;

    /** Author-and-guide-only feedback — never returned on any public review surface. */
    @Column(name = "private_feedback")
    private String privateFeedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "review_status", nullable = false)
    private ReviewStatus status;

    @Column(name = "guide_response")
    private String guideResponse;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
