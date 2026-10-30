package com.CampusToursLive.domain.guide;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuideProfileRepository extends JpaRepository<GuideProfileEntity, UUID> {
    Optional<GuideProfileEntity> findByUserId(UUID userId);

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
