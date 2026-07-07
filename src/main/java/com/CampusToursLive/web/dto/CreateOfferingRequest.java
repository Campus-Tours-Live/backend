package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Body for POST /guide/offerings — create a tour offering (starts as DRAFT). */
@Schema(
        name = "CreateOfferingRequest",
        description = "Body to create a guide's tour offering; it starts in DRAFT status.")
public record CreateOfferingRequest(
        @Schema(
                        description = "Public title of the offering.",
                        example = "North Campus highlights",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String title,
        @Schema(
                        description = "Id of the university this offering is at.",
                        example = "u1a2c3d4-0000-4000-8000-000000000003",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String universityId,
        @Schema(
                        description = "Controlled tour topic (see GET /meta/tour-topics).",
                        example = "GENERAL_CAMPUS",
                        allowableValues = {
                            "GENERAL_CAMPUS",
                            "DORM_HOUSING",
                            "DINING_STUDENT_LIFE",
                            "MAJOR_SPECIFIC",
                            "INTERNATIONAL_STUDENT",
                            "PARENT_FOCUSED",
                            "FRESHMAN",
                            "TRANSFER"
                        },
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String topic,
        @Schema(
                        description = "Tour duration in minutes.",
                        example = "60",
                        minimum = "15",
                        maximum = "240",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer durationMin,
        @Schema(
                        description = "Price in integer US cents (e.g. 2500 = $25.00).",
                        example = "4200",
                        minimum = "0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long priceCents,
        @Schema(
                        description = "Longer marketing description of the tour.",
                        example = "A guided walk through the north campus.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String description,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description = "Languages the tour can be given in.",
                                        requiredMode = Schema.RequiredMode.NOT_REQUIRED),
                        schema = @Schema(description = "BCP-47 language tag.", example = "en-US"))
                List<String> languages) {}
