package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Every university in one state, sorted by name — the state page behind a click on the map or on a
 * row of the browse-by-state directory.
 *
 * <p>Not paginated. The largest state is California at about 150 schools, which is a single small
 * response; a page cursor would add a round trip and a state machine to a list that already fits.
 */
@Schema(name = "StateUniversities", description = "Every university in one state, sorted by name.")
public record StateUniversitiesResponse(
        @Schema(
                        description = "The USPS code these universities are in.",
                        example = "CA",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String state,
        @Schema(
                        description = "The state's universities, sorted by name.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<University> universities,
        @Schema(
                        description =
                                "How many are listed. Always equals universities.length — it is"
                                        + " sent so a caller can show a heading without walking the"
                                        + " array, not as a second, separately-derived figure.",
                        example = "148",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int total) {

    /** One school as the directory lists it. */
    @Schema(name = "DirectoryUniversity", description = "One university in the directory.")
    public record University(
            @Schema(
                            description =
                                    "The College Scorecard institution id — the same identifier"
                                            + " guide onboarding stores.",
                            example = "243744",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Schema(
                            description = "Official institution name.",
                            example = "Stanford University",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String name,
            @Schema(
                            description = "City. May be blank when the directory has none.",
                            example = "Stanford",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String city) {}
}
