package com.bank.cbs.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.config.CbsProperties;
import com.bank.cbs.domain.entity.User;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.dto.request.LoginRequest;
import com.bank.cbs.dto.response.LoginResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.repository.jpa.UserRepository;
import com.bank.cbs.security.JwtUtil;
import com.bank.cbs.security.SecurityAuditContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final JwtUtil               jwtUtil;
    private final CbsProperties         cbsProperties;

    private final AuditService              auditService;
    private final SecurityAuditContext      securityContext;
 
    /**
     * Authenticates a user and returns a signed JWT.
     *
     * <p>Checks (in order):
     * <ol>
     *   <li>Username exists and account is not soft-deleted.</li>
     *   <li>Password matches the stored BCrypt hash.</li>
     *   <li>Account is active (not deactivated by admin).</li>
     *   <li>Password expiry — if expired, login still succeeds but
     *       {@code mustChangePassword=true} is returned so the client
     *       can force the change-password screen.</li>
     * </ol>
     *
     * <p>A generic error message is used for both "user not found" and
     * "wrong password" to avoid username enumeration.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
 
        // ── 1. Lookup (same generic error for not-found vs wrong-password) ──
        User user = userRepository
                .findByUsernameAndIsDeletedFalse(request.username())
                .orElseThrow(() -> new BadRequestException("Invalid username or password."));
 
        // ── 2. Password check ────────────────────────────────────────────────
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for username '{}'", request.username());
            throw new BadRequestException("Invalid username or password.");
        }
 
        // ── 3. Account state ─────────────────────────────────────────────────
        if (!user.isActive()) {
            throw new BusinessException(
                    "Your account has been deactivated. Please contact your administrator.");
        }
 
        // ── 4. mustChangePassword logic ───────────────────────────────────────
        //   a) Flag already set in DB (first login / admin-forced reset)
        //   b) Password has expired per the user's policy interval
        boolean mustChange = user.isMustChangePassword()
                || passwordPolicyService.isExpired(user.getPasswordExpiresAt());
 
        // ── 5. Issue JWT ──────────────────────────────────────────────────────
        String token    = jwtUtil.generate(user.getUserId().toString(), user.getRole().name());
        long   expiresIn = cbsProperties.security().jwt().expirationSeconds();
 
        auditService.log("User", user.getUserId(), AuditAction.LOGIN,
        user.getUserId(), user.getRole().name(), securityContext.currentIp(),
        null, null, null);
        log.info("User '{}' logged in (role={}, mustChangePassword={})",
                user.getUsername(), user.getRole(), mustChange);
        return new LoginResponse(
                token, "Bearer", expiresIn,
                user.getUserId(), user.getUsername(), user.getRole().name(),
                mustChange
        );
    }
}
