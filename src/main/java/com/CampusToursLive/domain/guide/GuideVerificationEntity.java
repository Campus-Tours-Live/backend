package com.CampusToursLive.domain.guide;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps the {@code guide_verifications} table (V1__schema.sql) — a student/identity verification
 * submission. The MVP supports the UNIVERSITY_EMAIL method.
 */
@Getter
@Setter
@Entity
@Table(name = "guide_verifications")
public class GuideVerificationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    /** Verification method. Only {@code UNIVERSITY_EMAIL} is written today (the MVP method). */
    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "university_email")
    private String universityEmail;

    @Column(name = "document_key")
    private String documentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "guide_verification_status", nullable = false)
    private GuideVerificationStatus status = GuideVerificationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
