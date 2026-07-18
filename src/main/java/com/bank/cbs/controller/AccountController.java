package com.bank.cbs.controller;

import java.math.BigDecimal;
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

import com.bank.cbs.dto.request.CreateAccountRequest;
import com.bank.cbs.dto.response.AccountResponse;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management APIs")
public class AccountController {
    private final AccountService accountService;

    @PostMapping("/customers/{customerId}")
    @Operation(summary = "Open a new account for customer")
    public ResponseEntity<ApiResponse<AccountResponse>> create(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Account opened", accountService.create(customerId, request)));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<ApiResponse<AccountResponse>> findById(
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.findById(accountId)));
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(summary = "Get account by account number")
    public ResponseEntity<ApiResponse<AccountResponse>> findByAccountNumber(
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(ApiResponse.ok(AccountResponse.from(
            accountService.getByAccountNumberOrThrow(accountNumber))));
    }

    @GetMapping("/customers/{customerId}")
    @Operation(summary = "Get all accounts for a customer")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> findByCustomer(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.findByCustomer(customerId)));
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get account balance (cached)")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getBalance(accountId)));
    }

    @PatchMapping("/{accountId}/freeze")
    @Operation(summary = "Freeze account")
    public ResponseEntity<ApiResponse<Void>> freeze(@PathVariable UUID accountId) {
        accountService.freeze(accountId);
        return ResponseEntity.ok(ApiResponse.ok("Account frozen", null));
    }

    @PatchMapping("/{accountId}/unfreeze")
    @Operation(summary = "Unfreeze a frozen account")
    public ResponseEntity<ApiResponse<Void>> unfreeze(@PathVariable UUID accountId) {
        accountService.unfreeze(accountId);
        return ResponseEntity.ok(ApiResponse.ok("Account unfrozen", null));
    }

    @PatchMapping("/{accountId}/close")
    @Operation(summary = "Close account")
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable UUID accountId) {
        accountService.close(accountId);
        return ResponseEntity.ok(ApiResponse.ok("Account closed", null));
    }
}
