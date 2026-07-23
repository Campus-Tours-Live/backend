package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Dashboard stats snapshot for a guide: rating, monthly earnings, and upcoming payout. */
@Schema(
        name = "GuideDashboardStatsResponse",
        description =
                "Snapshot stats for the guide dashboard — aggregate rating, this-month earnings,"
                        + " and upcoming payout from confirmed tours.")
public record GuideDashboardStatsResponse(
        @Schema(
                        description =
                                "Average rating across all published reviews. Null when the guide"
                                        + " has no reviews yet.",
                        example = "4.5",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                BigDecimal avgRating,
        @Schema(
                        description = "Total published review count.",
                        example = "12",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int reviewCount,
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
