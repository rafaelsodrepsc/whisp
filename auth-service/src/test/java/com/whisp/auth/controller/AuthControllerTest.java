package com.whisp.auth.controller;

import com.whisp.auth.dto.AuthResponse;
import com.whisp.auth.exception.InvalidTokenException;
import com.whisp.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "refreshExpiration", 604800000L);
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
    }

    private List<String> setCookies(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE);
    }

    @Test
    void shouldScopeRefreshCookieToIncludeLogoutPath() {
        when(authService.refresh("old-refresh")).thenReturn(new AuthResponse("access", "new-refresh"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.refresh("old-refresh", response);

        assertThat(setCookies(response)).anySatisfy(cookie -> assertThat(cookie)
                .contains("refresh_token=new-refresh")
                .contains("Path=/auth;")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict"));
    }

    @Test
    void shouldOmitSecureAttributeOnlyWhenExplicitlyDisabled() {
        ReflectionTestUtils.setField(controller, "cookieSecure", false);
        when(authService.refresh("old-refresh")).thenReturn(new AuthResponse("access", "new-refresh"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.refresh("old-refresh", response);

        assertThat(setCookies(response)).allSatisfy(cookie -> assertThat(cookie).doesNotContain("Secure"));
    }

    @Test
    void shouldExpireLegacyRefreshCookieWhenIssuingANewOne() {
        when(authService.refresh("old-refresh")).thenReturn(new AuthResponse("access", "new-refresh"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.refresh("old-refresh", response);

        assertThat(setCookies(response)).anySatisfy(cookie -> assertThat(cookie)
                .contains("Path=/auth/refresh")
                .contains("Max-Age=0"));
    }

    @Test
    void shouldRevokeTokenAndClearBothCookiesOnLogout() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout("refresh-token", response);

        verify(authService).logout("refresh-token");
        assertThat(setCookies(response)).hasSize(2).allSatisfy(cookie -> assertThat(cookie)
                .contains("refresh_token=;")
                .contains("Max-Age=0"));
    }

    @Test
    void shouldClearCookiesOnLogoutEvenWhenTokenIsInvalid() {
        doThrow(new InvalidTokenException()).when(authService).logout("expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.logout("expired-token", response))
                .isInstanceOf(InvalidTokenException.class);

        assertThat(setCookies(response)).hasSize(2).allSatisfy(cookie -> assertThat(cookie)
                .contains("Max-Age=0"));
    }

    @Test
    void shouldNotCallServiceOnLogoutWithoutCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(null, response);

        verify(authService, never()).logout(any());
    }
}
