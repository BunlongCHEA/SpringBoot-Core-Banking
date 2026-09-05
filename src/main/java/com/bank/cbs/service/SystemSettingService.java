package com.bank.cbs.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.SystemSetting;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.SystemSettingRepository;
import com.bank.cbs.security.SecurityAuditContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemSettingService {
    private final SystemSettingRepository repository;
    private final AuditService              auditService;
    private final SecurityAuditContext      securityContext;

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        return repository.findById(key)
            .map(s -> new BigDecimal(s.getValue()))
            .orElse(fallback);
    }

    @Transactional
    public void set(String key, String value) {
        SystemSetting setting = repository.findById(key)
            .orElseThrow(() -> new ResourceNotFoundException("Unknown setting: " + key));
        String oldValue = setting.getValue();
        setting.setValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        repository.save(setting);

        auditService.log("SystemSetting", null, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("key", key, "value", oldValue), Map.of("key", key, "value", value), null);
    }
}
