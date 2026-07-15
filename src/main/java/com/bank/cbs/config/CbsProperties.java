package com.bank.cbs.config;

import java.util.List;

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

    public record SecurityProperties(JwtProperties jwt, CorsProperties cors) {
        public record JwtProperties(String secret, long expirationSeconds) {}

        /**
         * CORS allowed origins loaded from cbs.security.cors.allowed-origins.
         * Add any front-end origin that needs to reach this API:
         *   - http://localhost:3000   (Next.js dev server)
         *   - https://cbs.yourbank.com (production UI)
         */
        public record CorsProperties(List<String> allowedOrigins) {
            // Provide a safe default so the app starts even if the property is missing.
            public CorsProperties {
                if (allowedOrigins == null) allowedOrigins = List.of("http://localhost:3000");
            }
        }
    } 
}
