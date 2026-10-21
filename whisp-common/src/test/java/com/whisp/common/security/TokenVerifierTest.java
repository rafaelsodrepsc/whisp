package com.whisp.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldAcceptValidSecretAtStartup() {
        assertThatCode(tokenVerifier::validateSecret).doesNotThrowAnyException();
    }

    @Test
    void shouldFailAtStartupWhenSecretIsTooShort() {
        ReflectionTestUtils.setField(tokenVerifier, "secret",
                Base64.getEncoder().encodeToString("curto".getBytes()));

        assertThatThrownBy(tokenVerifier::validateSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailAtStartupWhenSecretIsEmpty() {
        ReflectionTestUtils.setField(tokenVerifier, "secret", "");

        assertThatThrownBy(tokenVerifier::validateSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailAtStartupWhenSecretIsNotBase64() {
        ReflectionTestUtils.setField(tokenVerifier, "secret", "seu_secret_base64_aqui");

        assertThatThrownBy(tokenVerifier::validateSecret).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptAccessTokenOnlyAsAccessToken() {
        String token = tokenIssuer.generateAccessToken(USER_ID, EMAIL, "testuser");

        assertThat(tokenVerifier.isAccessTokenValid(token)).isTrue();
        assertThat(tokenVerifier.isRefreshTokenValid(token)).isFalse();
    }

    @Test
    void shouldAcceptRefreshTokenOnlyAsRefreshToken() {
        String token = tokenIssuer.generateRefreshToken(USER_ID, EMAIL, "testuser");

        assertThat(tokenVerifier.isRefreshTokenValid(token)).isTrue();
        assertThat(tokenVerifier.isAccessTokenValid(token)).isFalse();
    }

    @Test
    void shouldRejectExpiredTokenEvenWithCorrectRole() {
        ReflectionTestUtils.setField(tokenIssuer, "refreshExpiration", -1000L);
        String token = tokenIssuer.generateRefreshToken(USER_ID, EMAIL, "testuser");

        assertThat(tokenVerifier.isRefreshTokenValid(token)).isFalse();
    }

    @Test
    void shouldRejectTokenWithoutTokenUseClaim() {
        String token = Jwts.builder()
                .subject(USER_ID)
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .compact();

        assertThat(tokenVerifier.isTokenValid(token)).isTrue();
        assertThat(tokenVerifier.isAccessTokenValid(token)).isFalse();
        assertThat(tokenVerifier.isRefreshTokenValid(token)).isFalse();
    }
}