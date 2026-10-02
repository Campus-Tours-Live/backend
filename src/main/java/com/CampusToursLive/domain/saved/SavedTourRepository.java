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

    /** Offering predicate must stay in sync with TourOfferingRepository.findDiscoverableById. */
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

    /** Returns 1 if inserted, 0 if already saved; ON CONFLICT keeps concurrent saves safe. */
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

    @Query(
            value = "select o " + VISIBLE_SAVED_FROM_WHERE + " order by s.createdAt desc",
            countQuery = "select count(o) " + VISIBLE_SAVED_FROM_WHERE)
    Page<TourOfferingEntity> findVisibleSavedOfferings(
            @Param("userId") UUID userId, Pageable pageable);

    @Query(
            """
            select s.tourOfferingId from SavedTourEntity s
            where s.userId = :userId
            order by s.createdAt desc
            """)
    List<UUID> findTourOfferingIdsByUserId(@Param("userId") UUID userId);
}
