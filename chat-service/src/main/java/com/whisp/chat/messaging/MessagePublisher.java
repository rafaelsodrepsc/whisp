package com.whisp.chat.messaging;

import com.whisp.common.event.MessageEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessagePublisher {

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    public MessagePublisher(KafkaTemplate<String, MessageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, MessageEvent message){
        kafkaTemplate.send(topic, message);
    }

}
