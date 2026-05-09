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
        try {
            log.info("Consumindo mensagem: {}", message.id());
            Message event = new Message(
                    message.id(),
                    message.roomId(),
                    message.senderId(),
                    message.content(),
                    message.status(),
                    message.sentAt()
            );
            messageRepository.save(event);
        } catch (Exception e) {
            log.error("Erro ao processar mensagem {}: {}", message.id(), e.getMessage());
        }
    }
}
