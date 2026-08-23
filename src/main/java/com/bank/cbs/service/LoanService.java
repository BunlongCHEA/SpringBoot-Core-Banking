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
import com.bank.cbs.domain.entity.AccountType;
import com.bank.cbs.domain.entity.Currency;
import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.entity.Loan;
import com.bank.cbs.domain.entity.LoanPayment;
import com.bank.cbs.domain.entity.Transaction;
import com.bank.cbs.domain.enums.AccountStatus;
import com.bank.cbs.domain.enums.LoanStatus;
import com.bank.cbs.dto.request.CreateLoanRequest;
import com.bank.cbs.dto.request.RecordLoanPaymentRequest;
import com.bank.cbs.dto.response.LoanPaymentResponse;
import com.bank.cbs.dto.response.LoanResponse;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.AccountRepository;
import com.bank.cbs.repository.jpa.AccountTypeRepository;
import com.bank.cbs.repository.jpa.CurrencyRepository;
import com.bank.cbs.repository.jpa.LoanPaymentRepository;
import com.bank.cbs.repository.jpa.LoanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {
    private final LoanRepository loanRepository;
    private final LoanPaymentRepository loanPaymentRepository;
    private final AccountRepository accountRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final AccountService accountService;

    @Transactional
    public LoanResponse apply(UUID customerId, CreateLoanRequest request) {
        Customer customer = customerService.getOrThrow(customerId);
        Currency currency = currencyRepository.findById(request.currencyCode())
            .orElseThrow(() -> new BusinessException("Unknown currency: " + request.currencyCode()));
        Account disbursementAccount = accountRepository.findByAccountNumber(request.disbursementAccountNumber())
            .orElseThrow(() -> new BusinessException("Unknown disbursement account: " + request.disbursementAccountNumber()));
        if (!disbursementAccount.getCustomer().getCustomerId().equals(customerId)) {
            throw new BusinessException("Disbursement account does not belong to this customer");
        }

        AccountType loanType = accountTypeRepository.findByCode("LOAN")
            .orElseThrow(() -> new IllegalStateException("LOAN account type missing from account_types"));

        // The loan's own backing account — tracks outstanding balance only.
        // Never touched by ordinary deposit/withdraw/transfer; it is asset-natured
        // (opposite normal-balance direction from a deposit account), so it's
        // deliberately excluded from the generic credit()/debit() flow.
        Account loanAccount = Account.builder()
            .accountNumber(accountService.generateAccountNumber())
            .customer(customer)
            .accountType(loanType)
            .currency(currency)
            .balance(BigDecimal.ZERO).availableBalance(BigDecimal.ZERO).holdBalance(BigDecimal.ZERO)
            .status(AccountStatus.ACTIVE)
            .openedAt(OffsetDateTime.now())
            .build();
        accountRepository.save(loanAccount);

        BigDecimal monthly = calculateMonthlyInstallment(request.principal(), request.interestRate(), request.termMonths());

        Loan loan = Loan.builder()
            .loanNumber(generateLoanNumber())
            .account(loanAccount)
            .disbursementAccount(disbursementAccount)
            .principal(request.principal())
            .outstandingBalance(request.principal())
            .interestRate(request.interestRate())
            .termMonths(request.termMonths())
            .monthlyInstallment(monthly)
            .currency(currency)
            .status(LoanStatus.PENDING)
            .build();

        Loan saved = loanRepository.save(loan);
        log.info("Loan application created: {} for customer {}", saved.getLoanNumber(), customerId);
        return LoanResponse.from(saved);
    }

    @Transactional
    public LoanResponse approve(UUID loanId, UUID approvedByUserId) {
        Loan loan = getOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new BusinessException("Only a PENDING loan can be approved. Current status: " + loan.getStatus());
        }
        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedAt(OffsetDateTime.now());
        // set loan.approvedBy from a User lookup by approvedByUserId if you thread the acting user through
        return LoanResponse.from(loanRepository.save(loan));
    }

    @Transactional
    public LoanResponse reject(UUID loanId, String reason) {
        Loan loan = getOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new BusinessException("Only a PENDING loan can be rejected. Current status: " + loan.getStatus());
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectedAt(OffsetDateTime.now());
        loan.setRejectionReason(reason);
        return LoanResponse.from(loanRepository.save(loan));
    }

    @Transactional
    public LoanResponse disburse(UUID loanId) {
        Loan loan = getOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new BusinessException("Only an APPROVED loan can be disbursed. Current status: " + loan.getStatus());
        }

        // Real ledger transaction — principal lands in the customer's actual
        // deposit account, fully auditable in transaction history, same as
        // any other credit. No more silent account.credit() bypass.
        transactionService.internalDeposit(
            loan.getDisbursementAccount().getAccountNumber(),
            loan.getPrincipal(),
            loan.getCurrency().getCurrencyCode(),
            "Loan disbursement " + loan.getLoanNumber()
        );

        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDisbursedAt(OffsetDateTime.now());
        loan.setMaturityDate(LocalDate.now().plusMonths(loan.getTermMonths()));
        loan.setNextPaymentDate(LocalDate.now().plusMonths(1));

        log.info("Loan disbursed: {}", loan.getLoanNumber());
        return LoanResponse.from(loanRepository.save(loan));
    }

    @Transactional
    public LoanResponse recordPayment(UUID loanId, RecordLoanPaymentRequest request) {
        Loan loan = getOrThrow(loanId);
        if (loan.getStatus() != LoanStatus.DISBURSED && loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessException("Loan is not open for repayment. Current status: " + loan.getStatus());
        }
        if (request.amount().compareTo(loan.getOutstandingBalance()) > 0) {
            throw new BusinessException("Payment exceeds outstanding balance ("
                + loan.getOutstandingBalance() + " " + loan.getCurrency().getCurrencyCode() + ")");
        }

        // Real withdrawal from the customer's paying account — correct semantics,
        // since a deposit account IS credit-natured and withdrawal correctly decreases it.
        Transaction txn = transactionService.internalWithdrawal(
            request.payingAccountNumber(),
            request.amount(),
            loan.getCurrency().getCurrencyCode(),
            "Loan repayment " + loan.getLoanNumber()
        );

        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal interestPortion = loan.getOutstandingBalance().multiply(monthlyRate).setScale(4, RoundingMode.HALF_UP);
        if (interestPortion.compareTo(request.amount()) > 0) interestPortion = request.amount();
        BigDecimal principalPortion = request.amount().subtract(interestPortion);

        BigDecimal newOutstanding = loan.getOutstandingBalance().subtract(principalPortion);
        loan.setOutstandingBalance(newOutstanding);

        // Loan's own backing account balance mirrors outstanding — deliberately
        // NOT run through Account.credit()/debit(), since those assume
        // liability-nature (increase = credit). This account is asset-natured;
        // outstanding always moves the opposite direction from a normal credit.
        Account loanAccount = loan.getAccount();
        loanAccount.setBalance(newOutstanding);
        loanAccount.setAvailableBalance(newOutstanding);
        accountRepository.save(loanAccount);

        if (loan.getStatus() == LoanStatus.DISBURSED) {
            loan.setStatus(LoanStatus.ACTIVE);   // first payment received
        }

        if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setNextPaymentDate(null);
        } else {
            loan.setNextPaymentDate(loan.getNextPaymentDate().plusMonths(1));
        }

        loanPaymentRepository.save(LoanPayment.builder()
            .loan(loan)
            .transactionId(txn.getTransactionId())
            .amount(request.amount())
            .principalPortion(principalPortion)
            .interestPortion(interestPortion)
            .outstandingAfter(newOutstanding)
            .paidAt(OffsetDateTime.now())
            .build());

        return LoanResponse.from(loanRepository.save(loan));
    }

    @Transactional(readOnly = true)
    public List<LoanPaymentResponse> paymentHistory(UUID loanId) {
        return loanPaymentRepository.findByLoan_LoanIdOrderByPaidAtDesc(loanId)
            .stream().map(LoanPaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> findByCustomer(UUID customerId) {
        return loanRepository.findByAccount_Customer_CustomerId(customerId)
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

    private BigDecimal calculateMonthlyInstallment(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 4, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        double pow = Math.pow(onePlusR.doubleValue(), termMonths);
        return principal.multiply(monthlyRate).multiply(BigDecimal.valueOf(pow))
            .divide(BigDecimal.valueOf(pow - 1), 4, RoundingMode.HALF_UP);
    }

    private String generateLoanNumber() {
        return "LN" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }
    
    // public String generateAccountNumber() {
    //     String number;
    //     do {
    //         number = String.valueOf(ThreadLocalRandom.current().nextLong(1000000000000000L, 9999999999999999L));
    //         number = number.substring(0, 16);
    //     } while (accountRepository.existsByAccountNumber(number));
    //     return number;
    // }
}