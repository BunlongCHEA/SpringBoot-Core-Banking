package com.bank.cbs.repository.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.cbs.domain.entity.TransactionFee;

public interface TransactionFeeRepository extends JpaRepository<TransactionFee, UUID> {
    
}
