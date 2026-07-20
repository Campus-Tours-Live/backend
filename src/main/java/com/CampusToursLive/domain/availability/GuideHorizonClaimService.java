package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-guide collaborator for {@link OccurrenceHorizonJob}.
 *
 * <p><b>Why a separate bean.</b> {@code @Transactional} is proxy-based: a method on bean A calling
 * another {@code @Transactional} method on the SAME bean A (self-invocation) bypasses the proxy
 * entirely, so the annotation is silently ignored. If {@link #claimAndRematerialize(UUID)} lived
 * directly on {@link OccurrenceHorizonJob} and the scheduled method called it internally, {@code
 * REQUIRES_NEW} below would never actually apply. Putting it here, on an injected collaborator that
 * the job calls through its Spring-managed reference, lets the AOP proxy intercept every call.
 *
 * <p><b>What one call does, in ONE new transaction:</b> (1) claims the guide via {@code SELECT ...
 * FOR UPDATE SKIP LOCKED} on their {@code guide_profiles} row ({@link GuideHorizonClaimRepository})
 * — if another scheduler instance already holds that row's lock, the claim returns empty and this
 * method is a no-op; (2) if claimed, calls {@link AvailabilityService#rematerialize(UUID)}. {@code
 * REQUIRES_NEW} gives every guide its own transaction, so one guide's failure/rollback can never
 * poison another guide's already-committed work from the same job run — see {@link
 * OccurrenceHorizonJob#rollHorizonForward()} for the per-guide try/catch that relies on this
 * isolation.
 */
@Component
public class GuideHorizonClaimService {

    private final GuideHorizonClaimRepository claims;
    private final AvailabilityService availabilityService;

    @Autowired
    public GuideHorizonClaimService(
            GuideHorizonClaimRepository claims, AvailabilityService availabilityService) {
        this.claims = claims;
        this.availabilityService = availabilityService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claimAndRematerialize(UUID guideId) {
        List<UUID> claimed = claims.claimForUpdateSkipLocked(guideId);
        if (!claimed.isEmpty()) {
            // CTL-54 B5: rematerialize takes the per-guide advisory lock at the start of THIS
            // transaction (it joins under REQUIRED propagation), so this per-guide horizon
            // roll-forward is serialized against a concurrent guide-facing rematerialize for the
            // same guide on the identical key — the two paths never interleave their wholesale
            // delete+insert. (The SKIP LOCKED claim above only de-dupes competing scheduler
            // instances; it does not cover the write-path race, which the advisory lock does.)
            availabilityService.rematerialize(guideId);
        }
    }
}
