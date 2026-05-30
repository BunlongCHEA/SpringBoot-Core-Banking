package com.bank.cbs.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import com.bank.cbs.domain.enums.EntryType;

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
@Table(name = "account_ledgers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLedger {
    @Id
    @UuidGenerator
    @Column(name = "ledger_id")
    private UUID ledgerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entry_type", nullable = false, columnDefinition = "entry_type")
    private EntryType entryType;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "posting_date", nullable = false)
    private OffsetDateTime postingDate;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
