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

/** CTL-50 propose endpoint. Non-owners → 404 in the service. */
@RestController
@RequestMapping("/bookings")
@Tag(name = "Reschedule", description = "Propose moving a CONFIRMED booking to a new time.")
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
                    "PENDING_COUNTERPARTY proposal for a CONFIRMED booking owned by the caller."
                            + " Validates notice/advance, availability, and slot conflicts.")
    @ApiResponse(
            responseCode = "200",
            description = "Pending proposal.",
            content =
                    @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ApiExamples.RESCHEDULE_PROPOSAL)))
    @ApiResponse(
            responseCode = "409",
            description = "State conflict.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_409)))
    @ApiResponse(
            responseCode = "422",
            description = "Validation failed.",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Problem.class),
                            examples = @ExampleObject(value = ApiExamples.PROBLEM_422)))
    @PostMapping("/{bookingId}/reschedule-proposals")
    public ApiEnvelope<RescheduleProposalResponse> propose(
            @PathVariable UUID bookingId, @RequestBody CreateRescheduleProposalRequest req) {
        return ApiEnvelope.of(rescheduleService.propose(currentUser.require(), bookingId, req));
    }
}
