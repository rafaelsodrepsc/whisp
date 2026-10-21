package com.whisp.auth.controller;

import com.whisp.auth.dto.AuthResponse;
import com.whisp.auth.dto.LoginRequest;
import com.whisp.auth.dto.MessageResponse;
import com.whisp.auth.dto.RefreshRequest;
import com.whisp.auth.dto.RegisterRequest;
import com.whisp.auth.exception.InvalidTokenException;
import com.whisp.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // o path precisa cobrir /auth/refresh e /auth/logout, senão o logout não recebe o cookie
    private static final String REFRESH_COOKIE_PATH = "/auth";
    private static final String LEGACY_REFRESH_COOKIE_PATH = "/auth/refresh";

    private final AuthService authService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // true por padrão; só desligue em desenvolvimento local sem HTTPS
    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(new MessageResponse("User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse tokens = authService.login(request);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken(), null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new InvalidTokenException();
        }
        AuthResponse tokens = authService.refresh(refreshToken);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken(), null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            if (refreshToken != null) {
                authService.logout(refreshToken);
            }
        } finally {
            // limpa o cookie mesmo se o token estiver expirado ou for inválido
            clearRefreshCookie(response);
        }
        return ResponseEntity.noContent().build();
    }

    private void addRefreshCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie(token, REFRESH_COOKIE_PATH, Duration.ofMillis(refreshExpiration)).toString());
        expireLegacyRefreshCookie(response);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie("", REFRESH_COOKIE_PATH, Duration.ZERO).toString());
        expireLegacyRefreshCookie(response);
    }

    // o cookie antigo (Path=/auth/refresh) seria enviado antes do novo e sobrepor-se-ia a ele
    private void expireLegacyRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie("", LEGACY_REFRESH_COOKIE_PATH, Duration.ZERO).toString());
    }

    private ResponseCookie refreshCookie(String value, String path, Duration maxAge) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(path)
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
    }
}