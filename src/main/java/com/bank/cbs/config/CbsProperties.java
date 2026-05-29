package com.bank.cbs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.data.redis.support.collections.RedisProperties;

@ConfigurationProperties(prefix = "cbs")
public record CbsProperties(
    RedisProperties redis,
    TransactionProperties transaction,
    SecurityProperties security
) {
    
   public record RedisProperties(TtlProperties ttl) {
        public record TtlProperties(
            long sessionSeconds,
            long otpSeconds,
            long balanceCacheSeconds,
            long idempotencySeconds,
            long rateLimitSeconds,
            long lockSeconds
        ) {}
    }

    public record TransactionProperties(
        java.math.BigDecimal maxDailyLimit,
        java.math.BigDecimal maxSingleTransfer
    ) {}

    public record SecurityProperties(JwtProperties jwt) {
        public record JwtProperties(String secret, long expirationSeconds) {}
    } 
}
