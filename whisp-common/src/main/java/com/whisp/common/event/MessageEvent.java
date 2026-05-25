package com.whisp.common.event;

import java.time.Instant;

public record MessageEvent(
        String id,
        String roomId,
        String senderId,
        String senderUsername,
        String content,
        MessageStatus status,
        Instant sentAt,
        String correlationId
) {}