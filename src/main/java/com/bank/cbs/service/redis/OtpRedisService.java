package com.bank.cbs.service.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.bank.cbs.config.CbsProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpRedisService {
    private static final String PREFIX = "otp:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CbsProperties cbsProperties;

    public void save(String customerId, String channel, String otp) {
        stringRedisTemplate.opsForValue().set(
            buildKey(customerId, channel), otp,
            Duration.ofSeconds(cbsProperties.redis().ttl().otpSeconds())
        );
    }

    public Optional<String> get(String customerId, String channel) {
        return Optional.ofNullable(
            stringRedisTemplate.opsForValue().get(buildKey(customerId, channel))
        );
    }

    public boolean verify(String customerId, String channel, String otp) {
        String stored = stringRedisTemplate.opsForValue().get(buildKey(customerId, channel));
        if (otp.equals(stored)) {
            invalidate(customerId, channel);
            return true;
        }
        return false;
    }

    public void invalidate(String customerId, String channel) {
        stringRedisTemplate.delete(buildKey(customerId, channel));
    }

    public boolean exists(String customerId, String channel) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildKey(customerId, channel)));
    }

    private String buildKey(String customerId, String channel) {
        return PREFIX + customerId + ":" + channel;
    }
}
