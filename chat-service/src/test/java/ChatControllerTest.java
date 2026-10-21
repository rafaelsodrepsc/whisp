import com.whisp.chat.config.StompPrincipal;
import com.whisp.chat.controller.ChatController;
import com.whisp.chat.dto.ChatMessage;
import com.whisp.chat.messaging.MessagePublisher;
import com.whisp.common.event.MessageEvent;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private MessagePublisher messagePublisher;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatController controller;

    private final StompPrincipal principal = new StompPrincipal("user-1", "alice");

    @BeforeEach
    void setUp() {
        controller = new ChatController(messagePublisher, messagingTemplate,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    private ChatMessage buildMessage(String roomId, String content) {
        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setContent(content);
        return message;
    }

    @Test
    void shouldPublishAndBroadcastValidMessage() {
        controller.sendMessage("room-1", buildMessage("room-1", "Olá!"), principal);

        verify(messagePublisher).publish(eq("chat.messages"), any(MessageEvent.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/room-1"), any(MessageEvent.class));
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> controller.sendMessage("room-1", buildMessage("room-1", "   "), principal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Mensagem inválida");

        verifyNoInteractions(messagePublisher, messagingTemplate);
    }

    @Test
    void shouldRejectContentAboveMaximumLength() {
        assertThatThrownBy(() -> controller.sendMessage("room-1", buildMessage("room-1", "x".repeat(2001)), principal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Mensagem inválida");

        verifyNoInteractions(messagePublisher, messagingTemplate);
    }

    @Test
    void shouldRejectRoomIdAboveMaximumLength() {
        assertThatThrownBy(() -> controller.sendMessage("r".repeat(65), buildMessage("r".repeat(65), "Olá!"), principal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Mensagem inválida");

        verifyNoInteractions(messagePublisher, messagingTemplate);
    }

    @Test
    void shouldRejectRoomIdWithUnexpectedCharacters() {
        assertThatThrownBy(() -> controller.sendMessage("room 1/../x", buildMessage("room 1/../x", "Olá!"), principal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Mensagem inválida");

        verifyNoInteractions(messagePublisher, messagingTemplate);
    }

    @Test
    void shouldRejectBodyRoomIdDifferentFromDestinationRoom() {
        assertThatThrownBy(() -> controller.sendMessage("room-1", buildMessage("room-2", "Olá!"), principal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Mensagem inválida");

        verifyNoInteractions(messagePublisher, messagingTemplate);
    }

    @Test
    void shouldRejectUnexpectedPrincipal() {
        assertThatThrownBy(() -> controller.sendMessage("room-1", buildMessage("room-1", "Olá!"), null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Unauthorized");

        verifyNoInteractions(messagePublisher, messagingTemplate);
    }
}
