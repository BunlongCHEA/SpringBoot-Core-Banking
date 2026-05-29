package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.enums.AccountType;

public record AccountResponse(
    UUID          accountId,
    String        accountNumber,
    UUID          customerId,
    AccountType   accountType,
    String        currencyCode,
    BigDecimal    balance,
    BigDecimal    availableBalance,
    BigDecimal    holdBalance,
    AccountStatus status,
    BigDecimal    dailyLimit,
    OffsetDateTime openedAt,
    OffsetDateTime createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
            a.getAccountId(), a.getAccountNumber(),
            a.getCustomer().getCustomerId(),
            a.getAccountType(), a.getCurrency().getCurrencyCode(),
            a.getBalance(), a.getAvailableBalance(), a.getHoldBalance(),
            a.getStatus(), a.getDailyLimit(), a.getOpenedAt(), a.getCreatedAt()
        );
    }
}
