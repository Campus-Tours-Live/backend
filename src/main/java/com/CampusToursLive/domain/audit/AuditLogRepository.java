package com.CampusToursLive.domain.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByTargetTypeAndTargetId(String targetType, String targetId);
}
