package com.bank.cbs.service;

import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.bank.cbs.domain.document.AuditEventDocument;
import com.bank.cbs.repository.mongo.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditEventRepository auditEventRepository;

    @Async
    public void log(String entityType, String entityId, String action,
                    String performedBy, String role, String ipAddress,
                    Map<String, Object> beforeState, Map<String, Object> afterState) {
        AuditEventDocument doc = AuditEventDocument.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .performedBy(performedBy)
            .role(role)
            .ipAddress(ipAddress)
            .beforeState(beforeState)
            .afterState(afterState)
            .build();
        auditEventRepository.save(doc);
        log.debug("Audit logged: {} {} {}", action, entityType, entityId);
    }
}
