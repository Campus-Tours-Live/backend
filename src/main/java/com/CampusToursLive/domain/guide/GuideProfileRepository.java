package com.CampusToursLive.domain.guide;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuideProfileRepository extends JpaRepository<GuideProfileEntity, UUID> {
    Optional<GuideProfileEntity> findByUserId(UUID userId);

    /**
     * Recompute {@code avg_rating} / {@code review_count} for one guide from the source of truth —
     * its PUBLISHED reviews. Idempotent and concurrency-safe (no read-modify-write): two reviews
     * publishing at once each recompute the same aggregate rather than racing on a cached count.
     * Call inside the review-write transaction.
     */
    @Modifying
    @Query(
            value =
                    "UPDATE guide_profiles SET"
                            + " avg_rating = COALESCE((SELECT AVG(overall_rating) FROM reviews WHERE"
                            + " guide_id = :guideId AND status = 'PUBLISHED'), 0),"
                            + " review_count = (SELECT COUNT(*) FROM reviews WHERE guide_id = :guideId"
                            + " AND status = 'PUBLISHED')"
                            + " WHERE id = :guideId",
            nativeQuery = true)
    void recomputeRatingAggregate(@Param("guideId") UUID guideId);
}
