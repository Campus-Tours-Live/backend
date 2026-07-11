package com.CampusToursLive.web;

import com.CampusToursLive.domain.booking.SlotGenerationService;
import com.CampusToursLive.domain.user.UserRole;
import com.CampusToursLive.security.CurrentUser;
import com.CampusToursLive.web.dto.ApiEnvelope;
import com.CampusToursLive.web.dto.SlotResponse;
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
    @GetMapping("/{id}/slots")
    public ApiEnvelope<List<SlotResponse>> getSlots(
            @PathVariable UUID id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        currentUser.requireRole(UserRole.PARTICIPANT);
        return ApiEnvelope.of(slots.getBookableSlots(id, from, to));
    }
}
