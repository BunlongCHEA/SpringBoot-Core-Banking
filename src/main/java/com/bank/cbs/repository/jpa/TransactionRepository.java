package com.bank.cbs.repository.jpa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Transaction;
import com.bank.cbs.domain.enums.TransactionStatus;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    Page<Transaction> findByDebitAccount_AccountIdOrCreditAccount_AccountId(
        UUID debitAccountId, UUID creditAccountId, Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.debitAccount.accountId = :accountId
          AND t.status = 'COMPLETED'
          AND t.initiatedAt >= :from
          AND t.initiatedAt < :to
        """)
    BigDecimal sumDailyDebits(
        @Param("accountId") UUID accountId,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );

    Page<Transaction> findByDebitAccount_AccountIdAndStatus(
        UUID accountId, TransactionStatus status, Pageable pageable
    );
}
