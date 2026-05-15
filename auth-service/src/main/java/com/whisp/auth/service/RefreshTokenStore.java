package com.whisp.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public void save(String userId, String token) {
        redisTemplate.opsForValue().set(
                PREFIX + userId,
                token,
                refreshExpiration,
                TimeUnit.MILLISECONDS
        );
    }

    public Optional<String> find(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + userId));
    }

    public void delete(String userId) {
        redisTemplate.delete(PREFIX + userId);
    }
}
