import com.whisp.common.event.MessageStatus;
import com.whisp.message.dto.MessageResponse;
import com.whisp.message.entity.Message;
import com.whisp.message.repository.MessageRepository;
import com.whisp.message.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageService messageService;

    private Message buildMessage(String id, String roomId) {
        return new Message(id, roomId, "user-ID","username", "Olá!", MessageStatus.SENT, Instant.now());
    }

    @Test
    void shouldReturnPagedMessagesForRoom() {
        String roomId = "room-1";
        List<Message> messages = List.of(
                buildMessage("msg-1", roomId),
                buildMessage("msg-2", roomId)
        );
        Page<Message> page = new PageImpl<>(messages, PageRequest.of(0, 50), 2);

        when(messageRepository.findByRoomIdOrderBySentAtAsc(eq(roomId), any())).thenReturn(page);

        Page<MessageResponse> result = messageService.findByRoom(roomId, 0, 50);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).roomId()).isEqualTo(roomId);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyPageWhenRoomHasNoMessages() {
        String roomId = "room-vazio";
        Page<Message> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);

        when(messageRepository.findByRoomIdOrderBySentAtAsc(eq(roomId), any())).thenReturn(emptyPage);

        Page<MessageResponse> result = messageService.findByRoom(roomId, 0, 50);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void shouldMapMessageFieldsCorrectly() {
        String roomId = "room-1";
        Message message = new Message("msg-1", roomId, "user-ID","username", "Teste", MessageStatus.SENT, Instant.now());
        Page<Message> page = new PageImpl<>(List.of(message), PageRequest.of(0, 50), 1);

        when(messageRepository.findByRoomIdOrderBySentAtAsc(eq(roomId), any())).thenReturn(page);

        MessageResponse response = messageService.findByRoom(roomId, 0, 50).getContent().get(0);

        assertThat(response.id()).isEqualTo("msg-1");
        assertThat(response.senderId()).isEqualTo("user-456");
        assertThat(response.content()).isEqualTo("Teste");
        assertThat(response.status()).isEqualTo("SENT");
    }
}