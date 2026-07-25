package com.bank.cbs.domain.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "system_settings")
@Getter 
@Setter 
@Builder 
@NoArgsConstructor 
@AllArgsConstructor
public class SystemSetting {
    @Id @Column(name = "setting_key") private String settingKey;
    @Column(nullable = false) private String value;
    private String description;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;
}
