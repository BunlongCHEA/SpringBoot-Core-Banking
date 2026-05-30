package com.bank.cbs.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import com.bank.cbs.domain.enums.CardStatus;
import com.bank.cbs.domain.enums.CardType;

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
@Table(name = "cards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card extends BaseEntity {
    
    @Id
    @UuidGenerator
    @Column(name = "card_id")
    private UUID cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "card_number_hash", nullable = false, unique = true, length = 64)
    private String cardNumberHash;

    @Column(name = "card_last_four", nullable = false, length = 4, columnDefinition = "VARCHAR(4)")
    private String cardLastFour;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "card_type", nullable = false, columnDefinition = "card_type")
    private CardType cardType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "card_status")
    private CardStatus status = CardStatus.PENDING;

    @Column(name = "daily_limit", precision = 20, scale = 4)
    private BigDecimal dailyLimit = new BigDecimal("5000.0000");

    @Column(name = "contactless_enabled", nullable = false)
    private boolean contactlessEnabled = true;

    @Column(name = "international_enabled", nullable = false)
    private boolean internationalEnabled = false;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;
}
