package com.bank.cbs.domain.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

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
@Table(name = "addresses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address extends BaseEntity {
    
    @Id
    @UuidGenerator
    @Column(name = "address_id")
    private UUID addressId;

    @Column(nullable = false)
    private String line1;

    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2, columnDefinition = "VARCHAR(2)")
    private String countryCode;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;    
}
