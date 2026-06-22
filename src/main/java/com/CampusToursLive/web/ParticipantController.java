package com.CampusToursLive.web;

import com.CampusToursLive.domain.participant.ParticipantService;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.ParticipantProfileResponse;
import com.CampusToursLive.web.dto.ParticipantProfileUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Participant profile endpoints (BFF maps /v1/participant/profile → here). */
@RestController
@RequestMapping("/participant")
public class ParticipantController {

    private final CurrentUser currentUser;
    private final ParticipantService participantService;

    public ParticipantController(CurrentUser currentUser, ParticipantService participantService) {
        this.currentUser = currentUser;
        this.participantService = participantService;
    }

    @GetMapping("/profile")
    public ApiEnvelope<ParticipantProfileResponse> getProfile() {
        return ApiEnvelope.of(participantService.getProfile(currentUser.require()));
    }

    @PatchMapping("/profile")
    public ApiEnvelope<ParticipantProfileResponse> updateProfile(
            @RequestBody ParticipantProfileUpdateRequest req) {
        return ApiEnvelope.of(participantService.updateProfile(currentUser.require(), req));
    }
}
