package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// import com.bank.cbs.domain.document.AuditEventDocument;
import com.bank.cbs.domain.entity.AuditLog;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.repository.jpa.AuditLogRepository;
// import com.bank.cbs.repository.mongo.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
        private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional
    public void log(String entityType, UUID entityId, AuditAction action,
                     UUID changedBy, String changedByRole, String ipAddress,
                     Map<String, Object> oldValue, Map<String, Object> newValue,
                     Map<String, Object> metadata) {
        try {
            auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .changedBy(changedBy)
                .changedByRole(changedByRole)
                .ipAddress(ipAddress)
                .oldValue(oldValue)
                .newValue(newValue)
                .metadata(metadata)
                .changedAt(OffsetDateTime.now())
                .build());
        } catch (Exception e) {
            // Deliberately swallowed: a failed audit write must never break
            // the actual business operation it's describing. Logged loudly
            // instead so it's caught by monitoring, not silently lost.
            log.error("AUDIT WRITE FAILED for {}#{} action={}: {}", entityType, entityId, action, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String entityType, UUID entityId, AuditAction action,
                                   UUID changedBy, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (entityType != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entityType"), entityType));
        if (entityId != null)   spec = spec.and((r, q, cb) -> cb.equal(r.get("entityId"), entityId));
        if (action != null)     spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action));
        if (changedBy != null)  spec = spec.and((r, q, cb) -> cb.equal(r.get("changedBy"), changedBy));
        if (from != null)       spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("changedAt"), from));
        if (to != null)         spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("changedAt"), to));
        return auditLogRepository.findAll(spec, pageable);
    }
}
