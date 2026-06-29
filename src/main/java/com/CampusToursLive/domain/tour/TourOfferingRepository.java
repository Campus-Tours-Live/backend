package com.CampusToursLive.domain.tour;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourOfferingRepository extends JpaRepository<TourOfferingEntity, UUID> {

    List<TourOfferingEntity> findByGuideId(UUID guideId);

    boolean existsByGuideIdAndSlug(UUID guideId, String slug);

    Optional<TourOfferingEntity> findByIdAndGuideId(UUID id, UUID guideId);

    /**
     * Active offerings from approved guides at active universities — the public marketplace
     * catalog. {@code universityId} and {@code topic} are optional filters; {@code q} matches
     * title, description, or university name (case-insensitive).
     */
    @Query(
            """
            select o from TourOfferingEntity o
            inner join GuideProfileEntity g on g.id = o.guideId
            inner join UniversityEntity u on u.id = o.universityId
            where o.status = com.CampusToursLive.domain.tour.TourStatus.ACTIVE
              and g.applicationStatus = com.CampusToursLive.domain.guide.GuideApplicationStatus.APPROVED
              and u.status = com.CampusToursLive.domain.university.UniversityStatus.ACTIVE
              and o.universityId = coalesce(:universityId, o.universityId)
              and o.topic = coalesce(:topic, o.topic)
              and (
                :q = ''
                or lower(o.title) like lower(concat('%', :q, '%'))
                or lower(o.description) like lower(concat('%', :q, '%'))
                or lower(u.name) like lower(concat('%', :q, '%'))
                or lower(coalesce(u.shortName, '')) like lower(concat('%', :q, '%'))
              )
            """)
    List<TourOfferingEntity> findDiscoverable(
            @Param("universityId") UUID universityId,
            @Param("topic") TourTopic topic,
            @Param("q") String q,
            Pageable pageable);

    @Query(
            """
            select o from TourOfferingEntity o
            inner join GuideProfileEntity g on g.id = o.guideId
            inner join UniversityEntity u on u.id = o.universityId
            where o.id = :id
              and o.status = com.CampusToursLive.domain.tour.TourStatus.ACTIVE
              and g.applicationStatus = com.CampusToursLive.domain.guide.GuideApplicationStatus.APPROVED
              and u.status = com.CampusToursLive.domain.university.UniversityStatus.ACTIVE
            """)
    Optional<TourOfferingEntity> findDiscoverableById(@Param("id") UUID id);
}
