package com.CampusToursLive.domain.booking;

/** Matches the PostgreSQL enum type {@code booking_actor} (V1__schema.sql). */
public enum BookingActor {
    PARTICIPANT,
    GUIDE,
    SYSTEM,
    ADMIN,
    STRIPE_WEBHOOK,
    DAILY_WEBHOOK
}
