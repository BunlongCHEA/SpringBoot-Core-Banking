package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Transaction;
// import com.bank.cbs.domain.enums.TransactionChannel;
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
    // TransactionChannel channel,
    ChannelSummary    channel,
    String            description,
    OffsetDateTime    initiatedAt,
    OffsetDateTime    completedAt
) {
    public record ChannelSummary(UUID channelId, String code, String name) {}

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
            t.getTransactionId(), t.getReferenceNumber(),
            t.getDebitAccount()  != null ? t.getDebitAccount().getAccountNumber()  : null,
            t.getCreditAccount() != null ? t.getCreditAccount().getAccountNumber() : null,
            t.getTransactionType(), t.getAmount(),
            t.getCurrency().getCurrencyCode(),
            t.getStatus(), 
            // t.getChannel(),
            t.getChannel() != null ? new ChannelSummary(t.getChannel().getChannelId(), t.getChannel().getCode(), t.getChannel().getName()) : null,
            t.getDescription(),
            t.getInitiatedAt(), t.getCompletedAt()
        );
    }   
}
