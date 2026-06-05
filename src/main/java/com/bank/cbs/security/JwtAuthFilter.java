package com.bank.cbs.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Validates the Bearer JWT on every request.
 *
 * <p><b>Authority naming:</b> the role stored in the JWT (e.g. {@code SUPER_ADMIN})
 * is registered as a {@link SimpleGrantedAuthority} <em>without</em> a {@code ROLE_}
 * prefix.  This is intentional: all {@code @PreAuthorize} annotations in this project
 * use {@code hasAnyAuthority('SUPER_ADMIN', …)} (exact-match), not
 * {@code hasAnyRole('SUPER_ADMIN')} (which would expect {@code ROLE_SUPER_ADMIN}).
 *
 * <p>Adding {@code ROLE_} here would silently break every access-control check.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
 
    private final JwtUtil jwtUtil;
 
    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {
 
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                var claims = jwtUtil.parse(token);
                String role = claims.get("role", String.class);
 
                // ✅ No "ROLE_" prefix — matches hasAnyAuthority('SUPER_ADMIN') exactly
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null,
                        List.of(new SimpleGrantedAuthority(role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
