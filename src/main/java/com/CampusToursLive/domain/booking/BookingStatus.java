package com.CampusToursLive.domain.booking;

/** Matches the PostgreSQL enum type {@code booking_status} (V1__schema.sql). */
public enum BookingStatus {
    DRAFT,
    PENDING_PAYMENT_AUTH,
    PENDING_GUIDE_ACCEPTANCE,
    PAYMENT_ACTION_REQUIRED,
    CONFIRMED,
    DECLINED_BY_GUIDE,
    EXPIRED,
    CANCELLED_BY_PARTICIPANT,
    CANCELLED_BY_GUIDE,
    IN_PROGRESS,
    COMPLETED,
    PARTICIPANT_NO_SHOW,
    GUIDE_NO_SHOW,
    TECHNICAL_FAILURE,
    REFUND_PENDING,
    REFUNDED;

    /**
     * Simplified status string for the frontend. The BFF passes this through as-is so the frontend
     * does not need to know about every internal status variant.
     */
    public String displayStatus() {
        return switch (this) {
            case PENDING_PAYMENT_AUTH, PAYMENT_ACTION_REQUIRED -> "PENDING_PAYMENT";
            case PENDING_GUIDE_ACCEPTANCE -> "WAITING_FOR_GUIDE";
            case CONFIRMED -> "CONFIRMED";
            case IN_PROGRESS -> "CONFIRMED";
            case COMPLETED -> "COMPLETED";
            default -> "CANCELLED";
        };
    }
}
