package com.bank.cbs.config;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.bank.cbs.dto.event.KYCEventEnvelope;
import com.bank.cbs.dto.event.KycStatusPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class KycEventDecryptor {

    private static final int NONCE_LEN   = 12;  // GCM standard
    private static final int TAG_BITS    = 128; // GCM auth tag length

    private final Map<String, SecretKeySpec> keysByVersion = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public KycEventDecryptor(KycProperties kycProperties, ObjectMapper objectMapper) throws Exception {
        this.objectMapper = objectMapper;
 
        // BUG FIX: guard against null/blank keysJson.
        // kyc.mq.keys-json is optional in dev; if absent the bean still
        // initialises, but decryptAndVerify() will throw at call-time.
        String keysJson = kycProperties.getMq().getKeysJson();
        if (keysJson == null || keysJson.isBlank()) {
            log.warn("[KycEventDecryptor] kyc.mq.keys-json is not configured — "
                   + "MQ message decryption will be unavailable until the property is set.");
            return;
        }
 
        Map<String, String> raw = objectMapper.readValue(
                keysJson,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
 
        for (var entry : raw.entrySet()) {
            byte[] keyBytes = Base64.getDecoder().decode(entry.getValue());
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                    "MQ key " + entry.getKey() + " must decode to 32 bytes, got " + keyBytes.length);
            }
            keysByVersion.put(entry.getKey(), new SecretKeySpec(keyBytes, "AES"));
        }
        log.info("[KycEventDecryptor] loaded {} MQ key version(s): {}",
                 keysByVersion.size(), keysByVersion.keySet());
    }

    /**
     * Decrypts and authenticates in one step. GCM's Open() (Java: doFinal)
     * fails with AEADBadTagException if EITHER the ciphertext OR the AAD
     * (envelope metadata) was tampered with — this replaces the separate
     * HMAC verification step entirely.
     */
    public KycStatusPayload decryptAndVerify(KYCEventEnvelope env) throws GeneralSecurityException, Exception {
        if (keysByVersion.isEmpty()) {
            throw new IllegalStateException(
                "[KycEventDecryptor] No MQ keys loaded — set kyc.mq.keys-json before processing messages.");
        }

        SecretKeySpec key = keysByVersion.get(env.keyVersion());
        if (key == null) {
            throw new IllegalStateException("Unknown MQ key version: " + env.keyVersion()
                + " — key may have rotated out of retention, or config is stale");
        }

        byte[] sealed = Base64.getDecoder().decode(env.ciphertext());

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_LEN);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        // Reconstruct the EXACT same AAD the publisher used — any field
        // mismatch here causes doFinal() to throw AEADBadTagException.
        String aad = env.messageId() + "|" + env.timestamp() + "|" + env.eventType() + "|" + env.keyVersion();
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

        byte[] plaintext = cipher.doFinal(sealed, NONCE_LEN, sealed.length - NONCE_LEN);
        return objectMapper.readValue(plaintext, KycStatusPayload.class);
    }
}