package com.CampusToursLive.domain.participant;

/**
 * Matches the PostgreSQL enum type {@code participant_type}; the DB is authoritative for storage.
 */
public enum ParticipantType {
    HIGH_SCHOOL,
    PROSPECTIVE,
    TRANSFER,
    INTERNATIONAL,
    PARENT,
    OTHER
}
