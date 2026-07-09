package com.CampusToursLive.web.dto;

/** Request body for {@code POST /bookings/{id}/cancel}. The reason is optional. */
public record CancelBookingRequest(String reason) {}
