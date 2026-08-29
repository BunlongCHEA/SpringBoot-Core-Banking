package com.bank.cbs.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bank.cbs.domain.entity.Loan;
import com.bank.cbs.domain.enums.LoanStatus;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByAccount_AccountId(UUID accountId);
    Optional<Loan> findByLoanNumber(String loanNumber);
    List<Loan> findByStatus(LoanStatus status);
    // List<Loan> findByAccount_Customer_CustomerId(UUID customerId);
    List<Loan> findByDisbursementAccount_Customer_CustomerId(UUID customerId);
}
