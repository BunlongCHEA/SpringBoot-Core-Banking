package com.bank.cbs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bank.cbs.domain.entity.User;
import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.domain.enums.UserRole;
import com.bank.cbs.repository.jpa.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the default SUPER_ADMIN account on first startup.
 *
 * <p>The initial password is supplied via {@code cbs.super-admin.initial-password}
 * (environment variable / secret manager). It must satisfy the 15-character policy.
 * The {@code must_change_password} flag is set to {@code true} so the operator
 * is forced to change it on first login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${cbs.super-admin.username:superadmin}")
    private String superAdminUsername;

    @Value("${cbs.super-admin.email:superadmin@cbs.bank}")
    private String superAdminEmail;

    /**
     * Provide via environment variable CBS_SUPER_ADMIN_INITIAL_PASSWORD.
     * Must be ≥ 15 chars, mixed-case, digit, special character.
     * Example: "Sup3rAdm!n@CBS#26"
     */
    @Value("${cbs.super-admin.initial-password}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(superAdminUsername)) {
            log.info("SUPER_ADMIN '{}' already exists — skipping seed.", superAdminUsername);
            return;
        }

        User superAdmin = User.builder()
                .username(superAdminUsername)
                .email(superAdminEmail)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .role(UserRole.SUPER_ADMIN)
                .isActive(true)
                .mustChangePassword(true)               // force change on first login
                .passwordPolicy(PasswordPolicyInterval.TWELVE_MONTHS)
                .passwordExpiresAt(java.time.OffsetDateTime.now().plusMonths(12))
                .build();

        userRepository.save(superAdmin);
        log.warn("SUPER_ADMIN '{}' seeded. Password change is required on first login.",
                superAdminUsername);
    }
}
