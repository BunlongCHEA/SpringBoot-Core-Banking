package com.bank.cbs.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordLoanPaymentRequest(
    @NotBlank String idempotencyKey,
    @NotBlank String payingAccountNumber,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 16, fraction = 4) BigDecimal amount
) {}
