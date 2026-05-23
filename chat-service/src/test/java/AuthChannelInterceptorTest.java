import com.whisp.chat.config.AuthChannelInterceptor;
import com.whisp.common.security.TokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthChannelInterceptorTest {

    @Mock
    private TokenVerifier tokenVerifier;

    @InjectMocks
    private AuthChannelInterceptor interceptor;

    private Message<?> buildConnectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }

        accessor.setSessionId("test-session");
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void shouldAllowWhenTokenIsValid() {
        when(tokenVerifier.isTokenValid("valid-token")).thenReturn(true);
        when(tokenVerifier.extractUserId("valid-token")).thenReturn("user-123");

        Message<?> message = buildConnectMessage("Bearer valid-token");

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("user-123");
    }

    @Test
    void shouldRejectWhenAuthorizationHeaderIsMissing() {
        Message<?> message = buildConnectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Token ausente");
    }

    @Test
    void shouldRejectWhenTokenIsInvalid() {
        when(tokenVerifier.isTokenValid("invalid-token")).thenReturn(false);

        Message<?> message = buildConnectMessage("Bearer invalid-token");

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Token inválido ou expirado");
    }
}