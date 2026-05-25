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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional
    public RoomResponse create(CreateRoomRequest request, String userId) {
        Room room = Room.builder()
                .name(request.name())
                .type(RoomType.GROUP)
                .createdBy(userId)
                .build();

        RoomMember creator = RoomMember.builder()
                .room(room)
                .userId(userId)
                .role("owner")
                .build();

        room.getMembers().add(creator);
        roomRepository.save(room);

        return RoomResponse.from(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listByUser(String userId) {
        return roomRepository.findAllByMemberUserId(userId)
                .stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional
    public void join(String roomId, String userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new AlreadyMemberException(roomId);
        }

        RoomMember member = RoomMember.builder()
                .room(room)
                .userId(userId)
                .role("member")
                .build();

        roomMemberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listAll() {
        return roomRepository.findAll()
                .stream()
                .map(RoomResponse::from)
                .toList();
    }
}