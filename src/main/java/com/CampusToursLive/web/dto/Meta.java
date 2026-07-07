package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Response envelope metadata: { requestId, timestamp } (matches openapi Meta). */
@Schema(name = "Meta", description = "Per-response metadata attached to every success envelope.")
public record Meta(
        @Schema(
                        description = "Unique id for this response, for correlation in logs.",
                        example = "3f1c9a2e-7d40-4b2a-9c11-5b8e0a2f6d10",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String requestId,
        @Schema(
                        description = "ISO-8601 UTC instant the response was produced.",
                        example = "2026-07-07T12:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String timestamp) {
    public static Meta now() {
        return new Meta(UUID.randomUUID().toString(), Instant.now().toString());
    }
}
