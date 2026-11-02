package com.CampusToursLive.web.doc;

import com.CampusToursLive.web.dto.GuidePendingActionsResponse;
import com.CampusToursLive.web.dto.Meta;
import io.swagger.v3.oas.annotations.media.Schema;

/** Typed documentation of the pending-actions success envelope; runtime uses ApiEnvelope. */
@Schema(description = "Guide pending-action counts and response metadata.")
public record GuidePendingActionsEnvelope(
        @Schema(
                        description = "Outstanding guide booking action counts.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                GuidePendingActionsResponse data,
        @Schema(
                        description = "Response correlation and timestamp metadata.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Meta meta) {}
