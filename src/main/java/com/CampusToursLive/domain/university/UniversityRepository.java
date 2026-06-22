package com.CampusToursLive.domain.university;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UniversityRepository extends JpaRepository<UniversityEntity, UUID> {

    /**
     * Active-only catalog search by name / short name (case-insensitive). Empty q → all (paged).
     */
    @Query(
            """
            select u from UniversityEntity u
            where u.status = com.CampusToursLive.domain.university.UniversityStatus.ACTIVE
              and (
                :q = ''
                or lower(u.name) like lower(concat('%', :q, '%'))
                or lower(coalesce(u.shortName, '')) like lower(concat('%', :q, '%'))
              )
            order by u.name asc
            """)
    List<UniversityEntity> search(@Param("q") String q, Pageable pageable);
}
