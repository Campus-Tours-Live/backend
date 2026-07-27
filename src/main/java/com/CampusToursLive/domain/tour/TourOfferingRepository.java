package com.CampusToursLive.domain.tour;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourOfferingRepository extends JpaRepository<TourOfferingEntity, UUID> {

    List<TourOfferingEntity> findByGuideId(UUID guideId);

    boolean existsByGuideIdAndSlug(UUID guideId, String slug);

    Optional<TourOfferingEntity> findByIdAndGuideId(UUID id, UUID guideId);

    /**
     * The catalog's FROM + WHERE, shared verbatim by the list query and its count query.
     *
     * <p>Previously both were written out in full, so editing a filter on one side and not the
     * other desynchronised them: the page reported a total its rows could not account for,
     * producing empty or unreachable pages. A single constant makes that class of drift impossible
     * rather than merely discouraged. Annotation attributes need compile-time constants, and text
     * blocks concatenated from `static final` fields are exactly that.
     */
    String DISCOVERABLE_FROM_WHERE =
            """
            from TourOfferingEntity o
            inner join GuideProfileEntity g on g.id = o.guideId
            inner join UniversityEntity u on u.id = o.universityId
            where o.status = com.CampusToursLive.domain.tour.TourStatus.ACTIVE
              and g.status = com.CampusToursLive.domain.guide.GuideStatus.VERIFIED
              and u.status = com.CampusToursLive.domain.university.UniversityStatus.ACTIVE
              and o.universityId = coalesce(:universityId, o.universityId)
              and (:filterByTopic = false or o.topic in :topics)
              and (
                :q = ''
                or lower(o.title) like lower(concat('%', :q, '%')) escape '!'
                or lower(o.description) like lower(concat('%', :q, '%')) escape '!'
                or lower(u.name) like lower(concat('%', :q, '%')) escape '!'
                or lower(coalesce(u.shortName, '')) like lower(concat('%', :q, '%')) escape '!'
              )
            """;

    /**
     * Active offerings from approved guides at active universities — the public marketplace
     * catalog. {@code universityId} and {@code topic} are optional filters; {@code q} matches
     * title, description, university name, or short name (case-insensitive; LIKE wildcards in
     * {@code q} are escaped and matched literally).
     */
    @Query(
            value = "select o " + DISCOVERABLE_FROM_WHERE,
            countQuery = "select count(o) " + DISCOVERABLE_FROM_WHERE)
    Page<TourOfferingEntity> findDiscoverable(
            @Param("universityId") UUID universityId,
            @Param("filterByTopic") boolean filterByTopic,
            @Param("topics") List<TourTopic> topics,
            @Param("q") String q,
            Pageable pageable);

    @Query(
            """
            select o from TourOfferingEntity o
            inner join GuideProfileEntity g on g.id = o.guideId
            inner join UniversityEntity u on u.id = o.universityId
            where o.id = :id
              and o.status = com.CampusToursLive.domain.tour.TourStatus.ACTIVE
              and g.status = com.CampusToursLive.domain.guide.GuideStatus.VERIFIED
              and u.status = com.CampusToursLive.domain.university.UniversityStatus.ACTIVE
            """)
    Optional<TourOfferingEntity> findDiscoverableById(@Param("id") UUID id);
}
