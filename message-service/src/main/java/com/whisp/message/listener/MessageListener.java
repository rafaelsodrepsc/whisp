package com.whisp.message.listener;

import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import com.whisp.message.entity.Message;
import com.whisp.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessageListener {
    private final MessageRepository messageRepository;

    @KafkaListener(topics = "chat.messages", groupId = "message-service")
    public void listen(MessageEvent message) {
        Message event = new Message(
                message.id(),
                message.roomId(),
                message.senderId(),
                message.content(),
                message.status(),
                message.sentAt()
        );
        messageRepository.save(event);
    }
}
