package com.bank.cbs.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.bank.cbs.domain.entity.User;
import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.domain.enums.UserRole;

public record UserResponse(
        UUID userId,
        String username,
        String email,
        UserRole role,
        UUID branchId,
        boolean isActive,
        boolean mustChangePassword,
        OffsetDateTime passwordChangedAt,
        PasswordPolicyInterval passwordPolicy,
        OffsetDateTime passwordExpiresAt,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getUserId(), u.getUsername(), u.getEmail(), u.getRole(),
                u.getBranchId(), u.isActive(), u.isMustChangePassword(),
                u.getPasswordChangedAt(), u.getPasswordPolicy(),
                u.getPasswordExpiresAt(), u.getCreatedAt());
    }
}
