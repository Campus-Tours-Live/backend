package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 7807 {@code application/problem+json} error body — the single error shape Core returns and
 * the BFF forwards. Mirrors Spring's {@link org.springframework.http.ProblemDetail} at runtime;
 * this annotated record exists only so the generated OpenAPI spec documents the error contract
 * (Contract B) with described, exampled fields instead of the framework's undocumented schema.
 */
@Schema(
        name = "Problem",
        description = "RFC 7807 problem+json error body returned for every non-2xx response.")
public record Problem(
        @Schema(
                        description = "A short, human-readable summary of the problem.",
                        example = "Guide profile not found",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @Schema(
                        description = "The HTTP status code, repeated in the body.",
                        example = "404",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int status,
        @Schema(
                        description = "A human-readable explanation specific to this occurrence.",
                        example = "No guide profile exists for the given user id.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String detail,
        @Schema(
                        description =
                                "A URI reference identifying the problem type; defaults to"
                                        + " \"about:blank\".",
                        example = "about:blank",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String type,
        @Schema(
                        description = "A URI reference identifying the specific occurrence.",
                        example = "/guide/profile",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String instance) {}
