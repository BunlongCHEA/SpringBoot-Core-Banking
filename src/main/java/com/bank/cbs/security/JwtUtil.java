package com.bank.cbs.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.bank.cbs.config.CbsProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final CbsProperties cbsProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
            cbsProperties.security().jwt().secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generate(String subject, String role) {
        long now = System.currentTimeMillis();
        long expMs = cbsProperties.security().jwt().expirationSeconds() * 1000L;
        return Jwts.builder()
            .subject(subject)
            .claim("role", role)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expMs))
            .signWith(getSigningKey())
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String getSubject(String token) {
        return parse(token).getSubject();
    }
}
