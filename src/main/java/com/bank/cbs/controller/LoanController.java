package com.bank.cbs.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.dto.request.CreateLoanRequest;
import com.bank.cbs.dto.request.RecordLoanPaymentRequest;
import com.bank.cbs.dto.request.RejectLoanRequest;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.dto.response.LoanPaymentResponse;
import com.bank.cbs.dto.response.LoanResponse;
import com.bank.cbs.service.LoanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan management APIs, including application, approval, and repayment.")
public class LoanController {
    private final LoanService loanService;

    @PostMapping("/customers/{customerId}")
    public ResponseEntity<ApiResponse<LoanResponse>> apply(@PathVariable UUID customerId, @Valid @RequestBody CreateLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Loan application submitted", loanService.apply(customerId, request)));
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<ApiResponse<List<LoanResponse>>> findByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.findByCustomer(customerId)));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<ApiResponse<LoanResponse>> findById(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.findById(loanId)));
    }

    @PatchMapping("/{loanId}/approve")
    public ResponseEntity<ApiResponse<LoanResponse>> approve(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Loan approved", loanService.approve(loanId, null)));
    }

    @PatchMapping("/{loanId}/reject")
    public ResponseEntity<ApiResponse<LoanResponse>> reject(@PathVariable UUID loanId, @RequestBody RejectLoanRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Loan rejected", loanService.reject(loanId, body.reason())));
    }

    @PatchMapping("/{loanId}/disburse")
    public ResponseEntity<ApiResponse<LoanResponse>> disburse(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Loan disbursed", loanService.disburse(loanId)));
    }

    @PostMapping("/{loanId}/payments")
    public ResponseEntity<ApiResponse<LoanResponse>> recordPayment(@PathVariable UUID loanId, @Valid @RequestBody RecordLoanPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment recorded", loanService.recordPayment(loanId, request)));
    }

    @GetMapping("/{loanId}/payments")
    public ResponseEntity<ApiResponse<List<LoanPaymentResponse>>> payments(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.paymentHistory(loanId)));
    }
}
