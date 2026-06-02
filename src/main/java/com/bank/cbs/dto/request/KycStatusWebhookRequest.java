package com.bank.cbs.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload sent by Go-Blockchain-KYC when a customer's KYC status
 * changes to EXPIRED or SUSPENDED.
 *
 * {@code customerId} in Go_KYC equals {@code customer_code} in CBS.
 */
public record KycStatusWebhookRequest(

        @NotBlank
        String customerId,   // Go_KYC customer_id == CBS customer_code

        @NotBlank
        String kycStatus     // "EXPIRED" | "SUSPENDED"
) {
    
}
