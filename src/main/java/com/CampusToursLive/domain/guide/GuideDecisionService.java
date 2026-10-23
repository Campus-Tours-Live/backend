package com.CampusToursLive.domain.guide;

import com.CampusToursLive.domain.audit.AuditWriter;
import com.CampusToursLive.error.ConflictException;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin approve / reject of a PENDING guide application. Role checks live in the web layer. */
@Service
public class GuideDecisionService {

    static final String ACTION_GUIDE_APPLICATION_DECIDED = "GUIDE_APPLICATION_DECIDED";
    static final String TARGET_TYPE_GUIDE_PROFILE = "guide_profile";

    private final GuideProfileRepository guides;
    private final GuideUniversityRepository guideUniversities;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public GuideDecisionService(
            GuideProfileRepository guides,
            GuideUniversityRepository guideUniversities,
            AuditWriter auditWriter,
            Clock clock) {
        this.guides = guides;
        this.guideUniversities = guideUniversities;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional
    public GuideStatus decide(
            UUID adminUserId, UUID guideUserId, GuideDecision decision, String reason) {
        String trimmedReason = reason == null ? "" : reason.strip();
        if (decision == GuideDecision.REJECT && trimmedReason.isEmpty()) {
            throw new ValidationException("A reason is required to reject a guide application");
        }

        GuideProfileEntity profile =
                guides.findByUserId(guideUserId)
                        .orElseThrow(() -> new NotFoundException("Guide not found"));
        UUID profileId = profile.getId();
        Instant now = clock.instant();

        boolean approve = decision == GuideDecision.APPROVE;
        int updated =
                approve
                        ? guides.approveIfPending(profileId, now)
                        : guides.rejectIfPending(profileId);
        if (updated == 0) {
            GuideStatus current =
                    guides.findById(profileId)
                            .map(GuideProfileEntity::getStatus)
                            .orElse(profile.getStatus());
            throw ConflictException.guideApplicationNotPending(current.name());
        }
        if (approve) {
            guideUniversities.verifyPending(profileId, now);
        } else {
            guideUniversities.rejectPending(profileId);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", decision.name());
        if (!trimmedReason.isEmpty()) {
            metadata.put("reason", trimmedReason);
        }
        auditWriter.record(
                ACTION_GUIDE_APPLICATION_DECIDED,
                TARGET_TYPE_GUIDE_PROFILE,
                profileId.toString(),
                adminUserId,
                metadata);

        return approve ? GuideStatus.VERIFIED : GuideStatus.REJECTED;
    }
}
