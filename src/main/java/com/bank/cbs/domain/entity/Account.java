package com.bank.cbs.domain.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.enums.AccountType;

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
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {
    
    @Id
    @UuidGenerator
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, columnDefinition = "account_type")
    private AccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", nullable = false, referencedColumnName = "currency_code", columnDefinition = "VARCHAR(3)")
    private Currency currency;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "hold_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal holdBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "account_status")
    private AccountStatus status = AccountStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "daily_limit", precision = 20, scale = 4)
    private BigDecimal dailyLimit = new BigDecimal("50000.0000");

    @Column(name = "interest_rate", precision = 6, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new com.bank.cbs.exception.InsufficientFundsException(
                "Insufficient funds in account: " + this.accountNumber);
        }
        this.balance = this.balance.subtract(amount);
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public void placeHold(BigDecimal amount) {
        this.availableBalance = this.availableBalance.subtract(amount);
        this.holdBalance = this.holdBalance.add(amount);
    }

    public void releaseHold(BigDecimal amount) {
        this.holdBalance = this.holdBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }
}
