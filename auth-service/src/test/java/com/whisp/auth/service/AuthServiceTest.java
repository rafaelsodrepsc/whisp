package com.whisp.auth.service;

import com.whisp.auth.dto.AuthResponse;
import com.whisp.auth.dto.LoginRequest;
import com.whisp.auth.dto.RegisterRequest;
import com.whisp.auth.exception.EmailAlreadyExistsException;
import com.whisp.auth.exception.InvalidCredentialsException;
import com.whisp.auth.exception.InvalidTokenException;
import com.whisp.auth.exception.UserNotFoundException;
import com.whisp.auth.exception.UsernameAlreadyExistsException;
import com.whisp.auth.model.User;
import com.whisp.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id("user-123")
                .username("rafael")
                .email("rafael@whisp.com")
                .passwordHash("hashed-password")
                .build();
    }

    // --- register ---

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest request = new RegisterRequest("rafael", "rafael@whisp.com", "senha123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");

        authService.register(request);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("rafael", "rafael@whisp.com", "senha123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest("rafael", "rafael@whisp.com", "senha123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // --- login ---

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("rafael@whisp.com", "senha123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(user.getId(), user.getEmail())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user.getId(), user.getEmail())).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore, times(1)).save(user.getId(), "refresh-token");
    }

    @Test
    void shouldThrowWhenEmailNotFound() {
        LoginRequest request = new LoginRequest("naoexiste@whisp.com", "senha123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldThrowWhenPasswordIsWrong() {
        LoginRequest request = new LoginRequest("rafael@whisp.com", "senha-errada");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- refresh ---

    @Test
    void shouldRefreshSuccessfully() {
        String refreshToken = "valid-refresh-token";

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenStore.find(user.getId())).thenReturn(Optional.of(refreshToken));
        when(jwtService.generateAccessToken(user.getId(), user.getEmail())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user.getId(), user.getEmail())).thenReturn("new-refresh-token");

        AuthResponse response = authService.refresh(refreshToken);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenStore, times(1)).save(user.getId(), "new-refresh-token");
    }

    @Test
    void shouldThrowWhenRefreshTokenIsInvalid() {
        when(jwtService.isTokenValid("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldThrowWhenRefreshTokenNotFoundInRedis() {
        String refreshToken = "valid-refresh-token";

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenStore.find(user.getId())).thenReturn(Optional.empty());

        // token válido pelo JWT mas não existe no Redis — já foi usado ou expirou
        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void shouldThrowWhenUserNotFoundOnRefresh() {
        String refreshToken = "valid-refresh-token";

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn("ghost-user-id");
        when(userRepository.findById("ghost-user-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(UserNotFoundException.class);
    }
}