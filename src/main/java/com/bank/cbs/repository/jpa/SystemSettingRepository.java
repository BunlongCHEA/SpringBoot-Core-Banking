package com.bank.cbs.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.cbs.domain.entity.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
    
}
