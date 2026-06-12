package com.whisp.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
@ConditionalOnProperty(name = {"jwt.secret", "jwt.expiration"})
public class TokenIssuer {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey signingKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes after Base64 decode)");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String buildToken(String userId, String email, String username, long expirationTime) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signingKey())
                .compact();
    }

    public String generateAccessToken(String userId, String email, String username) {
        return buildToken(userId, email, username, expiration);
    }

    public String generateRefreshToken(String userId, String email, String username) {
        return buildToken(userId, email, username, refreshExpiration);
    }
}