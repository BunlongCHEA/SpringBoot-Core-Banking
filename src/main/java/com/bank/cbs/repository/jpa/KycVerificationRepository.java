package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.KycVerification;
import com.bank.cbs.domain.enums.KycStatus;

@Repository
public interface KycVerificationRepository extends JpaRepository<KycVerification, UUID> {
    List<KycVerification> findByCustomer_CustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<KycVerification> findByStatus(KycStatus status);
    boolean existsByCustomer_CustomerIdAndStatus(UUID customerId, KycStatus status);
}
