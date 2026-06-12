package com.whisp.chat.config;

import com.whisp.common.security.TokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final TokenVerifier tokenVerifier;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // valida no frame CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("Token ausente");
            }

            String token = authHeader.substring(7);

            if (!tokenVerifier.isTokenValid(token)) {
                throw new MessagingException("Token inválido ou expirado");
            }

            String userId = tokenVerifier.extractUserId(token);
            String username = tokenVerifier.extractUsername(token);
            accessor.setUser(new StompPrincipal(userId, username));
        }

        return message;
    }
}
