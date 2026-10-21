import com.whisp.chat.config.AuthChannelInterceptor;
import com.whisp.chat.config.StompPrincipal;
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

import java.security.Principal;

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

    private Message<?> buildFrame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setSessionId("test-session");
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void shouldAllowWhenTokenIsValid() {
        when(tokenVerifier.isAccessTokenValid("valid-token")).thenReturn(true);
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
        when(tokenVerifier.isAccessTokenValid("invalid-token")).thenReturn(false);

        Message<?> message = buildConnectMessage("Bearer invalid-token");

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Token inválido ou expirado");
    }

    @Test
    void shouldRejectSendWithoutAuthenticatedUser() {
        Message<?> message = buildFrame(StompCommand.SEND, "/app/chat/room-1", null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Não autenticado");
    }

    @Test
    void shouldRejectSendDirectlyToBrokerDestination() {
        Message<?> message = buildFrame(StompCommand.SEND, "/topic/chat/room-1",
                new StompPrincipal("user-123", "testuser"));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Destino de envio não permitido");
    }

    @Test
    void shouldAllowSendToApplicationDestination() {
        Message<?> message = buildFrame(StompCommand.SEND, "/app/chat/room-1",
                new StompPrincipal("user-123", "testuser"));

        assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
    }

    @Test
    void shouldRejectMessageCommandFromClientWithoutAuthenticatedUser() {
        Message<?> message = buildFrame(StompCommand.MESSAGE, "/topic/chat/room-1", null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Não autenticado");
    }

    @Test
    void shouldRejectMessageCommandFromClientToBrokerDestination() {
        Message<?> message = buildFrame(StompCommand.MESSAGE, "/topic/chat/room-1",
                new StompPrincipal("user-123", "testuser"));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Destino de envio não permitido");
    }

    @Test
    void shouldTreatStompCommandAsConnect() {
        Message<?> message = buildFrame(StompCommand.STOMP, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Token ausente");
    }

    @Test
    void shouldRejectSubscribeWithoutAuthenticatedUser() {
        Message<?> message = buildFrame(StompCommand.SUBSCRIBE, "/topic/chat/room-1", null);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Não autenticado");
    }

    @Test
    void shouldRejectSubscribeWithWildcard() {
        Message<?> message = buildFrame(StompCommand.SUBSCRIBE, "/topic/chat/**",
                new StompPrincipal("user-123", "testuser"));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Destino de inscrição não permitido");
    }

    @Test
    void shouldRejectSubscribeToOtherTopic() {
        Message<?> message = buildFrame(StompCommand.SUBSCRIBE, "/topic/other",
                new StompPrincipal("user-123", "testuser"));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Destino de inscrição não permitido");
    }

    @Test
    void shouldAllowSubscribeToRoomTopic() {
        Message<?> message = buildFrame(StompCommand.SUBSCRIBE,
                "/topic/chat/3f2b8c1e-5a4d-4e0b-9c1a-0d2e7f6a8b9c",
                new StompPrincipal("user-123", "testuser"));

        assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
    }
}