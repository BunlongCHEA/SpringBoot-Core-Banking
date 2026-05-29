package com.bank.cbs.dto.request;

import java.time.LocalDate;

import com.bank.cbs.domain.enums.KycDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KycRequest(
    @NotNull  KycDocumentType documentType,
    @NotBlank String documentNumber,
    LocalDate documentExpiry
) {
    
}
