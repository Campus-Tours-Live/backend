package com.CampusToursLive.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByOidcSubject(String oidcSubject);

    Optional<UserEntity> findByEmail(String email);

    /**
     * The ONE query behind {@code AccountResolver}: user fields + role flags + profile counts, all
     * read from a single MVCC snapshot via one {@code LEFT JOIN} + {@code GROUP BY}. Deliberately
     * does NOT issue separate role/profile lookups — that would let roles/profiles change between
     * queries and reintroduce the multi-snapshot bug this projection exists to close.
     *
     * <p>{@code COUNT(DISTINCT …)} (not {@code bool_or(EXISTS)}) so a duplicated/corrupt profile
     * row is detectable as {@code != 1} rather than silently collapsing to "present". Column
     * aliases are quoted to preserve case so they match {@link AccountProjection}'s getters
     * exactly.
     */
    @Query(
            value =
                    "SELECT "
                            + "u.id AS \"id\", "
                            + "u.oidc_subject AS \"oidcSubject\", "
                            + "u.email::text AS \"email\", "
                            + "u.first_name AS \"firstName\", "
                            + "u.last_name AS \"lastName\", "
                            + "u.display_name AS \"displayName\", "
                            + "u.account_status::text AS \"accountStatus\", "
                            + "u.age_band::text AS \"ageBand\", "
                            + "u.created_at AS \"createdAt\", "
                            + "u.deleted_at AS \"deletedAt\", "
                            + "COALESCE(bool_or(ur.role = 'GUIDE'), false) AS \"guideRole\", "
                            + "COALESCE(bool_or(ur.role = 'PARTICIPANT'), false) AS \"participantRole\", "
                            + "COALESCE(bool_or(ur.role = 'ADMIN'), false) AS \"adminRole\", "
                            + "COALESCE(bool_or(ur.role = 'SUPPORT'), false) AS \"supportRole\", "
                            + "COUNT(DISTINCT gp.id) AS \"guideProfileCount\", "
                            + "COUNT(DISTINCT pp.id) AS \"participantProfileCount\", "
                            + "COUNT(DISTINCT pp.participant_type) AS \"participantTypeCount\", "
                            + "MAX(pp.participant_type)::text AS \"participantType\" "
                            + "FROM users u "
                            + "LEFT JOIN user_roles ur ON ur.user_id = u.id "
                            + "LEFT JOIN guide_profiles gp ON gp.user_id = u.id "
                            + "LEFT JOIN participant_profiles pp ON pp.user_id = u.id "
                            + "WHERE u.oidc_subject = :oidcSubject "
                            + "GROUP BY u.id",
            nativeQuery = true)
    Optional<AccountProjection> findAccountProjectionByOidcSubject(
            @Param("oidcSubject") String oidcSubject);
}
