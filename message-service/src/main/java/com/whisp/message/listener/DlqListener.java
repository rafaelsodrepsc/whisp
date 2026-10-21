package com.whisp.message.listener;

import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
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
    // o DeadLetterPublishingRecoverer republica o próprio MessageEvent; o motivo vem nos headers
    public void listen(MessageEvent original,
                       @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("Mensagem descartada apos falha definitiva. messageId={} correlationId={} reason={}",
                original.id(), original.correlationId(), reason);

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
