package com.bank.cbs.repository.jpa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.entity.AccountType;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByCustomer_CustomerId(UUID customerId);
    List<Account> findByCustomer_CustomerIdAndStatus(UUID customerId, AccountStatus status);
    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT a FROM Account a WHERE a.customer.customerId = :customerId AND a.accountType = :type AND a.status = 'ACTIVE'")
    List<Account> findActiveByCustomerAndType(@Param("customerId") UUID customerId, @Param("type") AccountType type);

    @Modifying
    @Query("UPDATE Account a SET a.balance = :balance, a.availableBalance = :availableBalance WHERE a.accountId = :accountId")
    int updateBalance(
        @Param("accountId") UUID accountId,
        @Param("balance") BigDecimal balance,
        @Param("availableBalance") BigDecimal availableBalance
    );
}
