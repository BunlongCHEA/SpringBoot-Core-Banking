package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.Loan;
import com.bank.cbs.domain.enums.LoanStatus;

public record LoanResponse(
    UUID loanId, String loanNumber, UUID customerId, UUID accountId,
    BigDecimal principal, BigDecimal outstandingBalance, BigDecimal interestRate,
    Integer termMonths, BigDecimal monthlyInstallment, String currencyCode,
    LoanStatus status, boolean overdue,
    OffsetDateTime disbursedAt, LocalDate maturityDate, LocalDate nextPaymentDate,
    String rejectionReason
) {
    public static LoanResponse from(Loan l) {
        boolean overdue = (l.getStatus() == LoanStatus.DISBURSED || l.getStatus() == LoanStatus.ACTIVE)
            && l.getNextPaymentDate() != null
            && l.getNextPaymentDate().isBefore(LocalDate.now());

        UUID customerId = l.getDisbursementAccount().getCustomer().getCustomerId();   // always non-null from apply() onward
        UUID accountId = l.getAccount() != null ? l.getAccount().getAccountId() : null;   // null until approved

        return new LoanResponse(
            l.getLoanId(), l.getLoanNumber(), customerId, accountId,
            l.getPrincipal(), l.getOutstandingBalance(), l.getInterestRate(),
            l.getTermMonths(), l.getMonthlyInstallment(), l.getCurrency().getCurrencyCode(),
            l.getStatus(), overdue,
            l.getDisbursedAt(), l.getMaturityDate(), l.getNextPaymentDate(),
            l.getRejectionReason()
        );
    }
}
