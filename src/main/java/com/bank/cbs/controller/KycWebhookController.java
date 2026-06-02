package com.bank.cbs.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.dto.request.KycStatusWebhookRequest;
import com.bank.cbs.service.CustomerService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal webhook endpoint called by Go-Blockchain-KYC when a customer's
 * KYC status changes to EXPIRED or SUSPENDED.
 *
 * Protected by a shared secret header (X-Webhook-Api-Key).
 * This endpoint is NOT exposed through the public API gateway.
 */
@Slf4j
@RestController
@RequestMapping("/internal/webhook/kyc")
public class KycWebhookController {
    @Value("${go-kyc.webhook-secret}")
    private String webhookSecret;

    private final CustomerService customerService;

    public KycWebhookController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/status-changed")
    public ResponseEntity<Void> onStatusChanged(
            @RequestHeader("X-Webhook-Api-Key") String apiKey,
            @Valid @RequestBody KycStatusWebhookRequest request) {

        if (!webhookSecret.equals(apiKey)) {
            log.warn("Rejected KYC webhook call — invalid API key");
            return ResponseEntity.status(401).build();
        }

        String status = request.kycStatus().toUpperCase();
        if (!"EXPIRED".equals(status) && !"SUSPENDED".equals(status)) {
            // Only act on EXPIRED / SUSPENDED; silently accept others
            return ResponseEntity.ok().build();
        }

        log.info("KYC webhook received: customerId={} kycStatus={}",
                request.customerId(), request.kycStatus());

        // Both EXPIRED and SUSPENDED in Go_KYC → SUSPENDED in CBS
        customerService.updateStatusByCustomerCode(request.customerId(), CustomerStatus.SUSPENDED);

        return ResponseEntity.ok().build();
    }
}
