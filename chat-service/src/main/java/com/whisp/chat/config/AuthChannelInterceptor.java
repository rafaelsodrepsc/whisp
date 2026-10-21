package com.whisp.chat.config;

import com.whisp.common.security.TokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final String APP_PREFIX = "/app/";
    // só destinos literais de sala; sem curingas (* e **) do broker simples
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/chat/[A-Za-z0-9_-]+$");

    private final TokenVerifier tokenVerifier;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // ramifica pelo tipo da mensagem, não pelo comando: STOMP é alias de CONNECT e
        // um MESSAGE vindo do cliente é tratado pelo broker como um SEND
        if (SimpMessageType.CONNECT.equals(accessor.getMessageType())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("Token ausente");
            }

            String token = authHeader.substring(7);

            if (!tokenVerifier.isAccessTokenValid(token)) {
                throw new MessagingException("Token inválido ou expirado");
            }

            String userId = tokenVerifier.extractUserId(token);
            String username = tokenVerifier.extractUsername(token);
            accessor.setUser(new StompPrincipal(userId, username));
        } else if (SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
            requireAuthenticated(accessor);
            String destination = accessor.getDestination();

            // o cliente só publica via controllers (/app); /topic é exclusivo do servidor
            if (destination == null || !destination.startsWith(APP_PREFIX)) {
                throw new MessagingException("Destino de envio não permitido");
            }
        } else if (SimpMessageType.SUBSCRIBE.equals(accessor.getMessageType())) {
            requireAuthenticated(accessor);
            String destination = accessor.getDestination();

            if (destination == null || !ROOM_TOPIC.matcher(destination).matches()) {
                throw new MessagingException("Destino de inscrição não permitido");
            }
        }

        return message;
    }

    private void requireAuthenticated(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal)) {
            throw new MessagingException("Não autenticado");
        }
    }
}
