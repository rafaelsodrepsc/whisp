package com.whisp.chat.dto;

import com.whisp.chat.domain.Room;

import java.time.LocalDateTime;

public record RoomResponse(
        String id,
        String name,
        String type,
        String createdBy,
        LocalDateTime createdAt,
        int memberCount
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getType().name(),
                room.getCreatedBy(),
                room.getCreatedAt(),
                room.getMembers().size()
        );
    }
}