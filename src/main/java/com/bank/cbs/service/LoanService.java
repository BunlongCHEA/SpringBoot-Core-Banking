package com.bank.cbs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.Account;
import com.bank.cbs.domain.entity.Loan;
import com.bank.cbs.domain.enums.LoanStatus;
import com.bank.cbs.dto.request.CreateLoanRequest;
import com.bank.cbs.dto.response.LoanResponse;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.CurrencyRepository;
import com.bank.cbs.repository.jpa.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {
    private final LoanRepository     loanRepository;
    private final CurrencyRepository currencyRepository;
    private final AccountService     accountService;

    @Transactional
    public LoanResponse apply(UUID accountId, CreateLoanRequest request) {
        Account account = accountService.getOrThrow(accountId);
        var currency = currencyRepository.findById(request.currencyCode())
            .orElseThrow(() -> new ResourceNotFoundException("Currency not found: " + request.currencyCode()));

        BigDecimal monthly = calculateMonthlyInstallment(
            request.principal(), request.interestRate(), request.termMonths()
        );

        Loan loan = Loan.builder()
            .loanNumber(generateLoanNumber())
            .account(account)
            .principal(request.principal())
            .outstandingBalance(request.principal())
            .interestRate(request.interestRate())
            .termMonths(request.termMonths())
            .monthlyInstallment(monthly)
            .currency(currency)
            .status(LoanStatus.PENDING)
            .build();

        Loan saved = loanRepository.save(loan);
        log.info("Loan application created: {}", saved.getLoanNumber());
        return LoanResponse.from(saved);
    }

    @Transactional
    public LoanResponse disburse(UUID loanId) {
        Loan loan = getOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new BusinessException("Loan is not in PENDING status");
        }
        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDisbursedAt(OffsetDateTime.now());
        loan.setMaturityDate(LocalDate.now().plusMonths(loan.getTermMonths()));
        loan.setNextPaymentDate(LocalDate.now().plusMonths(1));

        // Credit principal to account
        Account account = loan.getAccount();
        account.credit(loan.getPrincipal());
        accountService.getOrThrow(account.getAccountId());

        log.info("Loan disbursed: {}", loan.getLoanNumber());
        return LoanResponse.from(loanRepository.save(loan));
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> findByAccount(UUID accountId) {
        return loanRepository.findByAccount_AccountId(accountId)
            .stream().map(LoanResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse findById(UUID loanId) {
        return LoanResponse.from(getOrThrow(loanId));
    }

    private Loan getOrThrow(UUID loanId) {
        return loanRepository.findById(loanId)
            .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanId));
    }

    private BigDecimal calculateMonthlyInstallment(BigDecimal principal,
                                                    BigDecimal annualRate,
                                                    int termMonths) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 4, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR    = BigDecimal.ONE.add(monthlyRate);
        double     pow         = Math.pow(onePlusR.doubleValue(), termMonths);
        BigDecimal numerator   = principal.multiply(monthlyRate).multiply(BigDecimal.valueOf(pow));
        BigDecimal denominator = BigDecimal.valueOf(pow - 1);
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private String generateLoanNumber() {
        return "LN" + System.currentTimeMillis()
            + ThreadLocalRandom.current().nextInt(100, 999);
    }
}
