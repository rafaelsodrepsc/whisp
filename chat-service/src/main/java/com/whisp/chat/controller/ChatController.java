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

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MessagePublisher messagePublisher;

    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage sendMessage(ChatMessage message, Principal principal) {
        message.setSenderId(principal.getName());

        MessageEvent event = new MessageEvent(
                UUID.randomUUID().toString(),
                message.getRoomId(),
                principal.getName(),
                message.getContent(),
                MessageStatus.SENT,
                Instant.now(),
                UUID.randomUUID().toString()
        );

        messagePublisher.publish("chat.messages", event);
        return message;
    }
}