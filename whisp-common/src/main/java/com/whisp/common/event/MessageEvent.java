package com.whisp.common.event;

import java.time.Instant;

public record MessageEvent(
        String id,
        String roomId,
        String senderId,
        String content,
        MessageStatus status,
        Instant sentAt
) {}