package com.CampusToursLive.domain.tour;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourOfferingRepository extends JpaRepository<TourOfferingEntity, UUID> {

    List<TourOfferingEntity> findByGuideId(UUID guideId);

    boolean existsByGuideIdAndSlug(UUID guideId, String slug);

    Optional<TourOfferingEntity> findByIdAndGuideId(UUID id, UUID guideId);

    /**
     * Recompute {@code avg_rating} / {@code review_count} for one offering from the source of truth
     * — its PUBLISHED reviews; call inside the review-write transaction. Keeps discovery ranking
     * (which sorts by these columns) accurate. Locks the row first so a concurrent review writer's
     * review is not missed (CTL-139); see {@code GuideProfileRepository#recomputeRatingAggregate}.
     */
    default void recomputeRatingAggregate(UUID offeringId) {
        lockForRatingRecompute(offeringId);
        updateRatingAggregate(offeringId);
    }

    /**
     * Row lock for {@link #recomputeRatingAggregate}; not meant to be called on its own. {@code FOR
     * NO KEY UPDATE}, not {@code FOR UPDATE}: the review INSERT that precedes the recompute already
     * holds a foreign-key {@code KEY SHARE} lock on this row, and {@code FOR UPDATE} conflicts with
     * another writer's {@code KEY SHARE}, so two writers that both inserted first would deadlock.
     * {@code FOR NO KEY UPDATE} is compatible with {@code KEY SHARE} yet still conflicts with
     * itself, so the recomputes are still serialized (CTL-139 review follow-up).
     */
    @Query(
            value = "SELECT 1 FROM tour_offerings WHERE id = :offeringId FOR NO KEY UPDATE",
            nativeQuery = true)
    List<Integer> lockForRatingRecompute(@Param("offeringId") UUID offeringId);

    /**
     * The recompute statement; call {@link #recomputeRatingAggregate}, which locks the row first.
     */
    @Modifying
    @Query(
            value =
                    "UPDATE tour_offerings SET"
                            + " avg_rating = COALESCE((SELECT AVG(overall_rating) FROM reviews WHERE"
                            + " tour_offering_id = :offeringId AND status = 'PUBLISHED'), 0),"
                            + " review_count = (SELECT COUNT(*) FROM reviews WHERE tour_offering_id ="
                            + " :offeringId AND status = 'PUBLISHED')"
                            + " WHERE id = :offeringId",
            nativeQuery = true)
    void updateRatingAggregate(@Param("offeringId") UUID offeringId);

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
