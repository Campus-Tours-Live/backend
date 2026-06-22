package com.CampusToursLive.domain.guide;

/** Matches the PostgreSQL enum type {@code guide_verification_status}. */
public enum GuideVerificationStatus {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}
