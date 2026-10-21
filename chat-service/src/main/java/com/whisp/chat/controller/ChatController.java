package com.whisp.chat.controller;

import com.whisp.chat.config.StompPrincipal;
import com.whisp.chat.dto.ChatMessage;
import com.whisp.chat.messaging.MessagePublisher;
import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessagePublisher messagePublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final Validator validator;

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(@DestinationVariable String roomId, ChatMessage message, Principal principal) {
        if (!(principal instanceof StompPrincipal stompPrincipal)) {
            log.warn("sendMessage called with unexpected principal type: {}",
                    principal != null ? principal.getClass().getName() : "null");
            throw new MessagingException("Unauthorized");
        }

        // a rota STOMP não valida o payload sozinha: sem isso o @Size(max = 2000) do DTO nunca é aplicado
        if (!validator.validate(message).isEmpty()) {
            throw new MessagingException("Mensagem inválida");
        }

        // a sala do destino é a autoridade: o corpo não pode apontar para outra sala
        if (!roomId.equals(message.getRoomId())) {
            throw new MessagingException("Mensagem inválida");
        }

        MessageEvent event = new MessageEvent(
                UUID.randomUUID().toString(),
                message.getRoomId(),
                stompPrincipal.getName(),
                stompPrincipal.username(),
                message.getContent(),
                MessageStatus.SENT,
                Instant.now(),
                UUID.randomUUID().toString()
        );

        messagePublisher.publish("chat.messages", event);

        messagingTemplate.convertAndSend(
                "/topic/chat/" + message.getRoomId(),
                event
        );
    }
}