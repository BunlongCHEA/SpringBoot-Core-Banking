package com.bank.cbs.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.TransactionReference;

// public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID>

@Repository
public interface TransactionReferenceRepository extends JpaRepository<TransactionReference, String> {
    boolean existsById(String id);
}
