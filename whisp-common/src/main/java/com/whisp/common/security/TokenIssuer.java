package com.whisp.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = {"jwt.secret", "jwt.expiration"})
public class TokenIssuer {

    public static final String TOKEN_USE_CLAIM = "token_use";
    public static final String ACCESS_USE = "access";
    public static final String REFRESH_USE = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // falha na subida, e não no primeiro login, se o segredo for inválido
    @PostConstruct
    void validateSecret() {
        signingKey();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes after Base64 decode)");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String buildToken(String userId, String email, String username, long expirationTime, String tokenUse) {
        return Jwts.builder()
                // jti único: sem ele, dois tokens emitidos no mesmo segundo seriam idênticos e a rotação não invalidaria o anterior
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("email", email)
                .claim("username", username)
                .claim(TOKEN_USE_CLAIM, tokenUse)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey())
                .compact();
    }

    public String generateAccessToken(String userId, String email, String username) {
        return buildToken(userId, email, username, expiration, ACCESS_USE);
    }

    public String generateRefreshToken(String userId, String email, String username) {
        return buildToken(userId, email, username, refreshExpiration, REFRESH_USE);
    }
}