package com.bank.cbs.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

// import com.bank.cbs.domain.enums.AccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    // @NotNull  AccountType accountType,
    @NotNull UUID accountTypeId, 
    @NotBlank @Size(min = 3, max = 3) String currencyCode,
    BigDecimal dailyLimit
) {
    
}
