package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Counts of outstanding guide booking actions for the dashboard / nav badge. */
@Schema(
        name = "GuidePendingActionsResponse",
        description = "Counts of outstanding actions for the guide's dashboard.")
public record GuidePendingActionsResponse(
        @Schema(
                        description = "Bookings awaiting this guide's accept/decline.",
                        example = "2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                long pendingAcceptance) {}
