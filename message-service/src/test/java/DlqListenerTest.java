import com.whisp.common.event.MessageEvent;
import com.whisp.common.event.MessageStatus;
import com.whisp.message.listener.DlqListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqListenerTest {

    @Mock
    private KafkaTemplate<String, MessageEvent> messageEventKafkaTemplate;

    @InjectMocks
    private DlqListener dlqListener;

    @Test
    void shouldPublishFailedEventForTheOriginalMessage() {
        MessageEvent original = new MessageEvent("msg-1", "room-1", "user-1", "alice", "Olá",
                MessageStatus.SENT, Instant.now(), "corr-1");
        when(messageEventKafkaTemplate.send(eq("chat.messages.failed"), any(MessageEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        dlqListener.listen(original, "value too long for type character varying(255)");

        ArgumentCaptor<MessageEvent> captor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(messageEventKafkaTemplate).send(eq("chat.messages.failed"), captor.capture());
        MessageEvent failed = captor.getValue();
        assertThat(failed.status()).isEqualTo(MessageStatus.FAILED);
        assertThat(failed.id()).isEqualTo("msg-1");
        assertThat(failed.roomId()).isEqualTo("room-1");
        assertThat(failed.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void shouldPublishFailedEventEvenWithoutExceptionHeader() {
        MessageEvent original = new MessageEvent("msg-2", "room-2", "user-2", "bob", "Oi",
                MessageStatus.SENT, Instant.now(), "corr-2");
        when(messageEventKafkaTemplate.send(eq("chat.messages.failed"), any(MessageEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        dlqListener.listen(original, null);

        verify(messageEventKafkaTemplate).send(eq("chat.messages.failed"), any(MessageEvent.class));
    }
}
