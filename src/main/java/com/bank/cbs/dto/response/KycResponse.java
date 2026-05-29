package com.bank.cbs.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.KycVerification;
import com.bank.cbs.domain.enums.KycDocumentType;
import com.bank.cbs.domain.enums.KycStatus;

public record KycResponse(
    UUID            kycId,
    UUID            customerId,
    KycDocumentType documentType,
    String          documentNumber,
    LocalDate       documentExpiry,
    KycStatus       status,
    UUID            verifiedBy,
    OffsetDateTime  verifiedAt,
    String          rejectionReason,
    OffsetDateTime  createdAt
) {
    public static KycResponse from(KycVerification k) {
        return new KycResponse(
            k.getKycId(), k.getCustomer().getCustomerId(),
            k.getDocumentType(), k.getDocumentNumber(),
            k.getDocumentExpiry(), k.getStatus(),
            k.getVerifiedBy(), k.getVerifiedAt(),
            k.getRejectionReason(), k.getCreatedAt()
        );
    }
}
