package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Card;
import com.bank.cbs.domain.enums.CardStatus;
import com.bank.cbs.domain.enums.CardType;

public record CardResponse(
    UUID       cardId,
    UUID       accountId,
    String     cardLastFour,
    CardType   cardType,
    LocalDate  expiryDate,
    CardStatus status,
    BigDecimal dailyLimit,
    String     currencyCode,
    boolean    contactlessEnabled,
    boolean    internationalEnabled,
    OffsetDateTime issuedAt
) {
    public static CardResponse from(Card c) {
        return new CardResponse(
            c.getCardId(), c.getAccount().getAccountId(),
            c.getCardLastFour(), c.getCardType(),
            c.getExpiryDate(), c.getStatus(),
            c.getDailyLimit(), c.getAccount().getCurrency().getCurrencyCode(), 
            c.isContactlessEnabled(), c.isInternationalEnabled(), c.getIssuedAt()
        );
    }
}
