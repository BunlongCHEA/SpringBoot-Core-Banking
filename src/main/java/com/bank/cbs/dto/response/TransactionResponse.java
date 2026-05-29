package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Transaction;
import com.bank.cbs.domain.enums.TransactionChannel;
import com.bank.cbs.domain.enums.TransactionStatus;
import com.bank.cbs.domain.enums.TransactionType;

public record TransactionResponse(
    UUID              transactionId,
    String            referenceNumber,
    String            debitAccountNumber,
    String            creditAccountNumber,
    TransactionType   transactionType,
    BigDecimal        amount,
    String            currencyCode,
    TransactionStatus status,
    TransactionChannel channel,
    String            description,
    OffsetDateTime    initiatedAt,
    OffsetDateTime    completedAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
            t.getTransactionId(), t.getReferenceNumber(),
            t.getDebitAccount()  != null ? t.getDebitAccount().getAccountNumber()  : null,
            t.getCreditAccount() != null ? t.getCreditAccount().getAccountNumber() : null,
            t.getTransactionType(), t.getAmount(),
            t.getCurrency().getCurrencyCode(),
            t.getStatus(), t.getChannel(), t.getDescription(),
            t.getInitiatedAt(), t.getCompletedAt()
        );
    }   
}
