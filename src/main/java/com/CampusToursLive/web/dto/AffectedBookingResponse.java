package com.CampusToursLive.web.dto;

/**
 * A booking that an availability edit left uncovered by any current materialized occurrence (CTL-54
 * Task 7 — "(A) allow + notify"). By the time this is returned the write has ALREADY succeeded and
 * the booking is UNCHANGED: still {@code CONFIRMED}, same {@code scheduledStartAt} / {@code
 * scheduledEndAt}. This is purely advisory so the guide-facing UI can warn the guide — CONFIRMED
 * bookings are immutable and are never retroactively cancelled or mutated by an availability edit.
 */
public record AffectedBookingResponse(
        String bookingId,
        String bookingNumber,
        String scheduledStartAt,
        String scheduledEndAt,
        String status) {}
