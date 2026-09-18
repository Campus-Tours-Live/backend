package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A guide's public marketplace profile (GET /guides/{guideId}) — what an anonymous visitor sees
 * before booking. Only VERIFIED guides are exposed, and only public fields: no school email, no
 * verification status, no internal ids beyond the guide id.
 */
@Schema(
        name = "PublicGuideProfileResponse",
        description = "A verified guide's public marketplace profile.")
public record PublicGuideProfileResponse(
        @Schema(
                        description = "Guide id (UUID).",
                        example = "g1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String guideId,
        @Schema(
                        description = "Guide's display name.",
                        example = "Maya Chen",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String displayName,
        @Schema(
                        description = "Guide's self-written bio, or null when none.",
                        example =
                                "Third-year marine biology major who loves the waterfront campus.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String bio,
        @Schema(
                        description = "BCP-47 language tags the guide speaks.",
                        example = "[\"en-US\",\"zh\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> spokenLanguages,
        @Schema(
                        description = "Tour-topic codes the guide specializes in.",
                        example = "[\"GENERAL_CAMPUS\",\"STEM\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<String> tourTopics) {}
