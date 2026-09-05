package com.bank.cbs.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.bank.cbs.domain.entity.AuditLog;
import com.bank.cbs.domain.enums.AuditAction;

public record AuditLogResponse(
    UUID auditId, String entityType, UUID entityId, AuditAction action,
    UUID changedBy, String changedByRole, String ipAddress,
    Map<String, Object> oldValue, Map<String, Object> newValue, Map<String, Object> metadata,
    OffsetDateTime changedAt
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getAuditId(), a.getEntityType(), a.getEntityId(), a.getAction(),
            a.getChangedBy(), a.getChangedByRole(), a.getIpAddress(),
            a.getOldValue(), a.getNewValue(), a.getMetadata(), a.getChangedAt());
    }
}
