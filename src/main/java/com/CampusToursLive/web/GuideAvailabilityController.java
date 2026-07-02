package com.CampusToursLive.web;

import com.CampusToursLive.domain.availability.GuideAvailabilityService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.AvailabilityExceptionResponse;
import com.CampusToursLive.web.dto.AvailabilityRuleResponse;
import com.CampusToursLive.web.dto.AvailabilitySummaryResponse;
import com.CampusToursLive.web.dto.BookingSettingsResponse;
import com.CampusToursLive.web.dto.CreateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.CreateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityExceptionRequest;
import com.CampusToursLive.web.dto.UpdateAvailabilityRuleRequest;
import com.CampusToursLive.web.dto.UpdateBookingSettingsRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guide availability management (BFF maps /v1/guide/availability → here). Recurring weekly rules,
 * date-specific exceptions, and booking policy settings.
 */
@RestController
@RequestMapping("/guide/availability")
public class GuideAvailabilityController {

    private final CurrentUser currentUser;
    private final GuideAvailabilityService availability;

    public GuideAvailabilityController(
            CurrentUser currentUser, GuideAvailabilityService availability) {
        this.currentUser = currentUser;
        this.availability = availability;
    }

    @GetMapping
    public ApiEnvelope<AvailabilitySummaryResponse> getSummary() {
        return ApiEnvelope.of(availability.getSummary(currentUser.requireRole(UserRole.GUIDE)));
    }

    @PostMapping("/rules")
    public ApiEnvelope<AvailabilityRuleResponse> createRule(
            @RequestBody CreateAvailabilityRuleRequest req) {
        return ApiEnvelope.of(
                availability.createRule(currentUser.requireRole(UserRole.GUIDE), req));
    }

    @PatchMapping("/rules/{ruleId}")
    public ApiEnvelope<AvailabilityRuleResponse> updateRule(
            @PathVariable UUID ruleId, @RequestBody UpdateAvailabilityRuleRequest req) {
        return ApiEnvelope.of(
                availability.updateRule(currentUser.requireRole(UserRole.GUIDE), ruleId, req));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ApiEnvelope<Void> deleteRule(@PathVariable UUID ruleId) {
        availability.deleteRule(currentUser.requireRole(UserRole.GUIDE), ruleId);
        return ApiEnvelope.of(null);
    }

    @PostMapping("/exceptions")
    public ApiEnvelope<AvailabilityExceptionResponse> createException(
            @RequestBody CreateAvailabilityExceptionRequest req) {
        return ApiEnvelope.of(
                availability.createException(currentUser.requireRole(UserRole.GUIDE), req));
    }

    @PatchMapping("/exceptions/{exceptionId}")
    public ApiEnvelope<AvailabilityExceptionResponse> updateException(
            @PathVariable UUID exceptionId, @RequestBody UpdateAvailabilityExceptionRequest req) {
        return ApiEnvelope.of(
                availability.updateException(
                        currentUser.requireRole(UserRole.GUIDE), exceptionId, req));
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    public ApiEnvelope<Void> deleteException(@PathVariable UUID exceptionId) {
        availability.deleteException(currentUser.requireRole(UserRole.GUIDE), exceptionId);
        return ApiEnvelope.of(null);
    }

    @PatchMapping("/booking-settings")
    public ApiEnvelope<BookingSettingsResponse> updateBookingSettings(
            @RequestBody UpdateBookingSettingsRequest req) {
        return ApiEnvelope.of(
                availability.updateBookingSettings(currentUser.requireRole(UserRole.GUIDE), req));
    }
}
