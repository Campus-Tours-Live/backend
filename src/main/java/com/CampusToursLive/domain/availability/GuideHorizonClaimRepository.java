package com.CampusToursLive.domain.availability;

import com.CampusToursLive.domain.guide.GuideProfileEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Dedicated repository for {@link OccurrenceHorizonJob}'s (CTL-54 Task 4) per-guide claim: a {@code
 * SELECT ... FOR UPDATE SKIP LOCKED} against the guide's OWN {@code guide_profiles} row, used
 * purely as a per-guide lock target so two scheduler instances never both materialize the same
 * guide concurrently. No CRUD on guide profiles happens through this repository — it exists only
 * for {@link #claimForUpdateSkipLocked(UUID)}.
 */
public interface GuideHorizonClaimRepository extends JpaRepository<GuideProfileEntity, UUID> {

    /**
     * Attempts to lock the guide's {@code guide_profiles} row without blocking. Returns the id (a
     * singleton list) if the row was locked by THIS transaction; returns an empty list if the row
     * is already locked by another in-flight transaction (another scheduler instance racing the
     * same guide) — {@code SKIP LOCKED} means this call never waits and never throws for that case.
     * The lock is held only for the lifetime of the caller's transaction (see {@link
     * GuideHorizonClaimService#claimAndRematerialize(UUID)}, {@code REQUIRES_NEW}).
     */
    @Query(
            value = "select id from guide_profiles where id = :guideId for update skip locked",
            nativeQuery = true)
    List<UUID> claimForUpdateSkipLocked(@Param("guideId") UUID guideId);
}
