package com.whisp.chat.service;

import com.whisp.chat.domain.Room;
import com.whisp.chat.domain.RoomMember;
import com.whisp.chat.domain.RoomType;
import com.whisp.chat.dto.CreateRoomRequest;
import com.whisp.chat.dto.RoomResponse;
import com.whisp.chat.exception.AlreadyMemberException;
import com.whisp.chat.exception.RoomNotFoundException;
import com.whisp.chat.repository.RoomMemberRepository;
import com.whisp.chat.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @InjectMocks
    private RoomService roomService;

    private Room buildRoom(String id, String name, String userId) {
        Room room = Room.builder()
                .id(id)
                .name(name)
                .type(RoomType.GROUP)
                .createdBy(userId)
                .build();

        RoomMember member = RoomMember.builder()
                .id("member-1")
                .room(room)
                .userId(userId)
                .role("owner")
                .joinedAt(LocalDateTime.now())
                .build();

        room.getMembers().add(member);
        return room;
    }

    // --- create ---

    @Test
    void shouldCreateRoomAndAddCreatorAsMember() {
        CreateRoomRequest request = new CreateRoomRequest("Sala Geral");
        String userId = "user-123";

        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.create(request, userId);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());

        Room saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Sala Geral");
        assertThat(saved.getType()).isEqualTo(RoomType.GROUP);
        assertThat(saved.getCreatedBy()).isEqualTo(userId);
        assertThat(saved.getMembers()).hasSize(1);
        assertThat(saved.getMembers().get(0).getUserId()).isEqualTo(userId);
        assertThat(saved.getMembers().get(0).getRole()).isEqualTo("owner");

        assertThat(response.name()).isEqualTo("Sala Geral");
        assertThat(response.memberCount()).isEqualTo(1);
    }

    // --- listByUser ---

    @Test
    void shouldReturnRoomsForUser() {
        String userId = "user-123";
        Room room = buildRoom("room-1", "Sala Geral", userId);

        when(roomRepository.findAllByMemberUserId(userId)).thenReturn(List.of(room));

        List<RoomResponse> result = roomService.listByUser(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Sala Geral");
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoRooms() {
        when(roomRepository.findAllByMemberUserId("user-999")).thenReturn(List.of());

        List<RoomResponse> result = roomService.listByUser("user-999");

        assertThat(result).isEmpty();
    }

    // --- join ---

    @Test
    void shouldJoinRoomSuccessfully() {
        String roomId = "room-1";
        String userId = "user-456";
        Room room = buildRoom(roomId, "Sala Geral", "user-123");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

        roomService.join(roomId, userId);

        ArgumentCaptor<RoomMember> captor = ArgumentCaptor.forClass(RoomMember.class);
        verify(roomMemberRepository).save(captor.capture());

        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRole()).isEqualTo("member");
    }

    @Test
    void shouldThrowWhenRoomNotFound() {
        when(roomRepository.findById("invalid-room")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.join("invalid-room", "user-123"))
                .isInstanceOf(RoomNotFoundException.class);

        verify(roomMemberRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUserAlreadyMember() {
        String roomId = "room-1";
        String userId = "user-123";
        Room room = buildRoom(roomId, "Sala Geral", userId);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.join(roomId, userId))
                .isInstanceOf(AlreadyMemberException.class);

        verify(roomMemberRepository, never()).save(any());
    }
}