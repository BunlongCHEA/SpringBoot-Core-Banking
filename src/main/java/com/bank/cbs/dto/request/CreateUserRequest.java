package com.bank.cbs.dto.request;

import java.util.UUID;

import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.domain.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Email
        String email,

        /** Must satisfy PasswordPolicyService.validate() — min 15 chars, mixed case, digit, special. */
        @NotBlank
        @Size(min = 15, max = 128)
        String initialPassword,

        @NotNull
        UserRole role,

        /** Defaults to THREE_MONTHS when null. */
        PasswordPolicyInterval passwordPolicy,

        UUID branchId
) {
    
}
