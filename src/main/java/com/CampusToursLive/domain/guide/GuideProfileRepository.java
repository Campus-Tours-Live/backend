package com.CampusToursLive.domain.guide;

import java.time.Instant;
import java.util.List;
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
     * its PUBLISHED reviews. Call inside the review-write transaction.
     *
     * <p><b>Locks the row first (CTL-139).</b> Under READ COMMITTED a single {@code UPDATE … SET x
     * = (SELECT … FROM reviews)} that waits on a concurrent writer's row lock re-applies with its
     * original statement snapshot, so its sub-selects miss the other transaction's review and the
     * aggregate undercounts. Taking {@code FOR UPDATE} in a separate statement means the recompute
     * statement starts only after any concurrent review writer has committed, so it sees that
     * review. Lock order is always guide then offering (both review writers recompute in that
     * order), so two writers cannot deadlock on these rows.
     */
    default void recomputeRatingAggregate(UUID guideId) {
        lockForRatingRecompute(guideId);
        updateRatingAggregate(guideId);
    }

    /** Row lock for {@link #recomputeRatingAggregate}; not meant to be called on its own. */
    @Query(
            value = "SELECT 1 FROM guide_profiles WHERE id = :guideId FOR UPDATE",
            nativeQuery = true)
    List<Integer> lockForRatingRecompute(@Param("guideId") UUID guideId);

    /**
     * The recompute statement; call {@link #recomputeRatingAggregate}, which locks the row first.
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
    void updateRatingAggregate(@Param("guideId") UUID guideId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            update GuideProfileEntity g
               set g.status = com.CampusToursLive.domain.guide.GuideStatus.VERIFIED,
                   g.approvedAt = :approvedAt
             where g.id = :id
               and g.status = com.CampusToursLive.domain.guide.GuideStatus.PENDING
            """)
    int approveIfPending(@Param("id") UUID id, @Param("approvedAt") Instant approvedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            update GuideProfileEntity g
               set g.status = com.CampusToursLive.domain.guide.GuideStatus.REJECTED
             where g.id = :id
               and g.status = com.CampusToursLive.domain.guide.GuideStatus.PENDING
            """)
    int rejectIfPending(@Param("id") UUID id);
}
