package com.CampusToursLive.domain.saved;

import com.CampusToursLive.domain.tour.TourOfferingEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedTourRepository extends JpaRepository<SavedTourEntity, UUID> {

    /**
     * FROM + WHERE shared by the visible-saves page and its count query. The offering predicate
     * mirrors {@code TourOfferingRepository.findDiscoverableById}, so a save whose offering is no
     * longer marketplace-visible stays in the table but drops out of both.
     */
    String VISIBLE_SAVED_FROM_WHERE =
            """
            from SavedTourEntity s
            inner join TourOfferingEntity o on o.id = s.tourOfferingId
            inner join GuideProfileEntity g on g.id = o.guideId
            inner join UniversityEntity u on u.id = o.universityId
            where s.userId = :userId
              and o.status = com.CampusToursLive.domain.tour.TourStatus.ACTIVE
              and g.status = com.CampusToursLive.domain.guide.GuideStatus.VERIFIED
              and u.status = com.CampusToursLive.domain.university.UniversityStatus.ACTIVE
            """;

    /**
     * Inserts the save unless the (user, offering) pair already exists. Returns 1 when a row was
     * written, 0 when it was already saved. {@code ON CONFLICT} rather than check-then-insert: two
     * concurrent saves would otherwise trip the unique constraint and fail the loser.
     */
    @Modifying
    @Query(
            value =
                    """
                    insert into saved_tours (id, user_id, tour_offering_id)
                    values (:id, :userId, :tourOfferingId)
                    on conflict (user_id, tour_offering_id) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("tourOfferingId") UUID tourOfferingId);

    @Modifying
    @Query(
            """
            delete from SavedTourEntity s
            where s.userId = :userId and s.tourOfferingId = :tourOfferingId
            """)
    int deleteByUserIdAndTourOfferingId(
            @Param("userId") UUID userId, @Param("tourOfferingId") UUID tourOfferingId);

    /** The caller's still-visible saved offerings, most recently saved first. */
    @Query(
            value = "select o " + VISIBLE_SAVED_FROM_WHERE + " order by s.createdAt desc",
            countQuery = "select count(o) " + VISIBLE_SAVED_FROM_WHERE)
    Page<TourOfferingEntity> findVisibleSavedOfferings(
            @Param("userId") UUID userId, Pageable pageable);

    /** Offering ids only — for lighting marketplace hearts without full summaries. */
    @Query(
            """
            select s.tourOfferingId from SavedTourEntity s
            where s.userId = :userId
            order by s.createdAt desc
            """)
    List<UUID> findTourOfferingIdsByUserId(@Param("userId") UUID userId);
}
