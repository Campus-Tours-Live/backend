package com.CampusToursLive.domain.user;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

/** Repository for {@code user_roles} — the authoritative role set per user. */
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.PK> {

    boolean existsByUserIdAndRole(UUID userId, UserRole role);

    List<UserRoleEntity> findByUserId(UUID userId);

    @Modifying
    @Transactional
    long deleteByUserIdAndRole(UUID userId, UserRole role);
}
