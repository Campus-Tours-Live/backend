package com.CampusToursLive.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Response envelope metadata: { requestId, timestamp } (matches openapi Meta). */
public record Meta(String requestId, String timestamp) {
    public static Meta now() {
        return new Meta(UUID.randomUUID().toString(), Instant.now().toString());
    }
}
