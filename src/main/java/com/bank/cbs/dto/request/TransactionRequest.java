package com.bank.cbs.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

// import com.bank.cbs.domain.enums.TransactionChannel;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
    @NotBlank  String idempotencyKey,
    String debitAccountNumber,
    String creditAccountNumber,
    @NotNull @DecimalMin("0.0001") BigDecimal amount,
    @NotBlank @Size(min = 3, max = 3) String currencyCode,
    // TransactionChannel channel,
    UUID channelId,
    String description
) {
    
}
