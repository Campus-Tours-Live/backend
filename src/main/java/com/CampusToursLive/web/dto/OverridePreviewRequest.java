package com.CampusToursLive.web.dto;

/**
 * Query params for {@code GET /availability/preview} (CTL-54 v2.1 Task 4): a proposed date-specific
 * override that has NOT been saved yet. Mirrors the multi-day shape of {@link
 * AvailabilityExceptionRequest} ({@code kind}/{@code startLocal}/{@code windowMin} plus an
 * inclusive date range), except {@code dateFrom}/{@code dateTo} are ALWAYS both required here —
 * there is no single-date shorthand, since this is a read-only {@code GET}.
 *
 * <p>No springdoc yet (CTL-54 v2.1 Task 5 adds it).
 */
public record OverridePreviewRequest(
        String dateFrom, String dateTo, String kind, String startLocal, Integer windowMin) {}
