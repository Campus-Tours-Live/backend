package com.CampusToursLive.domain.guide;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuideUniversityRepository extends JpaRepository<GuideUniversityEntity, UUID> {
    List<GuideUniversityEntity> findByGuideProfileId(UUID guideProfileId);

    List<GuideUniversityEntity> findByGuideProfileIdIn(Collection<UUID> guideProfileIds);

    /**
     * Bumps version so a concurrent profile edit fails its optimistic lock instead of undoing this.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            update GuideUniversityEntity gu
               set gu.verificationStatus =
                       com.CampusToursLive.domain.guide.GuideVerificationStatus.VERIFIED,
                   gu.verifiedAt = :verifiedAt,
                   gu.version = gu.version + 1
             where gu.guideProfileId = :guideProfileId
               and gu.verificationStatus =
                       com.CampusToursLive.domain.guide.GuideVerificationStatus.PENDING
            """)
    int verifyPending(
            @Param("guideProfileId") UUID guideProfileId, @Param("verifiedAt") Instant verifiedAt);

    /**
     * Bumps version so a concurrent profile edit fails its optimistic lock instead of undoing this.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            update GuideUniversityEntity gu
               set gu.verificationStatus =
                       com.CampusToursLive.domain.guide.GuideVerificationStatus.REJECTED,
                   gu.version = gu.version + 1
             where gu.guideProfileId = :guideProfileId
               and gu.verificationStatus =
                       com.CampusToursLive.domain.guide.GuideVerificationStatus.PENDING
            """)
    int rejectPending(@Param("guideProfileId") UUID guideProfileId);
}
