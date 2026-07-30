package com.whisp.message.listener;

import com.whisp.common.event.DlqEvent;
import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqListener {

    private static final String FAILED_TOPIC = "chat.messages.failed";

    private final KafkaTemplate<String, MessageEvent> messageEventKafkaTemplate;

    @KafkaListener(
            topics = "chat.messages.dlq",
            groupId = "message-service-dlq",
            containerFactory = "dlqKafkaListenerContainerFactory")
    public void listen(DlqEvent dlqEvent) {
        log.error("Mensagem descartada apos falha definitiva. messageId={} correlationId={} reason={}",
                dlqEvent.originalMessageId(), dlqEvent.correlationId(), dlqEvent.reason());

        MessageEvent original = dlqEvent.originalMessage();
        MessageEvent failedEvent = new MessageEvent(
                original.id(),
                original.roomId(),
                original.senderId(),
                original.senderUsername(),
                original.content(),
                MessageStatus.FAILED,
                original.sentAt(),
                original.correlationId()
        );

        messageEventKafkaTemplate.send(FAILED_TOPIC, failedEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("CRITICO: falha ao notificar remetente sobre mensagem descartada. messageId={} error={}",
                                original.id(), ex.getMessage());
                    }
                });
    }
}
