package com.CampusToursLive.web;

import com.CampusToursLive.domain.reschedule.RescheduleService;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.doc.ApiExamples;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.CreateRescheduleProposalRequest;
import com.CampusToursLive.web.dto.Problem;
import com.CampusToursLive.web.dto.RescheduleProposalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reschedule propose endpoint (CTL-50, Core: {@code /bookings/{id}/reschedule-proposals}). Either
 * party of the booking (participant or guide) may propose; ownership is enforced in the service
 * (non-owners get 404). Accept / decline / cancel land in CTL-51.
 */
@RestController
@RequestMapping("/bookings")
@Tag(
        name = "Reschedule",
        description =
                "Propose moving a CONFIRMED booking to a new time without cancelling it. Either"
                        + " the booking's participant or its guide may propose; the counterparty"
                        + " accepts or declines in a follow-up ticket (CTL-51).")
public class RescheduleController {

    private final CurrentUser currentUser;
    private final RescheduleService rescheduleService;

    public RescheduleController(CurrentUser currentUser, RescheduleService rescheduleService) {
        this.currentUser = currentUser;
        this.rescheduleService = rescheduleService;
    }

    @Operation(
            summary = "Propose a reschedule",
            description =
                    "Creates a PENDING_COUNTERPARTY proposal to move a CONFIRMED booking owned by"
                            + " the caller (participant or guide) to a new start time. The proposed"
                            + " end is derived from the booking's duration. The proposed start must"
                            + " satisfy the guide's notice/advance window, sit inside the guide's"
                            + " availability, and not conflict with other slot-holding bookings."
                            + " At most one active proposal may exist per booking. feeCents and"
                            + " priceDiffCents are 0 in the MVP.")
    @ApiResponse(
            responseCode = "200",
            description = "The created (or idempotently replayed) pending proposal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.RESCHEDULE_PROPOSAL)))
    @ApiResponse(
            responseCode = "401",
            description = "No valid principal / account not provisioned.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_401)))
    @ApiResponse(
            responseCode = "404",
            description = "Booking not found, or not owned by the caller.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_404)))
    @ApiResponse(
            responseCode = "409",
            description =
                    "State conflict — booking not CONFIRMED, proposed slot unavailable, or a"
                            + " proposal is already pending.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @ApiResponse(
            responseCode = "422",
            description =
                    "Validation failed — missing/invalid proposedStartAt, notice/advance window"
                            + " violated, or reason too long.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{bookingId}/reschedule-proposals")
    public ApiEnvelope<RescheduleProposalResponse> propose(
            @PathVariable UUID bookingId, @RequestBody CreateRescheduleProposalRequest req) {
        var user = currentUser.require();
        return ApiEnvelope.of(rescheduleService.propose(user, bookingId, req));
    }
}
