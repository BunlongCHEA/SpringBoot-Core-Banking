package com.bank.cbs.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.dto.request.TransactionRequest;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.dto.response.PageResponse;
import com.bank.cbs.dto.response.TransactionResponse;
import com.bank.cbs.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management APIs, including transfers, deposits, and withdrawals.")
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    @Operation(summary = "Fund transfer between accounts")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Transfer completed", transactionService.transfer(request)));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds into account")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Deposit completed", transactionService.deposit(request)));
    }

    @PostMapping("/withdrawal")
    @Operation(summary = "Withdraw funds from account")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdrawal(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Withdrawal completed", transactionService.withdrawal(request)));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get transaction history for an account")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = transactionService.getAccountTransactions(
            accountId, PageRequest.of(page, size, Sort.by("initiatedAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }
}
