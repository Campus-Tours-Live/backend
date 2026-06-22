package com.CampusToursLive.domain.guide;

/** Matches the PostgreSQL enum type {@code guide_application_status}. */
public enum GuideApplicationStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED
}
