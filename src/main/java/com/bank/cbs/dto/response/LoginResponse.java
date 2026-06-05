package com.bank.cbs.dto.response;

import java.util.UUID;

public record LoginResponse(
        String  accessToken,
        String  tokenType,        // always "Bearer"
        long    expiresIn,        // seconds
        UUID    userId,
        String  username,
        String  role,
        /**
         * true  → client MUST redirect to change-password before anything else.
         * Caused by: (a) first login, (b) admin-forced reset, (c) password expired.
         */
        boolean mustChangePassword
) {
    
}
