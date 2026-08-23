package com.bank.cbs.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLoanRequest(
    @NotNull @DecimalMin("1.00") BigDecimal principal,
    @NotNull @DecimalMin("0.01") BigDecimal interestRate,
    @NotNull @Min(1) @Max(360)   Integer termMonths,
    @NotBlank @Size(min=3,max=3) String currencyCode,
    @NotBlank String disbursementAccountNumber   // which of the customer's real deposit accounts receives the funds
) {
    
}
