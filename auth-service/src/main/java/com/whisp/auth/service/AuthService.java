package com.whisp.auth.service;

import com.whisp.auth.dto.AuthResponse;
import com.whisp.auth.dto.LoginRequest;
import com.whisp.auth.dto.RegisterRequest;
import com.whisp.auth.exception.*;
import com.whisp.auth.model.User;
import com.whisp.auth.repository.UserRepository;
import com.whisp.common.security.TokenIssuer;
import com.whisp.common.security.TokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenVerifier tokenVerifier;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;

    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = tokenIssuer.generateAccessToken(user.getId(), user.getEmail(), user.getUsername());
        String refreshToken = tokenIssuer.generateRefreshToken(user.getId(), user.getEmail(), user.getUsername());

        // salva o refresh token no Redis com TTL de 7 dias
        refreshTokenStore.save(user.getId(), refreshToken);

        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!tokenVerifier.isTokenValid(refreshToken)) {
            throw new InvalidTokenException();
        }

        String userId = tokenVerifier.extractUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // verifica se o token existe no Redis e se bate com o enviado
        String storedToken = refreshTokenStore.find(userId)
                .orElseThrow(InvalidTokenException::new);

        if (!storedToken.equals(refreshToken)) {
            // token já foi rotacionado, possível reuso de token roubado
            refreshTokenStore.delete(userId); // invalida tudo por segurança
            throw new InvalidTokenException();
        }

        String newAccessToken = tokenIssuer.generateAccessToken(user.getId(), user.getEmail(), user.getUsername());
        String newRefreshToken = tokenIssuer.generateRefreshToken(user.getId(), user.getEmail(), user.getUsername());

        // rotaciona, deleta o antigo, salva o novo
        refreshTokenStore.save(userId, newRefreshToken);

        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    public void logout(String refreshToken) {
        if (!tokenVerifier.isTokenValid(refreshToken)) {
            throw new InvalidTokenException();
        }

        String userId = tokenVerifier.extractUserId(refreshToken);
        refreshTokenStore.delete(userId);
    }
}