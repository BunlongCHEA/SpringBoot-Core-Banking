package com.bank.cbs.service;

import java.security.SecureRandom;
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

    // Character pools for generation — deliberately excludes visually
    // ambiguous characters (0/O, 1/l/I) since these get read off-screen
    // and typed by a human during an admin-assisted reset.
    private static final String GEN_UPPER   = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String GEN_LOWER   = "abcdefghijkmnpqrstuvwxyz";
    private static final String GEN_DIGITS  = "23456789";
    private static final String GEN_SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?/";
    private static final String GEN_ALL     = GEN_UPPER + GEN_LOWER + GEN_DIGITS + GEN_SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();

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

    /**
     * Generates a random temporary password guaranteed to satisfy every rule
     * enforced by {@link #validate}: minimum length, at least one of each
     * required character class, and no run of more than MAX_REPEAT_CHARS
     * identical characters. Used for admin-triggered password resets, where
     * there's no existing password to base a change off of.
     */
    public String generateTempPassword() {
        return generateTempPassword(null);
    }

    public String generateTempPassword(String usernameToAvoid) {
        String candidate;
        int attempts = 0;
        do {
            candidate = generateOnce(MIN_LENGTH);
            attempts++;
        } while ((REPEAT_CHAR.matcher(candidate).find()
                || (usernameToAvoid != null && !usernameToAvoid.isBlank()
                    && candidate.toLowerCase().contains(usernameToAvoid.toLowerCase())))
                && attempts < 20);
        return candidate;
    }

    private String generateOnce(int length) {
        StringBuilder sb = new StringBuilder(length);
        // Guarantee one character from each required class first.
        sb.append(GEN_UPPER.charAt(RANDOM.nextInt(GEN_UPPER.length())));
        sb.append(GEN_LOWER.charAt(RANDOM.nextInt(GEN_LOWER.length())));
        sb.append(GEN_DIGITS.charAt(RANDOM.nextInt(GEN_DIGITS.length())));
        sb.append(GEN_SPECIAL.charAt(RANDOM.nextInt(GEN_SPECIAL.length())));
        for (int i = sb.length(); i < length; i++) {
            sb.append(GEN_ALL.charAt(RANDOM.nextInt(GEN_ALL.length())));
        }
        // Shuffle so the four guaranteed characters aren't always at the front.
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
