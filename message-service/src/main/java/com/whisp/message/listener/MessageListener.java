package com.whisp.message.listener;

import com.whisp.common.event.MessageEvent;
import com.whisp.message.entity.Message;
import com.whisp.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {

    private final MessageRepository messageRepository;

    @KafkaListener(topics = "chat.messages", groupId = "message-service")
    public void listen(MessageEvent message) {
        log.info("Consumindo mensagem: {} correlationId: {}", message.id(), message.correlationId());
        Message entity = new Message(
                message.id(),
                message.roomId(),
                message.senderId(),
                message.senderUsername(),
                message.content(),
                message.status(),
                message.sentAt()
        );
        messageRepository.save(entity);
    }
}