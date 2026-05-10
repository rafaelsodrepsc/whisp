package com.whisp.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String USER_ID = "user-123";
    private static final String EMAIL = "rafael@whisp.com";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        String secret = Base64.getEncoder().encodeToString(
                "whisp-test-secret-key-32-bytes!!".getBytes()
        );

        ReflectionTestUtils.setField(jwtService, "secret", secret);
        ReflectionTestUtils.setField(jwtService, "expiration", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
    }

    @Test
    void shouldGenerateAccessTokenWithCorrectClaims() {
        String token = jwtService.generateAccessToken(USER_ID, EMAIL);
        Claims claims = jwtService.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID);
        assertThat(claims.get("email", String.class)).isEqualTo(EMAIL);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void shouldReturnTrueForValidToken() {
        String token = jwtService.generateAccessToken(USER_ID, EMAIL);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);
        String token = jwtService.generateAccessToken(USER_ID, EMAIL);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(USER_ID, EMAIL);
        String tampered = token + "invalido";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void shouldExtractCorrectUserId() {
        String token = jwtService.generateAccessToken(USER_ID, EMAIL);

        assertThat(jwtService.extractUserId(token)).isEqualTo(USER_ID);
    }
}