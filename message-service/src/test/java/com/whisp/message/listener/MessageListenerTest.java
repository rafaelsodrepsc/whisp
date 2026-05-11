package com.whisp.message.listener;

import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import com.whisp.message.entity.Message;
import com.whisp.message.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do MessageListener.
 *
 * Sem Kafka real - o listener é instanciado diretamente e o método listen()
 * é chamado com um MessageEvent construído manualmente.
 * Valida apenas a lógica de persistência e tratamento de erro.
 */
@ExtendWith(MockitoExtension.class)
class MessageListenerTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageListener messageListener;

    @Test
    void shouldSaveMessageWhenEventIsReceived() {
        MessageEvent event = new MessageEvent(
                "msg-123",
                "room-456",
                "user-789",
                "Olá, mundo!",
                MessageStatus.SENT,
                Instant.now()
        );

        messageListener.listen(event);

        // Valida que o repositório foi chamado uma vez com qualquer Message
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void shouldNotPropagateExceptionWhenRepositoryFails() {
        MessageEvent event = new MessageEvent(
                "msg-123",
                "room-456",
                "user-789",
                "Olá, mundo!",
                MessageStatus.SENT,
                Instant.now()
        );

        // Simula falha no banco - o listener deve absorver o erro e logar,
        // nunca propagar para o Kafka (evita reprocessamento infinito)
        doThrow(new RuntimeException("DB unavailable")).when(messageRepository).save(any());

        messageListener.listen(event);

        verify(messageRepository, times(1)).save(any(Message.class));
    }
}