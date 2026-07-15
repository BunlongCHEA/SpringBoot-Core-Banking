package com.bank.cbs.config;

import java.time.Duration;
import java.time.Instant;

import javax.crypto.AEADBadTagException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class KycEventConsumer {
    private final CustomerService customerService;
    private final KycEventDecryptor decryptor;
    private final ProcessedMessageRepository processedMessages; // Redis or DB idempotency store
    private final ObjectMapper ObjectMapper; // For JSON parsing

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onKycStatusChanged(
            @Payload String rawEnvelope,
            @Header(AmqpHeaders.MESSAGE_ID) String messageId) {

        // ── Idempotency check ─────────────────────────────────────────
        if (processedMessages.exists(messageId)) {
            log.info("Duplicate message {} — skipping", messageId);
            return;
        }

        // ── Parse outer envelope ──────────────────────────────────────
        KYCEventEnvelope env;
        try {
            env = ObjectMapper.readValue(rawEnvelope, KYCEventEnvelope.class);
        } catch (Exception e) {
            log.error("Malformed envelope, sending to DLQ: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Malformed envelope", e);
        }

        // ── Replay-attack guard (±5 min clock tolerance) ──────────────
        long ageSec = Instant.now().getEpochSecond() - env.timestamp();
        if (Math.abs(ageSec) > 300) {
            log.warn("Message {} is {} s old — rejected as stale", messageId, ageSec);
            throw new AmqpRejectAndDontRequeueException("Stale message");
        }

        // ── Single call: decrypts AND authenticates (GCM tag covers ciphertext + AAD) ──
        KycStatusPayload payload;
        try {
            payload = decryptor.decryptAndVerify(env);
        } catch (AEADBadTagException e) {
            log.error("AUTHENTICATION FAILED for message {} — ciphertext or metadata tampered!", messageId);
            throw new AmqpRejectAndDontRequeueException("GCM auth tag mismatch", e);
        } catch (Exception e) {
            log.error("Decryption failed for {}: {}", messageId, e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Decryption failed", e);
        }

        // ── Business logic ────────────────────────────────────────────
        log.info("KYC event: customerId={} status={} keyVersion={}",
            payload.customerId(), payload.kycStatus(), env.keyVersion());

        customerService.updateStatusByCustomerCode(payload.customerId(), CustomerStatus.SUSPENDED);

        // ── 7. Mark processed (idempotency) ─────────────────────────────
        processedMessages.markProcessed(messageId, Duration.ofHours(25));
        log.info("KYC event {} processed", messageId);
    }
}
