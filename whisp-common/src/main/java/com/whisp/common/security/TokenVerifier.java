package com.whisp.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
@ConditionalOnProperty(name = "jwt.secret")
public class TokenVerifier {

    @Value("${jwt.secret}")
    private String secret;

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

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaims(token).get("username", String.class);
    }

    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return extractClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // refresh e access token usam a mesma chave, então o papel do token precisa ser checado
    public boolean isAccessTokenValid(String token) {
        return isValidWithUse(token, TokenIssuer.ACCESS_USE);
    }

    public boolean isRefreshTokenValid(String token) {
        return isValidWithUse(token, TokenIssuer.REFRESH_USE);
    }

    private boolean isValidWithUse(String token, String expectedUse) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().after(new Date())
                    && expectedUse.equals(claims.get(TokenIssuer.TOKEN_USE_CLAIM, String.class));
        } catch (Exception e) {
            return false;
        }
    }
}