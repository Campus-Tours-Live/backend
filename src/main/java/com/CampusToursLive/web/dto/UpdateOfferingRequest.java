package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Partial body for PATCH /guide/offerings/{id}. Only supplied fields change. */
@Schema(
        name = "UpdateOfferingRequest",
        description =
                "Editable fields of a DRAFT or PAUSED guide offering. At least one field is required; "
                        + "ACTIVE offerings must be paused before editing.")
public record UpdateOfferingRequest(
        @Schema(description = "New public title.", example = "North Campus highlights")
                String title,
        @Schema(
                        description = "Verified university id for this offering.",
                        example = "u1a2c3d4-0000-4000-8000-000000000003")
                String universityId,
        @Schema(description = "Controlled tour topic.", example = "GENERAL_CAMPUS") String topic,
        @Schema(description = "Tour duration in minutes.", example = "60") Integer durationMin,
        @Schema(description = "Price in integer US cents.", example = "4200") Long priceCents,
        @Schema(description = "Longer marketing description. An empty value clears it.")
                String description,
        @ArraySchema(schema = @Schema(description = "BCP-47 language tag.", example = "en-US"))
                List<String> languages,
        @ArraySchema(schema = @Schema(description = "Feature enum name.", example = "Q_AND_A"))
                List<String> features) {

    /**
     * A PATCH with no fields is ambiguous and should be rejected rather than silently succeeding.
     */
    public boolean isEmpty() {
        return title == null
                && universityId == null
                && topic == null
                && durationMin == null
                && priceCents == null
                && description == null
                && languages == null
                && features == null;
    }
}
