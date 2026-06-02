package com.bank.cbs.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bank.cbs.domain.entity.PasswordHistory;
import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordPolicyService {
    /** Minimum password length enforced across the entire system. */
    public static final int MIN_LENGTH = 15;

    private static final int    HISTORY_DEPTH      = 5;   // cannot reuse last 5 passwords
    private static final int    MAX_REPEAT_CHARS   = 3;   // no more than 3 consecutive identical chars

    private static final Pattern HAS_UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWER   = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT   = Pattern.compile("\\d");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;':,.<>?/]");
    private static final Pattern REPEAT_CHAR = Pattern.compile("(.)\\1{" + MAX_REPEAT_CHARS + ",}");

    private final PasswordEncoder passwordEncoder;

    /**
     * Validates a candidate password against all policy rules.
     *
     * @param rawPassword  the plain-text candidate password
     * @param username     the user's username (must not appear inside the password)
     * @param history      recent password hashes to guard against reuse
     */
    public void validate(String rawPassword, String username, List<PasswordHistory> history) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new BadRequestException(
                    "Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (!HAS_UPPER.matcher(rawPassword).find()) {
            throw new BadRequestException("Password must contain at least one uppercase letter.");
        }
        if (!HAS_LOWER.matcher(rawPassword).find()) {
            throw new BadRequestException("Password must contain at least one lowercase letter.");
        }
        if (!HAS_DIGIT.matcher(rawPassword).find()) {
            throw new BadRequestException("Password must contain at least one digit.");
        }
        if (!HAS_SPECIAL.matcher(rawPassword).find()) {
            throw new BadRequestException(
                    "Password must contain at least one special character (!@#$%^&* …).");
        }
        if (REPEAT_CHAR.matcher(rawPassword).find()) {
            throw new BadRequestException(
                    "Password must not contain more than " + MAX_REPEAT_CHARS
                            + " consecutive identical characters.");
        }
        if (rawPassword.toLowerCase().contains(username.toLowerCase())) {
            throw new BadRequestException("Password must not contain your username.");
        }
        for (PasswordHistory h : history) {
            if (passwordEncoder.matches(rawPassword, h.getPasswordHash())) {
                throw new BadRequestException(
                        "Password was used recently. Please choose a different password.");
            }
        }
    }

    /** Calculates the expiry date based on the user's configured policy interval. */
    public OffsetDateTime computeExpiresAt(PasswordPolicyInterval policy) {
        OffsetDateTime now = OffsetDateTime.now();
        return switch (policy) {
            case ONE_MONTH      -> now.plusMonths(1);
            case THREE_MONTHS   -> now.plusMonths(3);
            case SIX_MONTHS     -> now.plusMonths(6);
            case TWELVE_MONTHS  -> now.plusMonths(12);
        };
    }

    /** Returns true when the user's password has passed its expiry date. */
    public boolean isExpired(OffsetDateTime passwordExpiresAt) {
        return passwordExpiresAt != null && OffsetDateTime.now().isAfter(passwordExpiresAt);
    }

    public int historyDepth() {
        return HISTORY_DEPTH;
    }
}
