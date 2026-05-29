package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Loan;
import com.bank.cbs.domain.enums.LoanStatus;

public record LoanResponse(
    UUID       loanId,
    String     loanNumber,
    UUID       accountId,
    BigDecimal principal,
    BigDecimal outstandingBalance,
    BigDecimal interestRate,
    Integer    termMonths,
    BigDecimal monthlyInstallment,
    String     currencyCode,
    LoanStatus status,
    OffsetDateTime disbursedAt,
    LocalDate  maturityDate,
    LocalDate  nextPaymentDate
)  {
    public static LoanResponse from(Loan l) {
        return new LoanResponse(
            l.getLoanId(), l.getLoanNumber(),
            l.getAccount().getAccountId(),
            l.getPrincipal(), l.getOutstandingBalance(),
            l.getInterestRate(), l.getTermMonths(),
            l.getMonthlyInstallment(),
            l.getCurrency().getCurrencyCode(),
            l.getStatus(), l.getDisbursedAt(),
            l.getMaturityDate(), l.getNextPaymentDate()
        );
    }
}
