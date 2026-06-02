package com.bank.cbs.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.domain.enums.PasswordPolicyInterval;
import com.bank.cbs.dto.request.ChangePasswordRequest;
import com.bank.cbs.dto.request.CreateUserRequest;
import com.bank.cbs.dto.response.UserResponse;
import com.bank.cbs.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /** SUPER_ADMIN / ADMIN only — create a new internal bank employee. */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request) {
        // TODO: extract createdBy from SecurityContext
        UserResponse response = userService.create(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Any authenticated user can change their own password. */
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN','CUSTOMER_SERVICE','TELLER','AUDITOR')")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    /** SUPER_ADMIN only — update password expiry policy for any user. */
    @PatchMapping("/{userId}/password-policy")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> updatePolicy(
            @PathVariable UUID userId,
            @RequestBody PasswordPolicyInterval policy) {
        return ResponseEntity.ok(userService.updatePasswordPolicy(userId, policy));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.findById(userId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID userId) {
        userService.deactivate(userId);
        return ResponseEntity.noContent().build();
    }
}
