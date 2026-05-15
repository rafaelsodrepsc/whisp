package com.whisp.common.event;

import java.time.Instant;

public record DlqEvent(
        String originalMessageId,
        String reason,
        MessageEvent originalMessage,
        Instant failedAt,
        String correlationId
) {}
