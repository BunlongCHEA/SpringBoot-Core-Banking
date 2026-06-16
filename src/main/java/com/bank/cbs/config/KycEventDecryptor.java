package com.bank.cbs.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bank.cbs.dto.event.KYCEventEnvelope;
import com.bank.cbs.dto.event.KycStatusPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KycEventDecryptor {
    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final ObjectMapper  objectMapper;

    public KycEventDecryptor(
            KycProperties kycProperties, 
            ObjectMapper objectMapper
    ) {
        byte[] aes  = Base64.getDecoder().decode(kycProperties.getMq().getAesKey());
        byte[] hmac = Base64.getDecoder().decode(kycProperties.getMq().getHmacKey());

        if (aes.length != 32 || hmac.length != 32)
            throw new IllegalStateException("MQ keys must be 32 bytes");
        this.aesKey  = new SecretKeySpec(aes,  "AES");
        this.hmacKey = new SecretKeySpec(hmac, "HmacSHA256");
        this.objectMapper = objectMapper;
    }

    public boolean verifySignature(KYCEventEnvelope env) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            String canonical = env.messageId() + "|" + env.timestamp() + "|" + env.ciphertext();
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual   = Base64.getDecoder().decode(env.signature());
            return MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (Exception e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    public KycStatusPayload decrypt(KYCEventEnvelope env) throws Exception {
        byte[] sealed = Base64.getDecoder().decode(env.ciphertext());
        int nonceLen  = 12; // GCM standard nonce size
        GCMParameterSpec spec = new GCMParameterSpec(128, sealed, 0, nonceLen);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        
        byte[] plaintext = cipher.doFinal(sealed, nonceLen, sealed.length - nonceLen);
        return objectMapper.readValue(plaintext, KycStatusPayload.class);
    }
}
