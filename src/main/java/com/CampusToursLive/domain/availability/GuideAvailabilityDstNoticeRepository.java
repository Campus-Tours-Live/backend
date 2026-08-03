package com.CampusToursLive.domain.availability;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * DST-notice cache access. Like the occurrence repository, this is a derived cache: {@link
 * #deleteByGuideId(UUID)} is the "replace" half of rematerialize, never a row-by-row edit.
 */
public interface GuideAvailabilityDstNoticeRepository
        extends JpaRepository<GuideAvailabilityDstNoticeEntity, UUID> {

    /** A guide's DST-adjusted-day notices — the read model (Task 5b) surfaces these. */
    List<GuideAvailabilityDstNoticeEntity> findByGuideId(UUID guideId);

    /** Wholesale delete of a guide's notices — the "replace" half of rematerialize. */
    long deleteByGuideId(UUID guideId);
}
