package com.bank.cbs.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import com.bank.cbs.domain.enums.LoanStatus;

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
@Table(name = "loans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan extends BaseEntity {
    
    @Id
    @UuidGenerator
    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "loan_number", nullable = false, unique = true, length = 30)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal principal;

    @Column(name = "outstanding_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal outstandingBalance;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_installment", precision = 20, scale = 4)
    private BigDecimal monthlyInstallment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", nullable = false)
    private Currency currency;

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "loan_status")
    private LoanStatus status = LoanStatus.PENDING;

    @Column(name = "approved_at") 
    private OffsetDateTime approvedAt;
    
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "approved_by") 
    private User approvedBy;
    
    @Column(name = "rejected_at") 
    private OffsetDateTime rejectedAt;
    
    @Column(name = "rejection_reason", length = 255) 
    private String rejectionReason;
    
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "disbursement_account_id") 
    private Account disbursementAccount;
}
