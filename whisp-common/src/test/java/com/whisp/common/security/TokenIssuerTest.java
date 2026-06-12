package com.whisp.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class TokenIssuerTest {

    private TokenIssuer tokenIssuer;

    private static final String USER_ID = "user-123";
    private static final String EMAIL = "rafael@whisp.com";

    @BeforeEach
    void setUp() {
        tokenIssuer = new TokenIssuer();

        String secret = Base64.getEncoder().encodeToString(
                "whisp-test-secret-key-32-bytes!!".getBytes()
        );

        ReflectionTestUtils.setField(tokenIssuer, "secret", secret);
        ReflectionTestUtils.setField(tokenIssuer, "expiration", 900000L);
        ReflectionTestUtils.setField(tokenIssuer, "refreshExpiration", 604800000L);
    }

    @Test
    void shouldGenerateAccessTokenWithCorrectSubject() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");

        TokenVerifier verifier = buildVerifier();
        Claims claims = verifier.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID);
    }

    @Test
    void shouldGenerateAccessTokenWithEmailClaim() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");

        TokenVerifier verifier = buildVerifier();
        Claims claims = verifier.extractClaims(token);

        assertThat(claims.get("email", String.class)).isEqualTo(EMAIL);
    }

    @Test
    void shouldGenerateAccessTokenWithFutureExpiration() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");

        TokenVerifier verifier = buildVerifier();
        Claims claims = verifier.extractClaims(token);

        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void shouldGenerateRefreshTokenWithCorrectSubject() {
        String token = tokenIssuer.generateRefreshToken(USER_ID, EMAIL,"testuser");

        TokenVerifier verifier = buildVerifier();
        Claims claims = verifier.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID);
    }

    @Test
    void shouldGenerateRefreshTokenWithLongerExpirationThanAccessToken() {
        String accessToken = tokenIssuer.generateAccessToken(USER_ID, EMAIL,"testuser");
        String refreshToken = tokenIssuer.generateRefreshToken(USER_ID, EMAIL,"testuser");

        TokenVerifier verifier = buildVerifier();
        Date accessExpiration = verifier.extractClaims(accessToken).getExpiration();
        Date refreshExpiration = verifier.extractClaims(refreshToken).getExpiration();

        assertThat(refreshExpiration).isAfter(accessExpiration);
    }

    // TokenVerifier como colaborador para inspecionar o token gerado
    private TokenVerifier buildVerifier() {
        TokenVerifier verifier = new TokenVerifier();
        String secret = Base64.getEncoder().encodeToString(
                "whisp-test-secret-key-32-bytes!!".getBytes()
        );
        ReflectionTestUtils.setField(verifier, "secret", secret);
        return verifier;
    }
}