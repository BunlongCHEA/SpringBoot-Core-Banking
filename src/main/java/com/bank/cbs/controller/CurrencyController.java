package com.bank.cbs.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.entity.Currency;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.repository.jpa.CurrencyRepository;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/currencies")
@RequiredArgsConstructor
public class CurrencyController {
    private final CurrencyRepository currencyRepository;

    @GetMapping
    @Operation(summary = "List active currencies")
    public ResponseEntity<ApiResponse<List<Currency>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(currencyRepository.findByIsActiveTrue()));
    }
}
