package com.bank.cbs.service.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bank.cbs.config.CbsProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyRedisService {
    private static final String PREFIX = "idempotency:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CbsProperties cbsProperties;

    public boolean exists(String idempotencyKey) {
        return Boolean.TRUE.equals(
            stringRedisTemplate.hasKey(buildKey(idempotencyKey))
        );
    }

    public void save(String idempotencyKey, String transactionId) {
        stringRedisTemplate.opsForValue().set(
            buildKey(idempotencyKey), transactionId,
            Duration.ofSeconds(cbsProperties.redis().ttl().idempotencySeconds())
        );
    }

    public Optional<String> getTransactionId(String idempotencyKey) {
        return Optional.ofNullable(
            stringRedisTemplate.opsForValue().get(buildKey(idempotencyKey))
        );
    }

    private String buildKey(String key) {
        return PREFIX + key;
    }
}
