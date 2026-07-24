package com.bank.cbs.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.cbs.domain.entity.TransactionIdempotency;

public interface TransactionIdempotencyRepository extends JpaRepository<TransactionIdempotency, String> {
    
}
