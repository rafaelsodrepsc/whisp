package com.whisp.message.dto;

import com.whisp.message.entity.Message;

import java.time.Instant;

public record MessageResponse(
        String id,
        String roomId,
        String senderId,
        String senderUsername,
        String content,
        String status,
        Instant sentAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                message.getSenderUsername(),
                message.getContent(),
                message.getStatus().name(),
                message.getSentAt()
        );
    }
}