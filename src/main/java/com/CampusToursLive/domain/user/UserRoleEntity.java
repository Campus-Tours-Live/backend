package com.CampusToursLive.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code user_roles} (V1__schema.sql) — the authoritative set of roles a user holds. One row
 * per (user, role); presence of a row IS the "role granted / onboarding complete" signal.
 *
 * <p>The {@code role} enum is mapped with {@link EnumType#STRING} (NOT
 * {@code @JdbcTypeCode(NAMED_ENUM)}) — the snake_case Postgres enum binds via the datasource's
 * {@code stringtype=unspecified}, matching every other enum here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_roles")
@IdClass(UserRoleEntity.PK.class)
public class UserRoleEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", columnDefinition = "user_role", nullable = false)
    private UserRole role;

    // DB default now(); never written by the app.
    @Column(name = "granted_at", nullable = false, insertable = false, updatable = false)
    private Instant grantedAt;

    public UserRoleEntity(UUID userId, UserRole role) {
        this.userId = userId;
        this.role = role;
    }

    /**
     * Composite primary key class for {@code @IdClass}. Field names/types must match the
     * {@code @Id} fields.
     */
    public static class PK implements Serializable {
        private UUID userId;
        private UserRole role;

        public PK() {}

        public PK(UUID userId, UserRole role) {
            this.userId = userId;
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId) && role == pk.role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, role);
        }
    }
}
