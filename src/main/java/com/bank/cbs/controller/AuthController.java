package com.bank.cbs.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.cbs.dto.response.ApiResponse;
import com.bank.cbs.security.JwtUtil;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication, including login and token management.")
public class AuthController {
    private final JwtUtil jwtUtil;

    // Temporary login endpoint for testing — replace with real user auth later
    @PostMapping("/token")
    public ApiResponse<Map<String, String>> getToken(@RequestParam String userId,
                                                      @RequestParam(defaultValue = "ADMIN") String role) {
        String token = jwtUtil.generate(userId, role);
        return ApiResponse.ok(Map.of("token", token, "type", "Bearer"));
    }
}
