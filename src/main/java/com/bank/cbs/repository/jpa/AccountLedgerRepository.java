package com.bank.cbs.repository.jpa;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.AccountLedger;

@Repository
public interface AccountLedgerRepository extends JpaRepository<AccountLedger, UUID> {
    Page<AccountLedger> findByAccount_AccountIdOrderByPostingDateDesc(UUID accountId, Pageable pageable);

    List<AccountLedger> findByTransactionId(UUID transactionId);

    @Query("""
        SELECT l FROM AccountLedger l
        WHERE l.account.accountId = :accountId
          AND l.valueDate BETWEEN :from AND :to
        ORDER BY l.postingDate DESC
        """)
    List<AccountLedger> findByAccountAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
