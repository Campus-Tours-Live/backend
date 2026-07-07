package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Standard success envelope: { data, meta }. */
@Schema(
        name = "ApiEnvelope",
        description = "Standard success envelope wrapping every 2xx payload as { data, meta }.")
public record ApiEnvelope<T>(
        @Schema(
                        description =
                                "The endpoint payload — an object, an array, or null depending on"
                                        + " the operation.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                T data,
        @Schema(
                        description = "Response metadata (request id + server timestamp).",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Meta meta) {
    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, Meta.now());
    }
}
