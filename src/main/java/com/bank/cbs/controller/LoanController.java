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
import com.bank.cbs.dto.response.ApiResponse;
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

    @PostMapping("/accounts/{accountId}")
    @Operation(summary = "Apply for a loan")
    public ResponseEntity<ApiResponse<LoanResponse>> apply(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Loan application submitted", loanService.apply(accountId, request)));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan by ID")
    public ResponseEntity<ApiResponse<LoanResponse>> findById(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.findById(loanId)));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get loans for an account")
    public ResponseEntity<ApiResponse<List<LoanResponse>>> findByAccount(
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.findByAccount(accountId)));
    }

    @PatchMapping("/{loanId}/disburse")
    @Operation(summary = "Disburse an approved loan")
    public ResponseEntity<ApiResponse<LoanResponse>> disburse(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("Loan disbursed", loanService.disburse(loanId)));
    }
}
