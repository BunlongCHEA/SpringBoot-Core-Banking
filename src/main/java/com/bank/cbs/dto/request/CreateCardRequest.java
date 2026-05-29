package com.bank.cbs.dto.request;

import java.math.BigDecimal;

import com.bank.cbs.domain.enums.CardType;

import jakarta.validation.constraints.NotNull;

public record CreateCardRequest(
    @NotNull CardType cardType,
    BigDecimal dailyLimit
) {
    
}
