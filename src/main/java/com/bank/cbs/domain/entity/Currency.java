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

@Entity
@Table(name = "currencies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Currency {
    
    @Id
    @Column(name = "currency_code", length = 3, columnDefinition = "VARCHAR(3)")
    private String currencyCode; // ISO 4217 code, e.g., USD, EUR

    @Column(nullable = false, length = 100)
    private String name; // Full name of the currency, e.g., US Dollar

    @Column(nullable = false, length = 5)
    private String symbol; // Currency symbol, e.g., $, €

    @Column(name = "decimal_places", nullable = false)
    private short decimalPlaces; // Number of decimal places, e.g., 2 for USD

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // Indicates if the currency is currently active in the system

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
