package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.PasswordHistory;
import com.bank.cbs.domain.entity.User;
import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.dto.request.ChangePasswordRequest;
import com.bank.cbs.dto.request.CreateUserRequest;
import com.bank.cbs.dto.response.UserResponse;
import com.bank.cbs.exception.BadRequestException;
import com.bank.cbs.exception.ConflictException;
import com.bank.cbs.exception.ResourceNotFoundException;
import com.bank.cbs.repository.jpa.PasswordHistoryRepository;
import com.bank.cbs.repository.jpa.UserRepository;

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

    // ── Creation ────────────────────────────────────────────────

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID createdBy) {
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
        log.info("User created: {} role={} by={}", saved.getUsername(), saved.getRole(), createdBy);
        return UserResponse.from(saved);
    }

    // ── Password Management ──────────────────────────────────────

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
        user.setPasswordPolicy(policy);
        user.setPasswordExpiresAt(passwordPolicyService.computeExpiresAt(policy));
        return UserResponse.from(userRepository.save(user));
    }

    // ── Queries ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        return UserResponse.from(getOrThrow(userId));
    }

    @Transactional(readOnly = true)
    public User getOrThrow(UUID userId) {
        return userRepository.findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public User getByUsernameOrThrow(String username) {
        return userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    // ── Soft Delete ──────────────────────────────────────────────

    @Transactional
    public void deactivate(UUID userId) {
        User user = getOrThrow(userId);
        user.setDeleted(true);
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", user.getUsername());
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
