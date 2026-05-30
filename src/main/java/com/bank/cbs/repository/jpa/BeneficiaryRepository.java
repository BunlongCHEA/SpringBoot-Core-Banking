package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Beneficiary;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByOwnerCustomer_CustomerIdAndIsActiveTrue(UUID customerId);
    List<Beneficiary> findByAccountNumber(String accountNumber);
}
