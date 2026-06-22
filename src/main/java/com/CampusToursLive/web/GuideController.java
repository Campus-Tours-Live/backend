package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import com.CampusToursLive.web.dto.GuideProfileUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guide application / profile endpoints (BFF maps /v1/guide/profile → here). */
@RestController
@RequestMapping("/guide")
public class GuideController {

    private final CurrentUser currentUser;
    private final GuideService guideService;

    public GuideController(CurrentUser currentUser, GuideService guideService) {
        this.currentUser = currentUser;
        this.guideService = guideService;
    }

    @GetMapping("/profile")
    public ApiEnvelope<GuideProfileResponse> getProfile() {
        return ApiEnvelope.of(guideService.getProfile(currentUser.require()));
    }

    @PatchMapping("/profile")
    public ApiEnvelope<GuideProfileResponse> updateProfile(
            @RequestBody GuideProfileUpdateRequest req) {
        return ApiEnvelope.of(guideService.updateProfile(currentUser.require(), req));
    }
}
