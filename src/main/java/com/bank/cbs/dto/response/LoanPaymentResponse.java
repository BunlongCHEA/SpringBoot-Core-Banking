package com.bank.cbs.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.LoanPayment;

public record LoanPaymentResponse(
    UUID loanPaymentId, BigDecimal amount, BigDecimal principalPortion,
    BigDecimal interestPortion, BigDecimal outstandingAfter, OffsetDateTime paidAt
) {
    public static LoanPaymentResponse from(LoanPayment p) {
        return new LoanPaymentResponse(p.getLoanPaymentId(), p.getAmount(), p.getPrincipalPortion(),
            p.getInterestPortion(), p.getOutstandingAfter(), p.getPaidAt());
    }
}
