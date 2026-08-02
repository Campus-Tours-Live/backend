package com.CampusToursLive.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps the {@code audit_log} table (V1__schema.sql) — the strongly-consistent audit trail written
 * IN the same transaction as the write it records (see {@link AuditWriter}). {@code id} is a DB
 * {@code GENERATED ALWAYS AS IDENTITY} column, so it is only ever assigned by Postgres on insert.
 *
 * <p>{@code metadata} is a {@code jsonb} column mapped as {@code Map<String,Object>} via Hibernate
 * 6's {@link SqlTypes#JSON}, matching the raw-JSON-column pattern already used by {@code
 * GuideProfileEntity} / {@code TourOfferingEntity} (this table only ever holds small, ad-hoc
 * key/value metadata rather than a fixed shape, so a raw map — not a JSON string — is the natural
 * mapping here).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "reason_code")
    private String reasonCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // DB default now(); never written by the app.
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    public AuditLogEntity(
            String action,
            String targetType,
            String targetId,
            UUID actorUserId,
            Map<String, Object> metadata) {
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actorUserId = actorUserId;
        this.metadata = metadata;
    }
}
