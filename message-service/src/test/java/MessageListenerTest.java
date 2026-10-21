import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import com.whisp.message.entity.Message;
import com.whisp.message.listener.MessageListener;
import com.whisp.message.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageListenerTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageListener messageListener;

    private MessageEvent buildEvent() {
        return new MessageEvent(
                "msg-123",
                "room-456",
                "user-789",
                "testuser",
                "Olá, mundo!",
                MessageStatus.SENT,
                Instant.now(),
                UUID.randomUUID().toString()
        );
    }

    @Test
    void shouldSaveMessageWhenEventIsReceived() {
        messageListener.listen(buildEvent());

        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void shouldIgnoreEventWhenMessageIdAlreadyExists() {
        when(messageRepository.existsById("msg-123")).thenReturn(true);

        messageListener.listen(buildEvent());

        verify(messageRepository, never()).save(any(Message.class));
    }

    // o retry e o envio ao DLQ são do DefaultErrorHandler do container, então o listener não pode engolir o erro
    @Test
    void shouldPropagateFailureSoTheErrorHandlerCanRetryAndDeadLetter() {
        doThrow(new RuntimeException("DB unavailable")).when(messageRepository).save(any());

        assertThatThrownBy(() -> messageListener.listen(buildEvent()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB unavailable");
    }
}
