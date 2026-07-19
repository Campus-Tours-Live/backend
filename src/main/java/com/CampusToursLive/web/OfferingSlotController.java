package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.SlotGenerationService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.SlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Participant-facing bookable-slots read (CTL-54 Task 8) — the primary consumer of the materialized
 * availability occurrences. GENERIC route (no {@code /guide} prefix); role comes from the security
 * context, never the URL (mirrors {@code AvailabilityController} / {@code BookingController}, the
 * CTL-43 convention). Consumed via the BFF (CTL-56), not directly by clients.
 */
@RestController
@RequestMapping("/offerings")
@Tag(
        name = "Bookable slots",
        description =
                "The concrete, bookable slots for a tour offering, derived from the guide's"
                        + " materialized availability occurrences. Every operation requires the"
                        + " PARTICIPANT role.")
public class OfferingSlotController {

    private final CurrentUser currentUser;
    private final SlotGenerationService slots;

    public OfferingSlotController(CurrentUser currentUser, SlotGenerationService slots) {
        this.currentUser = currentUser;
        this.slots = slots;
    }

    /**
     * The concrete bookable slots for one offering: each net-available occurrence sliced into
     * offering-duration-length slots, with times already taken by an existing booking (its buffered
     * reserved interval) and times outside the guide's notice/max-advance window removed. {@code
     * from}/{@code to} (ISO {@code yyyy-MM-dd}) optionally narrow which occurrences are considered;
     * omitted, every materialized occurrence is considered.
     */
    @Operation(
            summary = "List bookable slots for an offering",
            description =
                    "Returns the concrete bookable slots for one tour offering: each net-available"
                            + " occurrence sliced into offering-duration-length slots, with any time"
                            + " already taken by an existing booking (its buffered reserved"
                            + " interval) and any time outside the guide's notice/max-advance window"
                            + " removed. from/to (ISO yyyy-MM-dd) optionally narrow which"
                            + " occurrences are considered; omitted, every materialized occurrence"
                            + " is considered. A discoverable offering with no materialized"
                            + " occurrences yet returns an empty list, not a 404.")
    @ApiResponse(
            responseCode = "200",
            description = "The offering's bookable slots (empty array when none are bookable).",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.SLOT_LIST)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "403",
            description = "Caller does not hold the PARTICIPANT role.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_403)))
    @ApiResponse(
            responseCode = "404",
            description =
                    "Tour not found (unknown offering, or not currently discoverable — inactive"
                            + " offering/guide/university).",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "422",
            description = "from/to malformed (expected ISO yyyy-MM-dd), or to not after from.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @GetMapping("/{id}/slots")
    public ApiEnvelope<List<SlotResponse>> getSlots(
            @Parameter(description = "Id (UUID) of the tour offering.") @PathVariable UUID id,
            @Parameter(description = "ISO yyyy-MM-dd; inclusive lower bound of the window.")
                    @RequestParam(required = false)
                    String from,
            @Parameter(description = "ISO yyyy-MM-dd; exclusive upper bound of the window.")
                    @RequestParam(required = false)
                    String to) {
        currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(slots.getBookableSlots(id, from, to));
    }
}
