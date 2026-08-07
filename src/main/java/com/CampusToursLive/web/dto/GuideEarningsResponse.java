package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Earnings snapshot for a guide: this-month earnings and upcoming payout from confirmed tours. */
@Schema(
        name = "GuideEarningsResponse",
        description =
                "Guide earnings snapshot — this-month earnings and upcoming payout from confirmed"
                        + " tours.")
public record GuideEarningsResponse(
        @Schema(
                        description =
                                "Sum of guide_amount_cents for tours COMPLETED in the current UTC"
                                        + " calendar month. Zero when no tours were completed this"
                                        + " month.",
                        example = "8400",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long earningsThisMonthCents,
        @Schema(
                        description =
                                "Sum of guide_amount_cents for CONFIRMED or IN_PROGRESS tours —"
                                        + " earnings the guide will receive once these tours"
                                        + " complete. Zero when no active bookings exist.",
                        example = "4200",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long upcomingPayoutCents,
        @Schema(
                        description = "ISO-4217 currency for all cent values.",
                        example = "USD",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String currency) {}
