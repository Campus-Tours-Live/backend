package com.CampusToursLive.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Request body for {@code POST /availability/rules/replace} (CTL-54 v2.1 remediation B2): an ATOMIC
 * replace of ONE weekday's recurring availability rules. The guide's existing ACTIVE rules for
 * {@code dayOfWeek} are dropped and replaced by exactly {@code windows} in one transaction; other
 * weekdays' rules are left untouched. An EMPTY {@code windows} list is allowed and means "clear
 * this weekday's rules".
 *
 * <p>The weekly counterpart to {@link OverrideReplaceRequest} (which replaces a single date's
 * exceptions): this carries a {@code dayOfWeek} (0=Sunday .. 6=Saturday) plus a {@code windows}
 * list — there is deliberately no time-zone, effective-range, or {@code kind} field, because every
 * inserted rule takes the guide's settings timezone (the read-only-tz invariant), an open-ended
 * effective range starting today, and is active. Bound as a JSON body; a POST is the clean way to
 * carry the {@code windows} array.
 */
@Schema(
        name = "RulesReplaceRequest",
        description =
                "An atomic replace of one weekday's recurring availability rules: the guide's"
                        + " existing ACTIVE rules for the day of week are replaced by exactly these"
                        + " windows in one transaction (empty windows clears that weekday's rules);"
                        + " other weekdays are untouched.")
public record RulesReplaceRequest(
        @Schema(
                        description =
                                "Day of week whose rules are being replaced: 0 (Sunday) - 6"
                                        + " (Saturday).",
                        example = "1",
                        minimum = "0",
                        maximum = "6",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Integer dayOfWeek,
        @ArraySchema(
                        arraySchema =
                                @Schema(
                                        description =
                                                "The time windows this weekday should have after the"
                                                        + " replace. MAY be empty — an empty list"
                                                        + " clears this weekday's rules. Overlapping"
                                                        + " or touching windows are accepted and"
                                                        + " merged (coalesced) into disjoint rules,"
                                                        + " not rejected.",
                                        requiredMode = Schema.RequiredMode.REQUIRED))
                List<Window> windows) {

    /**
     * One time window (a wall-clock start plus a length in minutes) within a {@link
     * RulesReplaceRequest}. Same span shape as an availability rule (start + duration).
     */
    @Schema(
            name = "RulesReplaceWindow",
            description =
                    "One proposed time window (wall-clock start + length in minutes) within a weekly"
                            + " rules replace.")
    public record Window(
            @Schema(
                            description = "Wall-clock start time in the guide's settings timezone.",
                            example = "09:00",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String startLocal,
            @Schema(
                            description = "Window length in minutes.",
                            example = "60",
                            minimum = "1",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    Integer windowMin) {}
}
