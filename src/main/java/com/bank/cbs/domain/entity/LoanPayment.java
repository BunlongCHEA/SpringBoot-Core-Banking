package com.bank.cbs.domain.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
@Table(name = "loan_payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LoanPayment {
    @Id @UuidGenerator
    @Column(name = "loan_payment_id")
    private UUID loanPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false, precision = 20, scale = 4) 
    private BigDecimal amount;

    @Column(name = "principal_portion", nullable = false, precision = 20, scale = 4) 
    private BigDecimal principalPortion;
    
    @Column(name = "interest_portion", nullable = false, precision = 20, scale = 4) 
    private BigDecimal interestPortion;
    
    @Column(name = "outstanding_after", nullable = false, precision = 20, scale = 4) 
    private BigDecimal outstandingAfter;
    
    @Column(name = "paid_at", nullable = false) 
    private OffsetDateTime paidAt;
}