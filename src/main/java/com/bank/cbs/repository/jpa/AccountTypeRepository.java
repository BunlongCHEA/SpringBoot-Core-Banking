package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bank.cbs.domain.entity.AccountType;

public interface AccountTypeRepository extends JpaRepository<AccountType, UUID> {
    List<AccountType> findByIsActiveTrue();
    Optional<AccountType> findByCode(String code);
}
