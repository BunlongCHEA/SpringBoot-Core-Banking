package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.entity.KycVerification;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.domain.enums.KycDocumentType;
import com.bank.cbs.domain.enums.KycStatus;
import com.bank.cbs.dto.request.KycRequest;
import com.bank.cbs.dto.response.KycResponse;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.KycVerificationRepository;
import com.bank.cbs.security.SecurityAuditContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {
    private final KycVerificationRepository kycRepository;
    private final CustomerService           customerService;

    private final AuditService              auditService;
    private final SecurityAuditContext      securityContext;

    @Transactional
    public KycResponse submit(UUID customerId, KycRequest request) {
        Customer customer = customerService.getOrThrow(customerId);

        KycVerification kyc = KycVerification.builder()
            .customer(customer)
            .documentType(request.documentType())
            .documentNumber(request.documentNumber())
            .documentExpiry(request.documentExpiry())
            .status(KycStatus.PENDING)
            .build();

        return KycResponse.from(kycRepository.save(kyc));
    }

    @Transactional
    public KycResponse verify(UUID kycId, UUID verifiedBy) {
        KycVerification kyc = getOrThrow(kycId);
        kyc.setStatus(KycStatus.VERIFIED);
        kyc.setVerifiedBy(verifiedBy);
        kyc.setVerifiedAt(OffsetDateTime.now());

        auditService.log("KycVerification", kycId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", "PENDING"), Map.of("status", "VERIFIED"), null);
        log.info("KYC verified: {} by: {}", kycId, verifiedBy);
        return KycResponse.from(kycRepository.save(kyc));
    }

    @Transactional
    public KycResponse reject(UUID kycId, String reason) {
        KycVerification kyc = getOrThrow(kycId);
        kyc.setStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason);

        auditService.log("KycVerification", kycId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("status", "PENDING"), Map.of("status", "REJECTED"), Map.of("reason", reason));
        log.info("KYC rejected: {} reason: {}", kycId, reason);
        return KycResponse.from(kycRepository.save(kyc));
    }

    @Transactional(readOnly = true)
    public List<KycResponse> findByCustomer(UUID customerId) {
        return kycRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
            .stream().map(KycResponse::from).toList();
    }

    // recordExternalVerification used for external KYC verification, e.g., via third-party service. It creates a KYC record with VERIFIED status.
    @Transactional
    public KycResponse recordExternalVerification(Customer customer, String idTypeRaw, String documentNumber) {
        KycVerification kyc = KycVerification.builder()
            .customer(customer)
            .documentType(KycDocumentType.valueOf(idTypeRaw))
            .documentNumber(documentNumber)
            .status(KycStatus.VERIFIED)
            .verifiedAt(OffsetDateTime.now())
            .build();
        return KycResponse.from(kycRepository.save(kyc));
    }

    private KycVerification getOrThrow(UUID kycId) {
        return kycRepository.findById(kycId)
            .orElseThrow(() -> new ResourceNotFoundException("KYC record not found: " + kycId));
    }
}
