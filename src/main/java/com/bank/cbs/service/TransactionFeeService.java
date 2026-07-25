package com.bank.cbs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.bank.cbs.domain.entity.Channel;
import com.bank.cbs.domain.entity.Transaction;
import com.bank.cbs.domain.entity.TransactionFee;
import com.bank.cbs.domain.entity.TransactionReference;
import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.enums.EntryType;
import com.bank.cbs.domain.enums.FeeType;
import com.bank.cbs.domain.enums.TransactionStatus;
import com.bank.cbs.domain.enums.TransactionType;
import com.bank.cbs.dto.request.TransactionRequest;
import com.bank.cbs.dto.response.TransactionResponse;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.DuplicateTransactionException;
import com.bank.cbs.repository.jpa.AccountLedgerRepository;
import com.bank.cbs.repository.jpa.AccountRepository;
import com.bank.cbs.repository.jpa.ChannelRepository;
import com.bank.cbs.repository.jpa.CurrencyRepository;
import com.bank.cbs.repository.jpa.TransactionFeeRepository;
import com.bank.cbs.repository.jpa.TransactionReferenceRepository;
import com.bank.cbs.repository.jpa.TransactionRepository;
import com.bank.cbs.service.redis.BalanceCacheRedisService;
import com.bank.cbs.service.redis.DistributedLockRedisService;
import com.bank.cbs.service.redis.IdempotencyRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
public class TransactionFeeService {
    private final TransactionFeeRepository feeRepository;
    private final SystemSettingService settingService;

    private static final BigDecimal ATM_WITHDRAWAL_FEE      = new BigDecimal("2.50");
    private static final BigDecimal NON_BRANCH_TRANSFER_FEE = new BigDecimal("1.00");
    private static final BigDecimal HIGH_VALUE_FEE_RATE     = new BigDecimal("0.001"); // 0.1%
    private static final String THRESHOLD_KEY = "high_value_txn_threshold_usd";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("5000");

    @Transactional
    public void recordIfApplicable(Transaction txn) {
        recordChannelFee(txn);
        recordHighValueFee(txn);
    }

    private void recordChannelFee(Transaction txn) {
        BigDecimal amount; FeeType type;
        String channelCode = txn.getChannel() != null ? txn.getChannel().getCode() : "BRANCH";

        if (txn.getTransactionType() == TransactionType.WITHDRAWAL && "ATM".equals(channelCode)) {
            amount = ATM_WITHDRAWAL_FEE; type = FeeType.ATM_FEE;
        } else if (txn.getTransactionType() == TransactionType.TRANSFER && !"BRANCH".equals(channelCode)) {
            amount = NON_BRANCH_TRANSFER_FEE; type = FeeType.TRANSFER_FEE;
        } else {
            return;
        }
        saveFee(txn, type, amount, "Auto-assessed " + type);
    }

    private void recordHighValueFee(Transaction txn) {
        boolean isOutgoing = txn.getTransactionType() == TransactionType.WITHDRAWAL
                          || txn.getTransactionType() == TransactionType.TRANSFER;
        if (!isOutgoing) return;

        BigDecimal usdEquivalent = txn.getAmount().multiply(txn.getCurrency().getUsdExchangeRate());
        BigDecimal threshold = settingService.getDecimal(THRESHOLD_KEY, DEFAULT_THRESHOLD);

        if (usdEquivalent.compareTo(threshold) <= 0) return;

        BigDecimal feeAmount = txn.getAmount().multiply(HIGH_VALUE_FEE_RATE).setScale(4, RoundingMode.HALF_UP);
        saveFee(txn, FeeType.HIGH_VALUE_FEE, feeAmount,
            "High-value " + txn.getTransactionType() + " (> " + threshold + " USD equivalent)");
    }

    private void saveFee(Transaction txn, FeeType type, BigDecimal amount, String description) {
        feeRepository.save(TransactionFee.builder()
            .transactionId(txn.getTransactionId())
            .feeType(type)
            .amount(amount)
            .currency(txn.getCurrency())
            .description(description)
            .createdAt(OffsetDateTime.now())
            .build());
    }
}
