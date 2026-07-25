package com.bank.cbs.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.SystemSetting;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.SystemSettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemSettingService {
    private final SystemSettingRepository repository;

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
        setting.setValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        repository.save(setting);
    }
}
