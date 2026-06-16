package com.bank.cbs.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.dto.request.LoginRequest;
import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.dto.response.LoginResponse;
import com.bank.cbs.security.JwtUtil;
import com.bank.cbs.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication, including login and token management.")
public class AuthController {
    private final AuthService authService;
    private final JwtUtil     jwtUtil;       // kept for the dev-only /token shortcut
 
    // ── Login ─────────────────────────────────────────────────────────────
 
    /**
     * POST /api/v1/auth/login
     *
     * <p>Returns a signed JWT.  If {@code mustChangePassword} is {@code true}
     * the client MUST redirect the user to the change-password screen and block
     * all other operations until the password is updated.
     *
     * <p>Example request body:
     * <pre>
     * {
     *   "username": "superadmin",
     *   "password": "Sup3rAdm!n@CBS#26"
     * }
     * </pre>
     */
    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }
}
