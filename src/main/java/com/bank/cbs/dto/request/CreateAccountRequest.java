package com.bank.cbs.dto.request;

import java.math.BigDecimal;

import com.bank.cbs.domain.enums.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotNull  AccountType accountType,
    @NotBlank @Size(min = 3, max = 3) String currencyCode,
    BigDecimal dailyLimit
) {
    
}
