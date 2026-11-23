package com.CampusToursLive.domain.reschedule;

/** Matches the PostgreSQL enum type {@code reschedule_status} (V1__schema.sql). */
public enum RescheduleStatus {
    PENDING_COUNTERPARTY,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    CANCELLED
}
