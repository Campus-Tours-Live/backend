package com.CampusToursLive.domain.review;

/**
 * Moderation lifecycle of a {@link ReviewEntity}, mirroring the Postgres {@code review_status} enum
 * (V1__schema.sql).
 *
 * <p>Only {@code PUBLISHED} reviews are visible publicly and count toward a guide's / offering's
 * {@code avg_rating} and {@code review_count}. The MVP (CTL-107) auto-publishes on create; {@code
 * PENDING_MODERATION} and the moderation transitions are a later follow-up once an admin /
 * moderation surface exists.
 */
public enum ReviewStatus {
    PENDING_MODERATION,
    PUBLISHED,
    REMOVED
}
