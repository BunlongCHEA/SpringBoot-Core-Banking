package com.bank.cbs.service.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bank.cbs.config.CbsProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimitRedisService {

    private static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CbsProperties cbsProperties;

    public boolean isAllowed(String ip, String endpoint, int maxRequests) {
        String key = PREFIX + ip + ":" + endpoint;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(
                key, Duration.ofSeconds(cbsProperties.redis().ttl().rateLimitSeconds())
            );
        }
        return count != null && count <= maxRequests;
    }

    public long getCount(String ip, String endpoint) {
        String value = stringRedisTemplate.opsForValue().get(PREFIX + ip + ":" + endpoint);
        return value == null ? 0L : Long.parseLong(value);
    }

    public void reset(String ip, String endpoint) {
        stringRedisTemplate.delete(PREFIX + ip + ":" + endpoint);
    }
}
