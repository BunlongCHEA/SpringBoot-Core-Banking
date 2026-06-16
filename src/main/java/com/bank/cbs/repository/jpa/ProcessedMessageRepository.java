package com.bank.cbs.repository.jpa;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProcessedMessageRepository {
    private static final String PREFIX = "kyc:mq:processed:";

    private final StringRedisTemplate redisTemplate;

    public boolean exists(String messageId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + messageId));
    }

    public void markProcessed(String messageId, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + messageId, "1", ttl);
    }
}
