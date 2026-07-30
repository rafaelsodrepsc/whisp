package com.whisp.chat.messaging;

import com.whisp.common.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageFailureListener {

    private final SimpMessagingTemplate messagingTemplate;

    public MessageFailureListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "chat.messages.failed", groupId = "chat-service")
    public void listen(MessageEvent event) {
        log.warn("Notificando falha de envio. messageId={} roomId={} correlationId={}",
                event.id(), event.roomId(), event.correlationId());

        messagingTemplate.convertAndSend("/topic/chat/" + event.roomId(), event);
    }
}
