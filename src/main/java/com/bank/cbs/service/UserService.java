package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.PasswordHistory;
import com.bank.cbs.domain.entity.User;
import com.bank.cbs.domain.enums.AuditAction;
import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.domain.enums.UserRole;
import com.bank.cbs.dto.request.ChangePasswordRequest;
import com.bank.cbs.dto.request.CreateUserRequest;
import com.bank.cbs.dto.response.UserResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.BusinessException;
import com.bank.cbs.exception.ConflictException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.PasswordHistoryRepository;
import com.bank.cbs.repository.jpa.UserRepository;
import com.bank.cbs.security.SecurityAuditContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository            userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordPolicyService     passwordPolicyService;
    private final PasswordEncoder           passwordEncoder;
    private final AuditService              auditService;
    private final SecurityAuditContext      securityContext;

    // Creation

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID createdBy) {
        if (request.role() == UserRole.SUPER_ADMIN
            && userRepository.existsByRoleAndIsDeletedFalse(UserRole.SUPER_ADMIN)) {
            throw new BusinessException(
                "Only one SUPER_ADMIN account is permitted. Contact the existing super admin instead.");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }

        // Validate the initial password against policy (no history yet)
        passwordPolicyService.validate(request.initialPassword(), request.username(), List.of());

        String hash = passwordEncoder.encode(request.initialPassword());

        PasswordPolicyInterval policy = request.passwordPolicy() != null
                ? request.passwordPolicy()
                : PasswordPolicyInterval.THREE_MONTHS;

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(hash)
                .role(request.role())
                .branchId(request.branchId())
                .passwordPolicy(policy)
                .mustChangePassword(true)           // always force change on first login
                .passwordExpiresAt(passwordPolicyService.computeExpiresAt(policy))
                .createdBy(createdBy)
                .build();

        User saved = userRepository.save(user);
        recordHistory(saved.getUserId(), hash);

        auditService.log("User", user.getUserId(), AuditAction.CREATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        null, Map.of("username", user.getUsername(), "role", user.getRole()), null);
        log.info("User created: {} role={} by={}", saved.getUsername(), saved.getRole(), createdBy);
        return UserResponse.from(saved);
    }

    // Password Management

    /**
     * Changes a user's password.
     * <ul>
     *   <li>Validates new password against all policy rules.</li>
     *   <li>Checks history (last 5 passwords).</li>
     *   <li>Verifies the current password before allowing the change.</li>
     *   <li>Clears must_change_password flag after successful change.</li>
     * </ul>
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BadRequestException("New password must differ from the current password.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match.");
        }

        List<PasswordHistory> history = passwordHistoryRepository
                .findRecentByUserId(userId, passwordPolicyService.historyDepth());

        passwordPolicyService.validate(request.newPassword(), user.getUsername(), history);

        String newHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(newHash);
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(OffsetDateTime.now());
        user.setPasswordExpiresAt(passwordPolicyService.computeExpiresAt(user.getPasswordPolicy()));
        userRepository.save(user);

        recordHistory(userId, newHash);
        passwordHistoryRepository.pruneOldEntries(userId, passwordPolicyService.historyDepth());
        log.info("Password changed for user: {}", user.getUsername());
    }

    /**
     * Updates the password expiry policy for a user (SUPER_ADMIN operation).
     */
    @Transactional
    public UserResponse updatePasswordPolicy(UUID userId, PasswordPolicyInterval policy) {
        User user = getOrThrow(userId);
        PasswordPolicyInterval oldPolicy = user.getPasswordPolicy();
        user.setPasswordPolicy(policy);
        user.setPasswordExpiresAt(passwordPolicyService.computeExpiresAt(policy));

        auditService.log("User", userId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("passwordPolicy", oldPolicy), Map.of("passwordPolicy", policy), null);
        return UserResponse.from(userRepository.save(user));
    }

    // Queries

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        return UserResponse.from(getOrThrow(userId));
    }

    @Transactional(readOnly = true)
    private User getOrThrow(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public User getByUsernameOrThrow(String username) {
        return userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(UserRole role, Boolean isActive, Pageable pageable) {
        // Specification<User> spec = (root, query, cb) -> cb.conjunction();   // guaranteed non-null "match everything" base
        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("isDeleted"), false);

        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (isActive != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }

        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }

    // Reactivate and Reset Account

    @Transactional
    public void reactivate(UUID userId) {
        User user = getOrThrow(userId);
        if (user.isDeleted()) {
            throw new BusinessException("This user has been removed from the portal and cannot be reactivated here.");
        }

        if (user.isActive()) {
            throw new BusinessException("User is already active");
        }
        user.setActive(true);
        userRepository.save(user);

        auditService.log("User", userId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("isActive", false), Map.of("isActive", true), null);
        log.info("User reactivated: {}", user.getUsername());
    }
    
    @Transactional
    public void deactivate(UUID userId) {
        User user = getOrThrow(userId);
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("The SUPER_ADMIN account cannot be deactivated.");
        }
        
        user.setActive(false);
        userRepository.save(user);

        auditService.log("User", userId, AuditAction.UPDATE, securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(), 
        Map.of("isActive", true), Map.of("isActive", false), Map.of("reason", "deactivated"));
        log.info("User deactivated: {}", user.getUsername());
    }

    @Transactional
    public String resetPassword(UUID userId) {
        User user = getOrThrow(userId);
        String tempPassword = passwordPolicyService.generateTempPassword(user.getUsername());
        String hash = passwordEncoder.encode(tempPassword);
        user.setPasswordHash(hash);
        user.setMustChangePassword(true);
        user.setPasswordExpiresAt(passwordPolicyService.computeExpiresAt(user.getPasswordPolicy()));
        userRepository.save(user);
        recordHistory(userId, hash);

        auditService.log("User", userId, AuditAction.UPDATE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        null, null, Map.of("action", "password reset by admin"));
        // note: tempPassword itself NEVER goes in oldValue/newValue/metadata — audit records are not secret-safe storage
        log.warn("Password administratively reset for user: {}", user.getUsername());
        return tempPassword;   // shown once to the admin — never persisted in plaintext, never logged
    }

    @Transactional
    public void remove(UUID userId) {
        User user = getOrThrow(userId);
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("The SUPER_ADMIN account cannot be removed.");
        }
        if (user.isDeleted()) {
            throw new BusinessException("User has already been removed from the portal.");
        }
        user.setDeleted(true);            // hidden from portal UI from now on
        user.setActive(false);            // removal implies unusable too
        userRepository.save(user);

        auditService.log("User", userId, AuditAction.DELETE,
        securityContext.currentUserId(), securityContext.currentUserRole(), securityContext.currentIp(),
        Map.of("isDeleted", false), Map.of("isDeleted", true), null);
        log.info("User removed from portal (row retained in Postgres): {}", user.getUsername());
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void recordHistory(UUID userId, String hash) {
        passwordHistoryRepository.save(
                PasswordHistory.builder()
                        .userId(userId)
                        .passwordHash(hash)
                        .build());
    }
}
