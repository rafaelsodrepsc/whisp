package com.whisp.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenVerifierTest {

    private TokenVerifier tokenVerifier;
    private TokenIssuer tokenIssuer;

    private static final String USER_ID = "user-123";
    private static final String EMAIL = "rafael@whisp.com";
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "whisp-test-secret-key-32-bytes!!".getBytes()
    );

    @BeforeEach
    void setUp() {
        tokenVerifier = new TokenVerifier();
        ReflectionTestUtils.setField(tokenVerifier, "secret", SECRET);

        tokenIssuer = new TokenIssuer();
        ReflectionTestUtils.setField(tokenIssuer, "secret", SECRET);
        ReflectionTestUtils.setField(tokenIssuer, "expiration", 900000L);
        ReflectionTestUtils.setField(tokenIssuer, "refreshExpiration", 604800000L);
    }

    @Test
    void shouldReturnTrueForValidToken() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");

        assertThat(tokenVerifier.isTokenValid(token)).isTrue();
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(tokenIssuer, "expiration", -1000L);
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");

        assertThat(tokenVerifier.isTokenValid(token)).isFalse();
    }

    @Test
    void shouldReturnFalseForTamperedToken() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");
        String tampered = token + "invalido";

        assertThat(tokenVerifier.isTokenValid(tampered)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyToken() {
        assertThat(tokenVerifier.isTokenValid("")).isFalse();
    }

    @Test
    void shouldExtractCorrectUserId() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");

        assertThat(tokenVerifier.extractUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    void shouldExtractCorrectUserIdFromRefreshToken() {
        String token = tokenIssuer.generateRefreshToken(USER_ID, EMAIL,"testuser");

        assertThat(tokenVerifier.extractUserId(token)).isEqualTo(USER_ID);
    }
}