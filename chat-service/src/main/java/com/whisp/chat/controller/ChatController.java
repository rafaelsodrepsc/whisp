package com.whisp.chat.controller;

import com.whisp.chat.dto.ChatMessage;
import com.whisp.chat.messaging.MessagePublisher;
import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessagePublisher messagePublisher;

    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage sendMessage(ChatMessage message) {
        log.info(">>> Mensagem recebida: {} ", message.getContent());
        MessageEvent event = new MessageEvent(
                UUID.randomUUID().toString(),
                message.getRoomId(),
                message.getSenderId(),
                message.getContent(),
                MessageStatus.SENT,
                Instant.now()
        );
        messagePublisher.publish("chat.messages", event);
        return message;
    }
}