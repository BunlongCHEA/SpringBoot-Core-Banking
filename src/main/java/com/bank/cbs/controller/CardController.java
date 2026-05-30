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

import com.bank.cbs.dto.request.CreateCardRequest;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.dto.response.CardResponse;
import com.bank.cbs.service.CardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card management APIs, including issuance, activation, and blocking.")
public class CardController {
    private final CardService cardService;

    @PostMapping("/accounts/{accountId}")
    @Operation(summary = "Issue a new card for an account")
    public ResponseEntity<ApiResponse<CardResponse>> issue(
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Card issued", cardService.issue(accountId, request)));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get all cards for an account")
    public ResponseEntity<ApiResponse<List<CardResponse>>> findByAccount(
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok(cardService.findByAccount(accountId)));
    }

    @PatchMapping("/{cardId}/block")
    @Operation(summary = "Block a card")
    public ResponseEntity<ApiResponse<CardResponse>> block(@PathVariable UUID cardId) {
        return ResponseEntity.ok(ApiResponse.ok("Card blocked", cardService.block(cardId)));
    }

    @PatchMapping("/{cardId}/activate")
    @Operation(summary = "Activate a card")
    public ResponseEntity<ApiResponse<CardResponse>> activate(@PathVariable UUID cardId) {
        return ResponseEntity.ok(ApiResponse.ok("Card activated", cardService.activate(cardId)));
    }
}
