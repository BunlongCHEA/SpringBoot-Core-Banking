package com.bank.cbs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.config.CbsProperties;
import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.entity.AccountLedger;
import com.bank.cbs.domain.entity.Transaction;
import com.bank.cbs.domain.entity.TransactionReference;
import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.enums.EntryType;
import com.bank.cbs.domain.enums.TransactionStatus;
import com.bank.cbs.domain.enums.TransactionType;
import com.bank.cbs.dto.request.TransactionRequest;
import com.bank.cbs.dto.response.TransactionResponse;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.DuplicateTransactionException;
import com.bank.cbs.repository.jpa.AccountLedgerRepository;
import com.bank.cbs.repository.jpa.AccountRepository;
import com.bank.cbs.repository.jpa.CurrencyRepository;
import com.bank.cbs.repository.jpa.TransactionReferenceRepository;
import com.bank.cbs.repository.jpa.TransactionRepository;
import com.bank.cbs.service.redis.BalanceCacheRedisService;
import com.bank.cbs.service.redis.DistributedLockRedisService;
import com.bank.cbs.service.redis.IdempotencyRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository      transactionRepository;
    private final AccountRepository          accountRepository;
    private final AccountLedgerRepository    ledgerRepository;
    private final CurrencyRepository         currencyRepository;
    private final DistributedLockRedisService lockService;
    private final IdempotencyService         idempotencyService;
    private final BalanceCacheRedisService   balanceCacheService;
    private final CbsProperties              cbsProperties;
    private final TransactionReferenceRepository referenceRepository;

    @Transactional
    public TransactionResponse transfer(TransactionRequest request) {

        // ── 1. Idempotency check ─────────────────────────────
        if (idempotencyService.exists(request.idempotencyKey())) {
            return idempotencyService.getTransactionId(request.idempotencyKey())
                .flatMap(id -> transactionRepository.findById(UUID.fromString(id)))
                .map(TransactionResponse::from)
                .orElseThrow(() -> new DuplicateTransactionException("Duplicate request: " + request.idempotencyKey()));
        }

        // ── 2. Load accounts ─────────────────────────────────
        Account debit  = accountRepository.findByAccountNumber(request.debitAccountNumber())
            .orElseThrow(() -> new BusinessException("Debit account not found"));
        Account credit = accountRepository.findByAccountNumber(request.creditAccountNumber())
            .orElseThrow(() -> new BusinessException("Credit account not found"));

        // ── 3. Validations ───────────────────────────────────
        validateAccount(debit,  "Debit");
        validateAccount(credit, "Credit");
        validateDailyLimit(debit, request.amount());

        // ── 4. Acquire distributed locks (always in consistent order) ─
        String firstLock  = debit.getAccountId().compareTo(credit.getAccountId()) < 0
            ? debit.getAccountId().toString() : credit.getAccountId().toString();
        String secondLock = firstLock.equals(debit.getAccountId().toString())
            ? credit.getAccountId().toString() : debit.getAccountId().toString();

        lockService.acquireLockOrThrow(firstLock);
        try {
            lockService.acquireLockOrThrow(secondLock);
            try {
                return executeTransfer(debit, credit, request);
            } finally {
                lockService.releaseLock(secondLock);
            }
        } finally {
            lockService.releaseLock(firstLock);
        }
    }

    @Transactional
    public TransactionResponse deposit(TransactionRequest request) {
        Account credit = accountRepository.findByAccountNumber(request.creditAccountNumber())
            .orElseThrow(() -> new BusinessException("Account not found"));
        validateAccount(credit, "Credit");

        lockService.acquireLockOrThrow(credit.getAccountId().toString());
        try {
            Transaction txn = buildTransaction(request, null, credit, TransactionType.DEPOSIT);
            credit.credit(request.amount());
            accountRepository.save(credit);
            Transaction saved = transactionRepository.save(txn);
            postLedgerEntries(saved, null, credit);
            balanceCacheService.evict(credit.getAccountId().toString());

            // idempotencyService.save(request.idempotencyKey(), saved.getTransactionId().toString());
            idempotencyService.save(saved.getIdempotencyKey(), saved.getTransactionId(), saved.getInitiatedAt());

            referenceRepository.save(TransactionReference.builder()
                .referenceNumber(saved.getReferenceNumber())
                .transactionId(saved.getTransactionId())
                .initiatedAt(saved.getInitiatedAt())
                .build());
            log.info("Deposit completed: {}", saved.getReferenceNumber());
            return TransactionResponse.from(saved);
        } finally {
            lockService.releaseLock(credit.getAccountId().toString());
        }
    }

    @Transactional
    public TransactionResponse withdrawal(TransactionRequest request) {
        Account debit = accountRepository.findByAccountNumber(request.debitAccountNumber())
            .orElseThrow(() -> new BusinessException("Account not found"));
        validateAccount(debit, "Debit");
        validateDailyLimit(debit, request.amount());

        lockService.acquireLockOrThrow(debit.getAccountId().toString());
        try {
            Transaction txn = buildTransaction(request, debit, null, TransactionType.WITHDRAWAL);
            debit.debit(request.amount());
            accountRepository.save(debit);
            Transaction saved = transactionRepository.save(txn);
            postLedgerEntries(saved, debit, null);
            balanceCacheService.evict(debit.getAccountId().toString());

            // idempotencyService.save(request.idempotencyKey(), saved.getTransactionId().toString());
            idempotencyService.save(saved.getIdempotencyKey(), saved.getTransactionId(), saved.getInitiatedAt());

            referenceRepository.save(TransactionReference.builder()
                .referenceNumber(saved.getReferenceNumber())
                .transactionId(saved.getTransactionId())
                .initiatedAt(saved.getInitiatedAt())
                .build());
            log.info("Withdrawal completed: {}", saved.getReferenceNumber());
            return TransactionResponse.from(saved);
        } finally {
            lockService.releaseLock(debit.getAccountId().toString());
        }
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAccountTransactions(UUID accountId, Pageable pageable) {
        return transactionRepository
            .findByDebitAccount_AccountIdOrCreditAccount_AccountId(accountId, accountId, pageable)
            .map(TransactionResponse::from);
    }

    // ── Private helpers ───────────────────────────────────────

    private TransactionResponse executeTransfer(Account debit, Account credit, TransactionRequest request) {
        Transaction txn = buildTransaction(request, debit, credit, TransactionType.TRANSFER);
        debit.debit(request.amount());
        credit.credit(request.amount());
        accountRepository.save(debit);
        accountRepository.save(credit);
        Transaction saved = transactionRepository.save(txn);
        postLedgerEntries(saved, debit, credit);
        balanceCacheService.evict(debit.getAccountId().toString());
        balanceCacheService.evict(credit.getAccountId().toString());

        // idempotencyService.save(request.idempotencyKey(), saved.getTransactionId().toString());
        idempotencyService.save(saved.getIdempotencyKey(), saved.getTransactionId(), saved.getInitiatedAt());
        
        referenceRepository.save(TransactionReference.builder()
            .referenceNumber(saved.getReferenceNumber())
            .transactionId(saved.getTransactionId())
            .initiatedAt(saved.getInitiatedAt())
            .build());
        log.info("Transfer completed: {}", saved.getReferenceNumber());
        return TransactionResponse.from(saved);
    }

    private Transaction buildTransaction(TransactionRequest request,
                                          Account debit, Account credit,
                                          TransactionType type) {
        var currency = currencyRepository.findById(request.currencyCode())
            .orElseThrow(() -> new BusinessException("Currency not found: " + request.currencyCode()));

        return Transaction.builder()
            .referenceNumber(generateReference())
            .idempotencyKey(request.idempotencyKey())
            .debitAccount(debit)
            .creditAccount(credit)
            .transactionType(type)
            .amount(request.amount())
            .currency(currency)
            .exchangeRate(BigDecimal.ONE)
            .status(TransactionStatus.COMPLETED)
            .channel(request.channel())
            .description(request.description())
            .initiatedAt(OffsetDateTime.now())
            .completedAt(OffsetDateTime.now())
            .build();
    }

    private void postLedgerEntries(Transaction txn, Account debit, Account credit) {
        OffsetDateTime now   = OffsetDateTime.now();
        LocalDate      today = LocalDate.now();

        if (debit != null) {
            ledgerRepository.save(AccountLedger.builder()
                .account(debit)
                .transactionId(txn.getTransactionId())
                .entryType(EntryType.DEBIT)
                .amount(txn.getAmount())
                .balanceAfter(debit.getBalance())
                .valueDate(today)
                .postingDate(now)
                .description(txn.getDescription())
                .createdAt(now)
                .build());
        }

        if (credit != null) {
            ledgerRepository.save(AccountLedger.builder()
                .account(credit)
                .transactionId(txn.getTransactionId())
                .entryType(EntryType.CREDIT)
                .amount(txn.getAmount())
                .balanceAfter(credit.getBalance())
                .valueDate(today)
                .postingDate(now)
                .description(txn.getDescription())
                .createdAt(now)
                .build());
        }
    }

    private void validateAccount(Account account, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(label + " account is not active: " + account.getAccountNumber());
        }
    }

    private void validateDailyLimit(Account account, BigDecimal amount) {
        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime endOfDay   = startOfDay.plusDays(1);
        BigDecimal dailyTotal = transactionRepository.sumDailyDebits(
            account.getAccountId(), startOfDay, endOfDay
        );
        if (dailyTotal.add(amount).compareTo(account.getDailyLimit()) > 0) {
            throw new BusinessException("Daily transaction limit exceeded for account: "
                + account.getAccountNumber());
        }
    }

    private String generateReference() {
        String ref;
        do {
            ref = "TXN" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999);
        } while (referenceRepository.existsById(ref));
        return ref;
    }

    private static final java.util.concurrent.ThreadLocalRandom ThreadLocalRandom =
        java.util.concurrent.ThreadLocalRandom.current();
}
