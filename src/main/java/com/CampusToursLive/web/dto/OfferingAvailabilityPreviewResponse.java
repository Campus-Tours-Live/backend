package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
        description =
                "Public availability preview for a tour offering. Times are absolute UTC instants;"
                        + " clients render them in the viewer's local timezone.")
public record OfferingAvailabilityPreviewResponse(
        @Schema(description = "True when at least one currently bookable slot exists.")
                boolean hasAvailability,
        @Schema(description = "Number of currently bookable slots in the requested preview window.")
                int totalSlots,
        @Schema(description = "Earliest currently bookable slot start, or null when none exist.")
                Instant nextStartAt,
        @Schema(description = "Earliest currently bookable slot end, or null when none exist.")
                Instant nextEndAt,
        @Schema(description = "First few bookable slots for UI previews.")
                List<SlotResponse> sampleSlots) {}
