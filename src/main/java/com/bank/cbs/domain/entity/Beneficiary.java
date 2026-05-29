package com.bank.cbs.domain.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiary extends BaseEntity {
    
    @Id
    @UuidGenerator
    @Column(name = "beneficiary_id")
    private UUID beneficiaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_customer_id", nullable = false)
    private Customer ownerCustomer;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code")
    private Currency currency;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;    
}
