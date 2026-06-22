package com.CampusToursLive.web;

import com.CampusToursLive.domain.guide.GuideService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.GuideDecisionRequest;
import com.CampusToursLive.web.dto.GuideProfileResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff/admin endpoints (BFF maps /v1/admin/* → here). Requires the ADMIN role — an
 * externally-granted staff role; authorization reads user_roles, independent of the caller's active
 * consumer role.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final CurrentUser currentUser;
    private final GuideService guideService;

    public AdminController(CurrentUser currentUser, GuideService guideService) {
        this.currentUser = currentUser;
        this.guideService = guideService;
    }

    /** Approve or reject a guide application → sets guide_profile.application_status. */
    @PostMapping("/guides/{userId}/decision")
    public ApiEnvelope<GuideProfileResponse> decide(
            @PathVariable UUID userId, @RequestBody GuideDecisionRequest req) {
        currentUser.requireRole(UserRole.ADMIN);
        return ApiEnvelope.of(guideService.reviewApplication(userId, req.decision()));
    }
}
