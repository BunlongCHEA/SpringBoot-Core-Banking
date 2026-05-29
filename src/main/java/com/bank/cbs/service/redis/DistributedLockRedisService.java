package com.bank.cbs.service.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bank.cbs.config.CbsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockRedisService {
    private static final String PREFIX       = "lock:transfer:";
    private static final String LOCK_VALUE   = "LOCKED";

    private final StringRedisTemplate stringRedisTemplate;
    private final CbsProperties cbsProperties;

    public boolean acquireLock(String accountId) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
            buildKey(accountId), LOCK_VALUE,
            Duration.ofSeconds(cbsProperties.redis().ttl().lockSeconds())
        );
        boolean result = Boolean.TRUE.equals(acquired);
        if (!result) {
            log.warn("Failed to acquire lock for account: {}", accountId);
        }
        return result;
    }

    public void releaseLock(String accountId) {
        stringRedisTemplate.delete(buildKey(accountId));
    }

    public boolean isLocked(String accountId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildKey(accountId)));
    }

    public void acquireLockOrThrow(String accountId) {
        if (!acquireLock(accountId)) {
            throw new com.bank.cbs.exception.AccountLockedException(
                "Account is currently locked for processing: " + accountId
            );
        }
    }

    private String buildKey(String accountId) {
        return PREFIX + accountId;
    }
}
