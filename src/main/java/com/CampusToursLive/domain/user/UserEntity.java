package com.CampusToursLive.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Maps the {@code users} table (V1__schema.sql). */
@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "oidc_subject")
    private String oidcSubject;

    @Column(name = "email")
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // Which area the UI lands in. UX context only — never used for authorization.
    @Enumerated(EnumType.STRING)
    @Column(name = "last_active_role", columnDefinition = "user_role")
    private UserRole lastActiveRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", columnDefinition = "account_status", nullable = false)
    private AccountStatus accountStatus;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_band", columnDefinition = "age_band")
    private AgeBand ageBand;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
