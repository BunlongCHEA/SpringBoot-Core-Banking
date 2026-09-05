package com.bank.cbs.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bank.cbs.domain.entity.User;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Central place to pull "who did this, from where" out of the current
 * request — used exclusively for audit logging. Reused by every service
 * instead of each one reimplementing SecurityContext/HttpServletRequest
 * plumbing on its own.
 */
@Component
public class SecurityAuditContext {

    /** The currently authenticated user's ID, or null for unauthenticated actions (e.g. a failed login). */
    public UUID currentUserId() {
        Object principal = getPrincipal();
        if (principal instanceof User user) {
            return user.getUserId();
        }
        return null;
    }

    /** The currently authenticated user's role, or null if unauthenticated. */
    public String currentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            return null;
        }
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    /** Caller's IP — checks X-Forwarded-For first (behind a load balancer/proxy in production), falls back to remote addr. */
    public String currentIp() {
        HttpServletRequest request = getRequest();
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Object getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getPrincipal() : null;
    }

    private HttpServletRequest getRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}