package com.bank.cbs.config;

import java.time.Duration;
import java.time.Instant;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import com.bank.cbs.domain.enums.CustomerStatus;
import com.bank.cbs.dto.event.KYCEventEnvelope;
import com.bank.cbs.dto.event.KycStatusPayload;
import com.bank.cbs.repository.jpa.ProcessedMessageRepository;
import com.bank.cbs.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycEventConsumer {
    private final CustomerService customerService;
    private final KycEventDecryptor decryptor;
    private final ProcessedMessageRepository processedMessages; // Redis or DB idempotency store
    private final ObjectMapper ObjectMapper; // For JSON parsing

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onKycStatusChanged(
            @Payload String rawEnvelope,
            @Header(AmqpHeaders.MESSAGE_ID) String messageId) {

        // ── 1. Idempotency check ─────────────────────────────────────────
        if (processedMessages.exists(messageId)) {
            log.info("Duplicate message {} — skipping", messageId);
            return;
        }

        // ── 2. Parse outer envelope ──────────────────────────────────────
        KYCEventEnvelope env;
        try {
            env = ObjectMapper.readValue(rawEnvelope, KYCEventEnvelope.class);
        } catch (Exception e) {
            log.error("Malformed envelope, sending to DLQ: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Malformed envelope", e);
        }

        // ── 3. Replay-attack guard (±5 min clock tolerance) ──────────────
        long ageSec = Instant.now().getEpochSecond() - env.timestamp();
        if (Math.abs(ageSec) > 300) {
            log.warn("Message {} is {} s old — rejected as stale", messageId, ageSec);
            throw new AmqpRejectAndDontRequeueException("Stale message");
        }

        // ── 4. Verify HMAC ───────────────────────────────────────────────
        if (!decryptor.verifySignature(env)) {
            log.error("HMAC verification FAILED for message {} — possible tampering!", messageId);
            throw new AmqpRejectAndDontRequeueException("Signature invalid");
        }

        // ── 5. Decrypt payload ───────────────────────────────────────────
        KycStatusPayload payload;
        try {
            payload = decryptor.decrypt(env);
        } catch (Exception e) {
            log.error("Decryption failed for {}: {}", messageId, e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Decryption failed", e);
        }

        // ── 6. Business logic ────────────────────────────────────────────
        log.info("KYC event: customerId={} status={}", payload.customerId(), payload.kycStatus());
        CustomerStatus cbsStatus = CustomerStatus.SUSPENDED;

        customerService.updateStatusByCustomerCode(payload.customerId(), cbsStatus);

        // ── 7. Mark processed (idempotency) ─────────────────────────────
        processedMessages.markProcessed(messageId, Duration.ofHours(25));
        log.info("KYC event {} processed — customerId={} kycStatus={} → CBS:{}",
                messageId, payload.customerId(), payload.kycStatus(), cbsStatus);
    }
}
