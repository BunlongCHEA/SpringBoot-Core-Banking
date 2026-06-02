package com.bank.cbs.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for creating a CBS customer after verifying against the Go_KYC system.
 * The employee supplies only the identifying document; all personal data
 * is sourced from the verified Go_KYC record.
 */
public record CreateCustomerFromKycRequest(

        /** Document type as stored in Go_KYC — e.g. "NATIONAL_ID", "PASSPORT". */
        @NotBlank
        String idType,

        /** The actual document number used to look up the KYC record. */
        @NotBlank
        String idNumber,

        /** Bank ID registered in the Go_KYC system. */
        @NotBlank
        String bankId,

        /** Optional CBS branch to associate the new customer with. */
        UUID branchId
) {}
