package com.whisp.message.listener;

import com.whisp.common.event.DlqEvent;
import com.whisp.common.event.MessageEvent;
import com.whisp.message.entity.Message;
import com.whisp.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {

    private final MessageRepository messageRepository;
    private final KafkaTemplate<String, DlqEvent> dlqKafkaTemplate;

    private static final String DLQ_TOPIC = "chat.messages.dlq";

    @KafkaListener(topics = "chat.messages", groupId = "message-service")
    public void listen(MessageEvent message) {
        try {
            log.info("Consumindo mensagem: {} correlationId: {}", message.id(), message.correlationId());
            Message event = new Message(
                    message.id(),
                    message.roomId(),
                    message.senderId(),
                    message.senderUsername(),
                    message.content(),
                    message.status(),
                    message.sentAt()
            );
            messageRepository.save(event);
        } catch (Exception e) {
            log.error("Erro ao processar mensagem {}: {}", message.id(), e.getMessage());
            sendToDlq(message, e.getMessage());
        }
    }

    private void sendToDlq(MessageEvent original, String reason) {
        DlqEvent dlqEvent = new DlqEvent(
                original.id(),
                reason,
                original,
                Instant.now(),
                original.correlationId()
        );

        dlqKafkaTemplate.send(DLQ_TOPIC, dlqEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // se a DLQ também falhar, logamos com todos os detalhes
                        log.error("CRITICO: falha ao enviar para DLQ. messageId={} reason={} dlqError={}",
                                original.id(), reason, ex.getMessage());
                    } else {
                        log.info("Mensagem enviada para DLQ. messageId={} offset={}",
                                original.id(), result.getRecordMetadata().offset());
                    }
                });
    }
}
