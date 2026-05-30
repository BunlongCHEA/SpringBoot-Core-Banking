package com.bank.cbs.domain.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import com.bank.cbs.domain.enums.KycDocumentType;
import com.bank.cbs.domain.enums.KycStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "kyc_verifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerification extends BaseEntity {
    
    @Id
    @UuidGenerator
    @Column(name = "kyc_id")
    private UUID kycId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "document_type", nullable = false, columnDefinition = "kyc_document_type")
    private KycDocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "document_expiry")
    private LocalDate documentExpiry;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "kyc_status")
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
