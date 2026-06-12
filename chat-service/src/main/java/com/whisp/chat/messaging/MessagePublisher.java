package com.whisp.chat.messaging;

import com.whisp.common.event.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessagePublisher {

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    public MessagePublisher(KafkaTemplate<String, MessageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, MessageEvent message) {
        kafkaTemplate.send(topic, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("ERRO ao publicar no Kafka: {}", ex.getMessage());
                    } else {
                        log.info(">>> Publicado no Kafka offset: {}", result.getRecordMetadata().offset());
                    }
                });
    }

}
