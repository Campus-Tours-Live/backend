package com.CampusToursLive.web;

import com.CampusToursLive.domain.tour.TourOfferingService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.CreateOfferingRequest;
import com.CampusToursLive.web.dto.TourOfferingResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guide supply-side: tour offerings (BFF maps /v1/guide/offerings → here). Every action requires
 * the GUIDE role (authorization reads user_roles, never the active role). Going live additionally
 * requires an APPROVED application — enforced in the service.
 */
@RestController
@RequestMapping("/guide/offerings")
public class GuideOfferingController {

    private final CurrentUser currentUser;
    private final TourOfferingService offerings;

    public GuideOfferingController(CurrentUser currentUser, TourOfferingService offerings) {
        this.currentUser = currentUser;
        this.offerings = offerings;
    }

    @GetMapping
    public ApiEnvelope<List<TourOfferingResponse>> list() {
        return ApiEnvelope.of(offerings.listOwn(currentUser.requireRole(UserRole.GUIDE)));
    }

    @PostMapping
    public ApiEnvelope<TourOfferingResponse> create(@RequestBody CreateOfferingRequest req) {
        return ApiEnvelope.of(offerings.create(currentUser.requireRole(UserRole.GUIDE), req));
    }

    @PostMapping("/{id}/activate")
    public ApiEnvelope<TourOfferingResponse> activate(@PathVariable UUID id) {
        return ApiEnvelope.of(offerings.activate(currentUser.requireRole(UserRole.GUIDE), id));
    }
}
