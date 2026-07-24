package com.bank.cbs.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.entity.AccountType;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.repository.jpa.AccountTypeRepository;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/account-types")
@RequiredArgsConstructor
public class AccountTypeController {
    private final AccountTypeRepository accountTypeRepository;

    @GetMapping
    @Operation(summary = "List active account types")
    public ResponseEntity<ApiResponse<List<AccountType>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(accountTypeRepository.findByIsActiveTrue()));
    }
}
