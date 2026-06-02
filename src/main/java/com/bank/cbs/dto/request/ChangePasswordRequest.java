package com.bank.cbs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 15, max = 128,
              message = "Password must be at least 15 characters long.")
        String newPassword,

        @NotBlank
        String confirmPassword
) {
    
}
