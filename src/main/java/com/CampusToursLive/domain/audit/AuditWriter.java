package com.CampusToursLive.domain.audit;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes {@code audit_log} rows strongly consistently with the write they record — this class
 * deliberately carries NO {@code @Async} and NO {@code @Transactional} of its own. {@link #record}
 * is a plain repository save that rides whatever transaction is already open on the calling thread
 * (e.g. onboarding's provision + profile + role-grant transaction), so a grant that commits always
 * has its audit row, and a rollback always takes the audit row with it. This is NOT an outbox and
 * must never become one — if delivery guarantees weaker than "same transaction" are ever needed,
 * that is a different mechanism, not a change to this class.
 *
 * <p>Keep {@code metadata} minimal and never store request/session bodies here: no onboarding body,
 * no raw OIDC claims, no tokens, no other PII beyond what the call sites already pass (e.g. {@code
 * role}, {@code profileId}, {@code accountCreated}).
 */
@Component
public class AuditWriter {

    private final AuditLogRepository auditLogs;

    public AuditWriter(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public void record(
            String action,
            String targetType,
            String targetId,
            UUID actorUserId,
            Map<String, Object> metadata) {
        auditLogs.save(new AuditLogEntity(action, targetType, targetId, actorUserId, metadata));
    }
}
