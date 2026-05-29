package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.entity.KycVerification;
import com.bank.cbs.domain.enums.KycStatus;
import com.bank.cbs.dto.request.KycRequest;
import com.bank.cbs.dto.response.KycResponse;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.KycVerificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {
    private final KycVerificationRepository kycRepository;
    private final CustomerService           customerService;

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
        log.info("KYC verified: {} by: {}", kycId, verifiedBy);
        return KycResponse.from(kycRepository.save(kyc));
    }

    @Transactional
    public KycResponse reject(UUID kycId, String reason) {
        KycVerification kyc = getOrThrow(kycId);
        kyc.setStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason);
        log.info("KYC rejected: {} reason: {}", kycId, reason);
        return KycResponse.from(kycRepository.save(kyc));
    }

    @Transactional(readOnly = true)
    public List<KycResponse> findByCustomer(UUID customerId) {
        return kycRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
            .stream().map(KycResponse::from).toList();
    }

    private KycVerification getOrThrow(UUID kycId) {
        return kycRepository.findById(kycId)
            .orElseThrow(() -> new ResourceNotFoundException("KYC record not found: " + kycId));
    }
}
