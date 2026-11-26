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
import org.hibernate.annotations.DynamicUpdate;

/**
 * Maps the {@code reviews} table (V1__schema.sql) — a participant's review of a completed booking.
 *
 * <p>{@code guideId} / {@code tourOfferingId} are denormalized from the reviewed booking (the
 * {@code guide_profiles.id} PK and {@code tour_offerings.id} PK), not supplied by the client. One
 * review per booking is enforced by the DB {@code UNIQUE(booking_id)} constraint. Rating columns
 * are {@code smallint} bounded 1-5 by DB CHECK constraints; {@link #overallRating} is required, the
 * four sub-ratings are optional.
 *
 * <p><b>{@code @DynamicUpdate}: each write touches only the columns it changed.</b> Different
 * writers own different columns of the same row: a guide reply sets {@code guide_response}, while
 * admin moderation sets {@code status} / {@code published_at}. With Hibernate's default all-columns
 * UPDATE, a writer that loaded the row before a concurrent writer committed would write its stale
 * copy of the other's columns back. For example, a reply could restore {@code PUBLISHED} over an
 * admin's {@code REMOVED}, or a moderation could erase a reply. Writing only the dirty columns
 * keeps both changes, and the row lock taken by the UPDATE orders writes to the same column.
 */
@Getter
@Setter
@Entity
@DynamicUpdate
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
