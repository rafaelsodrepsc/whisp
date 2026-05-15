package com.whisp.message.listener;

import com.whisp.common.event.DlqEvent;
import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import com.whisp.message.entity.Message;
import com.whisp.message.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageListenerTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private KafkaTemplate<String, DlqEvent> dlqKafkaTemplate;

    @InjectMocks
    private MessageListener messageListener;

    private MessageEvent buildEvent() {
        return new MessageEvent(
                "msg-123",
                "room-456",
                "user-789",
                "Olá, mundo!",
                MessageStatus.SENT,
                Instant.now()
        );
    }

    @Test
    void shouldSaveMessageWhenEventIsReceived() {
        messageListener.listen(buildEvent());

        verify(messageRepository, times(1)).save(any(Message.class));
        verifyNoInteractions(dlqKafkaTemplate);
    }

    @Test
    void shouldSendToDlqWhenRepositoryFails() {
        doThrow(new RuntimeException("DB unavailable")).when(messageRepository).save(any());

        CompletableFuture<SendResult<String, DlqEvent>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(dlqKafkaTemplate.send(anyString(), any(DlqEvent.class))).thenReturn(future);

        messageListener.listen(buildEvent());

        verify(messageRepository, times(1)).save(any(Message.class));
        verify(dlqKafkaTemplate, times(1)).send(eq("chat.messages.dlq"), any(DlqEvent.class));
    }
}