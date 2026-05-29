package com.bank.cbs.service.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bank.cbs.config.CbsProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionRedisService {
    private static final String PREFIX = "session:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CbsProperties cbsProperties;

    public void save(String userId, String deviceId, String token) {
        String key = buildKey(userId, deviceId);
        stringRedisTemplate.opsForValue().set(
            key, token,
            Duration.ofSeconds(cbsProperties.redis().ttl().sessionSeconds())
        );
    }

    public Optional<String> get(String userId, String deviceId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(buildKey(userId, deviceId)));
    }

    public void refresh(String userId, String deviceId) {
        stringRedisTemplate.expire(
            buildKey(userId, deviceId),
            Duration.ofSeconds(cbsProperties.redis().ttl().sessionSeconds())
        );
    }

    public void invalidate(String userId, String deviceId) {
        stringRedisTemplate.delete(buildKey(userId, deviceId));
    }

    public void invalidateAll(String userId) {
        var keys = stringRedisTemplate.keys(PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private String buildKey(String userId, String deviceId) {
        return PREFIX + userId + ":" + deviceId;
    }
}
