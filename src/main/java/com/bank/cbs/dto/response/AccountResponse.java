package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.enums.AccountStatus;
// import com.bank.cbs.domain.enums.AccountType;

public record AccountResponse(
    UUID accountId,
    String accountNumber, 
    UUID customerId,
    AccountTypeSummary accountType,
    String currencyCode, 
    BigDecimal balance, 
    BigDecimal availableBalance, 
    BigDecimal holdBalance,
    AccountStatus status, 
    BigDecimal dailyLimit,
    OffsetDateTime openedAt,
    OffsetDateTime createdAt
) {
    public record AccountTypeSummary(UUID accountTypeId, String code, String name, boolean isCreditNature) {}

    public static AccountResponse from(Account a) {
        return new AccountResponse(
            a.getAccountId(), a.getAccountNumber(), a.getCustomer().getCustomerId(),
            new AccountTypeSummary(a.getAccountType().getAccountTypeId(),
             a.getAccountType().getCode(), 
             a.getAccountType().getName(), 
             a.getAccountType().isCreditNature()),
            a.getCurrency().getCurrencyCode(), a.getBalance(), a.getAvailableBalance(), a.getHoldBalance(),
            a.getStatus(), a.getDailyLimit(), a.getOpenedAt(), a.getCreatedAt()
        );
    }
}
