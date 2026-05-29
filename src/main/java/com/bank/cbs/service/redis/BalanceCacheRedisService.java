package com.bank.cbs.service.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bank.cbs.config.CbsProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BalanceCacheRedisService {
    private static final String PREFIX = "balance:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CbsProperties cbsProperties;

    public void cache(String accountId, BigDecimal balance) {
        stringRedisTemplate.opsForValue().set(
            buildKey(accountId), balance.toPlainString(),
            Duration.ofSeconds(cbsProperties.redis().ttl().balanceCacheSeconds())
        );
    }

    public Optional<BigDecimal> get(String accountId) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(accountId));
        return Optional.ofNullable(value).map(BigDecimal::new);
    }

    public void evict(String accountId) {
        stringRedisTemplate.delete(buildKey(accountId));
    }

    private String buildKey(String accountId) {
        return PREFIX + accountId;
    }
}
